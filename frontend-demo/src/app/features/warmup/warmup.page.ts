import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Observable, defaultIfEmpty, forkJoin, map, retry, take, takeUntil, tap, timeout, timer } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ServiceKey } from '../../../environments/environment.model';
import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';

/** If all four answer this fast, the screen never shows. */
export const SHOW_AFTER_MS = 1_500;
/** A service still silent after this long is "Sin respuesta". */
export const UNRESPONSIVE_AFTER_MS = 180_000;
const ATTEMPT_TIMEOUT_MS = 60_000;
const RETRY_DELAY_MS = 3_000;

type Status = 'waking' | 'ready' | 'unresponsive';

interface Row {
  readonly key: ServiceKey;
  readonly label: string;
  readonly service: string;
  readonly status: Status;
  readonly startedAt: number;
  readonly readyAfterMs: number | null;
}

const SERVICES: readonly Pick<Row, 'key' | 'label' | 'service'>[] = [
  { key: 'auth', label: 'Autenticación', service: 'auth-service' },
  { key: 'recycler', label: 'Asociaciones y recicladores', service: 'recycler-service' },
  { key: 'collection', label: 'Recojos y vecinos', service: 'collection-service' },
  { key: 'reporting', label: 'Reportes y certificados', service: 'reporting-service' },
];

const STATUS_TEXT: Record<Status, string> = {
  waking: 'Despertando',
  ready: 'Listo',
  unresponsive: 'Sin respuesta',
};

/**
 * Entry screen. Render's free services sleep after 15 min and take up to
 * ~2 min to wake, so the app first pings every service's public liveness
 * (forkJoin of the four). Any failure, network or CORS included, counts as
 * "still waking" until UNRESPONSIVE_AFTER_MS; only then does Retry appear.
 */
@Component({
  selector: 'app-warmup-page',
  templateUrl: './warmup.page.html',
  styleUrl: './warmup.page.css',
})
export class WarmupPage {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly rows = signal<readonly Row[]>(
    SERVICES.map((s) => ({ ...s, status: 'waking', startedAt: Date.now(), readyAfterMs: null })),
  );
  protected readonly visible = signal(false);
  private readonly now = signal(Date.now());
  private ticker: ReturnType<typeof setInterval> | null = null;

  protected readonly readyCount = computed(() => this.rows().filter((r) => r.status === 'ready').length);
  protected readonly anyUnresponsive = computed(() => this.rows().some((r) => r.status === 'unresponsive'));
  /** Changes only when a service changes state, so a screen reader hears it a handful of times, never every second. */
  protected readonly summary = computed(() => {
    const ready = this.readyCount();
    if (this.anyUnresponsive()) {
      return `${ready} de 4 servicios listos. Algún servicio no respondió.`;
    }
    return `${ready} de 4 servicios listos.`;
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.stopTicker());
    this.run(SERVICES.map((s) => s.key));
    timer(SHOW_AFTER_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.readyCount() < 4) {
          this.visible.set(true);
          this.startTicker();
        }
      });
  }

  protected statusText(status: Status): string {
    return STATUS_TEXT[status];
  }

  protected elapsed(row: Row): string {
    const ms = row.readyAfterMs ?? Math.min(this.now() - row.startedAt, UNRESPONSIVE_AFTER_MS);
    const seconds = Math.max(0, Math.floor(ms / 1000));
    return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
  }

  protected retry(): void {
    const failed = this.rows()
      .filter((r) => r.status === 'unresponsive')
      .map((r) => r.key);
    this.startTicker();
    this.run(failed);
  }

  private run(keys: readonly ServiceKey[]): void {
    const startedAt = Date.now();
    this.update(keys, () => ({ status: 'waking', startedAt, readyAfterMs: null }));
    forkJoin(keys.map((key) => this.probe(key, startedAt)))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.readyCount() === 4) {
          this.stopTicker();
          void this.router.navigate([this.session.isAuthenticated() ? '/empresas' : '/login'], { replaceUrl: true });
        }
      });
  }

  /** Emits once: true when the service answered 200, false at the deadline. */
  private probe(key: ServiceKey, startedAt: number): Observable<boolean> {
    return this.api.liveness(environment.services[key]).pipe(
      timeout(ATTEMPT_TIMEOUT_MS),
      retry({ delay: RETRY_DELAY_MS }),
      map(() => true),
      take(1),
      takeUntil(timer(UNRESPONSIVE_AFTER_MS)),
      defaultIfEmpty(false),
      tap((ok) =>
        this.update([key], () =>
          ok ? { status: 'ready', readyAfterMs: Date.now() - startedAt } : { status: 'unresponsive' },
        ),
      ),
    );
  }

  private update(keys: readonly ServiceKey[], change: () => Partial<Row>): void {
    this.rows.update((rows) => rows.map((row) => (keys.includes(row.key) ? { ...row, ...change() } : row)));
  }

  private startTicker(): void {
    if (this.ticker !== null) {
      return;
    }
    this.now.set(Date.now());
    this.ticker = setInterval(() => {
      this.now.set(Date.now());
      if (!this.rows().some((r) => r.status === 'waking')) {
        this.stopTicker();
      }
    }, 1000);
  }

  private stopTicker(): void {
    if (this.ticker !== null) {
      clearInterval(this.ticker);
      this.ticker = null;
    }
  }
}

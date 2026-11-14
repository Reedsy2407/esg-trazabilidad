import { Component, DestroyRef, ElementRef, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { RetentionRunResult } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { formatInstantDate, formatInstantTime, formatKg, limaDayEnd, limaDayStart } from '../../shared/format';

const RETENTION_UNCERTAIN =
  'No pudimos confirmar si la retención terminó. Es seguro volver a ejecutarla: aplicarla dos veces no cambia nada más.';

/**
 * A recycler's workdays (spec R28, CA-38) and, for an ADMIN, the on-demand retention (CA-39, CA-40). The date filter is in
 * Lima calendar days; the service filters by the instant the workday started. The retention deletes data, so it is
 * confirmed first and its answer is shown with the counts the service returned.
 */
@Component({
  selector: 'app-recycler-workdays-page',
  imports: [RouterLink],
  templateUrl: './recycler-workdays.page.html',
  styleUrl: './recycler-workdays.page.css',
})
export class RecyclerWorkdaysPage {
  private readonly api = inject(BackendApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly isAdmin = inject(SessionService).isAdmin;

  readonly associationId = input.required<string>();
  readonly recyclerId = input.required<string>();

  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly page = signal(0);

  protected readonly recycler = rxResource({
    params: () => ({ a: this.associationId(), r: this.recyclerId() }),
    stream: ({ params }) => this.api.recycler(params.a, params.r),
  });

  protected readonly workdays = rxResource({
    params: () => ({ r: this.recyclerId(), page: this.page(), from: this.from(), to: this.to() }),
    stream: ({ params }) =>
      this.api.recyclerWorkdays(
        params.r,
        params.page,
        params.from === '' ? null : limaDayStart(params.from),
        params.to === '' ? null : limaDayEnd(params.to),
      ),
  });

  protected readonly filtered = computed(() => this.from() !== '' || this.to() !== '');
  protected readonly rangeInvalid = computed(() => this.from() !== '' && this.to() !== '' && this.from() > this.to());

  protected readonly announcement = computed(() => {
    if (this.workdays.isLoading()) {
      return 'Cargando jornadas';
    }
    if (!this.workdays.hasValue()) {
      return '';
    }
    const total = this.workdays.value().totalElements;
    return total === 0 ? 'Ninguna jornada coincide.' : `${total} ${total === 1 ? 'jornada' : 'jornadas'}.`;
  });

  protected setFrom(value: string): void {
    this.from.set(value);
    this.page.set(0);
  }

  protected setTo(value: string): void {
    this.to.set(value);
    this.page.set(0);
  }

  protected clearFilter(): void {
    this.from.set('');
    this.to.set('');
    this.page.set(0);
    this.focus('#from');
  }

  protected previousPage(): void {
    this.page.update((p) => Math.max(0, p - 1));
  }

  protected nextPage(): void {
    this.page.update((p) => p + 1);
  }

  protected loadError(error: unknown, what: string): string {
    return loadErrorMessage(error, what, 'el servicio de recojos');
  }

  protected date(instant: string): string {
    return formatInstantDate(instant);
  }

  protected hours(start: string, end: string | null): string {
    return end === null ? `${formatInstantTime(start)} en curso` : `${formatInstantTime(start)}–${formatInstantTime(end)}`;
  }

  protected kg(value: number): string {
    return formatKg(value);
  }

  // --- retention (ADMIN) ---------------------------------------------------------------------

  protected readonly confirmingRetention = signal(false);
  protected readonly retentionBusy = signal(false);
  protected readonly retentionError = signal<string | null>(null);
  protected readonly retentionResult = signal<RetentionRunResult | null>(null);

  protected askRetention(): void {
    this.retentionError.set(null);
    this.retentionResult.set(null);
    this.confirmingRetention.set(true);
    this.focus('[data-confirm]');
  }

  protected cancelRetention(): void {
    this.confirmingRetention.set(false);
    this.focus('[data-retention]');
  }

  protected runRetention(): void {
    if (this.retentionBusy()) {
      return;
    }
    this.retentionBusy.set(true);
    this.retentionError.set(null);
    this.api
      .runRetention()
      .pipe(
        finalize(() => this.retentionBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.confirmingRetention.set(false);
          this.retentionResult.set(result);
          this.workdays.reload();
          this.focus('#retention-result');
        },
        error: (error: unknown) =>
          this.retentionError.set(
            writeErrorText(error, RETENTION_UNCERTAIN, { 'AUTH-000': 'Solo un administrador puede ejecutar la retención.' }),
          ),
      });
  }

  private focus(selector: string): void {
    setTimeout(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus());
  }
}

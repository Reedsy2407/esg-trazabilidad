import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Subject, catchError, forkJoin, of, retry, take, takeUntil, timeout, timer } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ServiceKey } from '../../../environments/environment.model';

import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { ApiError } from '../../core/http/api-error';
import { isUnavailable, unavailableText } from '../../shared/error-message';
import { Logo } from '../../shared/logo';
import { DEMO_ACCOUNT } from './demo-account';

/** Used when a 429 arrives without a readable Retry-After (the limiter refills one attempt every 12 s). */
const FALLBACK_LOCK_SECONDS = 60;
/** The services answer this fast when they are awake: only a slower wake-up gets a notice. */
export const WAKE_NOTICE_AFTER_MS = 1_500;
const WAKE_ATTEMPT_TIMEOUT_MS = 60_000;
const WAKE_RETRY_DELAY_MS = 3_000;
const WAKE_RETRIES = 20;
const SERVICE_KEYS: readonly ServiceKey[] = ['auth', 'recycler', 'collection', 'reporting'];

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, Logo],
  templateUrl: './login.page.html',
  styleUrl: './login.page.css',
})
export class LoginPage {
  private readonly api = inject(BackendApi);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  /** ?motivo=sesion, set by errorInterceptor when a token stops being valid. */
  readonly motivo = input<string | undefined>();

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  private readonly passwordInput = viewChild<ElementRef<HTMLInputElement>>('passwordInput');

  protected readonly submitting = signal(false);
  /** True while a sign-in started by the demonstration button is in flight (it names the account in the live region). */
  protected readonly demoSubmitting = signal(false);
  protected readonly submitted = signal(false);
  /** Form-level error, announced by role="alert" when it appears. */
  protected readonly error = signal<string | null>(null);

  private readonly lockedUntil = signal<number | null>(null);
  private readonly now = signal(Date.now());
  /**
   * Set once when the lock starts, inside a polite live region, so a screen
   * reader hears it once. The ticking countdown sits outside that region.
   */
  protected readonly lockAnnouncement = signal<string | null>(null);
  protected readonly lockSecondsLeft = computed(() => {
    const until = this.lockedUntil();
    return until === null ? 0 : Math.max(0, Math.ceil((until - this.now()) / 1000));
  });
  protected readonly locked = computed(() => this.lockSecondsLeft() > 0);

  private ticker: ReturnType<typeof setInterval> | null = null;

  /**
   * True while the four services have not all answered yet. Render's free services sleep after 15
   * minutes, and landing straight on this page (a bookmark, a logout) skips the entry screen that
   * wakes them, so the first login would otherwise sit silent for up to two minutes.
   */
  protected readonly waking = signal(false);

  constructor() {
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => this.stopTicker());
    this.wakeServices(destroyRef);
  }

  /** Background liveness pings (nothing blocks: the form works at once); a notice only if it is slow. */
  private wakeServices(destroyRef: DestroyRef): void {
    const done = new Subject<void>();
    timer(WAKE_NOTICE_AFTER_MS)
      .pipe(takeUntil(done), takeUntilDestroyed(destroyRef))
      .subscribe(() => this.waking.set(true));
    forkJoin(
      SERVICE_KEYS.map((key) =>
        this.api.liveness(environment.services[key]).pipe(
          timeout(WAKE_ATTEMPT_TIMEOUT_MS),
          retry({ count: WAKE_RETRIES, delay: WAKE_RETRY_DELAY_MS }),
          take(1),
          catchError(() => of(null)),
        ),
      ),
    )
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => {
        done.next();
        this.waking.set(false);
      });
  }

  protected fieldInvalid(name: 'email' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  /** Fills the form with the public demonstration account and sends it, exactly as if it had been typed. */
  protected useDemoAccount(): void {
    if (this.submitting() || this.locked()) {
      return;
    }
    this.form.setValue({ email: DEMO_ACCOUNT.email, password: DEMO_ACCOUNT.password });
    this.demoSubmitting.set(true);
    this.submit();
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.locked() || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    this.api.login(this.form.getRawValue()).subscribe({
      next: ({ accessToken }) => {
        this.session.start(accessToken);
        void this.router.navigate(['/empresas']);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.demoSubmitting.set(false);
        this.handleError(error);
      },
    });
  }

  private handleError(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.error.set('No se pudo iniciar sesión. Inténtalo de nuevo.');
      return;
    }
    switch (error.code) {
      case 'AUTH-001':
        this.error.set('El correo o la contraseña no son correctos.');
        // A fresh, untouched password field: only the credentials message shows, not also
        // "Escribe tu contraseña.". The next submit validates as usual.
        this.submitted.set(false);
        this.form.controls.password.reset();
        setTimeout(() => this.passwordInput()?.nativeElement.focus());
        return;
      case 'AUTH-004':
        this.startLock(error.retryAfterSeconds ?? FALLBACK_LOCK_SECONDS);
        return;
      default:
        this.error.set(
          error.status === 0
            ? 'No se pudo conectar con el servicio de autenticación. Comprueba tu conexión e inténtalo de nuevo.'
            : isUnavailable(error)
              ? unavailableText('el servicio de autenticación')
              : 'No se pudo iniciar sesión. Inténtalo de nuevo en un momento.',
        );
    }
  }

  private startLock(seconds: number): void {
    this.now.set(Date.now());
    this.lockedUntil.set(Date.now() + seconds * 1000);
    this.lockAnnouncement.set(
      `Demasiados intentos. Podrás volver a intentarlo en ${seconds} ${seconds === 1 ? 'segundo' : 'segundos'}.`,
    );
    this.stopTicker();
    this.ticker = setInterval(() => {
      this.now.set(Date.now());
      if (!this.locked()) {
        this.stopTicker();
        this.lockedUntil.set(null);
        this.lockAnnouncement.set(null);
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

import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { ApiError } from '../../core/http/api-error';

/** Used when a 429 arrives without a readable Retry-After (the limiter refills one attempt every 12 s). */
const FALLBACK_LOCK_SECONDS = 60;

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
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

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopTicker());
  }

  protected fieldInvalid(name: 'email' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
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
            : `No se pudo iniciar sesión (${error.code ?? `HTTP ${error.status}`}). Inténtalo de nuevo.`,
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

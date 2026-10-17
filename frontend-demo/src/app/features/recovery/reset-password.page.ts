import { AfterViewInit, Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { writeErrorText } from '../../shared/error-message';
import { PASSWORD_RULES_TEXT, passwordProblem, passwordProblemText } from '../staff/password-rules';
import {
  CONFIRM_UNCERTAIN,
  INVALID_CODE_MESSAGE,
  MAX_CODE_FAILURES,
  REQUEST_UNCERTAIN,
  RESENT_MESSAGE,
  RESEND_SECONDS,
  SENT_MESSAGE,
  TOO_MANY_FAILURES_MESSAGE,
  codeDigits,
  isCodeComplete,
  isRateLimited,
  readRecoveryState,
  waitText,
} from './recovery';

/** The same rule as the backend's StaffUser.isValidEmail: stricter than Angular's, which accepts "a@b". */
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/**
 * Paso 2: the code from the email and the new password, in one request. Nothing the person types here
 * is stored, logged or put in a URL; the form is emptied on success and the session is not started: they
 * go to the login with the new password. The same message answers every code that does not work; after
 * five of them the person is sent back to ask for a new one (the service spends the code at five too).
 */
@Component({
  selector: 'app-reset-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.page.html',
  styleUrl: './recovery.css',
})
export class ResetPasswordPage implements AfterViewInit {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  private readonly previous = readRecoveryState(this.router);
  protected readonly rules = PASSWORD_RULES_TEXT;

  protected readonly form = this.fb.group({
    email: [this.previous.email ?? ''],
    code: [''],
    next: [''],
    confirm: [''],
  });

  protected readonly show = signal(false);
  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly resending = signal(false);
  protected readonly error = signal<string | null>(null);
  /** What the last request for a code told the person (always the same, whether or not the account exists). */
  protected readonly info = signal<string>(this.previous.email === undefined ? '' : SENT_MESSAGE);
  /** The service said the code does not work (AUTH-011): shown under the code field. */
  protected readonly codeRefused = signal(false);
  /** The service refused the new password (AUTH-007). */
  protected readonly nextRefused = signal(false);
  /** Set once, when the wait ends, inside the polite live region. */
  protected readonly announcement = signal('');

  private failures = 0;
  private readonly sentAt = signal<number | null>(this.previous.sentAt ?? null);
  private readonly now = signal(Date.now());
  protected readonly secondsLeft = computed(() => {
    const at = this.sentAt();
    return at === null ? 0 : Math.max(0, RESEND_SECONDS - Math.floor((this.now() - at) / 1000));
  });
  protected readonly waiting = computed(() => this.secondsLeft() > 0);
  private ticker: ReturnType<typeof setInterval> | null = null;

  private readonly formValue = signal(0);
  protected readonly problems = computed(() => {
    // Read the controls through the form's own change signal so the messages follow every keystroke.
    this.formValue();
    const v = this.form.getRawValue();
    const next = passwordProblem(v.next);
    return {
      email: v.email.trim() === '' ? 'Escribe tu correo electrónico.' : EMAIL_PATTERN.test(v.email.trim()) ? null : 'Escribe un correo válido, por ejemplo nombre@asociacion.pe.',
      code: isCodeComplete(v.code) ? null : 'Escribe el código de 8 dígitos que te enviamos.',
      next: next !== null ? passwordProblemText(next) : null,
      confirm: v.confirm === '' ? 'Repite la contraseña nueva.' : v.confirm !== v.next ? 'Las dos contraseñas no coinciden.' : null,
    };
  });

  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('formElement');
  private readonly title = viewChild<ElementRef<HTMLElement>>('title');

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.formValue.update((n) => n + 1);
      this.error.set(null);
      this.codeRefused.set(false);
      this.nextRefused.set(false);
    });
    this.destroyRef.onDestroy(() => this.stopTicker());
    if (this.waiting()) {
      this.startTicker();
    }
  }

  ngAfterViewInit(): void {
    this.title()?.nativeElement.focus();
  }

  protected shown(field: 'email' | 'code' | 'next' | 'confirm'): string | null {
    return this.submitted() ? this.problems()[field] : null;
  }

  private focusFirstProblem(): void {
    setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.sending()) {
      return;
    }
    const p = this.problems();
    if (p.email !== null || p.code !== null || p.next !== null || p.confirm !== null) {
      this.focusFirstProblem();
      return;
    }
    const v = this.form.getRawValue();
    this.error.set(null);
    this.sending.set(true);
    this.api
      .confirmPasswordReset({ email: v.email.trim(), code: codeDigits(v.code), newPassword: v.next })
      .pipe(
        finalize(() => this.sending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.changed(),
        error: (error: unknown) => this.refused(error),
      });
  }

  private changed(): void {
    this.form.reset({ email: '', code: '', next: '', confirm: '' });
    this.show.set(false);
    void this.router.navigate(['/login'], { queryParams: { motivo: 'clave' }, replaceUrl: true });
  }

  private refused(error: unknown): void {
    const code = error instanceof ApiError ? error.code : null;
    if (code === 'AUTH-011') {
      this.failures += 1;
      if (this.failures >= MAX_CODE_FAILURES) {
        void this.router.navigate(['/olvide-contrasena'], {
          state: { email: this.form.controls.email.value.trim(), notice: TOO_MANY_FAILURES_MESSAGE },
        });
        return;
      }
      this.codeRefused.set(true);
      this.focusFirstProblem();
      return;
    }
    if (code === 'AUTH-007') {
      this.nextRefused.set(true);
      this.focusFirstProblem();
      return;
    }
    if (isRateLimited(error)) {
      this.error.set(waitText(error.retryAfterSeconds));
      return;
    }
    this.error.set(writeErrorText(error, CONFIRM_UNCERTAIN));
  }

  protected resend(): void {
    const email = this.form.controls.email.value.trim();
    if (this.waiting() || this.resending() || this.problems().email !== null) {
      this.submitted.set(true);
      this.focusFirstProblem();
      return;
    }
    this.error.set(null);
    this.resending.set(true);
    this.api
      .requestPasswordReset(email)
      .pipe(
        finalize(() => this.resending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.failures = 0;
          this.form.controls.code.reset('');
          this.info.set(RESENT_MESSAGE);
          this.sentAt.set(Date.now());
          this.now.set(Date.now());
          this.announcement.set('');
          this.startTicker();
        },
        error: (error: unknown) =>
          this.error.set(isRateLimited(error) ? waitText(error.retryAfterSeconds) : REQUEST_UNCERTAIN),
      });
  }

  protected setShow(event: Event): void {
    this.show.set((event.target as HTMLInputElement).checked);
  }

  private startTicker(): void {
    this.stopTicker();
    this.ticker = setInterval(() => {
      this.now.set(Date.now());
      if (!this.waiting()) {
        this.stopTicker();
        // Announced once, at the end: the ticking number itself is outside the live region.
        this.announcement.set('Ya puedes pedir un código nuevo.');
      }
    }, 1000);
  }

  private stopTicker(): void {
    if (this.ticker !== null) {
      clearInterval(this.ticker);
      this.ticker = null;
    }
  }

  protected readonly invalidCodeText = INVALID_CODE_MESSAGE;
}

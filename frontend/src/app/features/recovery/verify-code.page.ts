import { AfterViewInit, Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { writeErrorText } from '../../shared/error-message';
import { RecoverySession } from './recovery-session';
import { Logo } from '../../shared/logo';
import {
  INVALID_CODE_MESSAGE,
  MAX_CODE_FAILURES,
  REQUEST_UNCERTAIN,
  RESENT_MESSAGE,
  RESEND_SECONDS,
  SENT_MESSAGE,
  TOO_MANY_FAILURES_MESSAGE,
  VERIFY_UNCERTAIN,
  codeDigits,
  isCodeComplete,
  isRateLimited,
  readRecoveryState,
  waitText,
} from './recovery';

/**
 * Paso 2 de «¿Olvidaste tu contraseña?»: the code from the email. The service checks it before anything
 * else is asked: a right code is spent and trades for a reset token that stays in memory (RecoverySession)
 * while the person goes on to choose the new password. The email is shown as fixed text (it came from step
 * 1) with a link to change it. The same message answers every code that does not work; after five of them
 * the person is sent back to ask for a new one (the service spends the code at five too).
 */
@Component({
  selector: 'app-verify-code-page',
  imports: [ReactiveFormsModule, RouterLink, Logo],
  templateUrl: './verify-code.page.html',
  styleUrl: './recovery.css',
})
export class VerifyCodePage implements AfterViewInit {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly session = inject(RecoverySession);

  private readonly previous = readRecoveryState(this.router);
  /** From step 1; without it (a typed address) the person is sent to step 1. */
  protected readonly email = this.previous.email ?? '';
  protected readonly emailState = { email: this.email };

  protected readonly form = this.fb.group({ code: [''] });

  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly resending = signal(false);
  protected readonly error = signal<string | null>(null);
  /** What the last step told the person (always the same, whether or not the account exists). */
  protected readonly info = signal<string>(this.previous.notice ?? (this.email === '' ? '' : SENT_MESSAGE));
  /** The service said the code does not work (AUTH-011): shown under the code field. */
  protected readonly codeRefused = signal(false);
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

  private readonly codeValue = signal('');
  protected readonly codeProblem = computed(() =>
    isCodeComplete(this.codeValue()) ? null : 'Escribe el código de 8 dígitos que te enviamos.',
  );

  private readonly title = viewChild<ElementRef<HTMLElement>>('title');
  private readonly codeInput = viewChild<ElementRef<HTMLInputElement>>('codeInput');

  constructor() {
    if (this.email === '') {
      void this.router.navigate(['/olvide-contrasena'], { replaceUrl: true });
    }
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => {
      this.codeValue.set(value.code ?? '');
      this.error.set(null);
      this.codeRefused.set(false);
    });
    this.destroyRef.onDestroy(() => this.stopTicker());
    if (this.waiting()) {
      this.startTicker();
    }
  }

  ngAfterViewInit(): void {
    this.title()?.nativeElement.focus();
  }

  protected showError(): string | null {
    return this.submitted() ? this.codeProblem() : null;
  }

  private focusCode(): void {
    setTimeout(() => this.codeInput()?.nativeElement.focus());
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.sending()) {
      return;
    }
    if (this.codeProblem() !== null) {
      this.focusCode();
      return;
    }
    this.error.set(null);
    this.sending.set(true);
    this.api
      .verifyPasswordReset({ email: this.email, code: codeDigits(this.form.controls.code.value) })
      .pipe(
        finalize(() => this.sending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ resetToken }) => this.verified(resetToken),
        error: (error: unknown) => this.refused(error),
      });
  }

  private verified(resetToken: string): void {
    this.session.hold(resetToken);
    this.form.reset({ code: '' });
    void this.router.navigate(['/nueva-contrasena'], { replaceUrl: true, state: { email: this.email } });
  }

  private refused(error: unknown): void {
    const code = error instanceof ApiError ? error.code : null;
    if (code === 'AUTH-011') {
      this.failures += 1;
      if (this.failures >= MAX_CODE_FAILURES) {
        void this.router.navigate(['/olvide-contrasena'], {
          state: { email: this.email, notice: TOO_MANY_FAILURES_MESSAGE },
        });
        return;
      }
      this.codeRefused.set(true);
      this.focusCode();
      return;
    }
    if (isRateLimited(error)) {
      this.error.set(waitText(error.retryAfterSeconds));
      return;
    }
    this.error.set(writeErrorText(error, VERIFY_UNCERTAIN));
  }

  protected resend(): void {
    if (this.waiting() || this.resending()) {
      return;
    }
    this.error.set(null);
    this.resending.set(true);
    this.api
      .requestPasswordReset(this.email)
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

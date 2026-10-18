import { AfterViewInit, Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { writeErrorText } from '../../shared/error-message';
import { PASSWORD_RULES_TEXT, passwordProblem, passwordProblemText } from '../staff/password-rules';
import { RecoverySession } from './recovery-session';
import { CONFIRM_UNCERTAIN, EXPIRED_MESSAGE, isRateLimited, readRecoveryState, waitText } from './recovery';
import { Logo } from '../../shared/logo';

/**
 * Paso 3: the new password, sent with the reset token that step 2 earned (kept in memory by RecoverySession,
 * never in the URL or in storage). Nothing the person types here is stored or logged; the form is emptied on
 * success and the session is not started: they go to the login with the new password. A password the service
 * refuses does not spend the token, so the person can type another. When the token no longer works (it lasts 10
 * minutes and is used once) they go back to step 2 with a clear message.
 */
@Component({
  selector: 'app-new-password-page',
  imports: [ReactiveFormsModule, RouterLink, Logo],
  templateUrl: './new-password.page.html',
  styleUrl: './recovery.css',
})
export class NewPasswordPage implements AfterViewInit {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly session = inject(RecoverySession);

  /** The email travels only so a step back to the code screen can show it; the token proves the rest. */
  private readonly email = readRecoveryState(this.router).email ?? '';
  protected readonly rules = PASSWORD_RULES_TEXT;

  protected readonly form = this.fb.group({ next: [''], confirm: [''] });

  protected readonly show = signal(false);
  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The service refused the new password (AUTH-007). */
  protected readonly nextRefused = signal(false);

  private readonly formValue = signal(0);
  protected readonly problems = computed(() => {
    // Read the controls through the form's own change signal so the messages follow every keystroke.
    this.formValue();
    const v = this.form.getRawValue();
    const next = passwordProblem(v.next);
    return {
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
      this.nextRefused.set(false);
    });
    this.destroyRef.onDestroy(() => this.session.clear());
  }

  ngAfterViewInit(): void {
    this.title()?.nativeElement.focus();
  }

  protected shown(field: 'next' | 'confirm'): string | null {
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
    if (p.next !== null || p.confirm !== null) {
      this.focusFirstProblem();
      return;
    }
    const token = this.session.current();
    if (token === null) {
      this.expired();
      return;
    }
    this.error.set(null);
    this.sending.set(true);
    this.api
      .confirmPasswordReset({ resetToken: token, newPassword: this.form.controls.next.value })
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
    this.session.clear();
    this.form.reset({ next: '', confirm: '' });
    this.show.set(false);
    void this.router.navigate(['/login'], { queryParams: { motivo: 'clave' }, replaceUrl: true });
  }

  private expired(): void {
    this.session.clear();
    void this.router.navigate(['/restablecer-contrasena'], {
      replaceUrl: true,
      state: { email: this.email, notice: EXPIRED_MESSAGE },
    });
  }

  private refused(error: unknown): void {
    const code = error instanceof ApiError ? error.code : null;
    if (code === 'AUTH-011') {
      this.expired();
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

  protected setShow(event: Event): void {
    this.show.set((event.target as HTMLInputElement).checked);
  }
}

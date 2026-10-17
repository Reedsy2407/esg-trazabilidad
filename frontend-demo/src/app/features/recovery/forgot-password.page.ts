import { AfterViewInit, Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { REQUEST_UNCERTAIN, isRateLimited, readRecoveryState, waitText } from './recovery';

/**
 * Paso 1 de «¿Olvidaste tu contraseña?»: the email, and the service sends a code to it if the account
 * exists. The answer is always the same, so this screen never says whether the account exists; it only
 * carries the person to step 2 (the email travels in the browser's history state, never in the URL).
 */
@Component({
  selector: 'app-forgot-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.page.html',
  styleUrl: './recovery.css',
})
export class ForgotPasswordPage implements AfterViewInit {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  private readonly previous = readRecoveryState(this.router);
  /** Set when step 2 sent the person back (too many wrong codes). */
  protected readonly notice: string | null = this.previous.notice ?? null;

  protected readonly form = this.fb.group({
    email: [this.previous.email ?? '', [Validators.required, Validators.email]],
  });

  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly title = viewChild<ElementRef<HTMLElement>>('title');
  private readonly emailInput = viewChild<ElementRef<HTMLInputElement>>('emailInput');

  ngAfterViewInit(): void {
    // Each step starts at its title, so a screen reader reads where it is.
    this.title()?.nativeElement.focus();
  }

  protected emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.touched || this.submitted());
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.sending()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.emailInput()?.nativeElement.focus();
      return;
    }
    this.error.set(null);
    this.sending.set(true);
    const email = this.form.controls.email.value.trim();
    this.api
      .requestPasswordReset(email)
      .pipe(
        finalize(() => this.sending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => void this.router.navigate(['/restablecer-contrasena'], { state: { email, sentAt: Date.now() } }),
        error: (error: unknown) => this.refused(error),
      });
  }

  private refused(error: unknown): void {
    if (isRateLimited(error)) {
      this.error.set(waitText(error.retryAfterSeconds));
    } else if (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 408) {
      this.error.set('Revisa el correo e inténtalo de nuevo.');
    } else {
      this.error.set(REQUEST_UNCERTAIN);
    }
  }
}

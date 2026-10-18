import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { ApiError } from '../../core/http/api-error';
import { writeErrorText } from '../../shared/error-message';
import { PASSWORD_RULES_TEXT, passwordProblem, passwordProblemText } from './password-rules';

export const CHANGE_UNCERTAIN =
  'No pudimos confirmar si se cambió la contraseña. Si la nueva no funciona al iniciar sesión, usa la anterior.';

/**
 * Cambiar mi contraseña (POST /auth/me/password): the current one, the new one twice. The passwords
 * live only in the form's fields (never stored, logged or put in a URL) and the form is emptied on
 * success. A person whose password was chosen by someone else (a new account, an admin's reset) is
 * sent here before anything else; when they finish, the session ends and they sign in with the new
 * password, because the token they hold still says "change it". Otherwise the session goes on.
 */
@Component({
  selector: 'app-change-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './change-password.page.html',
  styleUrls: ['./staff.css', './change-password.page.css'],
})
export class ChangePasswordPage {
  private readonly api = inject(BackendApi);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly rules = PASSWORD_RULES_TEXT;
  /** Someone else chose the current password: this is the only screen open until it is changed. */
  protected readonly forced = this.session.mustChangePassword;

  protected readonly form = this.fb.group({
    current: [''],
    next: [''],
    confirm: [''],
  });

  protected readonly show = signal(false);
  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The service said the current password is wrong (AUTH-008): shown under that field. */
  protected readonly currentRefused = signal(false);
  /** The service refused the new password (AUTH-007): common, or built from the email. */
  protected readonly nextRefused = signal(false);
  protected readonly status = signal('');
  protected readonly done = signal(false);

  protected readonly problems = computed(() => {
    // Read the controls through the form's own change signal so the messages follow every keystroke.
    this.formValue();
    const v = this.form.getRawValue();
    const next = passwordProblem(v.next);
    return {
      current: v.current === '' ? 'Escribe tu contraseña actual.' : null,
      next:
        next !== null
          ? passwordProblemText(next)
          : v.next === v.current
            ? 'La nueva contraseña debe ser distinta de la actual.'
            : null,
      confirm: v.confirm === '' ? 'Repite la contraseña nueva.' : v.confirm !== v.next ? 'Las dos contraseñas no coinciden.' : null,
    };
  });
  private readonly formValue = signal(0);

  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('formElement');
  private readonly title = viewChild<ElementRef<HTMLElement>>('title');

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.formValue.update((n) => n + 1);
      this.error.set(null);
      this.currentRefused.set(false);
      this.nextRefused.set(false);
    });
  }

  protected shown(field: 'current' | 'next' | 'confirm'): string | null {
    if (!this.submitted()) {
      return null;
    }
    return this.problems()[field];
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.sending()) {
      return;
    }
    const p = this.problems();
    if (p.current !== null || p.next !== null || p.confirm !== null) {
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const v = this.form.getRawValue();
    this.error.set(null);
    this.sending.set(true);
    this.api
      .changeOwnPassword({ currentPassword: v.current, newPassword: v.next })
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
    this.form.reset({ current: '', next: '', confirm: '' });
    this.show.set(false);
    this.submitted.set(false);
    if (this.forced()) {
      // The token still carries "change your password": end the session and sign in with the new one.
      this.session.clear();
      void this.router.navigate(['/login'], { queryParams: { motivo: 'clave' } });
      return;
    }
    this.done.set(true);
    this.status.set('Contraseña cambiada.');
    setTimeout(() => this.title()?.nativeElement.focus());
  }

  private refused(error: unknown): void {
    const code = error instanceof ApiError ? error.code : null;
    if (code === 'AUTH-008') {
      this.currentRefused.set(true);
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    if (code === 'AUTH-007') {
      this.nextRefused.set(true);
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    this.error.set(writeErrorText(error, CHANGE_UNCERTAIN));
  }

  protected setShow(event: Event): void {
    this.show.set((event.target as HTMLInputElement).checked);
  }
}

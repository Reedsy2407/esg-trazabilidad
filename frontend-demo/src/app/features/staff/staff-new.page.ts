import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { writeErrorText } from '../../shared/error-message';
import { generateInitialPassword } from './initial-password';

/**
 * auth-service's domain rule for an email: stricter than @Email (it needs a dot in the domain).
 * Checked here first; the service answers AUTH-005 (400) if one gets through anyway.
 */
export const STAFF_EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

type Outcome =
  /** 201: the account exists with the password shown. */
  | { readonly kind: 'created'; readonly email: string }
  /** No answer, 408 or 5xx: it may exist with the password shown. */
  | { readonly kind: 'uncertain'; readonly email: string }
  /**
   * AUTH-002 right after an uncertain attempt: an account with that email exists. It may be the one
   * the first attempt created (then this is its password) or one that existed before (then this
   * password is not its own). The screen can't tell, so it says both and asks to check.
   */
  | { readonly kind: 'exists'; readonly email: string };

const HEADINGS: Record<Outcome['kind'], (email: string) => string> = {
  created: (email) => `Cuenta creada para ${email}`,
  uncertain: () => 'No pudimos confirmar si la cuenta se creó',
  exists: (email) => `Ya existe una cuenta con el correo ${email}`,
};

/**
 * Crear una cuenta del personal (POST /auth/staff-users). Any signed-in staff member can (no roles),
 * so the screen says what the new account can do and asks to confirm. The initial password is
 * generated here, never typed, and exists only in this component's memory: never in storage, the
 * URL, router state, the console or the session log; never in a DOM attribute (only a text node
 * while shown). "Ya la entregué", leaving the page, or a refused attempt that never used it, drop it.
 */
@Component({
  selector: 'app-staff-new-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './staff-new.page.html',
  styleUrls: ['./staff.css', './staff-new.page.css'],
})
export class StaffNewPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, (c: { value: string }) => (c.value.trim() === '' ? { required: true } : null)]],
    email: ['', [Validators.required, (c: { value: string }) => (c.value.trim() !== '' && !STAFF_EMAIL.test(c.value.trim()) ? { email: true } : null)]],
  });

  /** The initial password: only here, in memory. */
  private readonly password = signal<string | null>(null);
  protected readonly hasPassword = computed(() => this.password() !== null);
  protected readonly shown = signal(false);
  /** The text node shown on screen: the password only while "Mostrar" is on. */
  protected readonly passwordText = computed(() => (this.shown() ? this.password() : null));

  protected readonly acknowledged = signal(false);
  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly outcome = signal<Outcome | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly status = signal('');
  /** "Descartar" asks first: the account may exist with this password, which can't be reset. */
  protected readonly confirmingDiscard = signal(false);
  /** This screen put the password on the clipboard: it overwrites it when the password is dropped. */
  private copied = false;
  protected readonly heading = computed(() => {
    const o = this.outcome();
    return o === null ? '' : HEADINGS[o.kind](o.email);
  });

  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('formElement');
  private readonly resultTitle = viewChild<ElementRef<HTMLElement>>('resultTitle');
  private readonly formTitle = viewChild<ElementRef<HTMLElement>>('formTitle');
  private readonly discardText = viewChild<ElementRef<HTMLElement>>('discardText');
  private readonly discardButton = viewChild<ElementRef<HTMLElement>>('discardButton');

  constructor() {
    // Leaving the page forgets the password, and overwrites the clipboard if this screen filled it.
    this.destroyRef.onDestroy(() => this.forget());
  }

  protected invalid(name: 'fullName' | 'email'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected setAcknowledged(event: Event): void {
    this.acknowledged.set((event.target as HTMLInputElement).checked);
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.sending() || this.outcome()?.kind === 'created' || this.outcome()?.kind === 'exists') {
      return;
    }
    if (this.form.invalid || !this.acknowledged()) {
      this.form.markAllAsTouched();
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const retryingUncertain = this.outcome()?.kind === 'uncertain';
    // A retry after an unknown outcome reuses the same password: if the first attempt went
    // through, that is the account's password.
    const password = this.password() ?? generateInitialPassword();
    this.password.set(password);
    const v = this.form.getRawValue();
    const email = v.email.trim();
    this.error.set(null);
    this.status.set('');
    this.sending.set(true);
    this.api
      .createStaffUser({ email, password, fullName: v.fullName.trim() })
      .pipe(
        finalize(() => this.sending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (created) => this.settle({ kind: 'created', email: created.email }),
        error: (error: unknown) => {
          const uncertain = !(error instanceof ApiError) || error.status === 0 || error.status === 408 || error.status >= 500;
          if (uncertain) {
            this.settle({ kind: 'uncertain', email });
            return;
          }
          if (retryingUncertain && error.status === 409 && error.code === 'AUTH-002') {
            this.settle({ kind: 'exists', email });
            return;
          }
          // Refused: no account was made with this password (unless an earlier attempt is still unknown).
          if (!retryingUncertain) {
            this.forget();
          }
          this.error.set(
            writeErrorText(error, '', {
              'AUTH-002': `Ya existe una cuenta del personal con el correo ${email}.`,
              'AUTH-005': `El servicio rechazó el correo ${email}: le falta un punto en el dominio (por ejemplo, @asociacion.pe).`,
              VALIDATION_ERROR: 'El servicio rechazó los datos. Revisa el nombre y el correo.',
            }),
          );
        },
      });
  }

  private settle(outcome: Outcome): void {
    this.outcome.set(outcome);
    this.acknowledged.set(false);
    this.confirmingDiscard.set(false);
    this.form.disable();
    // Said by the live region too: going from one outcome to another keeps focus on the same heading.
    this.status.set(`${this.heading()}.`);
    setTimeout(() => this.resultTitle()?.nativeElement.focus());
  }

  protected toggleShown(): void {
    this.shown.update((v) => !v);
  }

  protected async copy(): Promise<void> {
    const password = this.password();
    if (password === null) {
      return;
    }
    try {
      await navigator.clipboard.writeText(password);
      this.copied = true;
      this.status.set('Contraseña copiada. Después de pegarla, copia otra cosa para que no quede en el portapapeles.');
    } catch {
      this.status.set('No se pudo copiar. Muéstrala y cópiala a mano.');
    }
  }

  /** The pressed button gets disabled: focus goes to the question, and back to the button on "No". */
  protected askDiscard(): void {
    this.confirmingDiscard.set(true);
    setTimeout(() => this.discardText()?.nativeElement.focus());
  }

  protected keepPassword(): void {
    this.confirmingDiscard.set(false);
    setTimeout(() => this.discardButton()?.nativeElement.focus());
  }

  /** "Ya la entregué", or a confirmed "Descartar": the password leaves memory, screen and clipboard. */
  protected handedOver(): void {
    this.forget();
    this.confirmingDiscard.set(false);
    this.outcome.set(null);
    this.submitted.set(false);
    this.form.enable();
    this.form.reset({ fullName: '', email: '' });
    this.status.set('La contraseña se borró de esta pantalla.');
    setTimeout(() => this.formTitle()?.nativeElement.focus());
  }

  private forget(): void {
    this.password.set(null);
    this.shown.set(false);
    if (this.copied) {
      this.copied = false;
      // Best effort: the browser may refuse without focus; the status text already asked to overwrite it.
      navigator.clipboard?.writeText('').catch(() => undefined);
    }
  }
}

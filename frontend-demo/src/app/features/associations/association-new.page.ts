import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { CreateAssociationRequest } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { writeErrorText } from '../../shared/error-message';
import { isValidRuc } from '../../shared/ruc';

export const ASSOCIATION_UNCERTAIN =
  'No pudimos confirmar si la asociación quedó registrada. Revisa la lista de asociaciones antes de volver a intentarlo.';

/**
 * Registrar asociación (recycler-service POST /associations): name and RUC required, the rest
 * optional. The RUC gets the same local check as the backend (prefix and modulo-11 digit, D7); it
 * does not ask SUNAT, so a well-formed RUC can still belong to nobody.
 */
@Component({
  selector: 'app-association-new-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './association-new.page.html',
  styles: `
    .ruc .control { max-width: 260px; }
    .optional { color: var(--ink-soft); font-weight: 400; }
  `,
})
export class AssociationNewPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    ruc: ['', [Validators.required, Validators.pattern(/^\d{11}$/)]],
    registrationNumber: ['', Validators.maxLength(255)],
    address: ['', Validators.maxLength(255)],
    contactEmail: ['', [Validators.email, Validators.maxLength(255)]],
    contactPhone: ['', Validators.maxLength(30)],
  });

  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('formElement');
  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly duplicateRuc = signal(false);

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.error.set(null);
      this.duplicateRuc.set(false);
    });
  }

  protected invalid(name: 'name' | 'ruc' | 'contactEmail'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected rucHasOtherCharacters(): boolean {
    return /\D/.test(this.form.controls.ruc.value);
  }

  /** 11 digits that fail the prefix or check-digit rule (the digits-only case has its own text). */
  protected rucFailsCheckDigit(): boolean {
    const ruc = this.form.controls.ruc.value;
    return /^\d{11}$/.test(ruc) && !isValidRuc(ruc);
  }

  protected normalizeRuc(): void {
    const control = this.form.controls.ruc;
    const digits = control.value.replace(/[\s.-]/g, '');
    if (digits !== control.value) {
      control.setValue(digits);
    }
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.submitting()) {
      return;
    }
    this.normalizeRuc();
    if (this.form.invalid || this.rucFailsCheckDigit()) {
      this.form.markAllAsTouched();
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const request = this.request();
    this.submitting.set(true);
    this.api
      .createAssociation(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (association) => void this.router.navigate(['/asociaciones', association.id]),
        error: (error: unknown) => {
          this.error.set(
            writeErrorText(error, ASSOCIATION_UNCERTAIN, {
              'ASO-002': `Ya hay una asociación registrada con el RUC ${request.ruc}. No se puede registrar dos veces: búscala en la lista.`,
            }),
          );
          this.duplicateRuc.set(error instanceof ApiError && error.code === 'ASO-002');
        },
      });
  }

  private request(): CreateAssociationRequest {
    const v = this.form.getRawValue();
    return {
      name: v.name.trim(),
      ruc: v.ruc,
      ...(v.registrationNumber.trim() !== '' && { registrationNumber: v.registrationNumber.trim() }),
      ...(v.address.trim() !== '' && { address: v.address.trim() }),
      ...(v.contactEmail.trim() !== '' && { contactEmail: v.contactEmail.trim() }),
      ...(v.contactPhone.trim() !== '' && { contactPhone: v.contactPhone.trim() }),
    };
  }
}

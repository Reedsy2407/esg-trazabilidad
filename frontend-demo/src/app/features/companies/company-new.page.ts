import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';

export const COMPANY_UNCERTAIN =
  'No pudimos confirmar si la empresa quedó registrada. Revisa la lista de empresas antes de volver a intentarlo.';

/** The backend's own rule (RegisterTrackedCompanyRequest): exactly 11 digits, nothing more. */
const RUC = /^\d{11}$/;

/**
 * Registrar empresa (reporting-service POST /tracked-companies): name, RUC and the association
 * whose collections will be traced for it. Exactly RegisterTrackedCompanyRequest. The backend
 * doesn't check the association exists, so the screen only offers the ones recycler-service lists.
 */
@Component({
  selector: 'app-company-new-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './company-new.page.html',
  styles: `
    .ruc .control { max-width: 260px; }
    .load-problem { margin-bottom: var(--space-2); }
  `,
})
export class CompanyNewPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, (c: { value: string }) => (c.value.trim() === '' ? { required: true } : null)]],
    ruc: ['', [Validators.required, Validators.pattern(RUC)]],
    associationId: ['', Validators.required],
  });

  protected readonly associations = rxResource({ stream: () => this.api.associations() });
  protected readonly associationOptions = computed(() =>
    this.associations.hasValue()
      ? this.associations.value().content.map((a) => ({
          id: a.id,
          label: `${a.name}${a.status === 'SUSPENDED' ? ' (suspendida)' : ''}`,
        }))
      : [],
  );
  protected readonly associationsTruncated = computed(
    () => this.associations.hasValue() && this.associations.value().totalElements > this.associations.value().content.length,
  );

  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('formElement');
  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The RUC the backend said is taken, to name it in the message and link to the list. */
  protected readonly duplicateRuc = signal<string | null>(null);

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.error.set(null);
      this.duplicateRuc.set(null);
    });
  }

  protected invalid(name: 'name' | 'ruc' | 'associationId'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'las asociaciones', 'el servicio de recicladores');
  }

  /** Separators already removed: anything left that isn't a digit (a letter, a slash). */
  protected rucHasOtherCharacters(): boolean {
    return /\D/.test(this.form.controls.ruc.value);
  }

  /** Digits only, as typed or pasted with spaces, dashes or dots ("20 512 345 678"). */
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
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const v = this.form.getRawValue();
    const request = { name: v.name.trim(), ruc: v.ruc, associationId: v.associationId };
    this.submitting.set(true);
    this.api
      .registerTrackedCompany(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (company) => void this.router.navigate(['/empresas', company.id], { state: { registrada: true } }),
        error: (error: unknown) => {
          this.error.set(
            writeErrorText(error, COMPANY_UNCERTAIN, {
              'RPT-002': `Ya hay una empresa registrada con el RUC ${request.ruc}. No se puede registrar dos veces: búscala en la lista de empresas.`,
            }),
          );
          this.duplicateRuc.set(error instanceof ApiError && error.status === 409 && error.code === 'RPT-002' ? request.ruc : null);
        },
      });
  }
}

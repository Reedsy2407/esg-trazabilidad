import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { RegisterTrackedCompanyRequest } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { codeMessage, loadErrorMessage, writeErrorText } from '../../shared/error-message';

export const COMPANY_UNCERTAIN =
  'No pudimos confirmar si la empresa quedó registrada. Revisa la lista de empresas antes de volver a intentarlo.';

/** The backend's own rule (RegisterTrackedCompanyRequest): exactly 11 digits, nothing more. */
const RUC = /^\d{11}$/;

/**
 * Registrar empresa (reporting-service POST /tracked-companies): the RUC, the association whose
 * collections will be traced for it, and an optional contact. The legal name is not typed: the
 * backend asks SUNAT for it. The backend doesn't check the association exists, so the screen only
 * offers the ones recycler-service lists.
 */
@Component({
  selector: 'app-company-new-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './company-new.page.html',
  styles: `
    .ruc .control { max-width: 260px; }
    .load-problem { margin-bottom: var(--space-2); }
    .optional { color: var(--ink-soft); font-weight: 400; }
    .fallback { margin: 0 0 var(--space-4); padding: var(--space-3) var(--space-4); border: 2px solid var(--ink); }
    .fallback legend { padding: 0 var(--space-2); font-weight: 700; }
    .fallback .btn { margin-top: var(--space-3); }
  `,
})
export class CompanyNewPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = this.fb.group({
    ruc: ['', [Validators.required, Validators.pattern(RUC)]],
    associationId: ['', Validators.required],
    contactName: ['', Validators.maxLength(255)],
    contactEmail: ['', [Validators.email, Validators.maxLength(255)]],
    contactPhone: ['', Validators.maxLength(30)],
    /** Only used by the fallback after RPT-011; the normal registration never sends it. */
    name: ['', Validators.maxLength(255)],
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
  /** After RPT-011: the screen offers to register the company UNVERIFIED, with a typed name. */
  protected readonly fallbackOffered = signal(false);
  protected readonly fallbackTried = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The RUC the backend said is taken, to name it in the message and link to the list. */
  protected readonly duplicateRuc = signal<string | null>(null);

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.error.set(null);
      this.duplicateRuc.set(null);
    });
  }

  protected nameMissing(): boolean {
    return this.fallbackTried() && this.form.controls.name.value.trim() === '';
  }

  protected invalid(name: 'ruc' | 'associationId' | 'contactEmail'): boolean {
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
    const request = this.request();
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
          // RPT-011 is a 503, but a definite one: SUNAT did not answer and nothing was stored.
          const sunatDown = error instanceof ApiError && error.code === 'RPT-011';
          this.fallbackOffered.set(sunatDown);
          this.error.set(
            sunatDown
              ? (codeMessage(error) ?? COMPANY_UNCERTAIN)
              : writeErrorText(error, COMPANY_UNCERTAIN, this.overrides(request.ruc)),
          );
          this.duplicateRuc.set(error instanceof ApiError && error.status === 409 && error.code === 'RPT-002' ? request.ruc : null);
        },
      });
  }

  /**
   * The fallback after RPT-011: the operator types the name and the company is stored UNVERIFIED.
   * The server repeats the SUNAT lookup first; if SUNAT answers after all (RPT-014) the offer is
   * withdrawn and the normal registration is the way.
   */
  protected submitUnverified(): void {
    this.fallbackTried.set(true);
    if (this.submitting()) {
      return;
    }
    this.normalizeRuc();
    const name = this.form.controls.name.value.trim();
    if (this.form.invalid || name === '') {
      this.form.markAllAsTouched();
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const request = { ...this.request(), name };
    this.submitting.set(true);
    this.api
      .registerUnverifiedTrackedCompany(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (company) => void this.router.navigate(['/empresas', company.id], { state: { registrada: true } }),
        error: (error: unknown) => {
          if (error instanceof ApiError && error.code === 'RPT-014') {
            this.fallbackOffered.set(false);
          }
          this.error.set(writeErrorText(error, COMPANY_UNCERTAIN, this.overrides(request.ruc)));
          this.duplicateRuc.set(error instanceof ApiError && error.status === 409 && error.code === 'RPT-002' ? request.ruc : null);
        },
      });
  }

  private request(): RegisterTrackedCompanyRequest {
    const v = this.form.getRawValue();
    return {
      ruc: v.ruc,
      associationId: v.associationId,
      ...(v.contactName.trim() !== '' && { contactName: v.contactName.trim() }),
      ...(v.contactEmail.trim() !== '' && { contactEmail: v.contactEmail.trim() }),
      ...(v.contactPhone.trim() !== '' && { contactPhone: v.contactPhone.trim() }),
    };
  }

  private overrides(ruc: string): Record<string, string> {
    return {
      'RPT-002': `Ya hay una empresa registrada con el RUC ${ruc}. No se puede registrar dos veces: búscala en la lista de empresas.`,
      'RPT-014': 'SUNAT sí respondió esta vez. Vuelve a pulsar «Registrar empresa»: el nombre se toma de SUNAT.',
    };
  }
}

import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { writeErrorText } from '../../shared/error-message';

export const NEIGHBOR_UNCERTAIN =
  'No pudimos confirmar si el vecino quedó registrado. Revisa la lista de vecinos antes de volver a intentarlo, para no duplicarlo.';

/** Blank (or only spaces) is empty: the backend's @NotBlank rejects it too. */
function notBlank(control: { value: string }) {
  return control.value.trim() === '' ? { required: true } : null;
}

/** "" or spaces → null: optional fields go as null, never as "". */
const optional = (value: string) => (value.trim() === '' ? null : value.trim());

/**
 * Registrar vecino (POST /neighbors): name and address required, district and phone optional,
 * exactly CreateNeighborRequest. The new neighbour is created active and opens on its own page.
 */
@Component({
  selector: 'app-neighbor-new-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './neighbor-new.page.html',
})
export class NeighborNewPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, notBlank]],
    address: ['', [Validators.required, notBlank]],
    district: [''],
    phone: [''],
  });

  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('formElement');
  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.error.set(null));
  }

  protected invalid(name: 'fullName' | 'address'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.api
      .createNeighbor({
        fullName: v.fullName.trim(),
        address: v.address.trim(),
        district: optional(v.district),
        phone: optional(v.phone),
      })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (neighbor) => void this.router.navigate(['/vecinos', neighbor.id], { state: { registrado: true } }),
        error: (error: unknown) => this.error.set(writeErrorText(error, NEIGHBOR_UNCERTAIN)),
      });
  }
}

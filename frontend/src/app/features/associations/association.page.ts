import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AssociationTransition } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { formatLocalDate, todayInLima } from '../../shared/format';
import { StatusLabel } from '../companies/status-label';
import { CertificationChip } from './certification-chip';
import { EXPIRING_SOON_DAYS, certificationState, expirationNote } from './certification-state';

const SERVICE = 'el servicio de recicladores';

const STATUS_UNCERTAIN =
  'No pudimos confirmar si el estado de la asociación cambió. Recarga la página antes de volver a intentarlo.';
const CERTIFICATION_UNCERTAIN =
  'No pudimos confirmar si la certificación quedó guardada. Mira la lista de certificaciones antes de volver a intentarlo.';
const RENEW_UNCERTAIN =
  'No pudimos confirmar si la certificación se renovó. Mira su fecha de vencimiento antes de volver a intentarlo.';

/**
 * One association (GET /associations/{id}) and its certifications
 * (GET /associations/{id}/certifications, soonest expiration first). Each block loads on its
 * own: failing certifications never take the header down. From here the association is suspended
 * or reactivated, a certification is registered, and a certification is renewed.
 */
@Component({
  selector: 'app-association-page',
  imports: [RouterLink, ReactiveFormsModule, StatusLabel, CertificationChip],
  templateUrl: './association.page.html',
  styleUrl: './association.page.css',
})
export class AssociationPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  readonly id = input.required<string>();

  protected readonly soonDays = EXPIRING_SOON_DAYS;

  protected readonly association = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.association(id),
  });

  protected readonly certifications = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.certifications(id),
  });

  /** The optional fields, in the request's order; a missing one says so instead of vanishing. */
  protected readonly details = computed(() => {
    if (!this.association.hasValue()) {
      return [];
    }
    const a = this.association.value();
    return [
      { label: 'N.º de registro', value: a.registrationNumber, mono: true },
      { label: 'Dirección', value: a.address, mono: false },
      { label: 'Correo de contacto', value: a.contactEmail, mono: false },
      { label: 'Teléfono de contacto', value: a.contactPhone, mono: true },
    ].map((d) => ({ ...d, value: d.value?.trim() || null }));
  });

  protected readonly rows = computed(() => {
    if (!this.certifications.hasValue()) {
      return null;
    }
    const today = todayInLima();
    const page = this.certifications.value();
    return {
      total: page.totalElements,
      items: page.content.map((c) => ({
        id: c.id,
        type: c.certificationType,
        issued: formatLocalDate(c.issuedDate),
        expires: formatLocalDate(c.expirationDate),
        state: certificationState(c, today),
        note: expirationNote(c, today),
      })),
    };
  });

  // --- suspend / activate -------------------------------------------------------------------

  protected readonly confirmingStatus = signal(false);
  protected readonly statusBusy = signal(false);
  protected readonly statusError = signal<string | null>(null);

  protected askStatusChange(): void {
    this.statusError.set(null);
    this.confirmingStatus.set(true);
  }

  protected cancelStatusChange(): void {
    this.confirmingStatus.set(false);
  }

  protected changeStatus(transition: AssociationTransition): void {
    if (this.statusBusy()) {
      return;
    }
    this.statusBusy.set(true);
    this.statusError.set(null);
    this.api
      .changeAssociationStatus(this.id(), transition)
      .pipe(
        finalize(() => this.statusBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.confirmingStatus.set(false);
          this.association.reload();
        },
        error: (error: unknown) => {
          this.statusError.set(writeErrorText(error, STATUS_UNCERTAIN));
          if (error instanceof ApiError && error.code === 'ASO-003') {
            // Someone else already changed it: show the current state.
            this.confirmingStatus.set(false);
            this.association.reload();
          }
        },
      });
  }

  // --- register a certification -------------------------------------------------------------

  protected readonly certForm = this.fb.group({
    certificationType: ['', [Validators.required, Validators.maxLength(255)]],
    issuedDate: ['', Validators.required],
    expirationDate: ['', Validators.required],
  });
  protected readonly certFormOpen = signal(false);
  protected readonly certSubmitted = signal(false);
  protected readonly certBusy = signal(false);
  protected readonly certError = signal<string | null>(null);

  protected openCertForm(): void {
    this.certError.set(null);
    this.certFormOpen.set(true);
  }

  protected closeCertForm(): void {
    this.certFormOpen.set(false);
    this.certSubmitted.set(false);
    this.certForm.reset();
  }

  protected certInvalid(name: 'certificationType' | 'issuedDate' | 'expirationDate'): boolean {
    const control = this.certForm.controls[name];
    return control.invalid && (control.touched || this.certSubmitted());
  }

  /** Issued on or after the expiration date: the backend refuses it with CER-003, so say it first. */
  protected datesOutOfOrder(): boolean {
    const { issuedDate, expirationDate } = this.certForm.getRawValue();
    return issuedDate !== '' && expirationDate !== '' && issuedDate >= expirationDate;
  }

  protected submitCertification(): void {
    this.certSubmitted.set(true);
    if (this.certBusy()) {
      return;
    }
    if (this.certForm.invalid || this.datesOutOfOrder()) {
      this.certForm.markAllAsTouched();
      return;
    }
    const v = this.certForm.getRawValue();
    this.certBusy.set(true);
    this.certError.set(null);
    this.api
      .createCertification(this.id(), {
        certificationType: v.certificationType.trim(),
        issuedDate: v.issuedDate,
        expirationDate: v.expirationDate,
      })
      .pipe(
        finalize(() => this.certBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.closeCertForm();
          this.certifications.reload();
        },
        error: (error: unknown) => this.certError.set(writeErrorText(error, CERTIFICATION_UNCERTAIN)),
      });
  }

  // --- renew a certification ----------------------------------------------------------------

  protected readonly renewing = signal<{ id: string; type: string } | null>(null);
  protected readonly renewDate = signal('');
  protected readonly renewSubmitted = signal(false);
  protected readonly renewBusy = signal(false);
  protected readonly renewError = signal<string | null>(null);

  protected startRenew(id: string, type: string): void {
    this.renewing.set({ id, type });
    this.renewDate.set('');
    this.renewSubmitted.set(false);
    this.renewError.set(null);
  }

  protected cancelRenew(): void {
    this.renewing.set(null);
  }

  protected submitRenew(): void {
    this.renewSubmitted.set(true);
    const target = this.renewing();
    if (target === null || this.renewBusy()) {
      return;
    }
    const date = this.renewDate();
    if (date === '') {
      return;
    }
    this.renewBusy.set(true);
    this.renewError.set(null);
    this.api
      .renewCertification(this.id(), target.id, date)
      .pipe(
        finalize(() => this.renewBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.renewing.set(null);
          this.certifications.reload();
        },
        error: (error: unknown) =>
          this.renewError.set(
            writeErrorText(error, RENEW_UNCERTAIN, {
              'CER-003': 'La nueva fecha de vencimiento debe ser posterior a la fecha de emisión.',
            }),
          ),
      });
  }

  /** ASO-001, or an id that isn't even a UUID (VALIDATION_ERROR 400): either way, no such association. */
  protected notFound(error: unknown): boolean {
    return error instanceof ApiError && (error.status === 404 || error.code === 'VALIDATION_ERROR');
  }

  protected errorText(error: unknown, what: string): string {
    return loadErrorMessage(error, what, SERVICE);
  }
}

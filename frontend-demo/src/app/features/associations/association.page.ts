import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { loadErrorMessage } from '../../shared/error-message';
import { formatLocalDate, todayInLima } from '../../shared/format';
import { StatusLabel } from '../companies/status-label';
import { CertificationChip } from './certification-chip';
import { EXPIRING_SOON_DAYS, certificationState, expirationNote } from './certification-state';

const SERVICE = 'el servicio de recicladores';

/**
 * One association (GET /associations/{id}) and its certifications
 * (GET /associations/{id}/certifications, soonest expiration first). Each block loads on its
 * own: failing certifications never take the header down.
 */
@Component({
  selector: 'app-association-page',
  imports: [RouterLink, StatusLabel, CertificationChip],
  templateUrl: './association.page.html',
  styleUrl: './association.page.css',
})
export class AssociationPage {
  private readonly api = inject(BackendApi);
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

  /** ASO-001, or an id that isn't even a UUID (VALIDATION_ERROR 400): either way, no such association. */
  protected notFound(error: unknown): boolean {
    return error instanceof ApiError && (error.status === 404 || error.code === 'VALIDATION_ERROR');
  }

  protected errorText(error: unknown, what: string): string {
    return loadErrorMessage(error, what, SERVICE);
  }
}

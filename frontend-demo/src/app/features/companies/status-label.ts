import { Component, input } from '@angular/core';

import { AssociationStatus, TrackedCompanyStatus } from '../../core/api/api.types';

type Status = TrackedCompanyStatus | AssociationStatus;

/** Feminine: both a company (empresa) and an association (asociación) use it. */
const LABELS: Record<Status, string> = { ACTIVE: 'Activa', INACTIVE: 'Inactiva', SUSPENDED: 'Suspendida' };

/**
 * A company's tracking status, or an association's (activa / suspendida). Not one of the VIGENTE / POR VENCER / VENCIDO
 * chips (those are for association certifications only, per the brief), so
 * it stays neutral: the word carries the meaning, the square only reinforces it.
 */
@Component({
  selector: 'app-status-label',
  template: `<span class="mark" [class.off]="status() !== 'ACTIVE'" aria-hidden="true"></span>{{ label() }}`,
  host: { '[class.inactive]': "status() !== 'ACTIVE'" },
  styles: `
    :host { display: inline-flex; align-items: center; gap: var(--space-2); }
    :host(.inactive) { color: var(--ink-soft); }
    .mark { width: 8px; height: 8px; background: var(--ink); }
    .mark.off { background: transparent; border: 1px solid var(--ink-soft); }
  `,
})
export class StatusLabel {
  readonly status = input.required<Status>();
  protected label(): string {
    return LABELS[this.status()];
  }
}

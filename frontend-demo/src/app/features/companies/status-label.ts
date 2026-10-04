import { Component, input } from '@angular/core';

import { TrackedCompanyStatus } from '../../core/api/api.types';

const LABELS: Record<TrackedCompanyStatus, string> = { ACTIVE: 'Activa', INACTIVE: 'Inactiva' };

/**
 * A company's tracking status. Not one of the VIGENTE / POR VENCER / VENCIDO
 * chips (those are for association certifications only, per the brief), so
 * it stays neutral: the word carries the meaning, the square only reinforces it.
 */
@Component({
  selector: 'app-status-label',
  template: `<span class="mark" [class.off]="status() === 'INACTIVE'" aria-hidden="true"></span>{{ label() }}`,
  host: { '[class.inactive]': "status() === 'INACTIVE'" },
  styles: `
    :host { display: inline-flex; align-items: center; gap: var(--space-2); }
    :host(.inactive) { color: var(--muted); }
    .mark { width: 8px; height: 8px; background: var(--petrol); }
    .mark.off { background: transparent; border: 1px solid var(--muted); }
  `,
})
export class StatusLabel {
  readonly status = input.required<TrackedCompanyStatus>();
  protected label(): string {
    return LABELS[this.status()];
  }
}

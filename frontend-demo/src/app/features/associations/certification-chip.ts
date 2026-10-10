import { Component, computed, input } from '@angular/core';

import { CertificationState } from './certification-state';

const LABELS: Record<CertificationState, string> = {
  vigente: 'Vigente',
  'por-vencer': 'Por vencer',
  vencido: 'Vencido',
};

/**
 * The one place green, amber and red appear (brief): a certification's state. The word always
 * carries the meaning; the colour and the mark's shape only repeat it (filled square, half-filled
 * square, struck square), so it still reads in greyscale and in forced colours.
 */
@Component({
  selector: 'app-certification-chip',
  template: `<span class="mark" aria-hidden="true"></span>{{ label() }}`,
  host: { '[attr.data-state]': 'state()' },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 2px 8px 2px 7px;
      border: 1.5px solid currentColor;
      font-size: 13px;
      font-weight: 600;
      white-space: nowrap;
    }
    :host([data-state='vigente']) { color: var(--vigente); }
    :host([data-state='por-vencer']) { color: var(--por-vencer); }
    :host([data-state='vencido']) { color: var(--vencido); }
    .mark { width: 9px; height: 9px; border: 1.5px solid currentColor; }
    :host([data-state='vigente']) .mark { background: currentColor; }
    :host([data-state='por-vencer']) .mark { background: linear-gradient(to top, currentColor 50%, transparent 50%); }
    :host([data-state='vencido']) .mark {
      background: linear-gradient(to top right, transparent calc(50% - 1px), currentColor calc(50% - 1px), currentColor calc(50% + 1px), transparent calc(50% + 1px));
    }
    @media (forced-colors: active) {
      :host { color: CanvasText; forced-color-adjust: none; }
      :host([data-state]) { color: CanvasText; }
    }
  `,
})
export class CertificationChip {
  readonly state = input.required<CertificationState>();
  protected readonly label = computed(() => LABELS[this.state()]);
}

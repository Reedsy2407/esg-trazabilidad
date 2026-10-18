import { Component } from '@angular/core';

/**
 * "chevron_right" from Material Symbols (outlined, weight 400), Google,
 * Apache License 2.0 (see public/ICONS-NOTICE.txt). Inlined as SVG so the
 * app makes no request to a third party. Decorative: always aria-hidden.
 */
@Component({
  selector: 'app-chevron-icon',
  template: `<svg viewBox="0 -960 960 960" width="20" height="20" aria-hidden="true" focusable="false">
    <path d="M504-480 348-636l56-56 212 212-212 212-56-56 156-156Z" />
  </svg>`,
  styles: `
    :host { display: inline-flex; }
    svg { fill: currentColor; }
  `,
})
export class ChevronIcon {}

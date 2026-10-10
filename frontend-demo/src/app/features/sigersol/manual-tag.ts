import { Component } from '@angular/core';

/**
 * "Ingreso manual": every SIGERSOL figure in this app was typed in by staff from SIGERSOL; there is
 * no integration. Drawn like a hand-written slip pinned to the ticket (dashed ink, no colour), so
 * it is never mistaken for data the system fetched.
 */
@Component({
  selector: 'app-manual-tag',
  template: `Ingreso manual`,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      padding: 1px 8px;
      border: 1.5px dashed var(--ink);
      color: var(--ink);
      font-size: 13px;
      font-weight: 600;
      white-space: nowrap;
      vertical-align: middle;
    }
  `,
})
export class ManualTag {}

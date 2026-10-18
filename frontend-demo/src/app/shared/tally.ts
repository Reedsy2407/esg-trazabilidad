import { Component, input } from '@angular/core';

export interface TallyRow {
  readonly label: string;
  readonly value: number | string;
}

/**
 * The list pages' side display (brief, «Visor y regla»): the total in big mono figures that count up, a 0–100
 * ruler for the share of the first row, and what the current page holds. Presentational; the figures are
 * derived from what the page already shows (the total and the rows on this page).
 */
@Component({
  selector: 'app-tally',
  template: `
    <div class="in" style="--i: 4">
      <section class="visor fold fold-dark tally" [attr.aria-label]="label()">
        <p class="lab">{{ label() }}</p>
        <p class="big tally-figure">
          <span class="countup" [style.--n]="total()" aria-hidden="true"></span>
          <span class="visually-hidden">{{ total() }}</span>
        </p>
        @if (share() !== null) {
          <div class="ruler" [style.--v]="share()" aria-hidden="true">
            <div class="nums"><span>0</span><span>50</span><span>100 %</span></div>
            <div class="ticks"></div>
            <div class="needle"></div>
          </div>
        }
        <p class="lab scope">{{ scope() }}</p>
        @for (row of rows(); track row.label) {
          <p class="row"><span>{{ row.label }}</span><b>{{ row.value }}</b></p>
        }
      </section>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .tally-figure {
      font-size: min(36cqw, var(--t-tally));
      margin: var(--space-2) 0 var(--space-4);
    }
    /* Angosto: el visor es una franja con la cifra y el rótulo; las filas se leen en la lista. */
    @media (max-width: 1099.98px) {
      .tally-figure {
        font-size: 64px;
        margin: 0;
      }
      .scope,
      .row,
      .ruler {
        display: none;
      }
    }
    .ruler {
      margin-bottom: var(--space-3);
    }
    .scope {
      margin-top: var(--space-3);
    }
    .row {
      margin: 0;
    }
  `,
})
export class Tally {
  readonly label = input.required<string>();
  readonly total = input.required<number>();
  readonly scope = input('En esta página');
  readonly rows = input.required<readonly TallyRow[]>();
  /** Share (0–100) drawn on the ruler; null hides the ruler. */
  readonly share = input<number | null>(null);
}

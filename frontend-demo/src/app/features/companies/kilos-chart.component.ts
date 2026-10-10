import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';

import { ChartModel } from './kilos-chart';

const W = 640;
const H = 260;
const BOTTOM = 32;
const TOP = 20;
const PLOT_H = H - TOP - BOTTOM;
/** Phones draw text at 2x (see styles), so the axis needs more room on both sides. */
const COMPACT_QUERY = '(max-width: 639.98px)';
const MARGINS = { wide: { left: 64, right: 8 }, compact: { left: 112, right: 44 } };

let nextChartId = 0;

/**
 * Kilos per certified period as plain SVG bars, no chart library. The SVG is
 * decorative to assistive tech; the same numbers sit in a visually hidden
 * table right after it, so a screen reader reads exact values instead of
 * guessing them from bar heights. Bars are ink-soft on paper (6.9:1 > 3:1);
 * no meaning depends on color.
 */
@Component({
  selector: 'app-kilos-chart',
  imports: [DecimalPipe],
  template: `
    <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" width="100%" aria-hidden="true" focusable="false">
      <!-- Thermal-print stripes, only on the highlighted period: on every bar they aliased at
           phone widths and blurred the bars' top edges (see LEARNINGS). -->
      <defs>
        <pattern [attr.id]="patternId" patternUnits="userSpaceOnUse" [attr.width]="stripe() * 2" [attr.height]="stripe() * 2">
          <rect [attr.width]="stripe() * 2" [attr.height]="stripe()" class="stripe" />
        </pattern>
      </defs>
      @for (tick of model().ticks; track tick) {
        <line class="grid" [attr.x1]="left()" [attr.x2]="width - right()" [attr.y1]="y(tick)" [attr.y2]="y(tick)" />
        <text class="tick" [attr.x]="left() - 8" [attr.y]="y(tick) + 4" text-anchor="end">{{ tick | number }}</text>
      }
      @for (bar of model().bars; track bar.key; let i = $index, last = $last) {
        <rect
          class="bar"
          [class.now]="last"
          [style.fill]="last ? 'url(#' + patternId + ')' : null"
          [attr.x]="barX(i)"
          [attr.y]="y(bar.kg)"
          [attr.width]="barWidth()"
          [attr.height]="plotHeight - (y(bar.kg) - top)"
        />
        <text
          class="label"
          [class.current]="last"
          [class.skip]="skipLabel(i)"
          [attr.x]="barX(i) + barWidth() / 2"
          [attr.y]="height - 10"
          text-anchor="middle"
        >
          {{ bar.label }}
        </text>
      }
    </svg>
    <!-- The wrapper is what's visually hidden: a <table> ignores width: 1px and
         would stretch the page to its content width (found at 360 px). -->
    <div class="visually-hidden">
    <table>
      <caption>Kilos trazados por período certificado, del más antiguo al más reciente</caption>
      <thead>
        <tr><th scope="col">Período</th><th scope="col">Kilos trazados</th></tr>
      </thead>
      <tbody>
        @for (bar of model().bars; track bar.key) {
          <tr><th scope="row">{{ bar.longLabel }}</th><td>{{ bar.kgText }}</td></tr>
        }
      </tbody>
    </table>
    </div>
  `,
  styles: `
    :host { display: block; position: relative; }
    svg { display: block; }
    .grid { stroke: var(--line); stroke-width: 1; }
    /* Solid bars keep heights comparable; the latest period is printed (striped) and
       outlined in ink, so its top edge stays exact. ink-soft is 6.9:1 on paper. */
    .bar { fill: var(--ink-soft); }
    .bar.now { stroke: var(--ink); stroke-width: 1.5; }
    .stripe { fill: var(--ink); }
    text { font-family: var(--font-sans); font-size: 11px; fill: var(--ink-soft); }
    .tick { font-family: var(--font-mono); font-size: 10px; }
    .label.current { fill: var(--ink); font-weight: 600; }
    /* Phones: the 640-unit drawing shrinks about 2x, so its text doubles to stay
       legible; skipLabel() then keeps one period label in three. */
    @media (max-width: 639.98px) {
      text { font-size: 22px; }
      .tick { font-size: 20px; }
    }
    .label.skip { display: none; }
  `,
})
export class KilosChart {
  readonly model = input.required<ChartModel>();
  /** Unique per chart, so two charts on one page never share a pattern. */
  protected readonly patternId = `kilos-print-${nextChartId++}`;

  protected readonly width = W;
  protected readonly height = H;
  protected readonly top = TOP;
  protected readonly plotHeight = PLOT_H;

  private readonly compact = signal(false);
  protected readonly left = computed(() => MARGINS[this.compact() ? 'compact' : 'wide'].left);
  protected readonly right = computed(() => MARGINS[this.compact() ? 'compact' : 'wide'].right);

  private readonly slot = computed(
    () => (W - this.left() - this.right()) / Math.max(1, this.model().bars.length),
  );

  constructor() {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      const query = window.matchMedia(COMPACT_QUERY);
      this.compact.set(query.matches);
      const onChange = (event: MediaQueryListEvent) => this.compact.set(event.matches);
      query.addEventListener('change', onChange);
      inject(DestroyRef).onDestroy(() => query.removeEventListener('change', onChange));
    }
  }
  /** Stripe thickness in drawing units: thicker on phones, where the drawing shrinks about 2x. */
  protected readonly stripe = computed(() => (this.compact() ? 4 : 2));
  protected readonly barWidth = computed(() => Math.min(28, this.slot() * 0.55));

  /** On phones, one label in three, counted back from the latest period so it always shows. */
  protected skipLabel(index: number): boolean {
    return this.compact() && (this.model().bars.length - 1 - index) % 3 !== 0;
  }

  protected y(kg: number): number {
    return TOP + PLOT_H * (1 - kg / this.model().axisTop);
  }

  protected barX(index: number): number {
    return this.left() + index * this.slot() + (this.slot() - this.barWidth()) / 2;
  }
}

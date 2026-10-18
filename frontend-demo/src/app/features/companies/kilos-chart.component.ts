import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, input, signal } from '@angular/core';

import { ChartModel } from './kilos-chart';

const H = 280;
const BOTTOM = 32;
const TOP = 20;
const PLOT_H = H - TOP - BOTTOM;
const LEFT = 76;
const RIGHT = 26;
/** Drawing units are pixels: the viewBox follows the container width, so text keeps its real size at any width. */
const DEFAULT_WIDTH = 640;

let nextChartId = 0;

/**
 * Kilos per certified period as plain SVG bars, no chart library. The SVG is
 * decorative to assistive tech; the same numbers sit in a visually hidden
 * table right after it, so a screen reader reads exact values instead of
 * guessing them from bar heights. Bars are the logo's blue on paper (7.9:1 > 3:1);
 * no meaning depends on color.
 */
@Component({
  selector: 'app-kilos-chart',
  imports: [DecimalPipe],
  template: `
    <svg [attr.viewBox]="'0 0 ' + width() + ' ' + height" width="100%" aria-hidden="true" focusable="false">
      <!-- Thermal-print stripes, only on the highlighted period: on every bar they aliased at
           phone widths and blurred the bars' top edges (see LEARNINGS). -->
      <defs>
        <pattern [attr.id]="patternId" patternUnits="userSpaceOnUse" [attr.width]="stripe() * 2" [attr.height]="stripe() * 2">
          <rect [attr.width]="stripe() * 2" [attr.height]="stripe()" class="stripe" />
        </pattern>
      </defs>
      @for (tick of model().ticks; track tick) {
        <line class="grid" [attr.x1]="left" [attr.x2]="width() - right" [attr.y1]="y(tick)" [attr.y2]="y(tick)" />
        <text class="tick" [attr.x]="left - 8" [attr.y]="y(tick) + 4" text-anchor="end">{{ tick | number }}</text>
      }
      @for (bar of model().bars; track bar.key; let i = $index, last = $last) {
        <rect
          class="bar"
          [class.now]="last"
          [style.--i]="i"
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
          [attr.y]="height - 8"
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
       outlined in ink, so its top edge stays exact. brand is 7.9:1 on paper. */
    .bar { fill: var(--brand); transform-box: fill-box; transform-origin: 50% 100%; }
    .bar.now { stroke: var(--ink); stroke-width: 1.5; }
    .stripe { fill: var(--brand-deep); }
    text { font-family: var(--font-sans); font-size: 15px; fill: var(--ink-soft); }
    .tick { font-family: var(--font-mono); font-size: var(--t-note); }
    .label.current { fill: var(--ink); font-weight: 600; }
    /* Las barras suben, escalonadas, una vez (solo transform). */
    @media (prefers-reduced-motion: no-preference) {
      .bar { animation: grow 0.7s var(--ease) both; animation-delay: calc(0.5s + var(--i, 0) * 55ms); }
      @keyframes grow { from { transform: scaleY(0); } }
    }
        .label.skip { display: none; }
  `,
})
export class KilosChart {
  readonly model = input.required<ChartModel>();
  /** Unique per chart, so two charts on one page never share a pattern. */
  protected readonly patternId = `kilos-print-${nextChartId++}`;

  protected readonly width = signal(DEFAULT_WIDTH);
  protected readonly height = H;
  protected readonly top = TOP;
  protected readonly plotHeight = PLOT_H;
  protected readonly left = LEFT;
  protected readonly right = RIGHT;

  private readonly slot = computed(() => (this.width() - LEFT - RIGHT) / Math.max(1, this.model().bars.length));

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver((entries) => {
        const width = Math.round(entries[0].contentRect.width);
        if (width > 0) {
          this.width.set(Math.max(280, width));
        }
      });
      observer.observe(host);
      inject(DestroyRef).onDestroy(() => observer.disconnect());
    }
  }
  /** Stripe thickness in drawing units. */
  protected readonly stripe = computed(() => 2);
  protected readonly barWidth = computed(() => Math.min(56, this.slot() * 0.55));

  /** Narrow slots keep one period label in 2 or 3, counted back from the latest period so it always shows. */
  protected skipLabel(index: number): boolean {
    const every = this.slot() < 24 ? 4 : this.slot() < 36 ? 3 : this.slot() < 62 ? 2 : 1;
    return (this.model().bars.length - 1 - index) % every !== 0;
  }

  protected y(kg: number): number {
    return TOP + PLOT_H * (1 - kg / this.model().axisTop);
  }

  protected barX(index: number): number {
    return LEFT + index * this.slot() + (this.slot() - this.barWidth()) / 2;
  }
}

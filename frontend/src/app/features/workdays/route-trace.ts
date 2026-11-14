import { Component, computed, input } from '@angular/core';

import { LatLng, fitProjection, pathOf } from './route-geometry';

/** A check-in drawn on the route; `n` is the number the check-ins table shows for it. */
export interface TraceStop extends LatLng {
  readonly n: number;
  readonly label: string;
}

const WIDTH = 720;
const HEIGHT = 440;
const PADDING = 44;

/**
 * A workday's route as an inline SVG: the line, where it started (a dot) and ended (a square), the check-ins that
 * carry a location (numbered diamonds) and a scale bar. No map under it, no service, no library: drawing the
 * points by hand keeps the coordinates in this browser. North is up.
 */
@Component({
  selector: 'app-route-trace',
  template: `
    @if (geometry(); as g) {
      <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" role="img" [attr.aria-label]="description()" focusable="false">
        <rect class="frame" x="1" y="1" [attr.width]="width - 2" [attr.height]="height - 2" />
        <path class="line" [attr.d]="g.path" />
        <circle class="start" [attr.cx]="g.start.x" [attr.cy]="g.start.y" r="8" />
        @if (g.points.length > 1) {
          <rect class="end" [attr.x]="g.end.x - 8" [attr.y]="g.end.y - 8" width="16" height="16" />
        }
        @for (s of g.stops; track s.n) {
          <g class="stop" [attr.transform]="'translate(' + s.x + ' ' + s.y + ')'">
            <path d="M0 -15 L15 0 L0 15 L-15 0 Z" />
            <text y="5" text-anchor="middle">{{ s.n }}</text>
          </g>
        }
        <g class="scale" [attr.transform]="'translate(' + padding + ' ' + (height - 16) + ')'">
          <path [attr.d]="'M0 -6 V0 H' + g.scale.px + ' V-6'" />
          <text x="0" y="-12">{{ g.scale.label }}</text>
        </g>
        <g class="north" [attr.transform]="'translate(' + (width - 30) + ' 44)'">
          <path d="M0 -22 L9 0 L0 -6 L-9 0 Z" />
          <text y="20" text-anchor="middle">N</text>
        </g>
      </svg>
      <ul class="key" aria-hidden="true">
        <li><svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="6" /></svg> Inicio</li>
        @if (g.points.length > 1) {
          <li><svg viewBox="0 0 16 16"><rect x="2" y="2" width="12" height="12" /></svg> Fin</li>
        }
        @if (g.stops.length > 0) {
          <li><svg viewBox="0 0 16 16"><path d="M8 1 L15 8 L8 15 L1 8 Z" /></svg> Check-in con ubicación</li>
        }
      </ul>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    svg[role='img'] {
      display: block;
      width: 100%;
      height: auto;
      background: var(--paper);
    }
    .frame {
      fill: none;
      stroke: var(--line);
      stroke-width: 2;
    }
    .line {
      fill: none;
      stroke: var(--brand);
      stroke-width: 4;
      stroke-linejoin: round;
      stroke-linecap: round;
    }
    .start,
    .end {
      fill: var(--ink);
      stroke: var(--paper);
      stroke-width: 3;
    }
    .stop path {
      fill: var(--paper);
      stroke: var(--ink);
      stroke-width: 3;
    }
    .stop text {
      fill: var(--ink);
      font: 600 14px var(--font-mono);
    }
    .scale path,
    .north path {
      fill: none;
      stroke: var(--ink);
      stroke-width: 2;
    }
    .north path {
      fill: var(--ink);
    }
    .scale text,
    .north text {
      fill: var(--ink-soft);
      font: 400 15px var(--font-mono);
    }
    .north text {
      text-anchor: middle;
    }
    .key {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2) var(--space-5);
      margin: var(--space-2) 0 0;
      padding: 0;
      list-style: none;
      color: var(--ink-soft);
      font-size: var(--t-note);
    }
    .key li {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
    }
    .key svg {
      width: 16px;
      height: 16px;
      fill: var(--ink);
    }
  `,
})
export class RouteTrace {
  /** The route, in order. */
  readonly points = input.required<readonly LatLng[]>();
  readonly stops = input<readonly TraceStop[]>([]);
  /** What a screen reader hears instead of the drawing. */
  readonly description = input.required<string>();

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly padding = PADDING;

  protected readonly geometry = computed(() => {
    const points = this.points();
    const stops = this.stops();
    if (points.length === 0) {
      return null;
    }
    const { project, scaleBar } = fitProjection([...points, ...stops], WIDTH, HEIGHT, PADDING);
    const drawn = points.map(project);
    return {
      points: drawn,
      path: pathOf(drawn),
      start: drawn[0],
      end: drawn[drawn.length - 1],
      stops: stops.map((s) => ({ n: s.n, ...project(s) })),
      scale: scaleBar,
    };
  });
}

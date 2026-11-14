import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, Observable, expand, map, reduce } from 'rxjs';

import { WorkdayCheckin, WorkdayDetail } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { loadErrorMessage } from '../../shared/error-message';
import { formatInstantDate, formatInstantDateTime, formatInstantTime, formatKg } from '../../shared/format';
import { wasteTypeName } from '../../shared/waste-types';
import { formatDistance, walkedMeters } from './route-geometry';
import { RouteTrace, TraceStop } from './route-trace';

/** The signed photo URLs last 5 minutes; they are dropped from the page a little before that. */
export const PHOTO_URL_LIFETIME_MS = 5 * 60 * 1000 - 15_000;

/**
 * One workday for the staff (spec R28, CA-38): the route drawn by hand, the check-ins and their photos. The photo URLs are
 * asked for when the page opens and renewed with "Recargar fotos"; they are used only as the source of an image, never
 * shown as text or put in a link (so they do not reach the history), and they are dropped when they expire.
 */
@Component({
  selector: 'app-workday-page',
  imports: [RouterLink, RouteTrace, NgTemplateOutlet],
  templateUrl: './workday.page.html',
  styleUrl: './workday.page.css',
})
export class WorkdayPage {
  private readonly api = inject(BackendApi);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly timers = new Set<ReturnType<typeof setTimeout>>();

  readonly associationId = input.required<string>();
  readonly recyclerId = input.required<string>();
  readonly workdayId = input.required<string>();

  /** Photos that expired, failed to load, or that the person opened large. */
  protected readonly expired = signal(false);
  protected readonly failedPhotos = signal<ReadonlySet<string>>(new Set());
  protected readonly enlarged = signal<string | null>(null);
  protected readonly loadedAt = signal<string | null>(null);
  protected readonly status = signal('');

  protected readonly workday = rxResource({
    params: () => this.workdayId(),
    stream: ({ params }) => this.loadAll(params),
  });

  protected readonly recycler = rxResource({
    params: () => ({ a: this.associationId(), r: this.recyclerId() }),
    stream: ({ params }) => this.api.recycler(params.a, params.r),
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clearTimers());
  }

  /** The detail with every window of points: the service serves them in windows when a day has very many. */
  private loadAll(id: string): Observable<WorkdayDetail> {
    this.clearTimers();
    // The URLs of the previous answer stay hidden until the new answer arrives: a reload must not draw them again (they
    // would fail and mark the fresh ones as failed too). A first load has no URLs yet, so nothing is hidden by this.
    this.expired.set(true);
    this.enlarged.set(null);
    return this.api.workday(id, 0).pipe(
      expand((window) => {
        const next = window.pointsOffset + window.points.length;
        return window.points.length > 0 && next < window.pointsTotal ? this.api.workday(id, next) : EMPTY;
      }),
      reduce<WorkdayDetail, WorkdayDetail | null>((all, window) => (all === null ? window : { ...all, points: [...all.points, ...window.points] }), null),
      map((all) => {
        const detail = all as WorkdayDetail;
        this.failedPhotos.set(new Set());
        this.expired.set(false);
        this.loadedAt.set(new Date().toISOString());
        this.timers.add(setTimeout(() => this.expirePhotos(), PHOTO_URL_LIFETIME_MS));
        return detail;
      }),
    );
  }

  private expirePhotos(): void {
    this.expired.set(true);
    this.enlarged.set(null);
  }

  private clearTimers(): void {
    this.timers.forEach((t) => clearTimeout(t));
    this.timers.clear();
  }

  protected reloadPhotos(): void {
    this.status.set('');
    this.workday.reload();
    setTimeout(() => this.host.nativeElement.querySelector<HTMLElement>('#checkins-title')?.focus());
  }

  // --- what the page shows -------------------------------------------------------------------

  protected readonly detail = computed(() => (this.workday.hasValue() ? this.workday.value() : null));

  protected readonly routePoints = computed(() => {
    const d = this.detail();
    if (d === null) {
      return [];
    }
    if (d.points.length > 0) {
      return d.points.map((p) => ({ lat: p.lat, lng: p.lng }));
    }
    return d.routeSummary?.polyline.map(([lat, lng]) => ({ lat, lng })) ?? [];
  });

  /** Check-ins that carry a location, numbered as in the table. */
  protected readonly stops = computed<TraceStop[]>(() => {
    const d = this.detail();
    return d === null
      ? []
      : d.checkins.flatMap((c, i) =>
          c.lat === null || c.lng === null ? [] : [{ n: i + 1, lat: c.lat, lng: c.lng, label: c.neighborName }],
        );
  });

  protected readonly distance = computed(() => {
    const d = this.detail();
    if (d === null) {
      return null;
    }
    if (d.points.length > 0) {
      return walkedMeters(d.points);
    }
    return d.routeSummary?.distanceM ?? null;
  });

  protected readonly totalKg = computed(() => this.detail()?.checkins.reduce((sum, c) => sum + c.weightKg, 0) ?? 0);

  protected readonly traceDescription = computed(() => {
    const d = this.detail();
    if (d === null || this.routePoints().length === 0) {
      return '';
    }
    const summarized = d.points.length === 0 && d.routeSummary !== null;
    const count = summarized ? d.routeSummary!.pointCount : d.pointsTotal;
    const distance = this.distance();
    return (
      `Recorrido de ${count} puntos${distance === null ? '' : `, ${formatDistance(distance)}`}` +
      `${summarized ? ' (resumen simplificado)' : ''}, con ${this.stops().length} check-ins con ubicación. Norte arriba, sin mapa de fondo.`
    );
  });

  protected loadError(error: unknown, what: string): string {
    return loadErrorMessage(error, what, 'el servicio de recojos');
  }

  protected day(instant: string): string {
    return formatInstantDate(instant);
  }

  protected time(instant: string): string {
    return formatInstantTime(instant);
  }

  protected dateTime(instant: string): string {
    return formatInstantDateTime(instant);
  }

  protected kg(value: number): string {
    return formatKg(value);
  }

  protected waste(code: string): string {
    return wasteTypeName(code);
  }

  protected distanceText(meters: number): string {
    return formatDistance(meters);
  }

  // --- photos --------------------------------------------------------------------------------

  /** The source of an image, only while it is valid; null when it should not be drawn. */
  protected photoSource(c: WorkdayCheckin): string | null {
    return c.photo.state === 'AVAILABLE' && !this.expired() && !this.failedPhotos().has(c.checkinId) ? c.photo.url : null;
  }

  protected photoProblem(c: WorkdayCheckin): boolean {
    return c.photo.state === 'AVAILABLE' && (this.expired() || this.failedPhotos().has(c.checkinId));
  }

  protected photoFailed(id: string): void {
    this.failedPhotos.update((s) => new Set(s).add(id));
  }

  protected toggleEnlarged(id: string): void {
    this.enlarged.update((current) => (current === id ? null : id));
  }

  protected anyPhotoProblem(): boolean {
    return this.expired() || this.failedPhotos().size > 0;
  }

  protected readonly accuracy = (m: number | null): string => (m === null ? '' : `±${Math.round(m)} m`);
}

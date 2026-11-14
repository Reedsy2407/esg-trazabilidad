import { Component, DestroyRef, ElementRef, computed, effect, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AssignableSchedule, AssignedSchedule, BulkAssignmentResult, DayOfWeek } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { DAYS } from '../../shared/collection-labels';
import { districtByUbigeo } from '../../shared/districts';
import { DistrictSelect } from '../../shared/district-select/district-select';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';

const WRITE_UNCERTAIN =
  'No pudimos confirmar si la asignación se guardó. Recarga la lista de cronogramas asignados antes de volver a intentarlo.';

const DAY_ORDER = Object.keys(DAYS) as DayOfWeek[];

/** Where a schedule stands for this recycler. */
type Owner = 'mine' | 'free' | 'other';

/**
 * Assigning schedules to one recycler (spec R19, CA-30, CA-31). Two ways: pick them one by one inside a district
 * (PUT: it REPLACES the recycler's list and MOVES the schedules another recycler had, so that is shown and confirmed
 * first), or assign the whole district (POST bulk: it only takes the free ones and counts what it skipped). The
 * result of each operation is shown with the counts the service answered, never with a figure computed here.
 */
@Component({
  selector: 'app-recycler-schedules-page',
  imports: [RouterLink, ReactiveFormsModule, DistrictSelect],
  templateUrl: './recycler-schedules.page.html',
  styleUrl: './recycler-schedules.page.css',
})
export class RecyclerSchedulesPage {
  private readonly api = inject(BackendApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly associationId = input.required<string>();
  readonly recyclerId = input.required<string>();

  protected readonly days = DAYS;

  protected readonly recycler = rxResource({
    params: () => ({ a: this.associationId(), r: this.recyclerId() }),
    stream: ({ params }) => this.api.recycler(params.a, params.r),
  });

  protected readonly assignment = rxResource({
    params: () => this.recyclerId(),
    stream: ({ params }) => this.api.recyclerAssignment(params),
  });

  /** Monday to Sunday, then by time: the service orders the day names alphabetically. */
  protected readonly assigned = computed<readonly AssignedSchedule[]>(() =>
    this.assignment.hasValue()
      ? [...this.assignment.value().schedules].sort(
          (a, b) =>
            DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek) ||
            a.time.localeCompare(b.time) ||
            a.neighborName.localeCompare(b.neighborName),
        )
      : [],
  );

  protected readonly inactive = computed(() => this.recycler.hasValue() && this.recycler.value().status === 'INACTIVE');

  // --- district and its schedules -----------------------------------------------------------

  protected readonly districtControl = new FormControl<string | null>(null);
  protected readonly district = signal<string | null>(null);
  protected readonly page = signal(0);

  protected readonly assignable = rxResource({
    params: () => {
      const district = this.district();
      return district === null ? undefined : { district, page: this.page() };
    },
    stream: ({ params }) => this.api.assignableSchedules(params.district, params.page),
  });

  protected readonly districtName = computed(() => {
    const ubigeo = this.district();
    return ubigeo === null ? '' : (districtByUbigeo(ubigeo)?.name ?? ubigeo);
  });

  /** Chosen schedules, by id, and whether each one belongs to another recycler right now (known when it was ticked). */
  private readonly chosen = signal<ReadonlyMap<string, boolean>>(new Map());
  protected readonly chosenCount = computed(() => this.chosen().size);
  protected readonly chosenFromOthers = computed(() => [...this.chosen().values()].filter((other) => other).length);

  readonly status = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The operation waiting for its confirmation: moving other recyclers' schedules, or assigning a whole district. */
  protected readonly confirming = signal<'move' | 'bulk' | null>(null);
  protected readonly lastBulk = signal<{ district: string; result: BulkAssignmentResult } | null>(null);

  constructor() {
    this.districtControl.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
      this.district.set(value);
      this.page.set(0);
      this.chosen.set(new Map());
      this.confirming.set(null);
    });
    // A new recycler in the route starts clean.
    effect(() => {
      this.recyclerId();
      this.chosen.set(new Map());
      this.confirming.set(null);
      this.status.set('');
      this.error.set(null);
    });
  }

  protected owner(row: AssignableSchedule): Owner {
    if (row.assignedRecyclerId === null) {
      return 'free';
    }
    return row.assignedRecyclerId === this.recyclerId() ? 'mine' : 'other';
  }

  protected ownerText(row: AssignableSchedule): string {
    switch (this.owner(row)) {
      case 'mine':
        return 'Ya es de este reciclador';
      case 'other':
        return 'De otro reciclador';
      default:
        return 'Libre';
    }
  }

  protected isChosen(row: AssignableSchedule): boolean {
    return this.owner(row) === 'mine' || this.chosen().has(row.scheduleId);
  }

  protected toggle(row: AssignableSchedule, checked: boolean): void {
    if (this.owner(row) === 'mine' || this.busy()) {
      return;
    }
    const next = new Map(this.chosen());
    if (checked) {
      next.set(row.scheduleId, this.owner(row) === 'other');
    } else {
      next.delete(row.scheduleId);
    }
    this.chosen.set(next);
    this.confirming.set(null);
  }

  protected time(value: string): string {
    return value.slice(0, 5);
  }

  protected loadError(error: unknown, what: string): string {
    return loadErrorMessage(error, what, 'el servicio de recojos');
  }

  protected previousPage(): void {
    this.page.update((p) => Math.max(0, p - 1));
  }

  protected nextPage(): void {
    this.page.update((p) => p + 1);
  }

  // --- one by one ----------------------------------------------------------------------------

  protected askAssignChosen(): void {
    if (this.busy() || this.chosenCount() === 0) {
      return;
    }
    if (this.chosenFromOthers() > 0) {
      this.error.set(null);
      this.confirming.set('move');
      this.focus('[data-confirm]');
      return;
    }
    this.assignChosen();
  }

  protected assignChosen(): void {
    if (this.busy() || !this.assignment.hasValue()) {
      return;
    }
    const keep = this.assignment.value().schedules.map((s) => s.scheduleId);
    const picked = [...this.chosen().keys()];
    this.begin();
    this.api
      .replaceAssignment(this.recyclerId(), [...keep, ...picked])
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.chosen.set(new Map());
          this.confirming.set(null);
          this.lastBulk.set(null);
          this.status.set(
            `Asignación guardada. Se movieron ${result.moved.length} desde otro reciclador. Ahora tiene ${result.scheduleIds.length} en total.`,
          );
          this.assignment.reload();
          this.assignable.reload();
          this.focus('#assigned-title');
        },
        error: (error: unknown) => this.failed(error),
      });
  }

  // --- the whole district --------------------------------------------------------------------

  protected askBulk(): void {
    if (this.busy() || this.district() === null) {
      return;
    }
    this.error.set(null);
    this.confirming.set('bulk');
    this.focus('[data-confirm]');
  }

  protected assignDistrict(): void {
    const district = this.district();
    if (this.busy() || district === null) {
      return;
    }
    const name = this.districtName();
    this.begin();
    this.api
      .bulkAssign(this.recyclerId(), district)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.chosen.set(new Map());
          this.confirming.set(null);
          this.lastBulk.set({ district: name, result });
          this.status.set(
            `Distrito ${name}: se asignaron ${result.assigned}; se omitieron ${result.skippedOtherRecycler} de otro reciclador y ${result.skippedInactive} inactivos.`,
          );
          this.assignment.reload();
          this.assignable.reload();
          this.focus('#assigned-title');
        },
        error: (error: unknown) => this.failed(error),
      });
  }

  protected cancelConfirm(): void {
    this.confirming.set(null);
    this.focus('[data-assign]');
  }

  private begin(): void {
    this.busy.set(true);
    this.error.set(null);
    this.status.set('');
  }

  /** A failed write says why; when its outcome is unknown the list is read again, as the message tells the person. */
  private failed(error: unknown): void {
    const text = this.writeError(error);
    this.error.set(text);
    if (text === WRITE_UNCERTAIN) {
      this.assignment.reload();
    }
  }

  private writeError(error: unknown): string {
    return writeErrorText(error, WRITE_UNCERTAIN, {
      'COL-019': 'El reciclador no está activo: reactívalo antes de asignarle cronogramas.',
      'COL-006': 'Uno de los cronogramas ya no existe o fue cancelado. Actualiza la lista e inténtalo de nuevo.',
      'COL-010': 'El distrito no está en la lista de Lima Metropolitana y Callao.',
    });
  }

  /** Moves keyboard and screen-reader focus after the element that had it was replaced. */
  private focus(selector: string): void {
    setTimeout(() => {
      const element = this.host.nativeElement.querySelector<HTMLElement>(selector);
      element?.focus();
    });
  }
}

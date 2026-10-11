import { DecimalPipe, formatDate } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, input, linkedSignal, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, map } from 'rxjs';

import {
  CollectionRecord,
  CollectionSchedule,
  DayOfWeek,
  Neighbor,
  NeighborTransition,
  Page,
  ScheduleTransition,
} from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { DAYS, byWeekday } from '../../shared/collection-labels';
import { DistrictSelect } from '../../shared/district-select/district-select';
import { districtByUbigeo } from '../../shared/districts';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { LIMA_OFFSET, LOCALE, formatKg, formatLocalDate } from '../../shared/format';
import { notBlank, optional } from './neighbor-new.page';
import { ReasonConfirm } from '../../shared/reason-confirm';

const SERVICE = 'el servicio de recojos';
const LOCAL_DATE = /^\d{4}-\d{2}-\d{2}$/;

const STATE: Record<CollectionSchedule['status'], string> = { ACTIVE: 'Activo', PAUSED: 'Pausado', CANCELLED: 'Cancelado' };
const NEIGHBOR_STATE: Record<Neighbor['status'], string> = { ACTIVE: 'Activo', INACTIVE: 'Inactivo' };

export const EDIT_UNCERTAIN = 'No pudimos confirmar si los cambios se guardaron. Vuelve a cargar la página para ver los datos actuales.';
export const STATUS_UNCERTAIN = 'No pudimos confirmar si el cambio de estado se aplicó. Vuelve a cargar la página para ver el estado actual.';
export const DELETE_UNCERTAIN = 'No pudimos confirmar si el vecino se eliminó. Búscalo en la lista de vecinos antes de volver a intentarlo.';
export const COL_011_TEXT =
  'Este vecino tiene recojos registrados, así que no se puede eliminar: los recojos son historia. Desactívalo en su lugar.';
const DONE: Record<ScheduleTransition, string> = { pause: 'pausado', cancel: 'cancelado', reactivate: 'reactivado' };

/** "lunes 08:00": a schedule named inside a sentence. */
function scheduleName(s: Pick<CollectionSchedule, 'dayOfWeek' | 'time'>): string {
  return `${DAYS[s.dayOfWeek].toLowerCase()} ${s.time.slice(0, 5)}`;
}

/**
 * Was this page opened right after registering the neighbour (router state from NeighborNewPage)?
 * Read once and dropped from the history entry, so a reload doesn't announce it again.
 */
function justRegistered(): boolean {
  const state: unknown = history.state;
  if (typeof state !== 'object' || state === null || (state as Record<string, unknown>)['registrado'] !== true) {
    return false;
  }
  history.replaceState({ ...(state as Record<string, unknown>), registrado: false }, '');
  return true;
}

/**
 * One neighbour (GET /neighbors/{id}) with its schedules (create; pause / reactivate / cancel,
 * cancel being final) and its collections (newest first, by date range). Three blocks that load
 * on their own: a failing one never takes the others down.
 *
 * v3: its data can be edited (the district from the closed list); it can be deactivated and
 * reactivated with a reason (deactivating pauses its active schedules; after reactivating, the
 * paused ones are listed to reactivate one by one), and deleted with a reason only while it has
 * no collections. Every change of state is listed with its reason, who and when.
 */
@Component({
  selector: 'app-neighbor-page',
  imports: [RouterLink, ReactiveFormsModule, DecimalPipe, DistrictSelect, ReasonConfirm],
  templateUrl: './neighbor.page.html',
  styleUrl: './neighbor.page.css',
})
export class NeighborPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  readonly id = input.required<string>();

  protected readonly registered = justRegistered();
  protected readonly days = Object.entries(DAYS) as [DayOfWeek, string][];

  protected readonly neighbor = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.neighbor(id),
  });
  protected readonly active = computed(() => this.neighbor.hasValue() && this.neighbor.value().status === 'ACTIVE');
  protected readonly inactive = computed(() => this.neighbor.hasValue() && this.neighbor.value().status === 'INACTIVE');
  /** The district as the catalog names it, or the old free text of a neighbour registered before v3. */
  protected readonly districtName = computed(() => {
    if (!this.neighbor.hasValue()) {
      return null;
    }
    const n = this.neighbor.value();
    return districtByUbigeo(n.districtUbigeo)?.name ?? (n.district?.trim() || null);
  });

  // ---- Status history: newest first, with why and who.
  protected readonly statusChanges = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.neighborStatusChanges(id),
  });
  protected readonly historyRows = computed(() =>
    this.statusChanges.hasValue()
      ? this.statusChanges.value().map((c) => ({
          id: c.id,
          what: c.toStatus === 'INACTIVE' ? 'Desactivado' : 'Reactivado',
          when: formatDate(c.changedAt, 'dd/MM/yyyy HH:mm', LOCALE, LIMA_OFFSET),
          who: c.actorEmail?.trim() || 'Personal (sin correo en la sesión)',
          reason: c.reason,
        }))
      : [],
  );

  // ---- Edit.
  protected readonly editing = signal(false);
  protected readonly editForm = this.fb.group({
    fullName: ['', [Validators.required, notBlank]],
    address: ['', [Validators.required, notBlank]],
    districtUbigeo: this.fb.control<string | null>(null, Validators.required),
    phone: [''],
  });
  protected readonly editSubmitted = signal(false);
  protected readonly saving = signal(false);
  protected readonly editError = signal<string | null>(null);
  protected readonly neighborStatus = signal('');
  private readonly editFormEl = viewChild<ElementRef<HTMLFormElement>>('editFormEl');
  private readonly editButton = viewChild<ElementRef<HTMLButtonElement>>('editButton');

  // ---- Deactivate / reactivate / delete: one panel open at a time.
  protected readonly panel = signal<NeighborTransition | 'delete' | null>(null);
  protected readonly panelBusy = signal(false);
  protected readonly panelError = signal<string | null>(null);
  /** After a reactivation: its schedules stay paused, so they are offered right there (D8). */
  protected readonly justReactivated = signal(false);
  private readonly reasonPanel = viewChild(ReasonConfirm);
  private readonly stateActions = viewChild<ElementRef<HTMLElement>>('stateActions');

  protected readonly schedules = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.schedules(id).pipe(map((page) => [...page.content].sort(byWeekday))),
  });
  protected readonly scheduleRows = computed(() =>
    this.schedules.hasValue()
      ? this.schedules.value().map((s) => ({ ...s, name: scheduleName(s), label: `${DAYS[s.dayOfWeek]} ${s.time.slice(0, 5)}`, state: STATE[s.status] }))
      : [],
  );
  protected readonly pausedRows = computed(() => this.scheduleRows().filter((s) => s.status === 'PAUSED'));

  // ---- Records: newest first, optionally between two dates (both inclusive).
  protected readonly recordsPage = signal(0);
  protected readonly range = signal<{ from: string | null; to: string | null }>({ from: null, to: null });
  protected readonly records = rxResource({
    params: () => ({ id: this.id(), page: this.recordsPage(), ...this.range() }),
    stream: ({ params: p }) => this.api.collectionRecords(p.id, p.page, p.from, p.to),
  });
  /** Association names for the records; a failure only leaves the column without names. */
  private readonly associations = rxResource({ stream: () => this.api.associations() });
  /** The last records page answered stays on screen (busy) while the next page or range loads. */
  private readonly recordsShown = linkedSignal<
    { id: string; page: Page<CollectionRecord> | undefined },
    Page<CollectionRecord> | null
  >({
    source: () => ({ id: this.id(), page: this.records.hasValue() ? this.records.value() : undefined }),
    // Kept only for the same neighbour: another neighbour's rows are never shown as this one's.
    computation: (current, previous) =>
      current.page ?? (previous !== undefined && previous.source.id === current.id ? previous.value : null),
  });
  protected readonly recordRows = computed(() => {
    const page = this.recordsShown();
    if (page === null) {
      return null;
    }
    const names = new Map(this.associations.hasValue() ? this.associations.value().content.map((a) => [a.id, a.name]) : []);
    const schedules = new Map((this.schedules.hasValue() ? this.schedules.value() : []).map((s) => [s.id, `${DAYS[s.dayOfWeek]} ${s.time.slice(0, 5)}`]));
    return {
      page,
      items: page.content.map((r) => ({
        id: r.id,
        date: formatLocalDate(r.collectionDate),
        kilos: formatKg(r.weightKg),
        association: names.get(r.associationId) ?? 'Asociación no listada',
        schedule: r.scheduleId === null ? 'Sin cronograma' : (schedules.get(r.scheduleId) ?? '—'),
      })),
    };
  });
  /** Known to have collections (only while no date range hides some): it can't be deleted. */
  protected readonly hasRecords = computed(() => {
    const r = this.range();
    const page = this.records.hasValue() ? this.records.value() : null;
    return r.from === null && r.to === null && page !== null && page.totalElements > 0;
  });
  protected readonly rangeForm = this.fb.group({ from: [''], to: [''] });
  protected readonly rangeError = signal<string | null>(null);

  // ---- Schedule writes.
  protected readonly scheduleForm = this.fb.group({
    dayOfWeek: ['' as DayOfWeek | '', Validators.required],
    time: ['', Validators.required],
  });
  protected readonly scheduleSubmitted = signal(false);
  protected readonly adding = signal(false);
  protected readonly addError = signal<string | null>(null);
  /** The schedule whose "Cancelar" is waiting for confirmation (final: it can't be reactivated). */
  protected readonly confirmingCancel = signal<string | null>(null);
  protected readonly busy = signal<string | null>(null);
  protected readonly rowError = signal<{ id: string; message: string } | null>(null);
  /** Polite announcement of the last schedule change. */
  protected readonly scheduleStatus = signal('');

  private readonly confirmPrompt = viewChild<ElementRef<HTMLElement>>('confirmPrompt');
  private readonly scheduleList = viewChild<ElementRef<HTMLElement>>('scheduleList');
  private readonly rowProblem = viewChild<ElementRef<HTMLElement>>('rowProblem');

  protected neighborState(status: Neighbor['status']): string {
    return NEIGHBOR_STATE[status];
  }

  protected editInvalid(name: 'fullName' | 'address' | 'districtUbigeo'): boolean {
    const control = this.editForm.controls[name];
    return control.invalid && (control.touched || this.editSubmitted());
  }

  protected startEdit(): void {
    if (!this.neighbor.hasValue()) {
      return;
    }
    const n = this.neighbor.value();
    this.editForm.reset({ fullName: n.fullName, address: n.address, districtUbigeo: n.districtUbigeo, phone: n.phone ?? '' });
    this.editSubmitted.set(false);
    this.editError.set(null);
    this.neighborStatus.set('');
    this.editing.set(true);
    setTimeout(() => this.editFormEl()?.nativeElement.querySelector<HTMLElement>('input')?.focus());
  }

  protected cancelEdit(): void {
    this.editing.set(false);
    setTimeout(() => this.editButton()?.nativeElement.focus());
  }

  protected saveEdit(): void {
    this.editSubmitted.set(true);
    if (this.saving()) {
      return;
    }
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      setTimeout(() => this.editFormEl()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const v = this.editForm.getRawValue();
    this.editError.set(null);
    this.saving.set(true);
    this.api
      .editNeighbor(this.id(), {
        fullName: v.fullName.trim(),
        address: v.address.trim(),
        districtUbigeo: v.districtUbigeo as string,
        phone: optional(v.phone),
      })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.neighbor.set(updated);
          this.editing.set(false);
          this.neighborStatus.set('Datos del vecino guardados.');
          setTimeout(() => this.editButton()?.nativeElement.focus());
        },
        error: (error: unknown) => this.editError.set(writeErrorText(error, EDIT_UNCERTAIN)),
      });
  }

  protected openPanel(panel: NeighborTransition | 'delete'): void {
    this.panel.set(panel);
    this.panelError.set(null);
    this.neighborStatus.set('');
    setTimeout(() => this.reasonPanel()?.focus());
  }

  protected closePanel(): void {
    const closed = this.panel();
    this.panel.set(null);
    this.panelError.set(null);
    setTimeout(() => this.stateActions()?.nativeElement.querySelector<HTMLElement>(`[data-panel="${closed}"]`)?.focus());
  }

  protected changeStatus(transition: NeighborTransition, reason: string): void {
    if (this.panelBusy()) {
      return;
    }
    this.panelError.set(null);
    this.panelBusy.set(true);
    this.api
      .changeNeighborStatus(this.id(), transition, reason)
      .pipe(
        finalize(() => this.panelBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.neighbor.set(updated);
          this.panel.set(null);
          this.justReactivated.set(transition === 'activate');
          this.neighborStatus.set(
            transition === 'deactivate'
              ? 'Vecino desactivado. Sus cronogramas activos quedaron pausados.'
              : 'Vecino reactivado. Sus cronogramas siguen pausados: reactívalos uno por uno.',
          );
          this.statusChanges.reload();
          this.schedules.reload();
          setTimeout(() => this.stateActions()?.nativeElement.focus());
        },
        error: (error: unknown) => {
          this.panelError.set(
            writeErrorText(error, STATUS_UNCERTAIN, {
              'COL-012': 'El vecino ya estaba en ese estado. Vuelve a cargar la página para ver el actual.',
            }),
          );
          this.reasonPanel()?.focusError();
        },
      });
  }

  protected deleteNeighbor(reason: string): void {
    if (this.panelBusy() || !this.neighbor.hasValue()) {
      return;
    }
    const name = this.neighbor.value().fullName;
    this.panelError.set(null);
    this.panelBusy.set(true);
    this.api
      .deleteNeighbor(this.id(), reason)
      .pipe(
        finalize(() => this.panelBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => void this.router.navigate(['/vecinos'], { state: { eliminado: name } }),
        error: (error: unknown) => {
          this.panelError.set(writeErrorText(error, DELETE_UNCERTAIN, { 'COL-011': COL_011_TEXT }));
          this.reasonPanel()?.focusError();
        },
      });
  }

  protected notFound(error: unknown): boolean {
    return error instanceof ApiError && (error.status === 404 || error.code === 'VALIDATION_ERROR');
  }

  protected errorText(error: unknown, what: string): string {
    return loadErrorMessage(error, what, SERVICE);
  }

  protected scheduleInvalid(name: 'dayOfWeek' | 'time'): boolean {
    const control = this.scheduleForm.controls[name];
    return control.invalid && (control.touched || this.scheduleSubmitted());
  }

  protected addSchedule(): void {
    this.scheduleSubmitted.set(true);
    if (this.adding()) {
      return;
    }
    if (this.scheduleForm.invalid) {
      this.scheduleForm.markAllAsTouched();
      return;
    }
    const { dayOfWeek, time } = this.scheduleForm.getRawValue();
    const request = { dayOfWeek: dayOfWeek as DayOfWeek, time };
    this.addError.set(null);
    this.scheduleStatus.set('');
    this.adding.set(true);
    this.api
      .createSchedule(this.id(), request)
      .pipe(
        finalize(() => this.adding.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (created) => {
          this.scheduleForm.reset({ dayOfWeek: '', time: '' });
          this.scheduleSubmitted.set(false);
          this.scheduleStatus.set(`Cronograma del ${scheduleName(created)} agregado.`);
          this.schedules.reload();
        },
        error: (error: unknown) =>
          this.addError.set(
            writeErrorText(
              error,
              'No pudimos confirmar si el cronograma quedó agregado. Vuelve a cargar los cronogramas antes de intentarlo otra vez.',
              {
                'COL-002': `Este vecino ya tiene un cronograma activo el ${DAYS[request.dayOfWeek].toLowerCase()}. Pausa o cancela ese antes de agregar otro.`,
                'COL-013': 'El vecino está inactivo: reactívalo antes de agregarle cronogramas.',
              },
            ),
          ),
      });
  }

  protected askCancel(schedule: CollectionSchedule): void {
    this.rowError.set(null);
    this.confirmingCancel.set(schedule.id);
    setTimeout(() => this.confirmPrompt()?.nativeElement.focus());
  }

  protected keep(): void {
    const id = this.confirmingCancel();
    this.confirmingCancel.set(null);
    setTimeout(() => this.scheduleList()?.nativeElement.querySelector<HTMLElement>(`[data-cancel="${id}"]`)?.focus());
  }

  protected transition(schedule: CollectionSchedule & { name: string }, transition: ScheduleTransition): void {
    if (this.busy() !== null) {
      return;
    }
    this.rowError.set(null);
    this.scheduleStatus.set('');
    this.busy.set(schedule.id);
    this.api
      .transitionSchedule(this.id(), schedule.id, transition)
      .pipe(
        finalize(() => this.busy.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.confirmingCancel.set(null);
          this.scheduleStatus.set(`Cronograma del ${schedule.name} ${DONE[transition]}.`);
          this.schedules.reload();
          setTimeout(() => this.scheduleList()?.nativeElement.focus());
        },
        error: (error: unknown) => {
          this.confirmingCancel.set(null);
          this.rowError.set({
            id: schedule.id,
            message: writeErrorText(
              error,
              'No pudimos confirmar si el cambio se aplicó. Vuelve a cargar los cronogramas para ver su estado.',
              {
                'COL-008': 'El cronograma ya cambió de estado. Vuelve a cargar los cronogramas para ver el actual.',
                'COL-002': `No se puede reactivar: ya hay otro cronograma activo el ${DAYS[schedule.dayOfWeek].toLowerCase()}.`,
                'COL-013': 'El vecino está inactivo: reactiva primero al vecino y luego sus cronogramas.',
              },
            ),
          });
          // The button that was pressed may be gone (the confirmation closed): focus the reason.
          setTimeout(() => this.rowProblem()?.nativeElement.focus());
        },
      });
  }

  protected applyRange(): void {
    const { from, to } = this.rangeForm.getRawValue();
    const f = LOCAL_DATE.test(from) ? from : null;
    const t = LOCAL_DATE.test(to) ? to : null;
    if (f !== null && t !== null && f > t) {
      this.rangeError.set('La fecha «desde» debe ser anterior o igual a «hasta».');
      return;
    }
    this.rangeError.set(null);
    this.recordsPage.set(0);
    this.range.set({ from: f, to: t });
  }

  protected clearRange(): void {
    this.rangeForm.reset({ from: '', to: '' });
    this.rangeError.set(null);
    this.recordsPage.set(0);
    this.range.set({ from: null, to: null });
  }
}

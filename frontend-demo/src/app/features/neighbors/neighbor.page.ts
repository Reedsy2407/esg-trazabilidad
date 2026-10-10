import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, input, linkedSignal, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, map } from 'rxjs';

import { CollectionRecord, CollectionSchedule, DayOfWeek, Page, ScheduleTransition } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { DAYS, byWeekday } from '../../shared/collection-labels';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { formatKg, formatLocalDate } from '../../shared/format';

const SERVICE = 'el servicio de recojos';
const LOCAL_DATE = /^\d{4}-\d{2}-\d{2}$/;

const STATE: Record<CollectionSchedule['status'], string> = { ACTIVE: 'Activo', PAUSED: 'Pausado', CANCELLED: 'Cancelado' };
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
 */
@Component({
  selector: 'app-neighbor-page',
  imports: [RouterLink, ReactiveFormsModule, DecimalPipe],
  templateUrl: './neighbor.page.html',
  styleUrl: './neighbor.page.css',
})
export class NeighborPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  readonly id = input.required<string>();

  protected readonly registered = justRegistered();
  protected readonly days = Object.entries(DAYS) as [DayOfWeek, string][];

  protected readonly neighbor = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.neighbor(id),
  });

  protected readonly schedules = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.schedules(id).pipe(map((page) => [...page.content].sort(byWeekday))),
  });
  protected readonly scheduleRows = computed(() =>
    this.schedules.hasValue()
      ? this.schedules.value().map((s) => ({ ...s, name: scheduleName(s), label: `${DAYS[s.dayOfWeek]} ${s.time.slice(0, 5)}`, state: STATE[s.status] }))
      : [],
  );

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
              { 'COL-002': `Este vecino ya tiene un cronograma activo el ${DAYS[request.dayOfWeek].toLowerCase()}. Pausa o cancela ese antes de agregar otro.` },
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

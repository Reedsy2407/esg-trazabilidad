import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize, map } from 'rxjs';

import { CollectionRecord, CollectionSchedule, CollectionScheduleStatus, DayOfWeek } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { formatKg, formatLocalDate, todayInLima } from '../../shared/format';

/**
 * weight_kg is numeric(10,2): at most 8 integer digits and 2 decimals. A dot
 * for decimals, as the app shows kilos ("12,480.50 kg"); no thousands
 * separator, so "1,5" is never read as one and a half or as fifteen.
 */
const WEIGHT = /^\d{1,8}(\.\d{1,2})?$/;

function weightValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  if (value === '') {
    return null; // `required` speaks for an empty field
  }
  if (!WEIGHT.test(value)) {
    return { format: true };
  }
  return Number(value) > 0 ? null : { positive: true };
}

const DAYS: Record<DayOfWeek, string> = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
};

const DAY_ORDER = Object.keys(DAYS) as DayOfWeek[];

/** Monday to Sunday, then by time: the API's order is alphabetical (dayOfWeek is stored as text). */
export function byWeekday(a: CollectionSchedule, b: CollectionSchedule): number {
  return DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek) || a.time.localeCompare(b.time);
}

const SCHEDULE_STATUS: Record<CollectionScheduleStatus, string> = {
  ACTIVE: '',
  PAUSED: ' (pausado)',
  CANCELLED: ' (cancelado)',
};

/** "Lunes 08:00", plus its state when it isn't active. LocalTime comes as "08:00" or "08:00:00". */
export function scheduleLabel(schedule: CollectionSchedule): string {
  return `${DAYS[schedule.dayOfWeek]} ${schedule.time.slice(0, 5)}${SCHEDULE_STATUS[schedule.status]}`;
}

/**
 * One sentence per backend answer, in es-PE. The UI branches on `code`;
 * 401 never gets here (errorInterceptor ends the session).
 */
export function submitErrorText(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'No se pudo registrar el recojo. Inténtalo de nuevo.';
  }
  switch (error.code) {
    case 'COL-001':
      return 'El vecino elegido ya no existe. Elige otro.';
    case 'COL-006':
      return 'El cronograma elegido ya no existe o no es de este vecino. Elige otro o deja "Sin cronograma".';
    case 'COL-009':
      return 'La asociación tiene una certificación vencida y no puede registrar recojos.';
    case 'VALIDATION_ERROR':
      return 'El servicio rechazó los datos. Revisa la fecha y el peso.';
  }
  switch (error.status) {
    case 0:
      return 'No se pudo conectar con el servicio de recojos. Comprueba tu conexión; el recojo no se registró.';
    case 429:
      return error.retryAfterSeconds === null
        ? 'Demasiadas solicitudes. Espera un momento y vuelve a intentarlo.'
        : `Demasiadas solicitudes. Vuelve a intentarlo en ${error.retryAfterSeconds} s.`;
    default:
      return `No se pudo registrar el recojo (${error.code ?? `HTTP ${error.status}`}). Inténtalo de nuevo.`;
  }
}

function loadErrorText(error: unknown, what: string): string {
  if (error instanceof ApiError && error.status === 0) {
    return `No se pudo conectar para cargar ${what}.`;
  }
  return `No se pudo cargar ${what}${error instanceof ApiError ? ` (${error.code ?? `HTTP ${error.status}`})` : ''}.`;
}

/**
 * Registers one collection (POST /neighbors/{id}/collection-records). Only
 * the request's real fields: neighbor (the path), association, date and
 * weight required, schedule optional. Inactive neighbors, suspended
 * associations and paused or cancelled schedules are listed with their state
 * rather than hidden: the backend decides what it accepts (COL-009 for a
 * blocked association), the form doesn't invent rules.
 */
@Component({
  selector: 'app-collection-record-page',
  imports: [ReactiveFormsModule],
  templateUrl: './collection-record.page.html',
  styleUrl: './collection-record.page.css',
})
export class CollectionRecordPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = this.fb.group({
    neighborId: ['', Validators.required],
    associationId: ['', Validators.required],
    collectionDate: [todayInLima(), Validators.required],
    weightKg: ['', [Validators.required, weightValidator]],
    scheduleId: [''],
  });

  protected readonly neighbors = rxResource({ stream: () => this.api.neighbors() });
  protected readonly associations = rxResource({ stream: () => this.api.associations() });

  private readonly neighborId = toSignal(this.form.controls.neighborId.valueChanges, { initialValue: '' });
  protected readonly schedules = rxResource({
    params: () => this.neighborId() || undefined,
    stream: ({ params: neighborId }) => this.api.schedules(neighborId).pipe(map((page) => page.content)),
  });

  protected readonly neighborOptions = computed(() =>
    this.neighbors.hasValue()
      ? this.neighbors.value().content.map((n) => ({
          id: n.id,
          label: `${n.fullName} · ${n.district}${n.status === 'INACTIVE' ? ' (inactivo)' : ''}`,
        }))
      : [],
  );
  protected readonly associationOptions = computed(() =>
    this.associations.hasValue()
      ? this.associations.value().content.map((a) => ({
          id: a.id,
          label: `${a.name}${a.status === 'SUSPENDED' ? ' (suspendida)' : ''}`,
        }))
      : [],
  );
  protected readonly scheduleOptions = computed(() =>
    this.schedules.hasValue()
      ? [...this.schedules.value()].sort(byWeekday).map((s) => ({ id: s.id, label: scheduleLabel(s) }))
      : [],
  );
  /** More rows than one picker page: say so instead of silently cutting the list. */
  protected readonly neighborsTruncated = computed(
    () => this.neighbors.hasValue() && this.neighbors.value().totalElements > this.neighbors.value().content.length,
  );
  protected readonly associationsTruncated = computed(
    () =>
      this.associations.hasValue() && this.associations.value().totalElements > this.associations.value().content.length,
  );

  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('formElement');
  private readonly savedTitle = viewChild<ElementRef<HTMLElement>>('savedTitle');
  private readonly weightInput = viewChild<ElementRef<HTMLInputElement>>('weightInput');

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saved = signal<{ record: CollectionRecord; neighbor: string; kilos: string; date: string } | null>(
    null,
  );

  constructor() {
    // A schedule belongs to one neighbor: changing the neighbor drops it.
    this.form.controls.neighborId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.scheduleId.setValue(''));
    // A rejection is about what was sent: once the user edits, it no longer applies.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.error.set(null));
  }

  protected fieldInvalid(name: 'neighborId' | 'associationId' | 'collectionDate' | 'weightKg'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  /** aria-describedby from the ids that apply, or none. */
  protected describedBy(...ids: (string | null)[]): string | null {
    const present = ids.filter((id) => id !== null);
    return present.length === 0 ? null : present.join(' ');
  }

  protected loadError(error: unknown, what: string): string {
    return loadErrorText(error, what);
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Take the user to the first field to fix, after the errors render.
      setTimeout(() =>
        this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
      );
      return;
    }
    const value = this.form.getRawValue();
    const neighbor = this.neighborOptions().find((n) => n.id === value.neighborId)?.label ?? '';
    this.error.set(null);
    this.saved.set(null);
    this.submitting.set(true);
    this.api
      .createCollectionRecord(value.neighborId, {
        associationId: value.associationId,
        collectionDate: value.collectionDate,
        weightKg: Number(value.weightKg.trim()),
        scheduleId: value.scheduleId === '' ? null : value.scheduleId,
      })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (record) => {
          this.saved.set({
            record,
            neighbor,
            kilos: formatKg(record.weightKg),
            date: formatLocalDate(record.collectionDate),
          });
          // The submit button just left the page: focus the confirmation instead of losing it to <body>.
          setTimeout(() => this.savedTitle()?.nativeElement.focus());
        },
        error: (error: unknown) => this.error.set(submitErrorText(error)),
      });
  }

  /** Same neighbor, association and date (a round usually logs several); weight and schedule start over. */
  protected another(): void {
    this.saved.set(null);
    this.submitted.set(false);
    this.form.controls.weightKg.reset('');
    this.form.controls.scheduleId.setValue('');
    setTimeout(() => this.weightInput()?.nativeElement.focus());
  }
}

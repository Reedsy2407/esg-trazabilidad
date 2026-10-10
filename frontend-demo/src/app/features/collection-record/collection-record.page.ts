import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize, map } from 'rxjs';

import { CollectionRecord, CollectionSchedule, CollectionScheduleStatus, DayOfWeek, Neighbor } from '../../core/api/api.types';
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
 * The POST isn't idempotent, and with no answer (network drop, CORS, timeout) or a 408/5xx
 * (e.g. Render's 502/504 while a service wakes) it may already have been processed.
 */
export const UNCERTAIN_OUTCOME =
  'No pudimos confirmar si el recojo quedó registrado. Espera un momento y revisa antes de volver a intentarlo, para no duplicarlo.';

/**
 * "Name · District (inactivo)": the district is optional in the API (null, or blank), so a
 * neighbour without one shows just the name, never " · null".
 */
export function neighborLabel(n: Pick<Neighbor, 'fullName' | 'district' | 'status'>): string {
  const district = n.district?.trim();
  return `${n.fullName}${district ? ` · ${district}` : ''}${n.status === 'INACTIVE' ? ' (inactivo)' : ''}`;
}

/**
 * One sentence per backend answer, in es-PE. The UI branches on `code`;
 * 401 never gets here (errorInterceptor ends the session). Never claims the
 * record wasn't saved when the outcome is unknown (see UNCERTAIN_OUTCOME).
 */
export function submitErrorText(error: unknown): string {
  // Checked before any code: a 5xx with a code would still be an unknown outcome, and an
  // error that isn't an ApiError gives no evidence either way.
  if (!(error instanceof ApiError) || error.status === 0 || error.status === 408 || error.status >= 500) {
    return UNCERTAIN_OUTCOME;
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
          label: neighborLabel(n),
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
  /** The form's current value, as a signal, so the preview follows every keystroke. */
  private readonly formValue = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  /**
   * The ticket this record will print, built only from the request's real fields. Presentational:
   * the form already says all of it, so the preview is aria-hidden (no duplicate announcements).
   */
  protected readonly preview = computed(() => {
    const v = this.formValue();
    const pick = (options: { id: string; label: string }[], id: string) => options.find((o) => o.id === id)?.label ?? null;
    const weight = v.weightKg.trim();
    return {
      neighbor: pick(this.neighborOptions(), v.neighborId),
      association: pick(this.associationOptions(), v.associationId),
      date: /^\d{4}-\d{2}-\d{2}$/.test(v.collectionDate) ? formatLocalDate(v.collectionDate) : null,
      schedule: v.scheduleId === '' ? 'Sin cronograma' : pick(this.scheduleOptions(), v.scheduleId),
      kilos: weight !== '' && weightValidator(this.form.controls.weightKg) === null ? formatKg(Number(weight)) : null,
    };
  });

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

import { Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize, map } from 'rxjs';

import { CollectionRecord } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { CollectionLogService } from '../../core/session/collection-log.service';
import { rememberAssociation, rememberedAssociation } from '../../core/session/remembered-association';
import { BrowseMode } from '../../shared/browse-mode';
import { byWeekday, neighborLabel, scheduleLabel } from '../../shared/collection-labels';
import { loadErrorMessage } from '../../shared/error-message';
import { formatKg, formatLocalDate, todayInLima } from '../../shared/format';

/**
 * weight_kg is numeric(10,2): at most 8 integer digits and 2 decimals. A dot
 * for decimals, as the app shows kilos ("12,480.50 kg"); no thousands
 * separator, so "1,5" is never read as one and a half or as fifteen.
 */
const WEIGHT = /^\d{1,8}(\.\d{1,2})?$/;

/** Lower case, without accents: "quispe" finds "Quispe", "nunez" finds "Núñez". */
export function searchKey(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}

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

// Re-exported: the page's spec and older imports use them from here.
export { byWeekday, neighborLabel, scheduleLabel } from '../../shared/collection-labels';

/**
 * The POST isn't idempotent, and with no answer (network drop, CORS, timeout) or a 408/5xx
 * (e.g. Render's 502/504 while a service wakes) it may already have been processed.
 */
export const UNCERTAIN_OUTCOME =
  'No pudimos confirmar si el recojo quedó registrado. Espera un momento y revisa antes de volver a intentarlo, para no duplicarlo.';

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
    case 'COL-013':
      return 'El vecino está inactivo y no puede recibir recojos. Reactívalo desde su ficha o elige otro.';
    case 'VALIDATION_ERROR':
      return 'El servicio rechazó los datos. Revisa la fecha y el peso.';
  }
  switch (error.status) {
    case 429:
      return error.retryAfterSeconds === null
        ? 'Demasiadas solicitudes. Espera un momento y vuelve a intentarlo.'
        : `Demasiadas solicitudes. Vuelve a intentarlo en ${error.retryAfterSeconds} s.`;
    default:
      return 'No se pudo registrar el recojo. Inténtalo de nuevo.';
  }
}

/**
 * Registers one collection (POST /neighbors/{id}/collection-records). Only
 * the request's real fields: neighbor (the path), association, date and
 * weight required, schedule optional. Inactive neighbors, suspended
 * associations and paused or cancelled schedules are listed with their state
 * rather than hidden: the backend decides what it accepts (COL-009 for a
 * blocked association, COL-013 for an inactive neighbour), the form doesn't
 * invent rules.
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
  protected readonly log = inject(CollectionLogService);

  /** ?vecino=<id>, from a neighbour's page: that neighbour starts chosen. */
  readonly vecino = input<string | undefined>(undefined);

  protected readonly form = this.fb.group({
    neighborId: ['', Validators.required],
    associationId: ['', Validators.required],
    collectionDate: [todayInLima(), Validators.required],
    weightKg: ['', [Validators.required, weightValidator]],
    scheduleId: [''],
  });

  protected readonly neighbors = rxResource({ stream: () => this.api.neighbors() });
  protected readonly associations = rxResource({ stream: () => this.api.associations() });

  protected readonly neighborId = toSignal(this.form.controls.neighborId.valueChanges, { initialValue: '' });
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
  /**
   * The neighbour combobox: the text in the box, whether its list is open and the highlighted
   * row. The list narrows the loaded neighbours (the API has no name search); once one is chosen
   * the box shows its label and the whole list is offered again.
   */
  protected readonly neighborText = signal('');
  /** A click on a box that holds a neighbour empties it to browse; leaving without choosing restores it. */
  private readonly neighborBrowse = new BrowseMode();
  /** What the input shows: the text, or nothing while browsing. */
  protected readonly neighborShown = computed(() => this.neighborBrowse.show(this.neighborText()));
  protected readonly neighborOpen = signal(false);
  /** -1 until the user arrows or types: Enter then submits the form instead of choosing a row. */
  protected readonly neighborActive = signal(-1);
  private readonly neighborQuery = computed(() => (this.neighborId() ? '' : searchKey(this.neighborText())));
  protected readonly filteredNeighborOptions = computed(() => {
    const query = this.neighborQuery();
    const all = this.neighborOptions();
    return query === '' ? all : all.filter((n) => searchKey(n.label).includes(query));
  });
  /** "3 vecinos coinciden", said politely while typing; empty when not searching. */
  protected readonly neighborMatches = computed(() => {
    const query = this.neighborText().trim();
    if (this.neighborId() || query === '' || !this.neighbors.hasValue()) {
      return '';
    }
    const count = this.filteredNeighborOptions().length;
    return count === 0
      ? `Ningún vecino coincide con «${query}».`
      : `${count} ${count === 1 ? 'vecino coincide' : 'vecinos coinciden'} con «${query}».`;
  });

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

  /** The preview's big figure: the typed weight, or 0.00 (dimmed) until it is valid. */
  protected readonly previewFigure = computed(() => this.preview().kilos?.replace(' kg', '') ?? '0.00');
  /** Longer weights get a smaller figure so "99,999,999.99" still fits the display. */
  protected readonly previewSize = computed(() => {
    const length = this.previewFigure().length;
    return length <= 6 ? '20cqw' : length <= 9 ? '14cqw' : '10.5cqw';
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
    // A neighbour chosen from outside the box (?vecino=) shows its name in it.
    effect(() => {
      const id = this.neighborId();
      const label = this.neighborOptions().find((n) => n.id === id)?.label;
      if (id && label !== undefined) {
        untracked(() => this.neighborText.set(label));
      }
    });
    // A rejection is about what was sent: once the user edits, it no longer applies.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.error.set(null));

    // Once the lists arrive: the neighbour from ?vecino= and the association used last time,
    // each only if it is still in the list.
    // ?vecino= is applied once per value: a later link to another neighbour (same component,
    // new query) chooses that one, but re-rendering never undoes the user's own choice.
    let appliedVecino: string | undefined;
    effect(() => {
      const wanted = this.vecino();
      if (wanted && wanted !== appliedVecino && this.neighborOptions().some((n) => n.id === wanted)) {
        appliedVecino = wanted;
        untracked(() => this.form.controls.neighborId.setValue(wanted));
      }
    });
    effect(() => {
      const remembered = rememberedAssociation();
      if (remembered !== null && this.associationOptions().some((a) => a.id === remembered)) {
        untracked(() => {
          if (this.form.controls.associationId.value === '') {
            this.form.controls.associationId.setValue(remembered);
          }
        });
      }
    });
  }

  protected onNeighborInput(text: string): void {
    this.neighborBrowse.stop();
    this.neighborText.set(text);
    // Editing the text withdraws the choice: the box never shows one neighbour while sending another.
    if (this.form.controls.neighborId.value !== '') {
      this.form.controls.neighborId.setValue('');
    }
    this.neighborActive.set(0);
    this.neighborOpen.set(true);
  }

  protected openNeighbors(): void {
    if (!this.neighborOpen()) {
      this.neighborActive.set(-1);
      this.neighborOpen.set(true);
    }
  }

  /** A click: the chosen neighbour's name is hidden to browse the whole list; the choice itself stays. */
  protected clickNeighbors(): void {
    if (this.neighborId() !== '') {
      this.neighborBrowse.start(this.neighborText());
    }
    this.openNeighbors();
  }

  /** Set while validation moves the focus to the first field to fix: the list must not open over that field's own error. */
  private focusedByValidation = false;

  /** Focus by keyboard selects the text, so typing replaces it instead of continuing it. */
  protected focusNeighbors(event: FocusEvent): void {
    (event.target as HTMLInputElement).select();
    if (!this.focusedByValidation) {
      this.openNeighbors();
    }
  }

  private setActive(index: number): void {
    this.neighborActive.set(index);
    // Keep the highlighted row inside the list's 264 px window.
    setTimeout(() => document.getElementById('neighbor-opt-' + index)?.scrollIntoView({ block: 'nearest' }));
  }

  protected closeNeighbors(): void {
    this.neighborBrowse.stop();
    this.neighborOpen.set(false);
    this.form.controls.neighborId.markAsTouched();
  }

  protected pickNeighbor(id: string): void {
    this.neighborBrowse.stop();
    this.form.controls.neighborId.setValue(id);
    this.form.controls.neighborId.markAsTouched();
    this.neighborOpen.set(false);
  }

  protected onNeighborKey(event: KeyboardEvent): void {
    const count = this.filteredNeighborOptions().length;
    switch (event.key) {
      case 'ArrowDown':
      case 'ArrowUp': {
        event.preventDefault();
        if (!this.neighborOpen()) {
          this.neighborActive.set(-1);
          this.neighborOpen.set(true);
          return;
        }
        if (count > 0) {
          const current = this.neighborActive();
          const next = event.key === 'ArrowDown' ? (current + 1) % count : current <= 0 ? count - 1 : current - 1;
          this.setActive(next);
        }
        return;
      }
      case 'Enter': {
        // Open with a highlighted row: Enter chooses it. Closed: it submits the form as usual.
        const option = this.neighborActive() >= 0 ? this.filteredNeighborOptions()[this.neighborActive()] : undefined;
        if (this.neighborOpen() && option) {
          event.preventDefault();
          this.pickNeighbor(option.id);
        }
        return;
      }
      case 'Escape':
        if (this.neighborOpen()) {
          event.preventDefault();
          this.neighborBrowse.stop();
          this.neighborOpen.set(false);
        }
        return;
    }
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

  /** Neighbours and schedules come from collection-service, associations from recycler-service. */
  protected loadError(error: unknown, what: string): string {
    const service = what === 'las asociaciones' ? 'el servicio de recicladores' : 'el servicio de recojos';
    return loadErrorMessage(error, what, service);
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Take the user to the first field to fix, after the errors render.
      setTimeout(() => {
        this.focusedByValidation = true;
        this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
        this.focusedByValidation = false;
      });
      return;
    }
    const value = this.form.getRawValue();
    const neighbor = this.neighborOptions().find((n) => n.id === value.neighborId)?.label ?? '';
    const association = this.associationOptions().find((a) => a.id === value.associationId)?.label ?? '';
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
          const saved = { record, neighbor, kilos: formatKg(record.weightKg), date: formatLocalDate(record.collectionDate) };
          this.saved.set(saved);
          rememberAssociation(record.associationId);
          this.log.add({ id: record.id, neighbor, association, date: saved.date, kilos: saved.kilos });
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

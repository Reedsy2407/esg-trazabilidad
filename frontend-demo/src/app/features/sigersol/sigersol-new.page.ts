import { Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, map } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { decimalValidator } from '../../shared/decimal';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { periodLabel } from '../../shared/format';
import { ManualTag } from './manual-tag';

export const SIGERSOL_UNCERTAIN =
  'No pudimos confirmar si el dato SIGERSOL quedó registrado. Revisa los registros de la asociación antes de volver a intentarlo.';

/** source_note is varchar(255): longer would fail in the database (and come back as RPT-006). */
export const NOTE_MAX = 255;
const LOCAL_DATE = /^\d{4}-\d{2}-\d{2}$/;
const optionalDate = (value: string | undefined) => (value !== undefined && LOCAL_DATE.test(value) ? value : '');

/**
 * Registrar dato SIGERSOL (POST /sigersol-syncs), always as "ingreso manual": the period's official
 * figures for one association, typed in from SIGERSOL. Exactly RegisterSigersolSyncRequest, with
 * the columns' precision: compliance 0-100 with up to 2 decimals (decimal(5,2)), kilos up to 12
 * integer digits and 2 decimals (decimal(14,2)), note up to 255 characters.
 * ?asociacion=&desde=&hasta= prefill it (from a company whose period has no SIGERSOL record).
 */
@Component({
  selector: 'app-sigersol-new-page',
  imports: [ReactiveFormsModule, RouterLink, ManualTag],
  templateUrl: './sigersol-new.page.html',
  styles: `
    .lead app-manual-tag { margin-right: var(--space-2); }
    .num-field .control { max-width: 260px; text-align: right; }
    .counter { margin: var(--space-1) 0 0; color: var(--ink-soft); font-size: 13px; text-align: right; }
    textarea.control { height: auto; min-height: 92px; padding-top: var(--space-2); resize: vertical; }
    .load-problem { margin-bottom: var(--space-2); }
  `,
})
export class SigersolNewPage {
  private readonly api = inject(BackendApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly asociacion = input<string | undefined>(undefined);
  readonly desde = input<string | undefined>(undefined);
  readonly hasta = input<string | undefined>(undefined);

  protected readonly noteMax = NOTE_MAX;
  protected readonly form = this.fb.group({
    associationId: ['', Validators.required],
    periodStart: ['', Validators.required],
    periodEnd: ['', Validators.required],
    compliance: ['', [Validators.required, decimalValidator(3, 2, 0, 100)]],
    kilos: ['', decimalValidator(12, 2, 0)],
    note: ['', Validators.maxLength(NOTE_MAX)],
  });

  protected readonly associations = rxResource({ stream: () => this.api.associations() });
  protected readonly associationsTruncated = computed(
    () => this.associations.hasValue() && this.associations.value().totalElements > this.associations.value().content.length,
  );
  protected readonly associationOptions = computed(() =>
    this.associations.hasValue()
      ? this.associations.value().content.map((a) => ({ id: a.id, label: `${a.name}${a.status === 'SUSPENDED' ? ' (suspendida)' : ''}` }))
      : [],
  );

  private readonly value = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  /** The backend's RPT-008 rule, checked before sending: the end can't be before the start (equal is fine). */
  protected readonly periodBackwards = computed(() => {
    const { periodStart, periodEnd } = this.value();
    return LOCAL_DATE.test(periodStart) && LOCAL_DATE.test(periodEnd) && periodEnd < periodStart;
  });
  protected readonly periodText = computed(() => {
    const { periodStart, periodEnd } = this.value();
    return LOCAL_DATE.test(periodStart) && LOCAL_DATE.test(periodEnd) && !this.periodBackwards()
      ? periodLabel(periodStart, periodEnd)
      : null;
  });
  protected readonly noteLength = computed(() => this.value().note.length);

  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('formElement');
  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Where to check after an overlap or an unknown outcome: the association's records. */
  protected readonly checkAssociation = signal<string | null>(null);

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.error.set(null);
      this.checkAssociation.set(null);
    });
    // Prefill from the address once: dates at once, the association when it is listed.
    effect(() => {
      const desde = optionalDate(this.desde());
      const hasta = optionalDate(this.hasta());
      untracked(() => {
        if (desde !== '' && this.form.controls.periodStart.value === '') {
          this.form.controls.periodStart.setValue(desde);
        }
        if (hasta !== '' && this.form.controls.periodEnd.value === '') {
          this.form.controls.periodEnd.setValue(hasta);
        }
      });
    });
    effect(() => {
      const wanted = this.asociacion();
      if (wanted && this.associationOptions().some((a) => a.id === wanted)) {
        untracked(() => {
          if (this.form.controls.associationId.value === '') {
            this.form.controls.associationId.setValue(wanted);
          }
        });
      }
    });
  }

  protected invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    const periodError = (name === 'periodEnd' || name === 'periodStart') && this.periodBackwards();
    return (control.invalid || periodError) && (control.touched || this.submitted());
  }

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'las asociaciones', 'el servicio de recicladores');
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid || this.periodBackwards()) {
      this.form.markAllAsTouched();
      setTimeout(() => this.formElement()?.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const v = this.form.getRawValue();
    const request = {
      associationId: v.associationId,
      periodStart: v.periodStart,
      periodEnd: v.periodEnd,
      hierarchyCompliancePercent: Number(v.compliance.trim()),
      officialKilosDeclared: v.kilos.trim() === '' ? null : Number(v.kilos.trim()),
      sourceNote: v.note.trim() === '' ? null : v.note.trim(),
    };
    this.submitting.set(true);
    this.api
      .registerSigersolSync(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () =>
          void this.router.navigate(['/sigersol'], { queryParams: { asociacion: request.associationId }, state: { registrado: true } }),
        error: (error: unknown) => {
          this.error.set(
            writeErrorText(error, SIGERSOL_UNCERTAIN, {
              'RPT-006':
                'Esta asociación ya tiene un registro SIGERSOL que se superpone con ese período (compartir un solo día también cuenta). Cada día puede estar en un solo registro.',
              'RPT-008': 'El servicio rechazó el período: la fecha final no puede ser anterior a la inicial.',
            }),
          );
          const uncertain = !(error instanceof ApiError) || error.status === 0 || error.status === 408 || error.status >= 500;
          const overlap = error instanceof ApiError && error.status === 409 && error.code === 'RPT-006';
          this.checkAssociation.set(uncertain || overlap ? request.associationId : null);
        },
      });
  }
}

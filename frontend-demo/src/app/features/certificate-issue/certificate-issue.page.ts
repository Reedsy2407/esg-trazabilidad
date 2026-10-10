import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { formatKg, formatLocalDate, formatPercent, periodLabel, todayInLima } from '../../shared/format';
import { ManualTag } from '../sigersol/manual-tag';
import { Period, monthPeriod, overlaps, periodProblem, previousMonth } from './issue-period';

export const ISSUE_UNCERTAIN =
  'No pudimos confirmar si el certificado se emitió. Revisa los certificados de la empresa antes de volver a intentarlo: si ya se emitió, un nuevo intento será rechazado por superponerse.';

type Mode = 'mes' | 'rango';
type ErrorKind = 'overlap' | 'sigersol' | 'uncertain' | 'other';

/**
 * Emitir certificado (POST /tracked-companies/{id}/certificates). IRREVERSIBLE: no endpoint edits
 * or voids a certificate. Three steps on one page: choose the period (a whole month, or a free
 * range); review the draft, recomputed live by GET certificate-summary (nothing is issued); confirm
 * with a checkbox and a button that names the period. Never offered: a period that hasn't ended
 * (Lima), 0 kg, a period without SIGERSOL coverage, one overlapping a listed certificate.
 */
@Component({
  selector: 'app-certificate-issue-page',
  imports: [RouterLink, ManualTag],
  templateUrl: './certificate-issue.page.html',
  styleUrl: './certificate-issue.page.css',
})
export class CertificateIssuePage {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly id = input.required<string>();

  private readonly today = todayInLima();

  protected readonly company = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.trackedCompany(id),
  });
  /** Only for the draft's "Asociación" line; failing leaves the line without a name. */
  protected readonly association = rxResource({
    params: () => (this.company.hasValue() ? this.company.value().associationId : undefined),
    stream: ({ params: id }) => this.api.association(id),
  });
  /** The latest certificates, to warn about an overlap before asking; the backend decides (RPT-004). */
  private readonly certificates = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.certificates(id),
  });

  // ---- Step 1: the period.
  protected readonly mode = signal<Mode>('mes');
  protected readonly month = signal(previousMonth(this.today));
  protected readonly from = signal('');
  protected readonly to = signal('');

  protected readonly period = computed<Period | null>(() => {
    if (this.mode() === 'mes') {
      return monthPeriod(this.month());
    }
    const from = this.from();
    const to = this.to();
    return /^\d{4}-\d{2}-\d{2}$/.test(from) && /^\d{4}-\d{2}-\d{2}$/.test(to) ? { start: from, end: to } : null;
  });
  protected readonly problem = computed(() => {
    const period = this.period();
    return period === null ? null : periodProblem(period, this.today);
  });
  protected readonly problemText = computed(() => {
    const problem = this.problem();
    if (problem === null) {
      return null;
    }
    return problem.kind === 'backwards'
      ? 'La fecha «hasta» no puede ser anterior a «desde».'
      : `Este período aún no termina. Podrás emitirlo desde el ${formatLocalDate(problem.availableFrom)}.`;
  });
  protected readonly stepOneTried = signal(false);

  // ---- Step 2: the draft, for the period that was reviewed.
  protected readonly reviewed = signal<Period | null>(null);
  protected readonly summary = rxResource({
    params: () => this.reviewed() ?? undefined,
    stream: ({ params: p }) => this.api.certificateSummary(this.id(), p.start, p.end),
  });
  protected readonly draft = computed(() => {
    const period = this.reviewed();
    if (period === null || !this.summary.hasValue() || !this.company.hasValue()) {
      return null;
    }
    const s = this.summary.value();
    const c = this.company.value();
    const overlapping = this.certificates.hasValue()
      ? (this.certificates.value().content.find((cert) => overlaps(period, { start: cert.periodStart, end: cert.periodEnd })) ?? null)
      : null;
    return {
      period,
      label: periodLabel(period.start, period.end),
      dates: `${formatLocalDate(period.start)} – ${formatLocalDate(period.end)}`,
      company: c.name,
      ruc: c.ruc,
      associationId: c.associationId,
      association: this.association.hasValue() ? this.association.value().name : null,
      kilos: formatKg(s.kilosTrazados),
      compliance: formatPercent(s.hierarchyCompliancePercent),
      zero: s.kilosTrazados === 0,
      missingSigersol: s.hierarchyCompliancePercent === null,
      overlapping: overlapping === null ? null : { id: overlapping.id, label: periodLabel(overlapping.periodStart, overlapping.periodEnd) },
    };
  });
  /** Nothing stands in the way: the checkbox and the button are offered. */
  protected readonly issuable = computed(() => {
    const d = this.draft();
    return d !== null && !d.zero && !d.missingSigersol && d.overlapping === null;
  });

  // ---- Step 3: confirm and issue.
  protected readonly confirmed = signal(false);
  protected readonly issuing = signal(false);
  /** Issued: from the 201 until the certificate page opens, nothing here can be sent again. */
  protected readonly done = signal(false);
  /** The id of a certificate issued whose page then failed to open. */
  protected readonly issuedNotOpened = signal<string | null>(null);
  protected readonly error = signal<{ kind: ErrorKind; text: string } | null>(null);

  private readonly draftTitle = viewChild<ElementRef<HTMLElement>>('draftTitle');
  private readonly errorBlock = viewChild<ElementRef<HTMLElement>>('errorBlock');

  protected readonly reviewingLabel = computed(() => {
    const r = this.reviewed();
    return r === null ? '' : periodLabel(r.start, r.end);
  });

  protected loadError(error: unknown, what: string): string {
    return loadErrorMessage(error, what);
  }

  protected companyMissing(error: unknown): boolean {
    return error instanceof ApiError && (error.status === 404 || error.code === 'VALIDATION_ERROR');
  }

  /** Any change to the period discards the draft and the confirmation given for it. */
  protected setMode(mode: Mode): void {
    if (this.issuing() || this.done()) {
      return;
    }
    this.mode.set(mode);
    this.reset();
  }
  protected setValue(which: 'month' | 'from' | 'to', event: Event): void {
    if (this.issuing() || this.done()) {
      return;
    }
    this[which].set((event.target as HTMLInputElement).value);
    this.reset();
  }
  private reset(): void {
    this.reviewed.set(null);
    this.confirmed.set(false);
    this.error.set(null);
  }

  protected review(): void {
    this.stepOneTried.set(true);
    const period = this.period();
    if (period === null || this.problem() !== null) {
      return;
    }
    this.reset();
    // A copy: reviewing the same period again recomputes it (e.g. after registering SIGERSOL).
    this.reviewed.set({ ...period });
    setTimeout(() => this.draftTitle()?.nativeElement.focus());
  }

  protected setConfirmed(event: Event): void {
    this.confirmed.set((event.target as HTMLInputElement).checked);
  }

  protected issue(): void {
    const draft = this.draft();
    if (draft === null || !this.issuable() || !this.confirmed() || this.issuing() || this.done()) {
      return;
    }
    this.error.set(null);
    this.issuing.set(true);
    this.api
      .issueCertificate(this.id(), { periodStart: draft.period.start, periodEnd: draft.period.end })
      .pipe(
        finalize(() => this.issuing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (certificate) => {
          this.done.set(true);
          const path = ['/empresas', this.id(), 'certificados', certificate.id];
          // Issued either way: if its page can't open (a chunk that fails to load), say so and link to it.
          const notOpened = () => this.issuedNotOpened.set(certificate.id);
          this.router.navigate(path, { state: { emitido: true } }).then((opened) => opened || notOpened(), notOpened);
        },
        error: (error: unknown) => {
          const uncertain = !(error instanceof ApiError) || error.status === 0 || error.status === 408 || error.status >= 500;
          const code = error instanceof ApiError ? error.code : null;
          const kind: ErrorKind = uncertain ? 'uncertain' : code === 'RPT-004' ? 'overlap' : code === 'RPT-005' ? 'sigersol' : 'other';
          this.error.set({
            kind,
            text: writeErrorText(error, ISSUE_UNCERTAIN, {
              'RPT-004': `Esta empresa ya tiene un certificado que se superpone con ${draft.label}. Cada día se certifica una sola vez.`,
              'RPT-005': `No hay un registro SIGERSOL de la asociación que cubra todo ${draft.label}. Regístralo y vuelve a revisar el borrador.`,
              'RPT-009': `El servicio rechazó ${draft.label}: la fecha final es anterior a la inicial. Revisa las fechas del período.`,
            }),
          });
          // Whatever happened, the next attempt needs a fresh confirmation, and the list the screen
          // checks overlaps against may now hold a certificate (RPT-004, or an uncertain outcome).
          this.confirmed.set(false);
          this.certificates.reload();
          setTimeout(() => this.errorBlock()?.nativeElement.focus());
        },
      });
  }
}

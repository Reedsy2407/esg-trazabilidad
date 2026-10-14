import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { TrackedCompanyTransition } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { ReasonConfirm } from '../../shared/reason-confirm';
import { coverageNote } from '../sigersol/origin-label';

const STATUS_UNCERTAIN =
  'No pudimos confirmar si cambió el estado de la empresa. Recarga la página para ver el actual antes de volver a intentarlo.';
const DELETE_UNCERTAIN =
  'No pudimos confirmar si la empresa quedó eliminada. Revisa la lista de empresas antes de volver a intentarlo.';
import { formatInstantDate, formatKg, formatPercent, periodLabel } from '../../shared/format';
import { buildKilosChart, latestCertificate } from './kilos-chart';
import { KilosChart } from './kilos-chart.component';
import { ChevronIcon } from '../../shared/chevron-icon';
import { StatusLabel } from './status-label';

/** The list page the row was opened from (router state), so "Volver" returns to it. */
function originPage(): number | null {
  const state: unknown = history.state;
  if (typeof state !== 'object' || state === null || !('pagina' in state)) {
    return null;
  }
  const pagina: unknown = (state as Record<string, unknown>)['pagina'];
  return typeof pagina === 'number' && Number.isInteger(pagina) && pagina > 1 ? pagina : null;
}

function justRegistered(): boolean {
  const state: unknown = history.state;
  if (typeof state !== 'object' || state === null || (state as Record<string, unknown>)['registrada'] !== true) {
    return false;
  }
  history.replaceState({ ...(state as Record<string, unknown>), registrada: false }, '');
  return true;
}

/**
 * Ficha de empresa (DESIGN-BRIEF.md, round-3 rules): header, period summary,
 * one 12-period chart, certificates table. Each block loads on its own, so a
 * failing certificates call never takes the header or the summary down.
 *
 * - Header: GET /tracked-companies/{id}.
 * - Chart and table: GET .../certificates, first page (20, newest issued first).
 * - Resumen del período: GET .../certificate-summary for the latest certified
 *   period (the chart's highlighted bar). Without certificates it isn't
 *   called at all: the block says there are none yet.
 */
@Component({
  selector: 'app-company-page',
  imports: [RouterLink, ReactiveFormsModule, StatusLabel, KilosChart, ChevronIcon, ReasonConfirm],
  templateUrl: './company.page.html',
  styleUrl: './company.page.css',
})
export class CompanyPage {
  private readonly api = inject(BackendApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  readonly id = input.required<string>();

  protected readonly backParams = { pagina: originPage() };
  /** Opened right after registering it (router state from CompanyNewPage); dropped so a reload doesn't repeat it. */
  protected readonly registered = justRegistered();

  protected readonly company = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.trackedCompany(id),
  });

  protected readonly certificates = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.certificates(id),
  });

  /**
   * Any certificate (issued or annulled) locks the company's deletion, as the server decides (RPT-017):
   * once the list is loaded and not empty the button gives way to the reason. While it loads or fails
   * the button stays and the server stays the authority.
   */
  protected readonly hasCertificates = computed(
    () => this.certificates.hasValue() && this.certificates.value().totalElements > 0,
  );
  /** The latest certified period; null while loading, on error or without certificates. */
  protected readonly latest = computed(() =>
    this.certificates.hasValue() ? latestCertificate(this.certificates.value().content) : null,
  );

  /** Idle (never requested) until there is a latest certificate to summarise. */
  protected readonly summary = rxResource({
    params: () => {
      const latest = this.latest();
      return latest === null
        ? undefined
        : { id: this.id(), periodStart: latest.periodStart, periodEnd: latest.periodEnd };
    },
    stream: ({ params: p }) => this.api.certificateSummary(p.id, p.periodStart, p.periodEnd),
  });

  protected readonly summaryLines = computed(() => {
    if (!this.summary.hasValue()) {
      return null;
    }
    const s = this.summary.value();
    return {
      period: periodLabel(s.periodStart, s.periodEnd),
      kilos: formatKg(s.kilosTrazados),
      compliance: formatPercent(s.hierarchyCompliancePercent),
      missingSigersol: s.hierarchyCompliancePercent === null,
      coverage: coverageNote(s),
    };
  });

  protected readonly rows = computed(() => {
    if (!this.certificates.hasValue()) {
      return null;
    }
    const page = this.certificates.value();
    return {
      total: page.totalElements,
      items: page.content.map((c) => ({
        id: c.id,
        period: periodLabel(c.periodStart, c.periodEnd),
        kilos: formatKg(c.kilosTrazados),
        compliance: formatPercent(c.hierarchyCompliancePercent),
        issued: formatInstantDate(c.issuedAt),
        annulled: c.status === 'ANNULLED',
      })),
    };
  });

  protected readonly chart = computed(() =>
    this.certificates.hasValue() ? buildKilosChart(this.certificates.value().content) : null,
  );

  protected readonly verifying = signal(false);
  protected readonly verifyError = signal<string | null>(null);
  /** What just happened, in an always-present live region (verified, contact saved, state changed). */
  protected readonly notice = signal('');

  /** "Verificar con SUNAT": asks the provider; on any failure the company is unchanged. */
  protected verify(): void {
    if (this.verifying()) {
      return;
    }
    this.verifying.set(true);
    this.verifyError.set(null);
    this.notice.set('');
    this.api
      .refreshTrackedCompanySunat(this.id())
      .pipe(
        finalize(() => this.verifying.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.notice.set('Empresa verificada con SUNAT.');
          this.company.reload();
        },
        error: (error: unknown) =>
          this.verifyError.set(
            // RPT-011 is a 503, but a definite one: SUNAT did not answer and nothing changed.
            error instanceof ApiError && error.code === 'RPT-011'
              ? 'No se pudo consultar SUNAT en este momento. La empresa sigue sin verificar: vuelve a intentarlo en un rato.'
              : writeErrorText(error, 'No pudimos confirmar si SUNAT respondió. Vuelve a intentarlo en un momento.', {
                  'RPT-013': 'El RUC está en baja definitiva en SUNAT: la empresa no se puede verificar.',
                }),
          ),
      });
  }

  // ── Contact edit ──────────────────────────────────────────────────────────────
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly editError = signal<string | null>(null);
  protected readonly contactForm = this.fb.group({
    contactName: ['', Validators.maxLength(255)],
    contactEmail: ['', [Validators.email, Validators.maxLength(255)]],
    contactPhone: ['', Validators.maxLength(30)],
  });
  private readonly editButton = viewChild<ElementRef<HTMLElement>>('editButton');

  protected startEdit(): void {
    if (!this.company.hasValue()) {
      return;
    }
    const c = this.company.value();
    this.contactForm.reset({
      contactName: c.contactName ?? '',
      contactEmail: c.contactEmail ?? '',
      contactPhone: c.contactPhone ?? '',
    });
    this.editError.set(null);
    this.notice.set('');
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    this.editing.set(false);
    setTimeout(() => this.editButton()?.nativeElement.focus());
  }

  protected saveContact(): void {
    if (this.saving()) {
      return;
    }
    if (this.contactForm.invalid) {
      this.contactForm.markAllAsTouched();
      return;
    }
    const v = this.contactForm.getRawValue();
    this.saving.set(true);
    this.editError.set(null);
    // A blank field is sent blank on purpose: that is how the contact is cleared.
    this.api
      .editTrackedCompany(this.id(), { contactName: v.contactName.trim(), contactEmail: v.contactEmail.trim(), contactPhone: v.contactPhone.trim() })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.editing.set(false);
          this.notice.set('Contacto guardado.');
          this.company.reload();
          setTimeout(() => this.editButton()?.nativeElement.focus());
        },
        error: (error: unknown) => this.editError.set(writeErrorText(error, 'No pudimos confirmar si se guardó el contacto. Recarga la página para ver el actual.')),
      });
  }

  // ── Deactivate / reactivate / delete ──────────────────────────────────────────
  protected readonly panel = signal<TrackedCompanyTransition | 'delete' | null>(null);
  protected readonly panelBusy = signal(false);
  protected readonly panelError = signal<string | null>(null);
  private readonly reasonPanel = viewChild(ReasonConfirm);
  private readonly actions = viewChild<ElementRef<HTMLElement>>('actions');

  protected openPanel(panel: TrackedCompanyTransition | 'delete'): void {
    this.panel.set(panel);
    this.panelError.set(null);
    this.notice.set('');
    setTimeout(() => this.reasonPanel()?.focus());
  }

  protected closePanel(): void {
    const closed = this.panel();
    this.panel.set(null);
    this.panelError.set(null);
    setTimeout(() => this.actions()?.nativeElement.querySelector<HTMLElement>(`[data-panel="${closed}"]`)?.focus());
  }

  protected changeStatus(transition: TrackedCompanyTransition, reason: string): void {
    if (this.panelBusy()) {
      return;
    }
    this.panelError.set(null);
    this.panelBusy.set(true);
    this.api
      .changeTrackedCompanyStatus(this.id(), transition, reason)
      .pipe(
        finalize(() => this.panelBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.panel.set(null);
          this.notice.set(
            transition === 'deactivate'
              ? 'Empresa desactivada. No se le pueden emitir certificados mientras esté inactiva.'
              : 'Empresa reactivada.',
          );
          this.company.reload();
          setTimeout(() => this.actions()?.nativeElement.focus());
        },
        error: (error: unknown) => {
          this.panelError.set(
            writeErrorText(error, STATUS_UNCERTAIN, {
              'RPT-018': 'La empresa ya estaba en ese estado. Vuelve a cargar la página para ver el actual.',
            }),
          );
          this.reasonPanel()?.focusError();
        },
      });
  }

  protected deleteCompany(reason: string): void {
    if (this.panelBusy()) {
      return;
    }
    this.panelError.set(null);
    this.panelBusy.set(true);
    this.api
      .deleteTrackedCompany(this.id(), reason)
      .pipe(
        finalize(() => this.panelBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => void this.router.navigate(['/empresas']),
        error: (error: unknown) => {
          this.panelError.set(
            writeErrorText(error, DELETE_UNCERTAIN, {
              'RPT-017':
                'La empresa tiene certificados (aunque estén anulados): no se puede eliminar. Desactívala si ya no debe recibir certificados.',
            }),
          );
          this.reasonPanel()?.focusError();
        },
      });
  }

  protected formatDate(instant: string): string {
    return formatInstantDate(instant);
  }

  protected companyNotFound(error: unknown): boolean {
    return error instanceof ApiError && error.status === 404;
  }

  protected errorText(error: unknown, what: string): string {
    return loadErrorMessage(error, what);
  }
}

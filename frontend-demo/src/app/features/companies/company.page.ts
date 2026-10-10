import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { loadErrorMessage } from '../../shared/error-message';
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
  imports: [RouterLink, StatusLabel, KilosChart, ChevronIcon],
  templateUrl: './company.page.html',
  styleUrl: './company.page.css',
})
export class CompanyPage {
  private readonly api = inject(BackendApi);
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
      })),
    };
  });

  protected readonly chart = computed(() =>
    this.certificates.hasValue() ? buildKilosChart(this.certificates.value().content) : null,
  );

  protected companyNotFound(error: unknown): boolean {
    return error instanceof ApiError && error.status === 404;
  }

  protected errorText(error: unknown, what: string): string {
    return loadErrorMessage(error, what);
  }
}

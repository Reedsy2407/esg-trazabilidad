import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, linkedSignal, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { Page, SigersolSync } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { loadErrorMessage } from '../../shared/error-message';
import { formatInstantDate, formatKg, formatPercentExact, periodLabel } from '../../shared/format';
import { ManualTag } from './manual-tag';

/** Was this page opened right after registering a record? Read once, then dropped from history. */
function justRegistered(): boolean {
  const state: unknown = history.state;
  if (typeof state !== 'object' || state === null || (state as Record<string, unknown>)['registrado'] !== true) {
    return false;
  }
  history.replaceState({ ...(state as Record<string, unknown>), registrado: false }, '');
  return true;
}

/**
 * Registros SIGERSOL (reporting-service GET /sigersol-syncs): newest period first, 20 per page,
 * optionally one association's. Always labelled "ingreso manual": the figures were typed in.
 */
@Component({
  selector: 'app-sigersol-page',
  imports: [RouterLink, DecimalPipe, ManualTag],
  templateUrl: './sigersol.page.html',
  styleUrl: './sigersol.page.css',
})
export class SigersolPage {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);

  readonly pagina = input(1, { transform: (value: unknown) => Math.max(1, Math.floor(numberAttribute(value, 1))) });
  readonly asociacion = input<string | undefined>(undefined);

  protected readonly registered = justRegistered();
  protected readonly associationId = computed(() => this.asociacion() || null);

  protected readonly syncs = rxResource({
    params: () => ({ page: this.pagina() - 1, associationId: this.associationId() }),
    stream: ({ params }) => this.api.sigersolSyncs(params.page, params.associationId),
  });
  protected readonly associations = rxResource({ stream: () => this.api.associations() });
  protected readonly associationOptions = computed(() =>
    this.associations.hasValue() ? this.associations.value().content.map((a) => ({ id: a.id, name: a.name })) : [],
  );

  /** Filtered by an association the picker doesn't list (past its first 100): say so, don't show "Todas". */
  protected readonly filterUnlisted = computed(() => {
    const id = this.associationId();
    return id !== null && this.associations.hasValue() && !this.associationOptions().some((a) => a.id === id);
  });

  protected readonly page = linkedSignal<Page<SigersolSync> | undefined, Page<SigersolSync> | null>({
    source: () => (this.syncs.hasValue() ? this.syncs.value() : undefined),
    computation: (current, previous) => current ?? previous?.value ?? null,
  });

  protected readonly rows = computed(() => {
    const page = this.page();
    if (page === null) {
      return [];
    }
    const names = new Map(this.associationOptions().map((a) => [a.id, a.name]));
    return page.content.map((s) => ({
      id: s.id,
      association: names.get(s.associationId) ?? 'Asociación no listada',
      period: periodLabel(s.periodStart, s.periodEnd),
      compliance: formatPercentExact(s.hierarchyCompliancePercent),
      kilos: s.officialKilosDeclared === null ? 'No declarado' : formatKg(s.officialKilosDeclared),
      declared: formatInstantDate(s.declaredAt),
      note: s.sourceNote?.trim() || null,
    }));
  });

  protected readonly range = computed(() => {
    const current = this.page();
    if (current === null || current.content.length === 0) {
      return null;
    }
    const first = current.page * current.size + 1;
    return { first, last: first + current.content.length - 1, total: current.totalElements };
  });

  protected readonly announcement = computed(() => {
    if (this.syncs.isLoading() || !this.syncs.hasValue()) {
      return '';
    }
    const r = this.range();
    return r === null ? '' : `Mostrando ${r.first}–${r.last} de ${r.total} ${r.total === 1 ? 'registro' : 'registros'}.`;
  });

  protected readonly loadError = computed(() => {
    const error = this.syncs.error();
    return error === undefined ? null : loadErrorMessage(error, 'los registros SIGERSOL');
  });

  protected filterBy(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    void this.router.navigate([], { queryParams: { asociacion: id || null, pagina: null }, queryParamsHandling: 'merge' });
  }

  protected goTo(pagina: number): void {
    void this.router.navigate([], { queryParams: { pagina: pagina === 1 ? null : pagina }, queryParamsHandling: 'merge' });
  }
}

import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, linkedSignal, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { Association, AssociationStatus, Page } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { loadErrorMessage } from '../../shared/error-message';
import { StatusLabel } from '../companies/status-label';

/** ?estado= in Spanish for people, the backend's enum for the API. */
const FILTERS = [
  { estado: null, status: null, label: 'Todas' },
  { estado: 'activas', status: 'ACTIVE', label: 'Activas' },
  { estado: 'suspendidas', status: 'SUSPENDED', label: 'Suspendidas' },
] as const satisfies readonly { estado: string | null; status: AssociationStatus | null; label: string }[];

/**
 * Asociaciones (recycler-service GET /associations): 20 per page by name, filtered by state
 * with the backend's own `status` parameter. Each row opens the association's page.
 */
@Component({
  selector: 'app-associations-page',
  imports: [RouterLink, DecimalPipe, StatusLabel],
  templateUrl: './associations.page.html',
})
export class AssociationsPage {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);

  readonly pagina = input(1, { transform: (value: unknown) => Math.max(1, Math.floor(numberAttribute(value, 1))) });
  readonly estado = input<string | undefined>(undefined);

  protected readonly filters = FILTERS;
  protected readonly filter = computed(() => FILTERS.find((f) => f.estado === this.estado()) ?? FILTERS[0]);

  protected readonly associations = rxResource({
    params: () => ({ page: this.pagina() - 1, status: this.filter().status }),
    stream: ({ params }) => this.api.associationsPage(params.page, params.status),
  });

  /**
   * The last page answered. While the next page or filter loads it stays on screen (marked busy),
   * so the pager button the user pressed keeps its focus instead of vanishing into a skeleton.
   */
  protected readonly page = linkedSignal<Page<Association> | undefined, Page<Association> | null>({
    source: () => (this.associations.hasValue() ? this.associations.value() : undefined),
    computation: (current, previous) => current ?? previous?.value ?? null,
  });

  /** Said by the polite live region once a page or filter has loaded. */
  protected readonly announcement = computed(() => {
    if (this.associations.isLoading() || !this.associations.hasValue()) {
      return '';
    }
    const r = this.range();
    return r === null ? '' : `Mostrando ${r.first}–${r.last} de ${r.total} ${r.total === 1 ? 'asociación' : 'asociaciones'}.`;
  });

  protected readonly range = computed(() => {
    const current = this.page();
    if (current === null || current.content.length === 0) {
      return null;
    }
    const first = current.page * current.size + 1;
    return { first, last: first + current.content.length - 1, total: current.totalElements };
  });

  protected readonly loadError = computed(() => {
    const error = this.associations.error();
    return error === undefined ? null : loadErrorMessage(error, 'las asociaciones', 'el servicio de recicladores');
  });

  protected goTo(pagina: number): void {
    void this.router.navigate([], { queryParams: { pagina: pagina === 1 ? null : pagina }, queryParamsHandling: 'merge' });
  }
}

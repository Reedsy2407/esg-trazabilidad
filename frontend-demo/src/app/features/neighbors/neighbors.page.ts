import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, linkedSignal, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { Neighbor, NeighborStatus, Page } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { loadErrorMessage } from '../../shared/error-message';

const FILTERS = [
  { estado: null, status: null, label: 'Todos' },
  { estado: 'activos', status: 'ACTIVE', label: 'Activos' },
  { estado: 'inactivos', status: 'INACTIVE', label: 'Inactivos' },
] as const satisfies readonly { estado: string | null; status: NeighborStatus | null; label: string }[];

/**
 * Vecinos (collection-service GET /neighbors): 20 per page by name, filtered by state and by
 * district. The district filter is the backend's exact match, so the page says so; the API has
 * no search by name.
 */
@Component({
  selector: 'app-neighbors-page',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './neighbors.page.html',
  styleUrl: './neighbors.page.css',
})
export class NeighborsPage {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);

  readonly pagina = input(1, { transform: (value: unknown) => Math.max(1, Math.floor(numberAttribute(value, 1))) });
  readonly estado = input<string | undefined>(undefined);
  readonly distrito = input<string | undefined>(undefined);

  protected readonly filters = FILTERS;
  protected readonly filter = computed(() => FILTERS.find((f) => f.estado === this.estado()) ?? FILTERS[0]);
  protected readonly district = computed(() => this.distrito()?.trim() || null);

  protected readonly neighbors = rxResource({
    params: () => ({ page: this.pagina() - 1, status: this.filter().status, district: this.district() }),
    stream: ({ params }) => this.api.neighborsPage(params.page, params.status, params.district),
  });

  /** The last page answered stays on screen (busy) while the next loads: see AssociationsPage. */
  protected readonly page = linkedSignal<Page<Neighbor> | undefined, Page<Neighbor> | null>({
    source: () => (this.neighbors.hasValue() ? this.neighbors.value() : undefined),
    computation: (current, previous) => current ?? previous?.value ?? null,
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
    if (this.neighbors.isLoading() || !this.neighbors.hasValue()) {
      return '';
    }
    const r = this.range();
    return r === null ? 'Ningún vecino coincide.' : `Mostrando ${r.first}–${r.last} de ${r.total} ${r.total === 1 ? 'vecino' : 'vecinos'}.`;
  });

  protected readonly loadError = computed(() => {
    const error = this.neighbors.error();
    return error === undefined ? null : loadErrorMessage(error, 'los vecinos', 'el servicio de recojos');
  });

  protected searchDistrict(event: SubmitEvent): void {
    event.preventDefault();
    const value = new FormData(event.target as HTMLFormElement).get('distrito');
    const district = typeof value === 'string' && value.trim() !== '' ? value.trim() : null;
    void this.router.navigate([], { queryParams: { distrito: district, pagina: null }, queryParamsHandling: 'merge' });
  }

  protected goTo(pagina: number): void {
    void this.router.navigate([], { queryParams: { pagina: pagina === 1 ? null : pagina }, queryParamsHandling: 'merge' });
  }
}

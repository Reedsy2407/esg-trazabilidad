import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, linkedSignal, numberAttribute } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Neighbor, NeighborStatus, Page } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { DistrictSelect } from '../../shared/district-select/district-select';
import { districtByName, districtByUbigeo } from '../../shared/districts';
import { loadErrorMessage } from '../../shared/error-message';

const FILTERS = [
  { estado: null, status: null, label: 'Todos' },
  { estado: 'activos', status: 'ACTIVE', label: 'Activos' },
  { estado: 'inactivos', status: 'INACTIVE', label: 'Inactivos' },
] as const satisfies readonly { estado: string | null; status: NeighborStatus | null; label: string }[];

/**
 * ?distrito= holds a ubigeo. An older link may carry a name ("Comas", "rimac"): it still works
 * when it names a district of the list; anything else is ignored (no filter).
 */
export function districtFilter(param: string | undefined): string | null {
  const value = param?.trim() ?? '';
  return districtByUbigeo(value)?.ubigeo ?? districtByName(value)?.ubigeo ?? null;
}

/**
 * The name of the neighbour just deleted (router state from NeighborPage), read once and dropped
 * from the history entry so a reload doesn't announce it again.
 */
function justDeleted(): string | null {
  const state: unknown = history.state;
  const name = typeof state === 'object' && state !== null ? (state as Record<string, unknown>)['eliminado'] : null;
  if (typeof name !== 'string') {
    return null;
  }
  history.replaceState({ ...(state as Record<string, unknown>), eliminado: null }, '');
  return name;
}

/**
 * Vecinos (collection-service GET /neighbors): 20 per page by name, filtered by state and by
 * district (one of the 50 of Lima Metropolitana and Callao, by ubigeo). The API has no search by
 * name. Inactive neighbours are reactivated from their own page (it asks for a reason).
 */
@Component({
  selector: 'app-neighbors-page',
  imports: [RouterLink, DecimalPipe, ReactiveFormsModule, DistrictSelect],
  templateUrl: './neighbors.page.html',
  styleUrl: './neighbors.page.css',
})
export class NeighborsPage {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);

  readonly pagina = input(1, { transform: (value: unknown) => Math.max(1, Math.floor(numberAttribute(value, 1))) });
  readonly estado = input<string | undefined>(undefined);
  readonly distrito = input<string | undefined>(undefined);

  protected readonly deleted = justDeleted();
  protected readonly filters = FILTERS;
  protected readonly filter = computed(() => FILTERS.find((f) => f.estado === this.estado()) ?? FILTERS[0]);
  protected readonly district = computed(() => districtFilter(this.distrito()));

  /** The picker shows the district in the URL; choosing one navigates. */
  protected readonly districtControl = new FormControl<string | null>(null);

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

  constructor() {
    effect(() => this.districtControl.setValue(this.district(), { emitEvent: false }));
    this.districtControl.valueChanges.pipe(takeUntilDestroyed()).subscribe((ubigeo) => {
      if (ubigeo !== this.district()) {
        void this.router.navigate([], { queryParams: { distrito: ubigeo, pagina: null }, queryParamsHandling: 'merge' });
      }
    });
  }

  protected goTo(pagina: number): void {
    void this.router.navigate([], { queryParams: { pagina: pagina === 1 ? null : pagina }, queryParamsHandling: 'merge' });
  }
}

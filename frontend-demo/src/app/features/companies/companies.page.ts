import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { ChevronIcon } from '../../shared/chevron-icon';
import { StatusLabel } from './status-label';

@Component({
  selector: 'app-companies-page',
  imports: [RouterLink, DecimalPipe, ChevronIcon, StatusLabel],
  templateUrl: './companies.page.html',
  styleUrl: './companies.page.css',
})
export class CompaniesPage {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);

  /** ?pagina=N, one-based for people; the API is zero-based. */
  readonly pagina = input(1, { transform: (value: unknown) => Math.max(1, numberAttribute(value, 1)) });

  protected readonly companies = rxResource({
    params: () => this.pagina() - 1,
    stream: ({ params: page }) => this.api.trackedCompanies(page),
  });

  protected readonly page = computed(() => (this.companies.hasValue() ? this.companies.value() : null));

  protected readonly range = computed(() => {
    const current = this.page();
    if (current === null || current.content.length === 0) {
      return null;
    }
    const first = current.page * current.size + 1;
    return { first, last: first + current.content.length - 1, total: current.totalElements };
  });

  protected readonly loadError = computed(() => {
    const error = this.companies.error();
    if (error === undefined) {
      return null;
    }
    return error instanceof ApiError && error.status === 0
      ? 'No se pudo conectar con el servicio de reportes.'
      : 'No se pudo cargar la lista de empresas.';
  });

  protected goTo(pagina: number): void {
    void this.router.navigate([], { queryParams: { pagina: pagina === 1 ? null : pagina } });
  }
}

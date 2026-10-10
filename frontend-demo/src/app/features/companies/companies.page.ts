import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, computed, effect, inject, input, numberAttribute, signal, viewChild } from '@angular/core';
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

  /** "Actualizar la lista" in flight: the button says so and waits; one refresh at a time. */
  protected readonly refreshing = signal(false);
  /**
   * Said once by the polite live region when a refresh succeeds. A failed refresh is already
   * announced by the error block (role="alert"), so it isn't repeated here.
   */
  protected readonly refreshMessage = signal('');

  private readonly list = viewChild<ElementRef<HTMLElement>>('list');
  private readonly retryButton = viewChild<ElementRef<HTMLElement>>('retryButton');

  constructor() {
    effect(() => {
      if (this.refreshing() && !this.companies.isLoading()) {
        this.refreshing.set(false);
        const failed = this.loadError() !== null;
        this.refreshMessage.set(failed ? '' : 'Lista actualizada.');
        // The button the user pressed is gone: put focus where the result is.
        setTimeout(() => (failed ? this.retryButton() : this.list())?.nativeElement.focus());
      }
    });
  }

  protected refresh(): void {
    if (this.refreshing()) {
      return;
    }
    this.refreshMessage.set('');
    this.refreshing.set(true);
    this.companies.reload();
  }

  protected retry(): void {
    this.refreshMessage.set('');
    this.companies.reload();
  }

  protected goTo(pagina: number): void {
    this.refreshMessage.set('');
    void this.router.navigate([], { queryParams: { pagina: pagina === 1 ? null : pagina } });
  }
}

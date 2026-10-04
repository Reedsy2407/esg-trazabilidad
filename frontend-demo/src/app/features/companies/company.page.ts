import { Component, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
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

/**
 * Where a company row leads. This slice only has the ficha's header (brief:
 * back link, name, RUC in mono, status) from GET /tracked-companies/{id};
 * the period summary, chart and certificates table come in the next slice,
 * and the page says so instead of ending in a blank canvas.
 */
@Component({
  selector: 'app-company-page',
  imports: [RouterLink, StatusLabel],
  template: `
    <a class="back" routerLink="/empresas" [queryParams]="backParams">Volver a empresas</a>
    <div aria-live="polite" [attr.aria-busy]="company.isLoading()">
      @if (company.hasValue()) {
        @let c = company.value();
        <header class="head">
          <h1>{{ c.name }}</h1>
          <p class="meta">
            <span>RUC <span class="mono">{{ c.ruc }}</span></span>
            <app-status-label [status]="c.status" />
          </p>
        </header>
        <p class="next">El resumen del período y los certificados de esta empresa se mostrarán aquí en la próxima versión.</p>
      } @else if (company.error(); as error) {
        <div class="problem" role="alert">
          @if (isNotFound(error)) {
            <p>Esta empresa no existe o ya no está registrada.</p>
            <a routerLink="/empresas">Ver todas las empresas</a>
          } @else {
            <p>No se pudo cargar la empresa.</p>
            <button type="button" class="btn" (click)="company.reload()">Volver a intentar</button>
          }
        </div>
      } @else {
        <div class="head" aria-hidden="true">
          <span class="bone title"></span>
          <span class="bone"></span>
        </div>
        <p class="visually-hidden">Cargando empresa</p>
      }
    </div>
  `,
  styles: `
    .back { display: inline-block; margin-bottom: var(--space-5); }
    .head { padding-bottom: var(--space-4); border-bottom: 1px solid var(--line); }
    h1 { font-size: 26px; }
    .meta { display: flex; flex-wrap: wrap; gap: var(--space-4); margin: var(--space-2) 0 0; color: var(--muted); }
    .next { margin: var(--space-5) 0 0; color: var(--muted); max-width: 64ch; }
    .bone {
      display: block; width: 160px; height: 12px; margin-top: var(--space-3);
      background: var(--line); border-radius: var(--radius-data);
      animation: pulse 1.2s ease-in-out infinite alternate;
    }
    .bone.title { width: 320px; max-width: 100%; height: 22px; margin-top: 0; }
    @keyframes pulse { from { opacity: 0.45; } to { opacity: 1; } }
  `,
})
export class CompanyPage {
  private readonly api = inject(BackendApi);
  readonly id = input.required<string>();

  protected readonly backParams = { pagina: originPage() };

  protected readonly company = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.trackedCompany(id),
  });

  protected isNotFound(error: unknown): boolean {
    return error instanceof ApiError && error.status === 404;
  }
}

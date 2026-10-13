import { Component, computed, input } from '@angular/core';

import { DistrictLine } from '../core/api/api.types';
import { formatKg } from './format';

/**
 * The kilos of each district with SIGERSOL Municipal's aggregated public counts beside them. The
 * wording is deliberate: these are the district's figures, with their year and source, and they say
 * nothing about the company; when SIGERSOL had nothing the line says "no disponible" and nothing
 * else changes. Shown on the certificate (frozen at issue) and on its draft (live).
 */
@Component({
  selector: 'app-district-context',
  template: `
    @if (rows().length > 0) {
      <section class="district-context" [attr.aria-labelledby]="idPrefix() + '-title'">
        <h2 class="sec" [id]="idPrefix() + '-title'">Contexto distrital</h2>
        <p class="note">
          Cifras públicas de SIGERSOL Municipal por distrito, agregadas. Describen el distrito, no a la empresa ni a sus proveedores.
        </p>
        <ul>
          @for (row of rows(); track row.key) {
            <li>
              <span class="name">{{ row.name }}</span>
              <span class="mono kilos">{{ row.kilos }}</span>
              <span class="detail">{{ row.detail }}</span>
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    .district-context { margin-top: var(--space-5); }
    .note { margin: 0 0 var(--space-3); color: var(--ink-soft); font-size: 14px; }
    ul { margin: 0; padding: 0; list-style: none; }
    li { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0 var(--space-4); padding: var(--space-2) 0; border-top: 1px solid var(--ink-soft); }
    .name { font-weight: 600; }
    .kilos { text-align: right; }
    .detail { grid-column: 1 / -1; color: var(--ink-soft); font-size: 14px; }
  `,
})
export class DistrictContext {
  readonly lines = input.required<readonly DistrictLine[] | null | undefined>();
  readonly idPrefix = input('district');

  protected readonly rows = computed(() =>
    (this.lines() ?? []).map((line, index) => ({
      key: `${line.districtUbigeo ?? 'desconocido'}-${index}`,
      name: line.districtName,
      kilos: formatKg(line.kilos),
      detail: detailOf(line),
    })),
  );
}

function detailOf(line: DistrictLine): string {
  switch (line.status) {
    case 'AVAILABLE':
      return `Asociaciones: ${line.associationCount}; recicladores: ${line.recyclerCount}. Fuente: SIGERSOL Municipal, año ${line.year}, datos agregados.`;
    case 'UNAVAILABLE':
      return 'Contexto de SIGERSOL Municipal no disponible.';
    case 'UNKNOWN_DISTRICT':
      return 'Las pesadas de estos kilos no registran distrito.';
  }
}

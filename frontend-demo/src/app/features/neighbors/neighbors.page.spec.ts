import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { Neighbor } from '../../core/api/api.types';
import { errorInterceptor } from '../../core/http/interceptors';
import { NeighborsPage } from './neighbors.page';

const url = `${environment.services.collection}/neighbors`;
const neighbor = (n: number, district: string | null = 'Comas'): Neighbor => ({
  id: `n-${n}`,
  fullName: `Vecino ${n}`,
  phone: null,
  address: `Calle ${n}`,
  district,
  status: 'ACTIVE',
});

describe('NeighborsPage', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;

  const setup = (inputs: Record<string, unknown>) => {
    TestBed.configureTestingModule({
      imports: [NeighborsPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(NeighborsPage);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
  };
  const pending = (): TestRequest => backend.expectOne((r) => r.url === url);
  const settle = async () => {
    await Promise.resolve();
    TestBed.tick();
  };
  const answer = async (content: Neighbor[], totalElements: number, number = 0) => {
    pending().flush({ content, page: number, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) });
    await settle();
  };

  it('sends state, exact district (trimmed) and the page to the API', () => {
    setup({ pagina: 3, estado: 'inactivos', distrito: '  Rímac ' });
    const params = pending().request.params;
    expect(params.get('page')).toBe('2');
    expect(params.get('status')).toBe('INACTIVE');
    expect(params.get('district')).toBe('Rímac');
  });

  it('no filters: neither status nor district is sent', () => {
    setup({ distrito: '   ' });
    const params = pending().request.params;
    expect(params.has('status')).toBe(false);
    expect(params.has('district')).toBe(false);
  });

  it('a missing district shows a dash, never null', async () => {
    setup({});
    await answer([neighbor(1, null), neighbor(2, '  ')], 2);
    const cells = [...page.querySelectorAll('table.ledger tbody tr td:nth-child(2)')].map((td) => td.textContent?.trim());
    expect(cells).toEqual(['—', '—']);
    expect(page.querySelector('[role="status"]')?.textContent).toBe('Mostrando 1–2 de 2 vecinos.');
  });

  it('three empty cases: none yet, none for the filter, and a page past the end', async () => {
    setup({});
    await answer([], 0);
    expect(page.querySelector('.empty-note')?.textContent).toContain('Todavía no hay vecinos registrados.');

    TestBed.resetTestingModule();
    setup({ distrito: 'Nada' });
    await answer([], 0);
    expect(page.querySelector('.empty-note')?.textContent).toContain('Ningún vecino coincide con este filtro.');

    TestBed.resetTestingModule();
    setup({ pagina: 4 });
    await answer([], 25, 3);
    expect(page.querySelector('.empty-note')?.textContent).toContain('Esta página ya no tiene vecinos.');
  });

  it('a failure offers a retry that asks again', async () => {
    setup({});
    pending().flush('Bad Gateway', { status: 502, statusText: 'Bad Gateway' });
    await settle();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'No se pudieron cargar los vecinos: el servicio de recojos no respondió (HTTP 502).',
    );
    (page.querySelector('[role="alert"] button') as HTMLButtonElement).click();
    TestBed.tick();
    await answer([neighbor(1)], 1);
    expect(page.querySelectorAll('table.ledger tbody tr')).toHaveLength(1);
  });
});

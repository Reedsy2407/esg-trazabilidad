import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { Neighbor } from '../../core/api/api.types';
import { errorInterceptor } from '../../core/http/interceptors';
import { pickDistrict } from '../../testing/district-select';
import { NeighborsPage } from './neighbors.page';

const url = `${environment.services.collection}/neighbors`;
const neighbor = (n: number, district: string | null = 'Comas', status: Neighbor['status'] = 'ACTIVE'): Neighbor => ({
  id: `n-${n}`,
  fullName: `Vecino ${n}`,
  phone: null,
  address: `Calle ${n}`,
  district,
  districtUbigeo: district === 'Comas' ? '150110' : null,
  status,
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

  it('sends state, the district as its ubigeo and the page to the API; the picker shows the district', () => {
    setup({ pagina: 3, estado: 'inactivos', distrito: '150128' });
    const params = pending().request.params;
    expect(params.get('page')).toBe('2');
    expect(params.get('status')).toBe('INACTIVE');
    expect(params.get('districtUbigeo')).toBe('150128');
    expect(params.has('district')).toBe(false);
    expect((page.querySelector('#district') as HTMLInputElement).value).toBe('Rímac');
  });

  it('an older link with a district name still filters; anything else is no filter', () => {
    setup({ distrito: '  rimac ' });
    expect(pending().request.params.get('districtUbigeo')).toBe('150128');

    TestBed.resetTestingModule();
    setup({ distrito: 'Cercado de Lima' });
    const params = pending().request.params;
    expect(params.has('districtUbigeo')).toBe(false);
    expect(params.has('status')).toBe(false);
    expect((page.querySelector('#district') as HTMLInputElement).value).toBe('Todos los distritos');
  });

  it('choosing a district navigates with its ubigeo and back to the first page', () => {
    setup({ pagina: 2 });
    pending();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    pickDistrict(page, 'district', 'callao');
    expect(navigate).toHaveBeenCalledWith([], { queryParams: { distrito: '070101', pagina: null }, queryParamsHandling: 'merge' });
  });

  it('the Inactivos tab explains how to reactivate, and inactive rows link to the neighbour', async () => {
    setup({ estado: 'inactivos' });
    await answer([neighbor(1, 'Comas', 'INACTIVE')], 1);
    expect(page.querySelector('.note')?.textContent).toContain('Para reactivarlo, ábrelo');
    const link = page.querySelector('td.action a') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Reactivar…');
    expect(link.getAttribute('href')).toBe('/vecinos/n-1');
  });

  it('no hint asks to type the district exactly', async () => {
    setup({});
    await answer([], 0);
    expect(page.textContent).not.toContain('Escríbelo');
  });

  it('a missing district shows a dash, never null', async () => {
    setup({});
    await answer([neighbor(1, null), neighbor(2, '  ')], 2);
    const cells = [...page.querySelectorAll('table.ledger tbody tr td:nth-child(2)')].map((td) => td.textContent?.trim());
    expect(cells).toEqual(['—', '—']);
    expect(page.querySelector('.announce')?.textContent).toBe('Mostrando 1–2 de 2 vecinos.');
  });

  it('three empty cases: none yet, none for the filter, and a page past the end', async () => {
    setup({});
    await answer([], 0);
    expect(page.querySelector('.empty-note')?.textContent).toContain('Todavía no hay vecinos registrados.');

    TestBed.resetTestingModule();
    setup({ distrito: '150110' });
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

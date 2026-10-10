import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { Association } from '../../core/api/api.types';
import { errorInterceptor } from '../../core/http/interceptors';
import { AssociationsPage } from './associations.page';

const url = `${environment.services.recycler}/associations`;

const association = (n: number, status: Association['status'] = 'ACTIVE'): Association => ({
  id: `a-${n}`,
  name: `Asociación ${n}`,
  ruc: `2060000000${n}`,
  registrationNumber: null,
  address: null,
  contactEmail: null,
  contactPhone: null,
  status,
});

describe('AssociationsPage', () => {
  let fixture: ComponentFixture<AssociationsPage>;
  let backend: HttpTestingController;
  let page: HTMLElement;

  const setup = (inputs: { pagina?: unknown; estado?: string }) => {
    TestBed.configureTestingModule({
      imports: [AssociationsPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AssociationsPage);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
  };
  const pending = (): TestRequest => backend.expectOne((r) => r.url === url);
  const answer = async (request: TestRequest, content: Association[], totalElements: number, number: number) => {
    request.flush({ content, page: number, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) });
    await Promise.resolve();
    TestBed.tick();
  };

  it('maps ?estado= and ?pagina= (one-based, whole) to the API', () => {
    setup({ pagina: '2.7', estado: 'suspendidas' });
    const request = pending();
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('status')).toBe('SUSPENDED');
  });

  it('an unknown ?estado= lists all, without a status parameter', () => {
    setup({ estado: 'otra' });
    expect(pending().request.params.has('status')).toBe(false);
  });

  it('counts the rows shown and announces them once loaded', async () => {
    setup({ pagina: 2 });
    await answer(pending(), [association(1), association(2, 'SUSPENDED')], 22, 1);
    expect(page.querySelector('.list-foot .count')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Mostrando 21–22 de 22 asociaciones',
    );
    expect(page.querySelector('[role="status"]')?.textContent).toBe('Mostrando 21–22 de 22 asociaciones.');
    expect(page.querySelectorAll('table.ledger tbody tr')).toHaveLength(2);
  });

  it('says why a list is empty: nothing registered, nothing in this state, or a page past the end', async () => {
    setup({ estado: 'suspendidas' });
    await answer(pending(), [], 0, 0);
    expect(page.querySelector('.empty-note')?.textContent?.trim()).toBe('No hay asociaciones suspendidas.');

    TestBed.resetTestingModule();
    setup({});
    await answer(pending(), [], 0, 0);
    expect(page.querySelector('.empty-note')?.textContent?.trim()).toBe('Todavía no hay asociaciones registradas.');

    TestBed.resetTestingModule();
    setup({ pagina: 5 });
    await answer(pending(), [], 3, 4);
    expect(page.querySelector('.empty-note')?.textContent).toContain('Esta página ya no tiene asociaciones.');
    expect(page.querySelector('.empty-note a')?.textContent).toBe('Ir a la primera página');
  });
});

import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/interceptors';
import { todayInLima } from '../../shared/format';
import { AssociationPage } from './association.page';

const base = `${environment.services.recycler}/associations/a-1`;

function addDays(localDate: string, days: number): string {
  const d = new Date(`${localDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

describe('AssociationPage', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;

  const flush = async (url: string, body: object) => {
    backend.expectOne((r) => r.url === url).flush(body);
    await Promise.resolve();
    TestBed.tick();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AssociationPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AssociationPage);
    fixture.componentRef.setInput('id', 'a-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
  });

  it('shows a missing optional field as "No registrado", never null or blank', async () => {
    await flush(base, {
      id: 'a-1',
      name: 'Asociación Recicla Rímac',
      ruc: '20601234567',
      registrationNumber: 'REG-2021-0145',
      address: '  ',
      contactEmail: null,
      contactPhone: null,
      status: 'SUSPENDED',
    });
    const details = [...page.querySelectorAll('.details dl > div')].map(
      (d) => `${d.querySelector('dt')?.textContent?.trim()} ${d.querySelector('dd')?.textContent?.trim()}`,
    );
    expect(details).toEqual([
      'N.º de registro REG-2021-0145',
      'Dirección No registrado',
      'Correo de contacto No registrado',
      'Teléfono de contacto No registrado',
    ]);
    expect(page.querySelector('h1')?.textContent).toBe('Asociación Recicla Rímac');
    expect(page.textContent).toContain('Suspendida');
    expect(page.textContent).not.toContain('null');
  });

  it('gives each certification its state chip and a note in days', async () => {
    const today = todayInLima();
    await flush(`${base}/certifications`, {
      content: [
        { id: 'c1', associationId: 'a-1', certificationType: 'Registro municipal', issuedDate: addDays(today, -400), expirationDate: addDays(today, -3), expired: true },
        { id: 'c2', associationId: 'a-1', certificationType: 'Segregación', issuedDate: addDays(today, -300), expirationDate: addDays(today, 12), expired: false },
        { id: 'c3', associationId: 'a-1', certificationType: 'MINAM', issuedDate: addDays(today, -10), expirationDate: addDays(today, 200), expired: false },
      ],
      page: 0,
      size: 100,
      totalElements: 3,
      totalPages: 1,
    });
    const rows = [...page.querySelectorAll('table.ledger tbody tr')];
    expect(rows.map((r) => r.getAttribute('data-state'))).toEqual(['vencido', 'por-vencer', 'vigente']);
    expect(rows.map((r) => r.querySelector('app-certification-chip')?.textContent?.trim())).toEqual([
      'Vencido',
      'Por vencer',
      'Vigente',
    ]);
    expect(rows[0].textContent).toContain('Venció hace 3 días');
    expect(rows[1].textContent).toContain('Vence en 12 días');
    expect(page.querySelector('.foot')?.textContent).toContain('próximos 30 días');
  });

  it('an unknown association says so, and its certifications are not shown', async () => {
    backend
      .expectOne(base)
      .flush({ code: 'ASO-001', detail: 'Asociación no encontrada' }, { status: 404, statusText: 'Not Found' });
    await Promise.resolve();
    TestBed.tick();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Esta asociación no existe.');
    expect(page.querySelector('#certs-title')).toBeNull();
  });
});

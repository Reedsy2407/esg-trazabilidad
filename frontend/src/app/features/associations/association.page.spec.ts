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
    expect(page.querySelector('.foot')?.textContent).toContain('vence en menos de 30 días');
  });

  describe('writes', () => {
    const ASSOCIATION = {
      id: 'a-1',
      name: 'Asociación Recicla Rímac',
      ruc: '20601234567',
      registrationNumber: null,
      address: null,
      contactEmail: null,
      contactPhone: null,
      status: 'ACTIVE',
    };
    const EMPTY = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 1 };
    const CERT = {
      id: 'c1',
      associationId: 'a-1',
      certificationType: 'Formalización MINAM',
      issuedDate: '2026-01-01',
      expirationDate: '2027-01-01',
      expired: false,
    };
    const click = (selector: string) => {
      (page.querySelector(selector) as HTMLElement).click();
      TestBed.tick();
    };
    const type = (id: string, value: string) => {
      const input = page.querySelector(`#${id}`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      TestBed.tick();
    };
    const buttonByText = (text: string) =>
      [...page.querySelectorAll('button')].find((b) => b.textContent?.trim() === text) as HTMLButtonElement;
    const settle = async () => {
      await Promise.resolve();
      TestBed.tick();
    };

    beforeEach(async () => {
      await flush(base, ASSOCIATION);
      await flush(`${base}/certifications`, { ...EMPTY, content: [CERT], totalElements: 1 });
    });

    it('suspending asks for confirmation first, then PATCHes and reloads the association', async () => {
      buttonByText('Suspender asociación').click();
      TestBed.tick();
      backend.expectNone(`${base}/suspend`);
      expect(page.querySelector('.status-actions')?.textContent).toContain('se deja de poder registrar recojos');
      buttonByText('Sí, suspender').click();
      TestBed.tick();
      backend.expectOne(`${base}/suspend`).flush({ ...ASSOCIATION, status: 'SUSPENDED' });
      await settle();
      backend.expectOne(base).flush({ ...ASSOCIATION, status: 'SUSPENDED' });
      await settle();
      expect(page.textContent).toContain('Suspendida');
      expect(buttonByText('Reactivar asociación')).toBeTruthy();
    });

    it('an association already in that state (ASO-003) is reloaded instead of leaving a stale button', async () => {
      buttonByText('Suspender asociación').click();
      TestBed.tick();
      buttonByText('Sí, suspender').click();
      TestBed.tick();
      backend
        .expectOne(`${base}/suspend`)
        .flush({ code: 'ASO-003', detail: 'x' }, { status: 409, statusText: 'Conflict' });
      await settle();
      expect(page.querySelector('.status-actions [role="alert"]')?.textContent).toContain('ya está en ese estado');
      backend.expectOne(base).flush({ ...ASSOCIATION, status: 'SUSPENDED' });
    });

    it('registering a certification needs the three fields and dates in order, then POSTs and reloads the list', async () => {
      buttonByText('Registrar certificación').click();
      TestBed.tick();
      (page.querySelector('form.cert-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      TestBed.tick();
      expect(page.querySelector('#certificationType-error')).not.toBeNull();
      type('certificationType', ' ISO 14001 ');
      type('issuedDate', '2026-05-01');
      type('expirationDate', '2026-04-01');
      (page.querySelector('form.cert-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      TestBed.tick();
      expect(page.querySelector('#expirationDate-error')?.textContent).toContain('posterior a la emisión');
      backend.expectNone(`${base}/certifications`);
      type('expirationDate', '2027-05-01');
      (page.querySelector('form.cert-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      TestBed.tick();
      const post = backend.expectOne((r) => r.method === 'POST' && r.url === `${base}/certifications`);
      expect(post.request.body).toEqual({ certificationType: 'ISO 14001', issuedDate: '2026-05-01', expirationDate: '2027-05-01' });
      post.flush({ ...CERT, id: 'c2' });
      await settle();
      backend.expectOne((r) => r.method === 'GET' && r.url === `${base}/certifications`).flush(EMPTY);
      await settle();
      expect(page.querySelector('form.cert-form')).toBeNull();
    });

    it('renewing PATCHes the new expiration date and reloads; a refused date shows the reason', async () => {
      click('table.ledger tbody button');
      expect(page.querySelector('#renew-title')?.textContent).toContain('Formalización MINAM');
      (page.querySelector('form.renew-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      TestBed.tick();
      expect(page.querySelector('#renewDate-error')).not.toBeNull();
      type('renewDate', '2025-01-01');
      (page.querySelector('form.renew-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      TestBed.tick();
      backend
        .expectOne(`${base}/certifications/c1/renew`)
        .flush({ code: 'CER-003', detail: 'x' }, { status: 400, statusText: 'Bad Request' });
      await settle();
      expect(page.querySelector('.renew-form [role="alert"]')?.textContent).toContain('posterior a la fecha de emisión');
      type('renewDate', '2028-01-01');
      (page.querySelector('form.renew-form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      TestBed.tick();
      const patch = backend.expectOne(`${base}/certifications/c1/renew`);
      expect(patch.request.body).toEqual({ newExpirationDate: '2028-01-01' });
      patch.flush({ ...CERT, expirationDate: '2028-01-01' });
      await settle();
      backend.expectOne((r) => r.method === 'GET' && r.url === `${base}/certifications`).flush(EMPTY);
      await settle();
      expect(page.querySelector('form.renew-form')).toBeNull();
    });
  });

  describe('recyclers (app access)', () => {
    const recyclersPage = (names: string[], pageNumber: number, totalPages: number) => ({
      content: names.map((n, i) => ({ id: `r-${pageNumber}-${i}`, fullName: n, associationId: 'a-1', status: i === 1 ? 'INACTIVE' : 'ACTIVE' })),
      page: pageNumber,
      size: 20,
      totalElements: totalPages * 20,
      totalPages,
    });

    it('lists the recyclers with their state and a link to each one\'s access screen, without DNI or phone', async () => {
      await flush(`${base}/recyclers`, recyclersPage(['Rosa Quispe Huamán', 'Julio Ccori Mamani'], 0, 1));
      const items = [...page.querySelectorAll('ul[aria-label="Recicladores"] .entry')];
      expect(items.map((i) => i.querySelector('.entry-title')?.textContent)).toEqual(['Rosa Quispe Huamán', 'Julio Ccori Mamani']);
      expect(items.map((i) => i.querySelector('dd')?.textContent)).toEqual(['Activo', 'Inactivo']);
      expect(items[0].querySelector('a')?.getAttribute('href')).toBe('/asociaciones/a-1/recicladores/r-0-0');
      expect(items[0].querySelector('a')?.getAttribute('aria-label')).toBe('Acceso a la app de Rosa Quispe Huamán');
      expect(page.querySelector('ul[aria-label="Recicladores"]')?.textContent).not.toMatch(/dni|\d{8}/i);
    });

    it('says so when the association has no recyclers', async () => {
      await flush(`${base}/recyclers`, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
      expect(page.textContent).toContain('todavía no tiene recicladores registrados');
    });

    it('a failed load says so and offers to retry, without hiding the rest of the page', async () => {
      backend.expectOne((r) => r.url === `${base}/recyclers`).flush({}, { status: 503, statusText: 'Unavailable' });
      await Promise.resolve();
      TestBed.tick();
      const alert = page.querySelector('#recyclers-title')?.parentElement?.querySelector('[role="alert"]');
      expect(alert?.querySelector('p')?.textContent).toBe(
        'No se pudieron cargar los recicladores: el servicio de recicladores no está disponible en este momento. Suele volver en menos de un minuto: vuelve a intentarlo.',
      );
      expect(alert?.textContent).not.toContain('No se encontraron');
      expect(page.querySelector('#certs-title')).not.toBeNull();
    });

    it('a network failure names the service, and "no se encontraron" never shows for a load that merely failed', async () => {
      backend.expectOne((r) => r.url === `${base}/recyclers`).error(new ProgressEvent('error'));
      await Promise.resolve();
      TestBed.tick();
      const text = page.querySelector('#recyclers-title')?.parentElement?.querySelector('[role="alert"] p')?.textContent;
      expect(text).toBe('No se pudo conectar con el servicio de recicladores para cargar los recicladores. Comprueba tu conexión.');
    });

    it('with more than one page, Siguiente asks for the next page', async () => {
      await flush(`${base}/recyclers`, recyclersPage(['Rosa Quispe Huamán', 'Julio Ccori Mamani'], 0, 2));
      expect(page.querySelector('.foot')?.textContent).toContain('Página 1 de 2');
      const next = [...page.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Siguiente') as HTMLButtonElement;
      next.click();
      TestBed.tick();
      const request = backend.expectOne((r) => r.url === `${base}/recyclers` && r.params.get('page') === '1');
      request.flush(recyclersPage(['Ana Torres'], 1, 2));
      await Promise.resolve();
      TestBed.tick();
      expect(page.querySelector('ul[aria-label="Recicladores"] .entry-title')?.textContent).toBe('Ana Torres');
    });
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

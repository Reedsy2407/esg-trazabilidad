import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Page, SigersolSync } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { SigersolPage } from './sigersol.page';

const page = <T,>(content: T[], totalElements = content.length, number = 0): Page<T> => ({
  content,
  page: number,
  size: 20,
  totalElements,
  totalPages: Math.ceil(totalElements / 20),
});
const sync = (id: string, over: Partial<SigersolSync> = {}): SigersolSync => ({
  id,
  associationId: 'a-1',
  periodStart: '2026-09-01',
  periodEnd: '2026-09-30',
  hierarchyCompliancePercent: 91.25,
  officialKilosDeclared: null,
  declaredAt: '2026-10-02T15:30:00Z',
  sourceNote: null,
  ...over,
});

describe('SigersolPage', () => {
  let calls: { page: number; associationId: string | null }[];

  const setup = async (content: SigersolSync[], inputs: Record<string, unknown> = {}, total = content.length) => {
    calls = [];
    TestBed.configureTestingModule({
      imports: [SigersolPage],
      providers: [
        provideRouter([]),
        {
          provide: BackendApi,
          useValue: {
            sigersolSyncs: (p: number, associationId: string | null) => {
              calls.push({ page: p, associationId });
              return of(page(content, total, p));
            },
            associations: () => of(page([{ id: 'a-1', name: 'Recicla Rímac' }])),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(SigersolPage);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    TestBed.tick();
    await Promise.resolve();
    TestBed.tick();
    return fixture.nativeElement as HTMLElement;
  };

  it('labels the page as manual entry and shows each record with names, not ids', async () => {
    const el = await setup([sync('s-1'), sync('s-2', { associationId: 'a-x', officialKilosDeclared: 12500, sourceNote: ' Reporte anual ' })]);
    expect(el.querySelector('app-manual-tag')?.textContent?.trim()).toBe('Ingreso manual');
    const rows = [...el.querySelectorAll('table.ledger tbody tr')].map((r) =>
      [...r.querySelectorAll('td')].map((td) => td.textContent?.replace(/\s+/g, ' ').trim()),
    );
    expect(rows[0]).toEqual(['Setiembre 2026', 'Recicla Rímac', '91.25 %', 'No declarado', '02/10/2026']);
    expect(rows[1]).toEqual(['Setiembre 2026 Reporte anual', 'Asociación no listada', '91.25 %', '12,500.00 kg', '02/10/2026']);
  });

  it('?asociacion= filters by association, and its empty list says why it matters', async () => {
    const el = await setup([], { asociacion: 'a-1' });
    expect(calls).toEqual([{ page: 0, associationId: 'a-1' }]);
    expect(el.querySelector('.empty-note')?.textContent).toContain('no se le puede emitir un certificado');
    expect((el.querySelector('#association-filter') as HTMLSelectElement).value).toBe('a-1');
  });

  it('pages, one-based in the address', async () => {
    await setup([sync('s-1')], { pagina: 2 }, 25);
    expect(calls).toEqual([{ page: 1, associationId: null }]);
  });
});

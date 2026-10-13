import { TestBed } from '@angular/core/testing';

import { DistrictLine } from '../core/api/api.types';
import { DistrictContext } from './district-context';

const line = (over: Partial<DistrictLine>): DistrictLine => ({
  districtUbigeo: '150114',
  districtName: 'La Molina',
  kilos: 1250.5,
  status: 'AVAILABLE',
  year: 2024,
  associationCount: 3,
  recyclerCount: 31,
  ...over,
});

describe('DistrictContext', () => {
  const render = (lines: readonly DistrictLine[] | null | undefined) => {
    const fixture = TestBed.createComponent(DistrictContext);
    fixture.componentRef.setInput('lines', lines);
    TestBed.tick();
    return fixture.nativeElement as HTMLElement;
  };
  const text = (el: HTMLElement) => el.textContent?.replace(/\s+/g, ' ').trim() ?? '';

  it('says each district\'s counts with their year and source, as aggregated figures', () => {
    const el = render([line({})]);
    expect(text(el)).toContain('La Molina');
    expect(text(el)).toContain('1,250.50 kg');
    expect(text(el)).toContain('Asociaciones: 3; recicladores: 31. Fuente: SIGERSOL Municipal, año 2024, datos agregados.');
    expect(text(el)).toContain('Describen el distrito, no a la empresa ni a sus proveedores.');
  });

  it('says "no disponible" for a district SIGERSOL had nothing for, and flags kilos without a district', () => {
    const el = render([
      line({ districtName: 'San Isidro', status: 'UNAVAILABLE', year: null, associationCount: null, recyclerCount: null }),
      line({ districtUbigeo: null, districtName: 'Distrito desconocido', status: 'UNKNOWN_DISTRICT', year: null, associationCount: null, recyclerCount: null }),
    ]);
    expect(text(el)).toContain('Contexto de SIGERSOL Municipal no disponible.');
    expect(text(el)).toContain('Las pesadas de estos kilos no registran distrito.');
  });

  it('renders nothing without districts, and never claims validation', () => {
    expect(text(render([]))).toBe('');
    expect(text(render(null))).toBe('');
    expect(text(render([line({})])).toLowerCase()).not.toContain('validad');
  });
});

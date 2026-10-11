import { readFileSync } from 'node:fs';

import { DISTRICTS, districtByName, districtByUbigeo, foldForSearch } from './districts';

// Vitest runs with frontend-demo/ as its working directory; the backend's catalog lives next door.
const CATALOG = '../shared-kernel/src/main/resources/pe/esgtrazabilidad/kernel/ubigeo/lima-callao-districts.csv';

describe('district catalog', () => {
  it('is exactly the backend catalog: same codes, areas and names, in the same order', () => {
    const rows = readFileSync(CATALOG, 'utf-8')
      .split(/\r?\n/)
      .filter((line) => line.trim() !== '' && !line.startsWith('#'))
      .slice(1) // header
      .map((line) => {
        const [ubigeo, area, , name] = line.split(';');
        return { ubigeo, area, name };
      });

    expect(rows).toHaveLength(50);
    expect(DISTRICTS.map((d) => ({ ...d }))).toEqual(rows);
  });

  it('has 43 districts of Lima Metropolitana and 7 of Callao', () => {
    expect(DISTRICTS.filter((d) => d.area === 'LIMA_METROPOLITANA')).toHaveLength(43);
    expect(DISTRICTS.filter((d) => d.area === 'CALLAO')).toHaveLength(7);
  });

  it('finds by code and by name, ignoring case, accents and extra spaces', () => {
    expect(districtByUbigeo('150128')?.name).toBe('Rímac');
    expect(districtByUbigeo('150201')).toBeUndefined();
    expect(districtByUbigeo(null)).toBeUndefined();
    expect(districtByName('  san martin de porres ')?.ubigeo).toBe('150135');
    expect(districtByName('BRENA')?.ubigeo).toBe('150105');
    expect(districtByName('Cercado de Lima')).toBeUndefined();
    expect(districtByName('')).toBeUndefined();
  });

  it('folds text only for comparison', () => {
    expect(foldForSearch('  Breña  Rímac ')).toBe('brena rimac');
  });
});

/**
 * The closed list of districts a neighbour can live in: the 43 of Lima Metropolitana (province
 * 1501) and the 7 of Callao (province 0701), INEI ubigeo. A copy of shared-kernel's
 * lima-callao-districts.csv (the backend's UbigeoCatalog); districts.spec.ts reads that file
 * and fails if the two ever differ.
 */
export type DistrictArea = 'LIMA_METROPOLITANA' | 'CALLAO';

export interface District {
  readonly ubigeo: string;
  readonly area: DistrictArea;
  /** The name to show, with its accents and ñ. */
  readonly name: string;
}

export const DISTRICTS: readonly District[] = [
  { ubigeo: '070101', area: 'CALLAO', name: 'Callao' },
  { ubigeo: '070102', area: 'CALLAO', name: 'Bellavista' },
  { ubigeo: '070103', area: 'CALLAO', name: 'Carmen de la Legua Reynoso' },
  { ubigeo: '070104', area: 'CALLAO', name: 'La Perla' },
  { ubigeo: '070105', area: 'CALLAO', name: 'La Punta' },
  { ubigeo: '070106', area: 'CALLAO', name: 'Ventanilla' },
  { ubigeo: '070107', area: 'CALLAO', name: 'Mi Perú' },
  { ubigeo: '150101', area: 'LIMA_METROPOLITANA', name: 'Lima' },
  { ubigeo: '150102', area: 'LIMA_METROPOLITANA', name: 'Ancón' },
  { ubigeo: '150103', area: 'LIMA_METROPOLITANA', name: 'Ate' },
  { ubigeo: '150104', area: 'LIMA_METROPOLITANA', name: 'Barranco' },
  { ubigeo: '150105', area: 'LIMA_METROPOLITANA', name: 'Breña' },
  { ubigeo: '150106', area: 'LIMA_METROPOLITANA', name: 'Carabayllo' },
  { ubigeo: '150107', area: 'LIMA_METROPOLITANA', name: 'Chaclacayo' },
  { ubigeo: '150108', area: 'LIMA_METROPOLITANA', name: 'Chorrillos' },
  { ubigeo: '150109', area: 'LIMA_METROPOLITANA', name: 'Cieneguilla' },
  { ubigeo: '150110', area: 'LIMA_METROPOLITANA', name: 'Comas' },
  { ubigeo: '150111', area: 'LIMA_METROPOLITANA', name: 'El Agustino' },
  { ubigeo: '150112', area: 'LIMA_METROPOLITANA', name: 'Independencia' },
  { ubigeo: '150113', area: 'LIMA_METROPOLITANA', name: 'Jesús María' },
  { ubigeo: '150114', area: 'LIMA_METROPOLITANA', name: 'La Molina' },
  { ubigeo: '150115', area: 'LIMA_METROPOLITANA', name: 'La Victoria' },
  { ubigeo: '150116', area: 'LIMA_METROPOLITANA', name: 'Lince' },
  { ubigeo: '150117', area: 'LIMA_METROPOLITANA', name: 'Los Olivos' },
  { ubigeo: '150118', area: 'LIMA_METROPOLITANA', name: 'Lurigancho' },
  { ubigeo: '150119', area: 'LIMA_METROPOLITANA', name: 'Lurín' },
  { ubigeo: '150120', area: 'LIMA_METROPOLITANA', name: 'Magdalena del Mar' },
  { ubigeo: '150121', area: 'LIMA_METROPOLITANA', name: 'Pueblo Libre' },
  { ubigeo: '150122', area: 'LIMA_METROPOLITANA', name: 'Miraflores' },
  { ubigeo: '150123', area: 'LIMA_METROPOLITANA', name: 'Pachacámac' },
  { ubigeo: '150124', area: 'LIMA_METROPOLITANA', name: 'Pucusana' },
  { ubigeo: '150125', area: 'LIMA_METROPOLITANA', name: 'Puente Piedra' },
  { ubigeo: '150126', area: 'LIMA_METROPOLITANA', name: 'Punta Hermosa' },
  { ubigeo: '150127', area: 'LIMA_METROPOLITANA', name: 'Punta Negra' },
  { ubigeo: '150128', area: 'LIMA_METROPOLITANA', name: 'Rímac' },
  { ubigeo: '150129', area: 'LIMA_METROPOLITANA', name: 'San Bartolo' },
  { ubigeo: '150130', area: 'LIMA_METROPOLITANA', name: 'San Borja' },
  { ubigeo: '150131', area: 'LIMA_METROPOLITANA', name: 'San Isidro' },
  { ubigeo: '150132', area: 'LIMA_METROPOLITANA', name: 'San Juan de Lurigancho' },
  { ubigeo: '150133', area: 'LIMA_METROPOLITANA', name: 'San Juan de Miraflores' },
  { ubigeo: '150134', area: 'LIMA_METROPOLITANA', name: 'San Luis' },
  { ubigeo: '150135', area: 'LIMA_METROPOLITANA', name: 'San Martín de Porres' },
  { ubigeo: '150136', area: 'LIMA_METROPOLITANA', name: 'San Miguel' },
  { ubigeo: '150137', area: 'LIMA_METROPOLITANA', name: 'Santa Anita' },
  { ubigeo: '150138', area: 'LIMA_METROPOLITANA', name: 'Santa María del Mar' },
  { ubigeo: '150139', area: 'LIMA_METROPOLITANA', name: 'Santa Rosa' },
  { ubigeo: '150140', area: 'LIMA_METROPOLITANA', name: 'Santiago de Surco' },
  { ubigeo: '150141', area: 'LIMA_METROPOLITANA', name: 'Surquillo' },
  { ubigeo: '150142', area: 'LIMA_METROPOLITANA', name: 'Villa El Salvador' },
  { ubigeo: '150143', area: 'LIMA_METROPOLITANA', name: 'Villa María del Triunfo' },
];

export const DISTRICT_AREAS: readonly { readonly area: DistrictArea; readonly label: string }[] = [
  { area: 'LIMA_METROPOLITANA', label: 'Lima Metropolitana' },
  { area: 'CALLAO', label: 'Callao' },
];

const BY_UBIGEO = new Map(DISTRICTS.map((d) => [d.ubigeo, d]));

export function districtByUbigeo(ubigeo: string | null | undefined): District | undefined {
  return ubigeo == null ? undefined : BY_UBIGEO.get(ubigeo);
}

/** For comparison only: no accents (ñ becomes n), lower case, single spaces. "Rímac" ≈ "RIMAC". */
export function foldForSearch(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/** The district a free-text name refers to (an old link, a pre-v3 neighbour), if any. */
export function districtByName(name: string | null | undefined): District | undefined {
  if (name == null) {
    return undefined;
  }
  const folded = foldForSearch(name);
  return folded === '' ? undefined : DISTRICTS.find((d) => foldForSearch(d.name) === folded);
}

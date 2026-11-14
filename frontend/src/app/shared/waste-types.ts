/** The catalogue of waste types (spec A.5b), by code. A code the catalogue does not know is shown as it came. */
export const WASTE_TYPE_NAMES: Readonly<Record<string, string>> = {
  PAPEL: 'Papel',
  CARTON: 'Cartón',
  PET: 'Plástico PET',
  PLAST_OTRO: 'Otros plásticos',
  VIDRIO: 'Vidrio',
  METAL: 'Metales',
  TETRABRIK: 'Tetrabrik y envases compuestos',
  TEXTIL: 'Textiles, caucho y cuero',
};

export function wasteTypeName(code: string): string {
  return WASTE_TYPE_NAMES[code] ?? code;
}

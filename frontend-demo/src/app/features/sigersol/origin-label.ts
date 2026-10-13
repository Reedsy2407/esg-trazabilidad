import { SigersolCoverage, SigersolOrigin } from '../../core/api/api.types';

/**
 * How a SIGERSOL figure was obtained, in words for the screen. Never says it was verified: it is
 * what the staff member said about their own source. A record made before v3 has no origin.
 */
export const ORIGIN_OPTIONS: readonly { value: SigersolOrigin; label: string }[] = [
  { value: 'DECLARED_BY_OPERATOR', label: 'Declarado por el operador' },
  { value: 'REFERENCED_PROOF', label: 'Con comprobante referenciado' },
];

/**
 * The line under a compliance figure: where it came from, or that nothing covers the period.
 * Wording is deliberate: it is a figure staff entered, never one SIGERSOL confirmed.
 */
export function coverageNote(summary: {
  sigersolCoverage?: SigersolCoverage;
  sigersolOrigin?: SigersolOrigin | null;
  sigersolRegisteredByEmail?: string | null;
  hierarchyCompliancePercent: number | null;
}): string {
  if (summary.sigersolCoverage === 'UNVERIFIED' || summary.hierarchyCompliancePercent === null) {
    return 'No verificado: ningún registro SIGERSOL cubre el período.';
  }
  const who = summary.sigersolRegisteredByEmail ? ` · registrado por ${summary.sigersolRegisteredByEmail}` : '';
  return `${originLabel(summary.sigersolOrigin ?? null)}${who}`;
}

export function originLabel(origin: SigersolOrigin | null): string {
  return ORIGIN_OPTIONS.find((o) => o.value === origin)?.label ?? 'Origen no registrado';
}

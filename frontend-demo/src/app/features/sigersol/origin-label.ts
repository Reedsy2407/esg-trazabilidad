import { SigersolCoverage, SigersolOrigin } from '../../core/api/api.types';

/**
 * How a SIGERSOL figure was obtained, in words for the screen. Never says it was verified: it is
 * what the staff member said about their own source. A record made before v3 has no origin.
 */
export const ORIGIN_OPTIONS: readonly { value: SigersolOrigin; label: string }[] = [
  { value: 'DECLARED_BY_OPERATOR', label: 'Declarado por el operador' },
  { value: 'REFERENCED_PROOF', label: 'Con comprobante referenciado' },
];

/** The label of the hierarchy figure on every screen, in the PDF and in the CSV. */
export const COMPLIANCE_LABEL = 'Cumplimiento de jerarquía (declarado)';

/**
 * The statement under the form and the list: these are figures the operator declared and somebody
 * typed in, neither read from SIGERSOL nor checked against it.
 */
export const DECLARED_STATEMENT =
  'Cifras declaradas por el operador, ingresadas a mano; esta aplicación no se conecta con SIGERSOL ni las verifica.';

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
    return 'Sin dato declarado: ningún registro cubre el período.';
  }
  const who = summary.sigersolRegisteredByEmail ? ` · registrado por ${summary.sigersolRegisteredByEmail}` : '';
  return `${originLabel(summary.sigersolOrigin ?? null)}${who}`;
}

export function originLabel(origin: SigersolOrigin | null): string {
  return ORIGIN_OPTIONS.find((o) => o.value === origin)?.label ?? 'Dato anterior al registro de origen';
}

/**
 * The note printed with a certificate's hierarchy figure, word for word what the PDF and the CSV say
 * (reporting-service HierarchyDeclaration.note): who declared it, with what origin and proof, and that
 * it is neither an official SIGERSOL figure nor checked by this platform. The parts not known are left out.
 */
export function hierarchyNote(declaration: {
  hierarchyOrigin?: SigersolOrigin | null;
  hierarchyRegisteredByEmail?: string | null;
  hierarchyReference?: string | null;
}): string {
  const origin = declaration.hierarchyOrigin ?? null;
  const originText =
    origin === 'DECLARED_BY_OPERATOR'
      ? 'declarado por el operador'
      : origin === 'REFERENCED_PROOF'
        ? 'con comprobante referenciado'
        : 'no registrado';
  const email = declaration.hierarchyRegisteredByEmail?.trim();
  const reference = declaration.hierarchyReference?.trim();
  const who = email ? `, registrado por ${email}` : '';
  const proof = reference ? `, respaldado por el comprobante «${reference}»` : '';
  return (
    `El cumplimiento de jerarquía es un dato declarado por el operador de la asociación (origen: ${originText}${who})${proof}; ` +
    'no es una cifra oficial de SIGERSOL ni la verifica esta plataforma.'
  );
}

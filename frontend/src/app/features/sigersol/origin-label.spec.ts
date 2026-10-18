import { COMPLIANCE_LABEL, DECLARED_STATEMENT, coverageNote, hierarchyNote } from './origin-label';

const TAIL = '; no es una cifra oficial de SIGERSOL ni la verifica esta plataforma.';
const HEAD = 'El cumplimiento de jerarquía es un dato declarado por el operador de la asociación';

describe('hierarchy declaration texts', () => {
  it('the label and the statement are the agreed wording', () => {
    expect(COMPLIANCE_LABEL).toBe('Cumplimiento de jerarquía (declarado)');
    expect(DECLARED_STATEMENT).toBe(
      'Cifras declaradas por el operador, ingresadas a mano; esta aplicación no se conecta con SIGERSOL ni las verifica.',
    );
  });

  it('a covered period says where the figure came from; an uncovered one says nothing was declared', () => {
    expect(coverageNote({ sigersolCoverage: 'RECORDED', sigersolOrigin: 'REFERENCED_PROOF', sigersolRegisteredByEmail: 'ana@asociacion.pe', hierarchyCompliancePercent: 80 })).toBe(
      'Con comprobante referenciado · registrado por ana@asociacion.pe',
    );
    expect(coverageNote({ sigersolCoverage: 'RECORDED', hierarchyCompliancePercent: 80 })).toBe('Dato anterior al registro de origen');
    expect(coverageNote({ sigersolCoverage: 'UNVERIFIED', hierarchyCompliancePercent: null })).toBe(
      'Sin dato declarado: ningún registro cubre el período.',
    );
    expect(coverageNote({ hierarchyCompliancePercent: null })).not.toContain('No verificado');
  });

  it('the certificate note is the one the PDF and the CSV print, word for word', () => {
    expect(hierarchyNote({ hierarchyOrigin: 'REFERENCED_PROOF', hierarchyRegisteredByEmail: 'ana@asociacion.pe', hierarchyReference: 'factura F001-123' })).toBe(
      `${HEAD} (origen: con comprobante referenciado, registrado por ana@asociacion.pe), respaldado por el comprobante «factura F001-123»${TAIL}`,
    );
    expect(hierarchyNote({ hierarchyOrigin: 'DECLARED_BY_OPERATOR', hierarchyRegisteredByEmail: 'ana@asociacion.pe' })).toBe(
      `${HEAD} (origen: declarado por el operador, registrado por ana@asociacion.pe)${TAIL}`,
    );
  });

  it('a record from before origins, or a list page, says the origin was not recorded and names nobody', () => {
    expect(hierarchyNote({})).toBe(`${HEAD} (origen: no registrado)${TAIL}`);
    expect(hierarchyNote({ hierarchyOrigin: null, hierarchyRegisteredByEmail: '  ', hierarchyReference: ' ' })).toBe(
      `${HEAD} (origen: no registrado)${TAIL}`,
    );
  });
});

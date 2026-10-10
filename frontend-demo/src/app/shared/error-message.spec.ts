import { ApiError } from '../core/http/api-error';
import { codeMessage, loadErrorMessage, writeErrorText } from './error-message';

const error = (status: number, code: string | null = null, retryAfter: number | null = null) =>
  new ApiError(status, code, null, retryAfter);

describe('loadErrorMessage', () => {
  it('names the block and says what to do, per status', () => {
    expect(loadErrorMessage(error(0), 'los certificados')).toContain('No se pudo conectar');
    expect(loadErrorMessage(error(403, 'AUTH-000'), 'los certificados')).toBe('No tienes permiso para ver los certificados.');
    expect(loadErrorMessage(error(404, 'RPT-001'), 'los certificados')).toContain('la empresa no existe');
    expect(loadErrorMessage(error(400, 'X-1'), 'los certificados')).toBe('No se pudieron cargar los certificados (X-1).');
    expect(loadErrorMessage(error(400, 'X-1'), 'el certificado')).toBe('No se pudo cargar el certificado (X-1).');
    expect(loadErrorMessage(error(404, 'ASO-001'), 'los datos de la asociación')).toBe(
      'No se encontraron los datos de la asociación: la asociación no existe.',
    );
    expect(loadErrorMessage(error(404), 'las certificaciones')).toBe('No se encontraron las certificaciones.');
    expect(loadErrorMessage(error(404, 'RPT-003'), 'el certificado')).toBe('No se encontró el certificado: el certificado no existe.');
    expect(loadErrorMessage(error(400, 'VALIDATION_ERROR'), 'las asociaciones')).toBe(
      'No se pudieron cargar las asociaciones: el servicio rechazó los datos enviados. Revisa los campos.',
    );
  });

  it('a bare 5xx (no problem detail) says the service did not answer', () => {
    expect(loadErrorMessage(error(503), 'las asociaciones', 'el servicio de recicladores')).toBe(
      'No se pudieron cargar las asociaciones: el servicio de recicladores no respondió (HTTP 503). Vuelve a intentarlo en un momento.',
    );
  });

  it('on 429 tells when to retry, from Retry-After', () => {
    expect(loadErrorMessage(error(429, null, 30), 'el resumen')).toBe('Demasiadas solicitudes. Vuelve a intentarlo en 30 s.');
    expect(loadErrorMessage(error(429), 'el resumen')).toContain('Espera un momento');
  });
});

describe('codeMessage', () => {
  it('gives the catalog sentence for a known code, null otherwise', () => {
    expect(codeMessage(error(409, 'RPT-002'))).toBe('Ya hay una empresa registrada con ese RUC.');
    expect(codeMessage(error(404, 'ASO-001'))).toBe('La asociación no existe.');
    expect(codeMessage(error(500, 'X-1'))).toBeNull();
    expect(codeMessage(error(502))).toBeNull();
    expect(codeMessage(new Error('x'))).toBeNull();
  });

  it('names the service it could not reach', () => {
    expect(loadErrorMessage(error(0), 'las asociaciones', 'el servicio de recicladores')).toBe(
      'No se pudo conectar con el servicio de recicladores para cargar las asociaciones. Comprueba tu conexión.',
    );
  });
});

describe('writeErrorText', () => {
  const UNCERTAIN = 'No pudimos confirmar si quedó registrado.';
  it('never claims a failure when the outcome is unknown', () => {
    for (const e of [error(0), error(408), error(500), error(502, 'RPT-002'), error(504), new Error('x')]) {
      expect(writeErrorText(e, UNCERTAIN)).toBe(UNCERTAIN);
    }
  });

  it('a page override, then the catalog, then the bare code', () => {
    expect(writeErrorText(error(409, 'RPT-002'), UNCERTAIN, { 'RPT-002': 'Ese RUC ya está.' })).toBe('Ese RUC ya está.');
    expect(writeErrorText(error(409, 'COL-002'), UNCERTAIN)).toBe('Este vecino ya tiene un cronograma activo ese día de la semana.');
    expect(writeErrorText(error(409, 'ZZZ-1'), UNCERTAIN)).toBe('El servicio no aceptó la operación (ZZZ-1).');
    expect(writeErrorText(error(429, null, 9), UNCERTAIN)).toBe('Demasiadas solicitudes. Vuelve a intentarlo en 9 s.');
  });
});

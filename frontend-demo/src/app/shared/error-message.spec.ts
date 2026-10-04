import { ApiError } from '../core/http/api-error';
import { loadErrorMessage } from './error-message';

const error = (status: number, code: string | null = null, retryAfter: number | null = null) =>
  new ApiError(status, code, null, retryAfter);

describe('loadErrorMessage', () => {
  it('names the block and says what to do, per status', () => {
    expect(loadErrorMessage(error(0), 'los certificados')).toContain('No se pudo conectar');
    expect(loadErrorMessage(error(403, 'AUTH-000'), 'los certificados')).toBe('No tienes permiso para ver los certificados.');
    expect(loadErrorMessage(error(404, 'RPT-001'), 'los certificados')).toContain('la empresa no existe');
    expect(loadErrorMessage(error(500, 'X-1'), 'los certificados')).toBe('No se pudo cargar los certificados (X-1).');
  });

  it('on 429 tells when to retry, from Retry-After', () => {
    expect(loadErrorMessage(error(429, null, 30), 'el resumen')).toBe('Demasiadas solicitudes. Vuelve a intentarlo en 30 s.');
    expect(loadErrorMessage(error(429), 'el resumen')).toContain('Espera un momento');
  });
});

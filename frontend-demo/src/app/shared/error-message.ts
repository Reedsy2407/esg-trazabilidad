import { ApiError } from '../core/http/api-error';

/**
 * One sentence for a block that failed to load, in es-PE. `what` names the
 * thing ("los certificados"). 401 never gets here: errorInterceptor already
 * ends the session and goes to the login.
 */
export function loadErrorMessage(error: unknown, what: string): string {
  if (!(error instanceof ApiError)) {
    return `No se pudo cargar ${what}.`;
  }
  switch (error.status) {
    case 0:
      return `No se pudo conectar con el servicio de reportes para cargar ${what}. Comprueba tu conexión.`;
    case 403:
      return `No tienes permiso para ver ${what}.`;
    case 404:
      return `No se encontraron ${what}: la empresa no existe o ya no está registrada.`;
    case 429:
      return error.retryAfterSeconds === null
        ? `Demasiadas solicitudes. Espera un momento y vuelve a intentarlo.`
        : `Demasiadas solicitudes. Vuelve a intentarlo en ${error.retryAfterSeconds} s.`;
    default:
      return `No se pudo cargar ${what} (${error.code ?? `HTTP ${error.status}`}).`;
  }
}

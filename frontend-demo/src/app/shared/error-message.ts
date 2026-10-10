import { ApiError } from '../core/http/api-error';

/**
 * Every `code` the four services answer with, in es-PE, phrased for the person using the
 * screen. Copied from the backend's error enums (AssociationErrors, CertificationErrors,
 * RecyclerErrors, CollectionErrors, ReportingErrors, AuthErrors) and the shared
 * GlobalExceptionHandler; a page may say something more specific for the codes it expects.
 */
export const CODE_MESSAGES: Readonly<Record<string, string>> = {
  'AUTH-000': 'Tu sesión no es válida para esta acción.',
  'AUTH-001': 'El correo o la contraseña no son correctos.',
  'AUTH-002': 'Ya existe una cuenta del personal con ese correo.',
  'AUTH-003': 'No se encontró la cuenta del personal.',
  'AUTH-004': 'Demasiados intentos de inicio de sesión. Espera un momento.',
  'AUTH-005': 'El correo no es válido: le falta un punto en el dominio.',
  'ASO-001': 'La asociación no existe.',
  'ASO-002': 'Ya hay una asociación registrada con ese RUC.',
  'ASO-003': 'La asociación ya está en ese estado.',
  'CER-001': 'La certificación no existe o no es de esta asociación.',
  'CER-002': 'La asociación indicada no existe.',
  'CER-003': 'La fecha de emisión debe ser anterior a la de vencimiento.',
  'REC-001': 'El reciclador no existe o no es de esta asociación.',
  'REC-002': 'Ya hay un reciclador registrado con ese DNI.',
  'REC-003': 'La asociación indicada no existe.',
  'REC-004': 'El reciclador ya está en ese estado.',
  'COL-001': 'El vecino no existe.',
  'COL-002': 'Este vecino ya tiene un cronograma activo ese día de la semana.',
  'COL-004': 'La empresa no existe.',
  'COL-005': 'Ya hay una empresa registrada con ese RUC.',
  'COL-006': 'El cronograma no existe o no es de este vecino.',
  'COL-007': 'El registro de recojo no existe.',
  'COL-008': 'El cronograma no puede pasar a ese estado desde el actual.',
  'COL-009': 'La asociación tiene una certificación vencida y no puede registrar recojos.',
  'RPT-001': 'La empresa no existe o ya no está registrada.',
  'RPT-002': 'Ya hay una empresa registrada con ese RUC.',
  'RPT-003': 'El certificado no existe.',
  'RPT-004': 'Ya hay un certificado emitido que se superpone con ese período.',
  'RPT-005': 'No hay un registro SIGERSOL de la asociación que cubra todo ese período.',
  'RPT-006': 'Ya hay un registro SIGERSOL de esa asociación que se superpone con ese período.',
  'RPT-007': 'El registro SIGERSOL no existe.',
  'RPT-008': 'Los datos del registro SIGERSOL no son válidos.',
  'RPT-009': 'La fecha final del período es anterior a la inicial.',
  VALIDATION_ERROR: 'El servicio rechazó los datos enviados. Revisa los campos.',
  DATA_CONFLICT: 'La operación choca con datos ya registrados.',
};

/** The catalog's sentence for this error's code, or null when it has none. */
export function codeMessage(error: unknown): string | null {
  return error instanceof ApiError && error.code !== null ? (CODE_MESSAGES[error.code] ?? null) : null;
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1);
}

const plural = (what: string) => /^(los|las) /.test(what);
/** "No se pudo" for "el certificado", "No se pudieron" for "los certificados". */
const couldNot = (what: string) => (plural(what) ? 'No se pudieron' : 'No se pudo');
const notFound = (what: string) => (plural(what) ? 'No se encontraron' : 'No se encontró');

/**
 * One sentence for a block that failed to load, in es-PE. `what` names the
 * thing ("los certificados"), `service` the service it comes from. A known
 * code speaks through CODE_MESSAGES; a bare 5xx (a gateway or a waking
 * service, no problem detail) says the service didn't answer. 401 never gets
 * here: errorInterceptor already ends the session and goes to the login.
 */
export function loadErrorMessage(error: unknown, what: string, service = 'el servicio de reportes'): string {
  if (!(error instanceof ApiError)) {
    return `${couldNot(what)} cargar ${what}.`;
  }
  const known = codeMessage(error);
  switch (error.status) {
    case 0:
      return `No se pudo conectar con ${service} para cargar ${what}. Comprueba tu conexión.`;
    case 403:
      return `No tienes permiso para ver ${what}.`;
    case 404:
      return known === null ? `${notFound(what)} ${what}.` : `${notFound(what)} ${what}: ${lowerFirst(known)}`;
    case 429:
      return error.retryAfterSeconds === null
        ? `Demasiadas solicitudes. Espera un momento y vuelve a intentarlo.`
        : `Demasiadas solicitudes. Vuelve a intentarlo en ${error.retryAfterSeconds} s.`;
  }
  if (known !== null) {
    return `${couldNot(what)} cargar ${what}: ${lowerFirst(known)}`;
  }
  if (error.status >= 500) {
    return `${couldNot(what)} cargar ${what}: ${service} no respondió (HTTP ${error.status}). Vuelve a intentarlo en un momento.`;
  }
  return `${couldNot(what)} cargar ${what} (${error.code ?? `HTTP ${error.status}`}).`;
}

/**
 * One sentence for a write (POST/PATCH) the backend didn't accept. With no answer, a 408 or a
 * 5xx the outcome is unknown (the write may have gone through; Render answers 502/504 while a
 * service wakes), so `uncertain` says how to check before retrying, and never claims it failed.
 * Otherwise `overrides` (page-specific wording) or the catalog speaks for the code.
 */
export function writeErrorText(
  error: unknown,
  uncertain: string,
  overrides: Readonly<Record<string, string>> = {},
): string {
  if (!(error instanceof ApiError) || error.status === 0 || error.status === 408 || error.status >= 500) {
    return uncertain;
  }
  const specific = error.code === null ? undefined : overrides[error.code];
  if (specific !== undefined) {
    return specific;
  }
  if (error.status === 429) {
    return error.retryAfterSeconds === null
      ? 'Demasiadas solicitudes. Espera un momento y vuelve a intentarlo.'
      : `Demasiadas solicitudes. Vuelve a intentarlo en ${error.retryAfterSeconds} s.`;
  }
  return codeMessage(error) ?? `El servicio no aceptó la operación (${error.code ?? `HTTP ${error.status}`}).`;
}

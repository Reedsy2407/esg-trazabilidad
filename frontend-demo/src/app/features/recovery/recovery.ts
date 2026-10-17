import { Router } from '@angular/router';

import { ApiError } from '../../core/http/api-error';

/** How long a person waits before asking for another code (the service also refuses one inside 60 s). */
export const RESEND_SECONDS = 60;
/** Wrong codes the screen tolerates before sending the person back to ask for a new one (the service spends the code at 5). */
export const MAX_CODE_FAILURES = 5;
export const CODE_LENGTH = 8;

/** The one message after asking for a code: it never says whether the account exists. */
export const SENT_MESSAGE =
  'Si el correo está registrado, te enviamos un código de 8 dígitos. Vence en 10 minutos. Revisa también la carpeta de spam.';
export const RESENT_MESSAGE =
  'Si el correo está registrado, te enviamos un código nuevo. El anterior ya no sirve. Revisa también la carpeta de spam.';
/** The same text for a wrong, expired or spent code, and for an account that does not exist. */
export const INVALID_CODE_MESSAGE = 'El código no es válido o venció. Pide uno nuevo.';
export const TOO_MANY_FAILURES_MESSAGE = 'Escribiste varios códigos que no sirven. Pide un código nuevo.';
export const REQUEST_UNCERTAIN =
  'No pudimos confirmar si se envió el código. Si ya te llegó, escríbelo en el siguiente paso; si no, inténtalo de nuevo en un momento.';
export const CONFIRM_UNCERTAIN =
  'No pudimos confirmar si se cambió la contraseña. Prueba a iniciar sesión con la nueva; si no funciona, pide otro código.';

/** What the person typed or pasted, with spaces and anything else that is not a digit removed ("1234 5678"). */
export function codeDigits(value: string): string {
  return value.replace(/\D/g, '');
}

export function isCodeComplete(value: string): boolean {
  return codeDigits(value).length === CODE_LENGTH;
}

/** "Prueba de nuevo en 1 minuto." / "…en 5 minutos." from a Retry-After in seconds (rounded up, at least one minute). */
export function waitText(retryAfterSeconds: number | null): string {
  if (retryAfterSeconds === null) {
    return 'Demasiados intentos. Prueba de nuevo más tarde.';
  }
  const minutes = Math.max(1, Math.ceil(retryAfterSeconds / 60));
  return `Demasiados intentos. Prueba de nuevo en ${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}.`;
}

/** AUTH-012 (a limit on the caller's address) or any 429. */
export function isRateLimited(error: unknown): error is ApiError {
  return error instanceof ApiError && (error.code === 'AUTH-012' || error.status === 429);
}

/** The state a page leaves in the browser history for the next step: the email (never the code or a password). */
export interface RecoveryState {
  readonly email?: string;
  /** When the code was asked for, to resume the resend countdown after a reload. */
  readonly sentAt?: number;
  /** Why the person was sent back to the first step. */
  readonly notice?: string;
}

/**
 * What the previous step left: while the router is still navigating, the state of that navigation (the
 * browser's history entry is only written once it ends); after a reload, the history entry itself.
 */
export function readRecoveryState(router: Router): RecoveryState {
  const state: unknown = router.currentNavigation()?.extras.state ?? history.state;
  if (typeof state !== 'object' || state === null) {
    return {};
  }
  const { email, sentAt, notice } = state as Record<string, unknown>;
  return {
    email: typeof email === 'string' ? email : undefined,
    sentAt: typeof sentAt === 'number' ? sentAt : undefined,
    notice: typeof notice === 'string' ? notice : undefined,
  };
}

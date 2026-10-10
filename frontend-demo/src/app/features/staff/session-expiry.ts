import { formatDate } from '@angular/common';

import { LIMA_OFFSET, LOCALE } from '../../shared/format';

/** 3_600_000 → "1 hora"; 5_400_000 → "1 hora y 30 minutos"; 1_800_000 → "30 minutos". */
export function durationText(ms: number): string {
  const totalMinutes = Math.round(ms / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  const h = hours === 0 ? '' : `${hours} ${hours === 1 ? 'hora' : 'horas'}`;
  const m = minutes === 0 ? '' : `${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}`;
  return h && m ? `${h} y ${m}` : h || m || 'menos de un minuto';
}

/**
 * "Tu sesión vence a las 15:42 (dura 1 hora y no se renueva)." Both figures come from the token
 * itself: `exp` for the time (shown in Lima) and `exp - iat` for the length, never an assumed hour.
 * auth-service issues no refresh token, so the session is never extended.
 */
export function sessionExpiryText(expiresAt: number, issuedAt: number | null): string {
  const time = formatDate(expiresAt, 'HH:mm', LOCALE, LIMA_OFFSET);
  return issuedAt === null || issuedAt >= expiresAt
    ? `Tu sesión vence a las ${time} y no se renueva.`
    : `Tu sesión vence a las ${time} (dura ${durationText(expiresAt - issuedAt)} y no se renueva).`;
}

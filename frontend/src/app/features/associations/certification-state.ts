import { Certification } from '../../core/api/api.types';
import { parseLocalDate } from '../../shared/format';

export type CertificationState = 'vigente' | 'por-vencer' | 'vencido';

/**
 * "Por vencer" is a rule of this frontend, not a backend state (the backend only says `expired`):
 * an unexpired certification that expires in less than this many days, counted on Lima's
 * calendar. Confirmed by the product owner on 2026-10-10; see docs/frontend.md.
 */
export const EXPIRING_SOON_DAYS = 30;

function epochDay(localDate: string): number {
  const { year, month, day } = parseLocalDate(localDate);
  return Date.UTC(year, month - 1, day) / 86_400_000;
}

/** Whole days from `today` to the expiration date: 0 expires today, negative already passed. */
export function daysUntilExpiration(certification: Pick<Certification, 'expirationDate'>, today: string): number {
  return epochDay(certification.expirationDate) - epochDay(today);
}

/**
 * Vencido is the backend's own `expired` (the same flag that makes collection-service block the
 * association, COL-009), plus any date already past in Lima. Of the rest, those expiring in less
 * than EXPIRING_SOON_DAYS days from Lima's today are por vencer.
 */
export function certificationState(
  certification: Pick<Certification, 'expirationDate' | 'expired'>,
  today: string,
): CertificationState {
  const days = daysUntilExpiration(certification, today);
  // days < 0 with expired=false can't happen while the server's clock (UTC) is at or ahead of
  // Lima's; if it ever did, the date has passed in Lima too, so it is vencido, never por vencer.
  if (certification.expired || days < 0) {
    return 'vencido';
  }
  return days < EXPIRING_SOON_DAYS ? 'por-vencer' : 'vigente';
}

/** "Vence hoy", "Vence en 1 día", "Vence en 12 días"; for an expired one, how long ago. */
export function expirationNote(certification: Pick<Certification, 'expirationDate' | 'expired'>, today: string): string {
  const days = daysUntilExpiration(certification, today);
  if (certification.expired && days >= 0) {
    return 'Ya venció'; // by the backend's clock, which can be a day ahead of Lima's
  }
  if (certification.expired || days < 0) {
    const ago = -days;
    return `Venció hace ${ago} ${ago === 1 ? 'día' : 'días'}`;
  }
  if (days === 0) {
    return 'Vence hoy';
  }
  return `Vence en ${days} ${days === 1 ? 'día' : 'días'}`;
}

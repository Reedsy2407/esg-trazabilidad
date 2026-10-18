import { CollectionSchedule, CollectionScheduleStatus, DayOfWeek, Neighbor } from '../core/api/api.types';

/** Labels shared by the collection form and the neighbour pages. */
export const DAYS: Record<DayOfWeek, string> = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
};

const DAY_ORDER = Object.keys(DAYS) as DayOfWeek[];

/** Monday to Sunday, then by time: the API's order is alphabetical (dayOfWeek is stored as text). */
export function byWeekday(a: CollectionSchedule, b: CollectionSchedule): number {
  return DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek) || a.time.localeCompare(b.time);
}

export const SCHEDULE_STATUS: Record<CollectionScheduleStatus, string> = {
  ACTIVE: '',
  PAUSED: ' (pausado)',
  CANCELLED: ' (cancelado)',
};

/** "Lunes 08:00", plus its state when it isn't active. LocalTime comes as "08:00" or "08:00:00". */
export function scheduleLabel(schedule: CollectionSchedule): string {
  return `${DAYS[schedule.dayOfWeek]} ${schedule.time.slice(0, 5)}${SCHEDULE_STATUS[schedule.status]}`;
}

/**
 * "Name · District (inactivo)": the district is optional in the API (null, or blank), so a
 * neighbour without one shows just the name, never " · null".
 */
export function neighborLabel(n: Pick<Neighbor, 'fullName' | 'district' | 'status'>): string {
  const district = n.district?.trim();
  return `${n.fullName}${district ? ` · ${district}` : ''}${n.status === 'INACTIVE' ? ' (inactivo)' : ''}`;
}

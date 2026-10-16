import { StaffUser } from '../core/api/api.types';

/** An auth-service StaffUserResponse for tests: an active STAFF account that never changed its status or password. */
export function staffUser(overrides: Partial<StaffUser> = {}): StaffUser {
  return {
    id: 'staff-1',
    email: 'ana@asociacion.pe',
    fullName: 'Ana Paredes Quispe',
    role: 'STAFF',
    active: true,
    createdAt: '2026-08-14T15:20:00Z',
    passwordChangedAt: null,
    mustChangePassword: false,
    statusReason: null,
    statusChangedAt: null,
    statusChangedBy: null,
    ...overrides,
  };
}

// Hand-written types for the endpoints this slice uses, copied field by field
// from the backend DTOs. Nothing here is guessed: add a field only once the
// backend record has it.

/** auth-service LoginRequest. */
export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

/** auth-service LoginResponse. */
export interface LoginResponse {
  readonly accessToken: string;
}

/** reporting-service TrackedCompanyStatus. */
export type TrackedCompanyStatus = 'ACTIVE' | 'INACTIVE';

/** reporting-service TrackedCompanyResponse (UUIDs arrive as strings). */
export interface TrackedCompany {
  readonly id: string;
  readonly name: string;
  readonly ruc: string;
  readonly associationId: string;
  readonly status: TrackedCompanyStatus;
}

/** shared-kernel PageResponse<T>. `page` is zero-based. */
export interface Page<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

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

/**
 * reporting-service EsgCertificateResponse. LocalDate fields arrive as
 * "yyyy-MM-dd", the Instant as ISO-8601 UTC, BigDecimals as JSON numbers.
 * hierarchyCompliancePercent is 0-100. A certificate has no status: the
 * VIGENTE / POR VENCER / VENCIDO states belong to association certifications.
 */
export interface EsgCertificate {
  readonly id: string;
  readonly trackedCompanyId: string;
  readonly associationId: string;
  readonly companyName: string;
  readonly companyRuc: string;
  readonly periodStart: string;
  readonly periodEnd: string;
  readonly kilosTrazados: number;
  readonly hierarchyCompliancePercent: number;
  readonly issuedAt: string;
}

/**
 * reporting-service CertificateSummaryResponse: a live preview for any period,
 * nothing is issued. hierarchyCompliancePercent is null when no SIGERSOL sync
 * covers the period.
 */
export interface CertificateSummary {
  readonly trackedCompanyId: string;
  readonly periodStart: string;
  readonly periodEnd: string;
  readonly kilosTrazados: number;
  readonly hierarchyCompliancePercent: number | null;
}

/** shared-kernel PageResponse<T>. `page` is zero-based. */
export interface Page<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

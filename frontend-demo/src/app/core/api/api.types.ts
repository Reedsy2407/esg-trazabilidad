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

/** collection-service NeighborResponse. */
export type NeighborStatus = 'ACTIVE' | 'INACTIVE';

export interface Neighbor {
  readonly id: string;
  readonly fullName: string;
  readonly phone: string;
  readonly address: string;
  readonly district: string;
  readonly status: NeighborStatus;
}

/** recycler-service AssociationResponse. */
export type AssociationStatus = 'ACTIVE' | 'SUSPENDED';

export interface Association {
  readonly id: string;
  readonly name: string;
  readonly ruc: string;
  readonly registrationNumber: string;
  readonly address: string;
  readonly contactEmail: string;
  readonly contactPhone: string;
  readonly status: AssociationStatus;
}

/** collection-service CollectionScheduleResponse: java.time.DayOfWeek and LocalTime ("HH:mm:ss" or "HH:mm"). */
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export type CollectionScheduleStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED';

export interface CollectionSchedule {
  readonly id: string;
  readonly neighborId: string;
  readonly dayOfWeek: DayOfWeek;
  readonly time: string;
  readonly status: CollectionScheduleStatus;
}

/**
 * CreateCollectionRecordRequest: associationId, collectionDate (LocalDate) and
 * weightKg (@Positive BigDecimal, stored as numeric(10,2)) are required;
 * scheduleId is optional and must belong to the neighbor (else COL-006).
 */
export interface CreateCollectionRecordRequest {
  readonly scheduleId: string | null;
  readonly associationId: string;
  readonly collectionDate: string;
  readonly weightKg: number;
}

/** CollectionRecordResponse. */
export interface CollectionRecord {
  readonly id: string;
  readonly neighborId: string;
  readonly scheduleId: string | null;
  readonly associationId: string;
  readonly collectionDate: string;
  readonly weightKg: number;
}

/** shared-kernel PageResponse<T>. `page` is zero-based. */
export interface Page<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

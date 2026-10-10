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

/** auth-service StaffUserResponse (GET /auth/me, POST /auth/staff-users). email is stored lower-cased. */
export interface StaffUser {
  readonly id: string;
  readonly email: string;
  readonly fullName: string;
  readonly active: boolean;
  readonly createdAt: string;
}

/**
 * auth-service CreateStaffUserRequest: all three @NotBlank, email @Email. The domain model also
 * requires a dot in the domain (^[^@\s]+@[^@\s]+\.[^@\s]+$), stricter than @Email: "a@b" passes
 * the annotation and then fails as a 500, so the form checks it first. AUTH-002 (409) for an email
 * already registered. No password rule in the backend; the browser generates it (16 characters).
 */
export interface CreateStaffUserRequest {
  readonly email: string;
  readonly password: string;
  readonly fullName: string;
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
 * reporting-service RegisterTrackedCompanyRequest: name @NotBlank, ruc exactly 11 digits,
 * associationId @NotNull (not checked against recycler-service). RPT-002 (409) for a RUC already
 * registered. Not the same as collection-service's /companies, which this app doesn't use.
 */
export interface RegisterTrackedCompanyRequest {
  readonly name: string;
  readonly ruc: string;
  readonly associationId: string;
}

/**
 * reporting-service SigersolSyncResponse: official figures copied by hand from SIGERSOL (no
 * integration exists). BigDecimals as JSON numbers; declaredAt is the server's Instant.now().
 */
export interface SigersolSync {
  readonly id: string;
  readonly associationId: string;
  readonly periodStart: string;
  readonly periodEnd: string;
  readonly hierarchyCompliancePercent: number;
  readonly officialKilosDeclared: number | null;
  readonly declaredAt: string;
  readonly sourceNote: string | null;
}

/**
 * RegisterSigersolSyncRequest: associationId, periodStart, periodEnd and hierarchyCompliancePercent
 * (0-100, stored decimal(5,2)) required; officialKilosDeclared (>= 0, decimal(14,2)) and sourceNote
 * (varchar(255)) optional. RPT-006 (409) if it overlaps another of the association's records (a
 * shared day counts); RPT-008 (400) if end is before start.
 */
export interface RegisterSigersolSyncRequest {
  readonly associationId: string;
  readonly periodStart: string;
  readonly periodEnd: string;
  readonly hierarchyCompliancePercent: number;
  readonly officialKilosDeclared: number | null;
  readonly sourceNote: string | null;
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
 * IssueCertificateRequest: both @NotNull LocalDates, nothing else checked by annotations. The
 * service answers RPT-001, then RPT-004 (overlap with this company's certificates, a shared day
 * counts), then RPT-005 (no single SIGERSOL record of the association covers the whole period).
 * It does NOT refuse a period that hasn't ended, 0 kg or end before start: the screen does.
 */
export interface IssueCertificateRequest {
  readonly periodStart: string;
  readonly periodEnd: string;
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

/**
 * collection-service NeighborResponse. CreateNeighborRequest requires only fullName and
 * address (@NotBlank); phone and district are optional, so null on the wire.
 */
export type NeighborStatus = 'ACTIVE' | 'INACTIVE';

export interface Neighbor {
  readonly id: string;
  readonly fullName: string;
  readonly phone: string | null;
  readonly address: string;
  readonly district: string | null;
  readonly status: NeighborStatus;
}

/**
 * recycler-service AssociationResponse. CreateAssociationRequest requires only name and RUC;
 * registrationNumber, address, contactEmail and contactPhone are optional, so null on the wire.
 */
export type AssociationStatus = 'ACTIVE' | 'SUSPENDED';

export interface Association {
  readonly id: string;
  readonly name: string;
  readonly ruc: string;
  readonly registrationNumber: string | null;
  readonly address: string | null;
  readonly contactEmail: string | null;
  readonly contactPhone: string | null;
  readonly status: AssociationStatus;
}

/**
 * recycler-service CertificationResponse. `expired` is the backend's only state: the
 * expiration date is before the service's today (a certification expiring today is not
 * expired yet). "Por vencer" is not a backend state; the screen derives it (see
 * certification-state.ts).
 */
export interface Certification {
  readonly id: string;
  readonly associationId: string;
  readonly certificationType: string;
  readonly issuedDate: string;
  readonly expirationDate: string;
  readonly expired: boolean;
}

/** collection-service CreateNeighborRequest: fullName and address @NotBlank; phone and district optional. */
export interface CreateNeighborRequest {
  readonly fullName: string;
  readonly address: string;
  readonly phone: string | null;
  readonly district: string | null;
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

/** CreateCollectionScheduleRequest: both @NotNull; `time` as LocalTime ("HH:mm"). COL-002 if the
 * neighbour already has an ACTIVE schedule that day of the week, whatever the time. */
export interface CreateCollectionScheduleRequest {
  readonly dayOfWeek: DayOfWeek;
  readonly time: string;
}

/** PATCH /neighbors/{id}/schedules/{scheduleId}/{transition}, no body. COL-008 when not allowed from the current state. */
export type ScheduleTransition = 'pause' | 'cancel' | 'reactivate';

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

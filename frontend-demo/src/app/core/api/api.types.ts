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

/** auth-service roles: ADMIN creates, deactivates and resets accounts; STAFF does everything else. */
export type StaffRole = 'ADMIN' | 'STAFF';

/**
 * auth-service StaffUserResponse (GET /auth/me, GET and POST /auth/staff-users, the status changes).
 * email is stored lower-cased. `statusReason`, `statusChangedAt` and `statusChangedBy` (an email, only
 * filled in the admin list) describe the last deactivation or reactivation and are null for an account
 * that never had one; `passwordChangedAt` is null until the password is first changed.
 */
export interface StaffUser {
  readonly id: string;
  readonly email: string;
  readonly fullName: string;
  readonly role: StaffRole;
  readonly active: boolean;
  readonly createdAt: string;
  readonly passwordChangedAt: string | null;
  readonly mustChangePassword: boolean;
  readonly statusReason: string | null;
  readonly statusChangedAt: string | null;
  readonly statusChangedBy: string | null;
}

/**
 * auth-service CreateStaffUserRequest: email, password and fullName @NotBlank, email @Email, `role`
 * optional (STAFF by default). The domain model also requires a dot in the domain
 * (^[^@\s]+@[^@\s]+\.[^@\s]+$), stricter than @Email: "a@b" passes the annotation and the service
 * answers AUTH-005 (400); the form checks it first. AUTH-002 (409) for an email already registered,
 * AUTH-007 (400) for a password the policy refuses, AUTH-006 (403) if the caller is not an admin.
 * The browser generates the password (16 characters).
 */
export interface CreateStaffUserRequest {
  readonly email: string;
  readonly password: string;
  readonly fullName: string;
  readonly role?: StaffRole;
}

/** auth-service ChangePasswordRequest (POST /auth/me/password): AUTH-008 if the current one is wrong. */
export interface ChangePasswordRequest {
  readonly currentPassword: string;
  readonly newPassword: string;
}

/** auth-service ConfirmPasswordResetRequest (POST /auth/password-reset/confirm). The code may carry spaces. */
export interface ConfirmPasswordResetRequest {
  readonly email: string;
  readonly code: string;
  readonly newPassword: string;
}

/** reporting-service TrackedCompanyStatus. */
export type TrackedCompanyStatus = 'ACTIVE' | 'INACTIVE';

/** Whether the legal name came from SUNAT (VERIFIED) or was typed by hand (UNVERIFIED). */
export type TrackedCompanyVerification = 'VERIFIED' | 'UNVERIFIED';

/**
 * reporting-service TrackedCompanyResponse (UUIDs arrive as strings). The SUNAT fields are null for
 * a company that was never verified; `warning` is set when SUNAT reports the taxpayer NO HABIDO.
 */
export interface TrackedCompany {
  readonly id: string;
  readonly name: string;
  readonly ruc: string;
  readonly associationId: string;
  readonly status: TrackedCompanyStatus;
  readonly verification: TrackedCompanyVerification;
  readonly sunatEstado: string | null;
  readonly sunatCondicion: string | null;
  readonly sunatCheckedAt: string | null;
  readonly contactName: string | null;
  readonly contactEmail: string | null;
  readonly contactPhone: string | null;
  readonly warning: string | null;
}

/**
 * reporting-service RegisterTrackedCompanyRequest: ruc exactly 11 digits (RPT-010 if the check digit
 * fails), associationId @NotNull (not checked against recycler-service), optional contact. The legal
 * name is not sent: it comes from SUNAT. RPT-002 (409) for a RUC already registered; RPT-011 (503),
 * RPT-012 and RPT-013 (422) from the SUNAT lookup. Not the same as collection-service's /companies,
 * which this app doesn't use.
 */
export interface RegisterTrackedCompanyRequest {
  readonly ruc: string;
  readonly associationId: string;
  readonly contactName?: string;
  readonly contactEmail?: string;
  readonly contactPhone?: string;
}

/**
 * reporting-service EditTrackedCompanyRequest. A field left out stays as it is; a blank contact
 * field clears it. `ruc`/`associationId` are refused (RPT-017) once the company has a certificate;
 * this app only edits the contact.
 */
export interface EditTrackedCompanyRequest {
  readonly contactName?: string;
  readonly contactEmail?: string;
  readonly contactPhone?: string;
}

export type TrackedCompanyTransition = 'deactivate' | 'activate';

/**
 * reporting-service RegisterUnverifiedTrackedCompanyRequest: the fallback for when SUNAT did not
 * answer (RPT-011). The name is the operator's; the server repeats the lookup and answers RPT-014
 * (409) if SUNAT answers after all. The company stays UNVERIFIED (no certificates, RPT-016) until
 * POST .../sunat-refresh succeeds.
 */
export interface RegisterUnverifiedTrackedCompanyRequest extends RegisterTrackedCompanyRequest {
  readonly name: string;
}

/**
 * reporting-service SigersolSyncResponse: official figures copied by hand from SIGERSOL (no
 * integration exists). BigDecimals as JSON numbers; declaredAt is the server's Instant.now().
 */
export type SigersolOrigin = 'DECLARED_BY_OPERATOR' | 'REFERENCED_PROOF';

/**
 * `origin`, `registeredByEmail` and `reference` are null for records made before v3 ("origen no
 * registrado"); who registered it comes from the token.
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
  readonly origin: SigersolOrigin | null;
  readonly registeredByEmail: string | null;
  readonly reference: string | null;
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
  /** Required by the backend (D1): how the operator got the figure. */
  readonly origin: SigersolOrigin;
  /**
   * A pointer to the operator's proof (a text, not a file), 255 characters at most. Required (RPT-021,
   * 400) when the origin is REFERENCED_PROOF; optional otherwise.
   */
  readonly reference?: string;
}

/**
 * reporting-service EsgCertificateResponse. LocalDate fields arrive as
 * "yyyy-MM-dd", the Instant as ISO-8601 UTC, BigDecimals as JSON numbers.
 * hierarchyCompliancePercent is 0-100. Its status is ISSUED or ANNULLED (shown Emitido / Anulado);
 * the VIGENTE / POR VENCER / VENCIDO states belong to association certifications. An annulled
 * certificate keeps its frozen numbers and gains when, who (from the token) and why.
 */
export type CertificateStatus = 'ISSUED' | 'ANNULLED';

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
  readonly status: CertificateStatus;
  readonly annulledAt: string | null;
  readonly annulledById: string | null;
  readonly annulledByEmail: string | null;
  readonly annulmentReason: string | null;
  /** Frozen at issue; absent in a list page (open the certificate to read it). */
  readonly districtContext?: readonly DistrictLine[] | null;
  /**
   * Where the declared hierarchy figure came from: how the operator got it, who registered it and the
   * proof it points to. Null in a list page and, on a single certificate, for a record made before
   * origins were kept (the reference is also null when none was given).
   */
  readonly hierarchyOrigin?: SigersolOrigin | null;
  readonly hierarchyRegisteredByEmail?: string | null;
  readonly hierarchyReference?: string | null;
}

/**
 * IssueCertificateRequest: both @NotNull LocalDates, nothing else checked by annotations. The
 * service answers RPT-009 (400, end before start; the draft's GET certificate-summary too), then
 * RPT-001, then RPT-015 (inactive company), then RPT-004 (overlap with this company's ISSUED certificates, a
 * shared day counts; an annulled one frees its period), then
 * RPT-005 (no single SIGERSOL record of the association covers the whole period). It does NOT
 * refuse a period that hasn't ended or 0 kg: the screen does.
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
  /** UNVERIFIED ("No verificado") when no SIGERSOL record covers the period; RECORDED otherwise. */
  readonly sigersolCoverage?: SigersolCoverage;
  /** Of the covering record; null for a record made before v3 ("origen no registrado"). */
  readonly sigersolOrigin?: SigersolOrigin | null;
  readonly sigersolRegisteredByEmail?: string | null;
  /** Kilos per district with SIGERSOL's context, live (the draft); empty when nothing was traced. */
  readonly districts?: readonly DistrictLine[];
}

export type SigersolCoverage = 'RECORDED' | 'UNVERIFIED';

export type DistrictLineStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'UNKNOWN_DISTRICT';

/**
 * One district of a certificate or of its draft: the kilos traced there and what SIGERSOL Municipal
 * says about the district (aggregated public counts of associations and recyclers, with their year;
 * never a claim about the company). UNAVAILABLE = "no disponible"; UNKNOWN_DISTRICT = kilos whose
 * records carry no district. On a certificate it is frozen when it is issued.
 */
export interface DistrictLine {
  readonly districtUbigeo: string | null;
  readonly districtName: string;
  readonly kilos: number;
  readonly status: DistrictLineStatus;
  readonly year: number | null;
  readonly associationCount: number | null;
  readonly recyclerCount: number | null;
}

/**
 * collection-service NeighborResponse. `districtUbigeo` is the INEI code of one of the 50
 * districts of Lima Metropolitana and Callao (null only for a neighbour registered before v3 whose
 * free text matched none); `district` is that district's name, accents included (or the old text).
 */
export type NeighborStatus = 'ACTIVE' | 'INACTIVE';

export interface Neighbor {
  readonly id: string;
  readonly fullName: string;
  readonly phone: string | null;
  readonly address: string;
  readonly district: string | null;
  readonly districtUbigeo: string | null;
  readonly status: NeighborStatus;
}

/** collection-service NeighborStatusChangeResponse: one deactivation or reactivation, newest first. */
export interface NeighborStatusChange {
  readonly id: string;
  readonly fromStatus: NeighborStatus;
  readonly toStatus: NeighborStatus;
  readonly reason: string;
  readonly actorId: string;
  /** From the staff member's token; may be missing. */
  readonly actorEmail: string | null;
  /** An Instant (ISO, UTC). */
  readonly changedAt: string;
}

/** PATCH /neighbors/{id}/deactivate or /activate. */
export type NeighborTransition = 'deactivate' | 'activate';

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
 * recycler-service CreateAssociationRequest: name and RUC required (ASO-004 when the RUC fails the
 * local check, ASO-002 when taken); the rest optional, null or omitted.
 */
export interface CreateAssociationRequest {
  readonly name: string;
  readonly ruc: string;
  readonly registrationNumber?: string;
  readonly address?: string;
  readonly contactEmail?: string;
  readonly contactPhone?: string;
}

/** PATCH /associations/{id}/suspend or /activate (ASO-003 when it is already in that state). */
export type AssociationTransition = 'suspend' | 'activate';

/** recycler-service CreateCertificationRequest: CER-003 unless issuedDate is before expirationDate. */
export interface CreateCertificationRequest {
  readonly certificationType: string;
  readonly issuedDate: string;
  readonly expirationDate: string;
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

/**
 * collection-service CreateNeighborRequest and EditNeighborRequest: fullName, address and the
 * district's ubigeo required (COL-010 outside the list), phone optional (null clears it on edit).
 */
export interface CreateNeighborRequest {
  readonly fullName: string;
  readonly address: string;
  readonly phone: string | null;
  readonly districtUbigeo: string;
}

export type EditNeighborRequest = CreateNeighborRequest;

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

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  Association,
  AssociationStatus,
  AssociationTransition,
  Certification,
  CreateAssociationRequest,
  CreateCertificationRequest,
  CertificateSummary,
  CollectionRecord,
  CollectionSchedule,
  CreateCollectionRecordRequest,
  CreateCollectionScheduleRequest,
  CreateNeighborRequest,
  CreateStaffUserRequest,
  EditNeighborRequest,
  EsgCertificate,
  IssueCertificateRequest,
  LoginRequest,
  LoginResponse,
  Neighbor,
  NeighborStatus,
  NeighborStatusChange,
  NeighborTransition,
  Page,
  RegisterSigersolSyncRequest,
  EditTrackedCompanyRequest,
  RegisterTrackedCompanyRequest,
  RegisterUnverifiedTrackedCompanyRequest,
  TrackedCompanyTransition,
  SigersolSync,
  StaffUser,
  ScheduleTransition,
  TrackedCompany,
} from './api.types';

const { auth, collection, recycler, reporting } = environment.services;

/**
 * One page for a picker: every service caps `size` at 100
 * (spring.data.web.pageable.max-page-size), so asking for more would be
 * silently cut. The form says so when totalElements is larger.
 */
export const PICKER_PAGE_SIZE = 100;

/** The backend endpoints this slice calls. */
@Injectable({ providedIn: 'root' })
export class BackendApi {
  private readonly http = inject(HttpClient);

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${auth}/auth/login`, request);
  }

  /** The signed-in staff member (the token's `sub`). */
  me(): Observable<StaffUser> {
    return this.http.get<StaffUser>(`${auth}/auth/me`);
  }

  /** 201 with the new account (active). Any signed-in staff member may call it: there are no roles. Not idempotent. */
  createStaffUser(request: CreateStaffUserRequest): Observable<StaffUser> {
    return this.http.post<StaffUser>(`${auth}/auth/staff-users`, request);
  }

  /** Sorted by name by the backend; `page` is zero-based, 20 per page. */
  trackedCompanies(page: number): Observable<Page<TrackedCompany>> {
    const params = new HttpParams().set('page', page).set('size', 20);
    return this.http.get<Page<TrackedCompany>>(`${reporting}/tracked-companies`, { params });
  }

  trackedCompany(id: string): Observable<TrackedCompany> {
    return this.http.get<TrackedCompany>(`${reporting}/tracked-companies/${encodeURIComponent(id)}`);
  }

  /**
   * 201 with the company (ACTIVE, VERIFIED, name from SUNAT); RPT-002 for a RUC already registered,
   * RPT-010 bad RUC, RPT-011 SUNAT did not answer, RPT-012 SUNAT has no such RUC, RPT-013 baja definitiva.
   * Not idempotent.
   */
  registerTrackedCompany(request: RegisterTrackedCompanyRequest): Observable<TrackedCompany> {
    return this.http.post<TrackedCompany>(`${reporting}/tracked-companies`, request);
  }

  /** 201 with an UNVERIFIED company. RPT-014 when SUNAT answers after all, RPT-002 for a taken RUC. Not idempotent. */
  registerUnverifiedTrackedCompany(request: RegisterUnverifiedTrackedCompanyRequest): Observable<TrackedCompany> {
    return this.http.post<TrackedCompany>(`${reporting}/tracked-companies/unverified`, request);
  }

  /**
   * "Verificar con SUNAT": 200 with the company, its name and estado/condicion from SUNAT and VERIFIED.
   * RPT-011/012/013 leave it unchanged. Asks the provider every time (the monthly quota is small).
   */
  refreshTrackedCompanySunat(id: string): Observable<TrackedCompany> {
    return this.http.post<TrackedCompany>(
      `${reporting}/tracked-companies/${encodeURIComponent(id)}/sunat-refresh`,
      null,
    );
  }

  /** 200 with the company. Contact fields only from this app; RPT-017 would answer a RUC/association change with certificates. */
  editTrackedCompany(id: string, request: EditTrackedCompanyRequest): Observable<TrackedCompany> {
    return this.http.patch<TrackedCompany>(`${reporting}/tracked-companies/${encodeURIComponent(id)}`, request);
  }

  /**
   * 200 with the company, now INACTIVE (it cannot receive certificates, RPT-015) or ACTIVE again.
   * The reason is required; who comes from the token. RPT-018 if it already was in that state.
   */
  changeTrackedCompanyStatus(id: string, transition: TrackedCompanyTransition, reason: string): Observable<TrackedCompany> {
    return this.http.patch<TrackedCompany>(
      `${reporting}/tracked-companies/${encodeURIComponent(id)}/${transition}`,
      { reason },
    );
  }

  /** Logical deletion, 204: only without certificates (RPT-017). Afterwards the company answers RPT-001 and its RUC is free. */
  deleteTrackedCompany(id: string, reason: string): Observable<void> {
    return this.http.delete<void>(`${reporting}/tracked-companies/${encodeURIComponent(id)}`, { body: { reason } });
  }

  /** Newest period first, 20 per page; `associationId` filters exactly. */
  sigersolSyncs(page: number, associationId: string | null): Observable<Page<SigersolSync>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (associationId !== null) {
      params = params.set('associationId', associationId);
    }
    return this.http.get<Page<SigersolSync>>(`${reporting}/sigersol-syncs`, { params });
  }

  /** 201; RPT-006 (overlap with the association's other records), RPT-008 (invalid data). Not idempotent. */
  registerSigersolSync(request: RegisterSigersolSyncRequest): Observable<SigersolSync> {
    return this.http.post<SigersolSync>(`${reporting}/sigersol-syncs`, request);
  }

  /** Most recently issued first, 20 per page (backend default). */
  certificates(companyId: string, page = 0): Observable<Page<EsgCertificate>> {
    const params = new HttpParams().set('page', page).set('size', 20);
    return this.http.get<Page<EsgCertificate>>(
      `${reporting}/tracked-companies/${encodeURIComponent(companyId)}/certificates`,
      { params },
    );
  }

  /** 201 with the certificate. Only annulling undoes it (annulCertificate); RPT-015 for an inactive company. */
  issueCertificate(companyId: string, request: IssueCertificateRequest): Observable<EsgCertificate> {
    return this.http.post<EsgCertificate>(`${reporting}/tracked-companies/${encodeURIComponent(companyId)}/certificates`, request);
  }

  /** RPT-003 (404) when the id doesn't exist or belongs to another company. */
  certificate(companyId: string, certificateId: string): Observable<EsgCertificate> {
    return this.http.get<EsgCertificate>(
      `${reporting}/tracked-companies/${encodeURIComponent(companyId)}/certificates/${encodeURIComponent(certificateId)}`,
    );
  }

  /**
   * 200 with the certificate, now ANNULLED. The reason is required (500 at most); who and when come
   * from the token and the server. Never deleted. RPT-019 if it was already annulled, RPT-003 if it
   * isn't this company's.
   */
  annulCertificate(companyId: string, certificateId: string, reason: string): Observable<EsgCertificate> {
    return this.http.post<EsgCertificate>(
      `${reporting}/tracked-companies/${encodeURIComponent(companyId)}/certificates/${encodeURIComponent(certificateId)}/annul`,
      { reason },
    );
  }

  /** Dates as "yyyy-MM-dd" (LocalDate). */
  certificateSummary(companyId: string, periodStart: string, periodEnd: string): Observable<CertificateSummary> {
    const params = new HttpParams().set('periodStart', periodStart).set('periodEnd', periodEnd);
    return this.http.get<CertificateSummary>(
      `${reporting}/tracked-companies/${encodeURIComponent(companyId)}/certificate-summary`,
      { params },
    );
  }

  /** Every neighbor, by name (backend sort), for the collection form. */
  neighbors(): Observable<Page<Neighbor>> {
    const params = new HttpParams().set('size', PICKER_PAGE_SIZE);
    return this.http.get<Page<Neighbor>>(`${collection}/neighbors`, { params });
  }

  /**
   * The neighbours screen: 20 per page by name, filtered by state and by district (its ubigeo;
   * COL-010 for a code outside the list). There is no search by name in the API.
   */
  neighborsPage(page: number, status: NeighborStatus | null, districtUbigeo: string | null): Observable<Page<Neighbor>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (status !== null) {
      params = params.set('status', status);
    }
    if (districtUbigeo !== null) {
      params = params.set('districtUbigeo', districtUbigeo);
    }
    return this.http.get<Page<Neighbor>>(`${collection}/neighbors`, { params });
  }

  /** COL-001 (404) when it doesn't exist. */
  neighbor(id: string): Observable<Neighbor> {
    return this.http.get<Neighbor>(`${collection}/neighbors/${encodeURIComponent(id)}`);
  }

  /** 201 with the new neighbour, always ACTIVE. Not idempotent. */
  createNeighbor(request: CreateNeighborRequest): Observable<Neighbor> {
    return this.http.post<Neighbor>(`${collection}/neighbors`, request);
  }

  /** Replaces name, address, district and phone (null clears it). COL-001, COL-010, VALIDATION_ERROR. */
  editNeighbor(id: string, request: EditNeighborRequest): Observable<Neighbor> {
    return this.http.patch<Neighbor>(`${collection}/neighbors/${encodeURIComponent(id)}`, request);
  }

  /**
   * deactivate: ACTIVE → INACTIVE, pausing its active schedules; activate: INACTIVE → ACTIVE,
   * leaving them paused. The reason is required; who comes from the token. COL-012 (same state).
   */
  changeNeighborStatus(id: string, transition: NeighborTransition, reason: string): Observable<Neighbor> {
    return this.http.patch<Neighbor>(`${collection}/neighbors/${encodeURIComponent(id)}/${transition}`, { reason });
  }

  /** The neighbour's deactivations and reactivations, newest first. */
  neighborStatusChanges(id: string): Observable<NeighborStatusChange[]> {
    return this.http.get<NeighborStatusChange[]>(`${collection}/neighbors/${encodeURIComponent(id)}/status-changes`);
  }

  /**
   * Logical deletion, 204. Only without collections: COL-011 otherwise (deactivate instead).
   * Its schedules are cancelled; afterwards the neighbour answers COL-001.
   */
  deleteNeighbor(id: string, reason: string): Observable<void> {
    return this.http.delete<void>(`${collection}/neighbors/${encodeURIComponent(id)}`, { body: { reason } });
  }

  /** 201; COL-001 (neighbour), COL-002 (an ACTIVE schedule that day already), COL-013 (inactive neighbour). */
  createSchedule(neighborId: string, request: CreateCollectionScheduleRequest): Observable<CollectionSchedule> {
    return this.http.post<CollectionSchedule>(`${collection}/neighbors/${encodeURIComponent(neighborId)}/schedules`, request);
  }

  /**
   * pause: ACTIVE → PAUSED; cancel: ACTIVE or PAUSED → CANCELLED (final); reactivate: PAUSED → ACTIVE
   * (COL-002 if another ACTIVE schedule took that day). COL-008 otherwise, COL-006 if it isn't this neighbour's.
   */
  transitionSchedule(neighborId: string, scheduleId: string, transition: ScheduleTransition): Observable<CollectionSchedule> {
    return this.http.patch<CollectionSchedule>(
      `${collection}/neighbors/${encodeURIComponent(neighborId)}/schedules/${encodeURIComponent(scheduleId)}/${transition}`,
      null,
    );
  }

  /** A neighbour's collections, newest first, 20 per page; `from`/`to` inclusive LocalDates. */
  collectionRecords(neighborId: string, page: number, from: string | null, to: string | null): Observable<Page<CollectionRecord>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (from !== null) {
      params = params.set('from', from);
    }
    if (to !== null) {
      params = params.set('to', to);
    }
    return this.http.get<Page<CollectionRecord>>(`${collection}/neighbors/${encodeURIComponent(neighborId)}/collection-records`, {
      params,
    });
  }

  /** Every association, by name (backend sort). Lives in recycler-service. */
  associations(): Observable<Page<Association>> {
    const params = new HttpParams().set('size', PICKER_PAGE_SIZE);
    return this.http.get<Page<Association>>(`${recycler}/associations`, { params });
  }

  /** The associations list screen: 20 per page by name; `status` filters exactly (ACTIVE / SUSPENDED). */
  associationsPage(page: number, status: AssociationStatus | null): Observable<Page<Association>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (status !== null) {
      params = params.set('status', status);
    }
    return this.http.get<Page<Association>>(`${recycler}/associations`, { params });
  }

  /** ASO-001 (404) when it doesn't exist; VALIDATION_ERROR (400) when the id isn't a UUID. */
  association(id: string): Observable<Association> {
    return this.http.get<Association>(`${recycler}/associations/${encodeURIComponent(id)}`);
  }

  /** 201 with the association; ASO-004 (invalid RUC), ASO-002 (RUC taken). Not idempotent. */
  createAssociation(request: CreateAssociationRequest): Observable<Association> {
    return this.http.post<Association>(`${recycler}/associations`, request);
  }

  /** suspend: ACTIVE → SUSPENDED; activate: SUSPENDED → ACTIVE. ASO-003 when already there. */
  changeAssociationStatus(id: string, transition: AssociationTransition): Observable<Association> {
    return this.http.patch<Association>(`${recycler}/associations/${encodeURIComponent(id)}/${transition}`, null);
  }

  /** 201 with the certification; CER-003 (issued must be before expiration), CER-002 (no such association). */
  createCertification(associationId: string, request: CreateCertificationRequest): Observable<Certification> {
    return this.http.post<Certification>(`${recycler}/associations/${encodeURIComponent(associationId)}/certifications`, request);
  }

  /** The certification with its new expiration date; CER-001 if it isn't this association's. */
  renewCertification(associationId: string, id: string, newExpirationDate: string): Observable<Certification> {
    return this.http.patch<Certification>(
      `${recycler}/associations/${encodeURIComponent(associationId)}/certifications/${encodeURIComponent(id)}/renew`,
      { newExpirationDate },
    );
  }

  /** An association's certifications, soonest expiration first (backend sort). */
  certifications(associationId: string): Observable<Page<Certification>> {
    const params = new HttpParams().set('size', PICKER_PAGE_SIZE);
    return this.http.get<Page<Certification>>(
      `${recycler}/associations/${encodeURIComponent(associationId)}/certifications`,
      { params },
    );
  }

  /**
   * A neighbor's collection schedules. The backend sorts dayOfWeek as stored
   * text (FRIDAY, MONDAY...), so the page re-sorts them Monday to Sunday.
   */
  schedules(neighborId: string): Observable<Page<CollectionSchedule>> {
    const params = new HttpParams().set('size', PICKER_PAGE_SIZE);
    return this.http.get<Page<CollectionSchedule>>(`${collection}/neighbors/${encodeURIComponent(neighborId)}/schedules`, {
      params,
    });
  }

  /** 201 with the record; COL-001 (neighbor), COL-006 (schedule), COL-009 (association blocked), VALIDATION_ERROR. */
  createCollectionRecord(neighborId: string, request: CreateCollectionRecordRequest): Observable<CollectionRecord> {
    return this.http.post<CollectionRecord>(
      `${collection}/neighbors/${encodeURIComponent(neighborId)}/collection-records`,
      request,
    );
  }

  liveness(base: string): Observable<unknown> {
    return this.http.get<unknown>(`${base}/actuator/health/liveness`);
  }
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  Association,
  AssociationStatus,
  Certification,
  CertificateSummary,
  CollectionRecord,
  CollectionSchedule,
  CreateCollectionRecordRequest,
  CreateCollectionScheduleRequest,
  CreateNeighborRequest,
  EsgCertificate,
  LoginRequest,
  LoginResponse,
  Neighbor,
  NeighborStatus,
  Page,
  RegisterSigersolSyncRequest,
  RegisterTrackedCompanyRequest,
  SigersolSync,
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

  /** Sorted by name by the backend; `page` is zero-based, 20 per page. */
  trackedCompanies(page: number): Observable<Page<TrackedCompany>> {
    const params = new HttpParams().set('page', page).set('size', 20);
    return this.http.get<Page<TrackedCompany>>(`${reporting}/tracked-companies`, { params });
  }

  trackedCompany(id: string): Observable<TrackedCompany> {
    return this.http.get<TrackedCompany>(`${reporting}/tracked-companies/${encodeURIComponent(id)}`);
  }

  /** 201 with the company, always ACTIVE; RPT-002 for a RUC already registered. Not idempotent. */
  registerTrackedCompany(request: RegisterTrackedCompanyRequest): Observable<TrackedCompany> {
    return this.http.post<TrackedCompany>(`${reporting}/tracked-companies`, request);
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

  /** RPT-003 (404) when the id doesn't exist or belongs to another company. */
  certificate(companyId: string, certificateId: string): Observable<EsgCertificate> {
    return this.http.get<EsgCertificate>(
      `${reporting}/tracked-companies/${encodeURIComponent(companyId)}/certificates/${encodeURIComponent(certificateId)}`,
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
   * The neighbours screen: 20 per page by name. `district` matches exactly (case-sensitive, the
   * backend's equality filter); there is no search by name in the API.
   */
  neighborsPage(page: number, status: NeighborStatus | null, district: string | null): Observable<Page<Neighbor>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (status !== null) {
      params = params.set('status', status);
    }
    if (district !== null) {
      params = params.set('district', district);
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

  /** 201; COL-001 (neighbour), COL-002 (an ACTIVE schedule that day already). */
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

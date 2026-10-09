import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  Association,
  CertificateSummary,
  CollectionRecord,
  CollectionSchedule,
  CreateCollectionRecordRequest,
  EsgCertificate,
  LoginRequest,
  LoginResponse,
  Neighbor,
  Page,
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

  /** Every association, by name (backend sort). Lives in recycler-service. */
  associations(): Observable<Page<Association>> {
    const params = new HttpParams().set('size', PICKER_PAGE_SIZE);
    return this.http.get<Page<Association>>(`${recycler}/associations`, { params });
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

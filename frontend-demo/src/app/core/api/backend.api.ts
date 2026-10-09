import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CertificateSummary, EsgCertificate, LoginRequest, LoginResponse, Page, TrackedCompany } from './api.types';

const { auth, reporting } = environment.services;

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

  liveness(base: string): Observable<unknown> {
    return this.http.get<unknown>(`${base}/actuator/health/liveness`);
  }
}

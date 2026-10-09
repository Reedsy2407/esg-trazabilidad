import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, from, of, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SessionService } from '../auth/session.service';
import { toApiError } from './api-error';

const serviceBases = Object.values(environment.services);

/** Requests that never carry a token: the login itself and the public actuator. */
function isPublic(url: string): boolean {
  return url.endsWith('/auth/login') || url.includes('/actuator/');
}

/**
 * Exactly one of our service bases, followed by a path or nothing. A bare
 * startsWith(base) would also match https://esg-auth-service.onrender.com.evil.example/x
 * and hand the token to that host.
 */
export function matchesServiceBase(url: string, bases: readonly string[]): boolean {
  return bases.some((base) => url === base || url.startsWith(`${base}/`) || url.startsWith(`${base}?`));
}

function isBackendCall(url: string): boolean {
  return matchesServiceBase(url, serviceBases);
}

/** Adds the bearer token, and only to our own services. */
export const jwtInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(SessionService).token();
  if (token === null || !isBackendCall(request.url) || isPublic(request.url)) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

/**
 * A failed request made with responseType 'blob' (the PDF/CSV downloads)
 * gets its problem detail as a Blob too: read it back as JSON so the `code`
 * (RPT-003...) is not lost. Only a small JSON Blob is read (a problem detail
 * is a few hundred bytes; a big HTML error page is not worth holding in
 * memory). Any other body passes through untouched, at once.
 */
export const MAX_ERROR_BLOB_BYTES = 64 * 1024;

function withReadableBody(error: HttpErrorResponse): Observable<HttpErrorResponse> {
  const blob: unknown = error.error;
  if (!(blob instanceof Blob) || !blob.type.includes('json') || blob.size > MAX_ERROR_BLOB_BYTES) {
    return of(error);
  }
  return from(
    blob.text().then(
      (text) => {
        let body: unknown = null;
        try {
          body = JSON.parse(text);
        } catch {
          // Not JSON (an HTML error page, an empty body): no code to keep.
        }
        return new HttpErrorResponse({
          error: body,
          headers: error.headers,
          status: error.status,
          statusText: error.statusText,
          url: error.url ?? undefined,
        });
      },
      () => error,
    ),
  );
}

/**
 * Maps every HTTP failure to an ApiError (RFC 7807 `code`, Retry-After).
 * A 401 outside the login form means the session is gone (AUTH-000: missing,
 * invalid or expired token): clear it and go back to the login. The login's
 * own 401 (AUTH-001, wrong credentials) is left to the form.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(SessionService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      // Decided on the status alone, before any body is read: the session
      // ends even if the caller unsubscribes while a Blob body is being read.
      if (error.status === 401 && isBackendCall(request.url) && !isPublic(request.url)) {
        session.clear();
        void router.navigate(['/login'], { queryParams: { motivo: 'sesion' } });
      }
      return withReadableBody(error).pipe(switchMap((readable) => throwError(() => toApiError(readable))));
    }),
  );
};

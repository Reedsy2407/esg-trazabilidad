import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

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
      const apiError = toApiError(error);
      if (apiError.status === 401 && isBackendCall(request.url) && !isPublic(request.url)) {
        session.clear();
        void router.navigate(['/login'], { queryParams: { motivo: 'sesion' } });
      }
      return throwError(() => apiError);
    }),
  );
};

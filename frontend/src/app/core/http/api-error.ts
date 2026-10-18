import { HttpErrorResponse } from '@angular/common/http';

/**
 * Every failed backend call, mapped once by errorInterceptor. The backend
 * answers errors as RFC 7807 problem details with a stable `code`
 * (AUTH-000, AUTH-001, AUTH-004, VALIDATION_ERROR...): the UI branches on
 * `code`, never on the human `detail`. Status 0 means the request never got
 * an HTTP answer (network down, or blocked by CORS).
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    readonly detail: string | null,
    /** From the Retry-After header of a 429, in whole seconds. */
    readonly retryAfterSeconds: number | null,
  ) {
    super(code ?? `HTTP ${status}`);
    this.name = 'ApiError';
  }
}

export function toApiError(error: HttpErrorResponse, now: () => number = Date.now): ApiError {
  const body: unknown = error.error;
  return new ApiError(
    error.status,
    stringField(body, 'code'),
    stringField(body, 'detail'),
    parseRetryAfter(error.headers?.get('Retry-After') ?? null, now),
  );
}

/** Retry-After is either delta-seconds or an HTTP date (RFC 9110 §10.2.3). */
export function parseRetryAfter(value: string | null, now: () => number = Date.now): number | null {
  if (value === null || value.trim() === '') {
    return null;
  }
  const trimmed = value.trim();
  if (/^\d+$/.test(trimmed)) {
    return Number(trimmed);
  }
  const date = Date.parse(trimmed);
  if (Number.isNaN(date)) {
    return null;
  }
  return Math.max(0, Math.ceil((date - now()) / 1000));
}

function stringField(body: unknown, field: string): string | null {
  if (typeof body !== 'object' || body === null || !(field in body)) {
    return null;
  }
  const value: unknown = (body as Record<string, unknown>)[field];
  return typeof value === 'string' ? value : null;
}

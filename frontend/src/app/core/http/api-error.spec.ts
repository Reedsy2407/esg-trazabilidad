import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';

import { parseRetryAfter, toApiError } from './api-error';

describe('toApiError', () => {
  it('reads code and detail from an RFC 7807 body, and Retry-After from the header', () => {
    const error = toApiError(
      new HttpErrorResponse({
        status: 429,
        headers: new HttpHeaders({ 'Retry-After': '8' }),
        error: { type: 'about:blank', title: 'Too Many Requests', status: 429, detail: 'Demasiados intentos', code: 'AUTH-004' },
      }),
    );

    expect(error.status).toBe(429);
    expect(error.code).toBe('AUTH-004');
    expect(error.detail).toBe('Demasiados intentos');
    expect(error.retryAfterSeconds).toBe(8);
  });

  it('keeps status 0 with no code when the request never got an answer (network or CORS)', () => {
    const error = toApiError(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));

    expect(error.status).toBe(0);
    expect(error.code).toBeNull();
    expect(error.retryAfterSeconds).toBeNull();
  });
});

describe('parseRetryAfter', () => {
  it('accepts delta-seconds and an HTTP date, and rejects anything else', () => {
    const now = () => Date.parse('2026-10-04T03:00:00Z');

    expect(parseRetryAfter('60', now)).toBe(60);
    expect(parseRetryAfter('Sun, 04 Oct 2026 03:00:12 GMT', now)).toBe(12);
    expect(parseRetryAfter('Sun, 04 Oct 2026 02:59:00 GMT', now)).toBe(0);
    expect(parseRetryAfter('soon', now)).toBeNull();
    expect(parseRetryAfter(null, now)).toBeNull();
  });
});

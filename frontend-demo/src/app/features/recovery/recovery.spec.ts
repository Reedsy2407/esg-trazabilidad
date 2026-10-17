import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { ApiError } from '../../core/http/api-error';
import { codeDigits, isCodeComplete, isRateLimited, readRecoveryState, waitText } from './recovery';

describe('recovery helpers', () => {
  afterEach(() => history.replaceState(null, ''));

  it('keeps only the digits of what was typed or pasted', () => {
    expect(codeDigits('1234 5678')).toBe('12345678');
    expect(codeDigits(' 0427-1983\n')).toBe('04271983');
    expect(codeDigits('abc')).toBe('');
  });

  it('a code is complete with exactly eight digits, leading zeros included', () => {
    expect(isCodeComplete('04271983')).toBe(true);
    expect(isCodeComplete('0427 1983')).toBe(true);
    expect(isCodeComplete('1234567')).toBe(false);
    expect(isCodeComplete('123456789')).toBe(false);
    expect(isCodeComplete('')).toBe(false);
  });

  it('says how long to wait in whole minutes, rounded up, and never promises less than one', () => {
    expect(waitText(null)).toBe('Demasiados intentos. Prueba de nuevo más tarde.');
    expect(waitText(1)).toBe('Demasiados intentos. Prueba de nuevo en 1 minuto.');
    expect(waitText(60)).toBe('Demasiados intentos. Prueba de nuevo en 1 minuto.');
    expect(waitText(61)).toBe('Demasiados intentos. Prueba de nuevo en 2 minutos.');
    expect(waitText(3000)).toBe('Demasiados intentos. Prueba de nuevo en 50 minutos.');
  });

  it('a limit is AUTH-012 or any 429, and nothing else', () => {
    expect(isRateLimited(new ApiError(429, 'AUTH-012', null, 30))).toBe(true);
    expect(isRateLimited(new ApiError(429, null, null, null))).toBe(true);
    expect(isRateLimited(new ApiError(400, 'AUTH-011', null, null))).toBe(false);
    expect(isRateLimited(new Error('x'))).toBe(false);
  });

  it('reads the state the previous step left in the history, ignoring anything of another shape', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const router = TestBed.inject(Router);

    history.replaceState({ email: 'ana@asociacion.pe', sentAt: 1_700_000_000_000, notice: 'hola', extra: 1 }, '');
    expect(readRecoveryState(router)).toEqual({ email: 'ana@asociacion.pe', sentAt: 1_700_000_000_000, notice: 'hola' });

    history.replaceState({ email: 42, sentAt: 'ayer' }, '');
    expect(readRecoveryState(router)).toEqual({ email: undefined, sentAt: undefined, notice: undefined });

    history.replaceState(null, '');
    expect(readRecoveryState(router)).toEqual({});
  });
});

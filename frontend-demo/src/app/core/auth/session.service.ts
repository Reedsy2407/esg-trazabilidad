import { Injectable, computed, signal } from '@angular/core';

const STORAGE_KEY = 'esg.session';

interface Session {
  readonly token: string;
  readonly email: string;
  /** Epoch milliseconds, from the token's `exp`. */
  readonly expiresAt: number;
}

/**
 * The signed-in staff session. The JWT lives in sessionStorage, never
 * localStorage: it dies with the tab, and auth-service tokens last one hour
 * with no refresh token anyway. The email shown in the top bar comes from the
 * token's own `email` claim, which auth-service issues.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly session = signal<Session | null>(readStored());

  readonly token = computed(() => this.session()?.token ?? null);
  readonly email = computed(() => this.session()?.email ?? null);

  isAuthenticated(now: number = Date.now()): boolean {
    const current = this.session();
    return current !== null && current.expiresAt > now;
  }

  /** Throws if the token isn't a JWT carrying `email` and `exp`. */
  start(token: string): void {
    const payload = decodeJwtPayload(token);
    const session: Session = { token, email: payload.email, expiresAt: payload.exp * 1000 };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }
}

export function decodeJwtPayload(token: string): { email: string; exp: number } {
  const part = token.split('.')[1];
  if (part === undefined) {
    throw new Error('Not a JWT');
  }
  const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
  const json = decodeURIComponent(
    Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), (c) =>
      `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`,
    ).join(''),
  );
  const payload: unknown = JSON.parse(json);
  if (typeof payload !== 'object' || payload === null) {
    throw new Error('JWT payload is not an object');
  }
  const { email, exp } = payload as Record<string, unknown>;
  if (typeof email !== 'string' || typeof exp !== 'number') {
    throw new Error('JWT payload lacks email or exp');
  }
  return { email, exp };
}

function readStored(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw === null) {
      return null;
    }
    const value: unknown = JSON.parse(raw);
    if (typeof value !== 'object' || value === null) {
      return null;
    }
    const { token, email, expiresAt } = value as Record<string, unknown>;
    return typeof token === 'string' && typeof email === 'string' && typeof expiresAt === 'number'
      ? { token, email, expiresAt }
      : null;
  } catch {
    return null;
  }
}

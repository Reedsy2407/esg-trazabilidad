import { Injectable, computed, signal } from '@angular/core';

const STORAGE_KEY = 'esg.session';
/** Other sessionStorage entries that belong to the signed-in person and end with the session. */
export const COLLECTION_LOG_KEY = 'esg.bitacora';

interface Session {
  /** Random, per sign-in: what session-scoped data (the bitácora) is tied to. Not a secret. */
  readonly id: string;
  readonly token: string;
  readonly email: string;
  /** Epoch milliseconds, from the token's `exp`. */
  readonly expiresAt: number;
  /** Epoch milliseconds, from the token's `iat` (auth-service sets it); null if a token lacks it. */
  readonly issuedAt: number | null;
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
  readonly expiresAt = computed(() => this.session()?.expiresAt ?? null);
  readonly issuedAt = computed(() => this.session()?.issuedAt ?? null);
  /** Changes with every sign-in, even of the same person; null when signed out. */
  readonly sessionId = computed(() => this.session()?.id ?? null);

  isAuthenticated(now: number = Date.now()): boolean {
    const current = this.session();
    return current !== null && current.expiresAt > now;
  }

  /** Throws if the token isn't a JWT carrying `email` and `exp`. */
  start(token: string): void {
    const payload = decodeJwtPayload(token);
    const session: Session = {
      id: newId(),
      token,
      email: payload.email,
      expiresAt: payload.exp * 1000,
      issuedAt: payload.iat === null ? null : payload.iat * 1000,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    // A new sign-in starts a new bitácora, whatever ended the previous session (sign-out, or an
    // expired token that sent the tab back to the login without passing through clear()).
    sessionStorage.removeItem(COLLECTION_LOG_KEY);
    this.session.set(session);
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(COLLECTION_LOG_KEY);
    this.session.set(null);
  }
}

export function decodeJwtPayload(token: string): { email: string; exp: number; iat: number | null } {
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
  const { email, exp, iat } = payload as Record<string, unknown>;
  if (typeof email !== 'string' || typeof exp !== 'number') {
    throw new Error('JWT payload lacks email or exp');
  }
  return { email, exp, iat: typeof iat === 'number' ? iat : null };
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
    const { id, token, email, expiresAt, issuedAt } = value as Record<string, unknown>;
    if (typeof token !== 'string' || typeof email !== 'string' || typeof expiresAt !== 'number') {
      return null;
    }
    const iat = typeof issuedAt === 'number' ? issuedAt : null;
    if (typeof id === 'string') {
      return { id, token, email, expiresAt, issuedAt: iat };
    }
    // Stored before sessions had an id: give it one and keep it, so a reload doesn't change it.
    const session = { id: newId(), token, email, expiresAt, issuedAt: iat };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    return session;
  } catch {
    return null;
  }
}

function newId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

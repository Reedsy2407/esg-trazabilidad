import { Injectable, computed, inject, signal } from '@angular/core';

import { COLLECTION_LOG_KEY, SessionService } from '../auth/session.service';

const MAX_ENTRIES = 50;

/** One collection this tab registered, as the confirmation showed it. */
export interface LoggedCollection {
  readonly id: string;
  readonly neighbor: string;
  readonly association: string;
  readonly date: string;
  readonly kilos: string;
}

interface StoredLog {
  /** SessionService.sessionId() of the sign-in that wrote it. */
  readonly owner: string;
  readonly entries: LoggedCollection[];
}

const isEntry = (e: unknown): e is LoggedCollection =>
  typeof e === 'object' &&
  e !== null &&
  ['id', 'neighbor', 'association', 'date', 'kilos'].every((k) => typeof (e as Record<string, unknown>)[k] === 'string');

function read(): StoredLog | null {
  try {
    const raw = sessionStorage.getItem(COLLECTION_LOG_KEY);
    const value: unknown = raw === null ? null : JSON.parse(raw);
    if (typeof value !== 'object' || value === null) {
      return null;
    }
    const { owner, entries } = value as Record<string, unknown>;
    return typeof owner === 'string' && Array.isArray(entries) ? { owner, entries: entries.filter(isEntry) } : null;
  } catch {
    return null;
  }
}

/**
 * Bitácora de la sesión: the collections registered in this tab since signing in, newest first.
 * Lives in sessionStorage (dies with the tab, like the token) and belongs to one sign-in: it is
 * shown only while that sign-in's session id is current, so whoever signs in next on the same tab
 * (after a sign-out or an expired session alike) never sees it. Nothing here is sent anywhere.
 */
@Injectable({ providedIn: 'root' })
export class CollectionLogService {
  private readonly session = inject(SessionService);
  private readonly log = signal<StoredLog | null>(read());

  readonly entries = computed(() => {
    const log = this.log();
    const owner = this.session.sessionId();
    return log !== null && owner !== null && log.owner === owner ? log.entries : [];
  });

  add(entry: LoggedCollection): void {
    const owner = this.session.sessionId();
    if (owner === null) {
      return;
    }
    const entries = [entry, ...this.entries().filter((e) => e.id !== entry.id)].slice(0, MAX_ENTRIES);
    this.log.set({ owner, entries });
    try {
      sessionStorage.setItem(COLLECTION_LOG_KEY, JSON.stringify(this.log()));
    } catch {
      // Storage full or blocked: the log still works for this page's life.
    }
  }
}

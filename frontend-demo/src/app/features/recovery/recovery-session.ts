import { Injectable, inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/** The service accepts the reset token for 10 minutes; the screen gives up a little earlier. */
export const TOKEN_LIFETIME_MS = 10 * 60 * 1000 - 15_000;

/**
 * Holds the reset token between step 1 (the code) and step 2 (the new password), in memory only: it is
 * never put in a URL, the history state, local or session storage, or a log. A reload or a closed tab
 * drops it, and then the person asks for a new code. Nothing else reads it.
 */
@Injectable({ providedIn: 'root' })
export class RecoverySession {
  private token: string | null = null;
  private expiresAt = 0;

  hold(token: string): void {
    this.token = token;
    this.expiresAt = Date.now() + TOKEN_LIFETIME_MS;
  }

  /** The token while it can still work; null once it expired or was cleared. */
  current(): string | null {
    if (this.token !== null && Date.now() >= this.expiresAt) {
      this.clear();
    }
    return this.token;
  }

  clear(): void {
    this.token = null;
    this.expiresAt = 0;
  }
}

/** Step 2 needs a token: without one (a reload, a typed address) the person starts again from the email. */
export const recoveryTokenGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(RecoverySession).current() !== null || router.parseUrl('/olvide-contrasena');
};

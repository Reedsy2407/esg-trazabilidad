import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { loadErrorMessage } from '../../shared/error-message';
import { formatInstantDate } from '../../shared/format';
import { sessionExpiryText } from './session-expiry';

/**
 * Mi sesión: who is signed in (GET /auth/me) and when the session ends, from the token's own
 * `exp` and `iat`. Never shows the token. Entry point to create staff accounts (no sixth section
 * in the top bar).
 */
@Component({
  selector: 'app-my-session-page',
  imports: [RouterLink],
  templateUrl: './my-session.page.html',
  styleUrl: './staff.css',
})
export class MySessionPage {
  private readonly api = inject(BackendApi);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  protected readonly me = rxResource({ stream: () => this.api.me() });
  protected readonly created = computed(() => (this.me.hasValue() ? formatInstantDate(this.me.value().createdAt) : ''));
  protected readonly expiry = computed(() => {
    const expiresAt = this.session.expiresAt();
    return expiresAt === null ? '' : sessionExpiryText(expiresAt, this.session.issuedAt());
  });

  protected errorText(error: unknown): string {
    return loadErrorMessage(error, 'los datos de tu cuenta', 'el servicio de autenticación');
  }

  protected signOut(): void {
    this.session.clear();
    void this.router.navigate(['/login']);
  }
}

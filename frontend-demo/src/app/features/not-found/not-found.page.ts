import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SessionService } from '../../core/auth/session.service';
import { Logo } from '../../shared/logo';

/**
 * 404: an address the app does not have (a mistyped link, an old bookmark). A ticket like the login's, with the
 * logo as the one identification (informative: there is no other name on the page), and a way back that depends
 * on whether there is a session.
 */
@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, Logo],
  template: `
    <main class="page">
      <div class="printout">
        <div class="printer" aria-hidden="true"></div>
        <section class="ticket fold card" aria-labelledby="nf-title">
          <app-logo class="brand-mark" [informative]="true" [size]="96" />
          <h1 id="nf-title">No encontramos esta página</h1>
          <p class="lead">La dirección no existe o cambió. Vuelve al inicio y sigue desde ahí.</p>
          <p class="actions">
            @if (session.isAuthenticated()) {
              <a class="btn btn-primary" routerLink="/empresas">Ir a Empresas</a>
            } @else {
              <a class="btn btn-primary" routerLink="/login">Iniciar sesión</a>
            }
          </p>
        </section>
      </div>
    </main>
  `,
  styles: `
    .page {
      min-height: 100vh;
      min-height: 100svh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--space-8) var(--space-4);
    }
    .printout {
      width: 100%;
      max-width: 520px;
    }
    .printer {
      height: 24px;
      margin: 0 -12px;
      background: var(--brand-deep);
    }
    .card {
      padding-left: var(--space-10);
      padding-right: var(--space-10);
      text-align: center;
    }
    .brand-mark {
      display: flex;
      justify-content: center;
      margin-bottom: var(--space-4);
    }
    h1 {
      font-size: var(--t-3);
    }
    .lead {
      margin: var(--space-3) 0 var(--space-6);
      color: var(--ink-soft);
    }
    .actions {
      margin: 0;
    }
    @media (max-width: 400px) {
      .card {
        padding-left: var(--space-5);
        padding-right: var(--space-5);
      }
    }
  `,
})
export class NotFoundPage {
  protected readonly session = inject(SessionService);
}

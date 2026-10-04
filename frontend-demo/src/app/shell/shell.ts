import { Component, ElementRef, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { SessionService } from '../core/auth/session.service';

/** Chrome level 1 of 3 (brief): the top bar. Pages bring their own title and content. */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterOutlet],
  template: `
    <a class="skip" href="#contenido">Saltar al contenido</a>
    <header class="bar">
      <a class="brand" routerLink="/empresas">Trazabilidad ESG</a>
      <div class="user">
        <span class="email">{{ session.email() }}</span>
        <button type="button" class="btn-link" (click)="signOut()">Cerrar sesión</button>
      </div>
    </header>
    <main id="contenido" tabindex="-1" #main>
      <router-outlet />
    </main>
  `,
  styles: `
    .skip {
      position: absolute;
      left: var(--space-4);
      top: -100px;
      padding: var(--space-2) var(--space-3);
      background: var(--surface);
      border: 1px solid var(--line-input);
    }
    .skip:focus { top: var(--space-2); }
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-6);
      background: var(--surface);
      border-bottom: 1px solid var(--line);
    }
    .brand {
      color: var(--petrol);
      font-weight: 600;
      font-size: 16px;
      text-decoration: none;
    }
    .user {
      display: flex;
      align-items: center;
      gap: var(--space-4);
      font-size: 14px;
      color: var(--muted);
    }
    .email { overflow-wrap: anywhere; }
    main {
      max-width: 1120px;
      margin: 0 auto;
      padding: var(--space-6);
    }
    main:focus { outline: none; }
    @media (max-width: 480px) {
      .bar, main { padding-left: var(--space-4); padding-right: var(--space-4); }
    }
  `,
})
export class Shell {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  constructor() {
    // After each in-app navigation, move focus to the new content, so a
    // screen reader starts at the page that just loaded, not at the old link.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => setTimeout(() => this.main().nativeElement.focus({ preventScroll: true })));
  }

  protected signOut(): void {
    this.session.clear();
    void this.router.navigate(['/login']);
  }
}

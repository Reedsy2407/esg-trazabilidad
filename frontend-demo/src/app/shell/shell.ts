import { Component, ElementRef, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { SessionService } from '../core/auth/session.service';

/** Chrome level 1 of 3 (brief): the top bar. Pages bring their own title and content. */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <a class="skip" href="#contenido">Saltar al contenido</a>
    <header class="bar">
      <a class="brand" routerLink="/empresas">Trazabilidad ESG</a>
      <nav aria-label="Secciones">
        <a routerLink="/empresas" routerLinkActive="current" ariaCurrentWhenActive="page">Empresas</a>
        <a routerLink="/asociaciones" routerLinkActive="current" ariaCurrentWhenActive="page">Asociaciones</a>
        <a routerLink="/vecinos" routerLinkActive="current" ariaCurrentWhenActive="page">Vecinos</a>
        <a routerLink="/recojos/nuevo" routerLinkActive="current" ariaCurrentWhenActive="page">Registrar recojo</a>
        <a routerLink="/sigersol" routerLinkActive="current" ariaCurrentWhenActive="page">SIGERSOL</a>
      </nav>
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
      z-index: 1;
      padding: var(--space-2) var(--space-3);
      background: var(--paper);
      border: 2px solid var(--ink);
    }
    .skip:focus { top: var(--space-2); }
    .skip:focus-visible { outline-color: var(--accent-on-ink); }
    /* The bar is the printer's head: one band of ink above the paper. */
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: stretch;
      gap: 0 var(--space-6);
      min-height: 60px;
      padding: 0 var(--space-8);
      background: var(--ink);
      color: var(--paper);
    }
    /* On the ink band, focus is the lifted cobalt: 6.5:1 against the bar. */
    .bar :focus-visible { outline-color: var(--accent-on-ink); }
    /* Brand and nav links fill the bar's height: draw the ring inside them, or its top and
       bottom edges fall outside the band. */
    .bar a:focus-visible { outline-offset: -4px; }
    .brand {
      display: flex;
      align-items: center;
      color: var(--paper);
      font-weight: 800;
      font-size: 17px;
      letter-spacing: -0.01em;
      text-decoration: none;
    }
    nav {
      display: flex;
      flex-wrap: wrap;
      gap: 0 var(--space-5);
      margin-right: auto;
      font-size: 14px;
    }
    nav a {
      display: inline-flex;
      align-items: center;
      min-height: 44px;
      padding-top: 3px;
      color: var(--ink-soft-on-ink);
      text-decoration: none;
      border-bottom: 3px solid transparent;
    }
    nav a:hover { color: var(--paper); }
    nav a.current {
      color: var(--paper);
      font-weight: 600;
      border-bottom-color: var(--accent-on-ink);
    }
    .user {
      display: flex;
      align-items: center;
      gap: var(--space-4);
      font-size: 13.5px;
      color: var(--ink-soft-on-ink);
    }
    .user .btn-link { color: var(--paper); min-height: 44px; }
    .email { overflow-wrap: anywhere; }
    main {
      max-width: 1120px;
      margin: 0 auto;
      padding: var(--space-8) var(--space-8) var(--space-12);
    }
    main:focus { outline: none; }
    @media (max-width: 640px) {
      .bar { padding: var(--space-2) var(--space-4) 0; gap: 0 var(--space-4); }
      .brand { min-height: 44px; }
      .user { margin-left: auto; }
      nav { order: 3; width: 100%; }
      main { padding: var(--space-6) var(--space-4) var(--space-8); }
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

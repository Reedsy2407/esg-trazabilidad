import { Component, ElementRef, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { SessionService } from '../core/auth/session.service';
import { Logo } from '../shared/logo';

/** Chrome level 1 of 3 (brief): the top bar. Pages bring their own title and content. */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, Logo],
  template: `
    <a class="skip" href="#contenido">Saltar al contenido</a>
    <div class="scroll-progress" aria-hidden="true"></div>
    <header class="bar">
      <a class="brand" routerLink="/empresas">
        <app-logo [size]="36" />
        <span>Trazabilidad ESG</span>
      </a>
      <nav aria-label="Secciones">
        <a routerLink="/empresas" routerLinkActive="current" ariaCurrentWhenActive="page">Empresas</a>
        <a routerLink="/asociaciones" routerLinkActive="current" ariaCurrentWhenActive="page">Asociaciones</a>
        <a routerLink="/vecinos" routerLinkActive="current" ariaCurrentWhenActive="page">Vecinos</a>
        <a routerLink="/recojos/nuevo" routerLinkActive="current" ariaCurrentWhenActive="page">Registrar recojo</a>
        <a routerLink="/sigersol" routerLinkActive="current" ariaCurrentWhenActive="page">SIGERSOL</a>
      </nav>
      <div class="user">
        <a class="email" routerLink="/mi-sesion" routerLinkActive="current" ariaCurrentWhenActive="page" aria-label="Mi sesión: {{ session.email() }}"><span class="email-full" [attr.title]="session.email()">{{ session.email() }}</span><span class="email-short" aria-hidden="true">Mi sesión</span></a>
        <button type="button" class="btn-link" (click)="signOut()">Cerrar sesión</button>
      </div>
    </header>
    <main id="contenido" tabindex="-1" #main>
      <router-outlet />
    </main>
    <footer class="site-foot">
      <app-logo variant="symbol" [size]="28" />
      <p>Trazabilidad ESG · Proyecto académico, sin validez legal</p>
    </footer>
  `,
  styles: `
    .skip {
      position: absolute;
      left: var(--space-4);
      top: -100px;
      z-index: 6;
      padding: var(--space-3) var(--space-4);
      background: var(--paper);
      border: 2px solid var(--ink);
    }
    .skip:focus { top: var(--space-2); }
    .skip:focus-visible { outline-color: var(--accent-on-ink); }
    /* La barra es el cabezal de la impresora: una banda del azul del logo sobre el papel. */
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: stretch;
      gap: 0 var(--space-8);
      min-height: 68px;
      padding: 0 var(--gutter);
      background: var(--brand-deep);
      color: var(--paper);
      view-transition-name: bar;
    }
    /* Sobre la banda, el foco es el azul claro: 7.1:1 contra la barra. */
    .bar :focus-visible { outline-color: var(--accent-on-ink); }
    /* La marca y los enlaces llenan la altura de la barra: el anillo va por dentro, o sus bordes
       superior e inferior caerían fuera de la banda. */
    .bar a:focus-visible { outline-offset: -4px; }
    .brand {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      color: var(--paper);
      font-weight: 800;
      font-size: var(--t-1);
      letter-spacing: -0.02em;
      text-decoration: none;
    }
    nav {
      display: flex;
      flex-wrap: wrap;
      gap: 0 var(--space-5);
      margin-right: auto;
      font-size: var(--t-0);
    }
    nav a {
      display: inline-flex;
      align-items: center;
      min-height: 68px;
      padding-top: 4px;
      color: var(--ink-soft-on-ink);
      font-weight: 600;
      text-decoration: none;
      border-bottom: 4px solid transparent;
    }
    nav a:hover { color: var(--paper); }
    nav a.current {
      color: var(--paper);
      border-bottom-color: var(--accent-on-ink);
    }
    .user {
      display: flex;
      align-items: center;
      gap: var(--space-4);
      font-size: var(--t-0);
      color: var(--ink-soft-on-ink);
    }
    .user .btn-link { color: var(--paper); min-height: var(--touch); }
    .email {
      display: inline-flex;
      align-items: center;
      min-height: var(--touch);
      color: var(--ink-soft-on-ink);
      overflow-wrap: anywhere;
      text-decoration: underline;
      text-decoration-color: transparent;
    }
    /* El correo se recorta con puntos suspensivos para que la barra quepa en una fila a 1280; entero desde 1500. */
    .email-short { display: none; }
    .email-full { display: inline-block; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    @media (min-width: 1500px) {
      .email-full { max-width: 340px; }
    }
    @media (max-width: 639.98px) {
      .email-full { display: none; }
      .email-short { display: inline; }
    }
    .email:hover { color: var(--paper); text-decoration-color: currentColor; }
    .email.current { color: var(--paper); font-weight: 600; text-decoration-color: var(--accent-on-ink); text-decoration-thickness: 3px; }
    main {
      max-width: 1920px;
      margin: 0 auto;
      padding: clamp(var(--space-6), 3vw, 56px) var(--gutter) var(--space-12);
    }
    main:focus { outline: none; }
    .site-foot {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--space-3);
      padding: 0 var(--gutter) var(--space-8);
      color: var(--ink-soft);
      font-size: var(--t-note);
    }
    .site-foot p { margin: 0; }
    @media (max-width: 1239.98px) {
      .bar { gap: 0 var(--space-4); }
      .brand { min-height: 56px; }
      .user { margin-left: auto; }
      nav { order: 3; width: 100%; gap: 0 var(--space-5); padding-bottom: var(--space-2); }
      nav a { min-height: 48px; }
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

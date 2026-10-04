# frontend-demo

Angular client for the ESG traceability platform. It talks to the four Spring Boot services on Render.
The design source of truth is [`design/DESIGN-BRIEF.md`](design/DESIGN-BRIEF.md); design tooling is recorded in [`design/TOOLING.md`](design/TOOLING.md).

Angular 21.2 (standalone, signals, zoneless, lazy routes, Reactive Forms). No NgRx, SSR or component library: own CSS with the brief's tokens.

## Run

Requires Node `^22.12` (checked with 22.19.0) and npm.

```bash
npm ci
npm start        # ng serve on http://localhost:5200
```

`ng serve` proxies `/svc/<service>/…` to the public Render services (`proxy.conf.mjs`), so local development needs no CORS. The first call to a sleeping service waits for its cold start, up to about 2 minutes. That's what the "Preparando el sistema" screen is for.

The default port is 5200, not 4200, because Windows hosts with Hyper-V/WSL2 often reserve 4200 (`listen EACCES`).

## Check

```bash
npm test -- --watch=false   # Vitest unit tests (interceptors, guards, login, warm-up)
npx ng lint                 # angular-eslint, including template accessibility rules
npx ng build                # production build
npx playwright install chromium   # once per machine
npx playwright test         # browser flows + AXE (WCAG 2.1 AA); the backend is faked with page.route (e2e/backend.ts)
```

## Configuration

`src/environments/environment.ts` holds the production base URLs of the four services (public, not secrets). The development file points them at the proxy paths.

In production the browser calls the services directly, so each service needs the frontend's origin in its `CORS_ALLOWED_ORIGINS` (Render's `esg-shared` group).

The session JWT is kept in `sessionStorage`, never `localStorage`. It lasts one hour, and there is no refresh token.

## Third-party assets

- IBM Plex Sans and Mono (`public/fonts/`), SIL Open Font License 1.1. Self-hosted Latin-1 subsets of the weights used.
- One Material Symbols icon, inlined as SVG, Apache License 2.0. See `public/ICONS-NOTICE.txt`.

No font, icon or script is loaded from a third-party server.

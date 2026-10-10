# frontend-demo

Angular client for the ESG traceability platform. It talks to the four Spring Boot services on Render.
The design source of truth is [`design/DESIGN-BRIEF.md`](design/DESIGN-BRIEF.md); design tooling is recorded in [`design/TOOLING.md`](design/TOOLING.md).

Angular 21.2 (standalone, signals, zoneless, lazy routes, Reactive Forms). No NgRx, SSR or component library: own CSS with the brief's tokens.

Which screen uses which endpoint, the rules that live only in the frontend (such as "por vencer" and what can't be issued), the known limitations and what the backend still lacks are in [`docs/frontend.md`](../docs/frontend.md).

## Run

Requires Node `^22.12` (checked with 22.19.0) and npm.

```bash
npm ci
npm start        # ng serve on http://localhost:5200
```

`ng serve` proxies `/svc/<service>/…` to the public Render services (`proxy.conf.mjs`), so local development needs no CORS. The first call to a sleeping service waits for its cold start, up to about 2 minutes. That's what the "Preparando el sistema" screen is for.

**`npm start` writes to production.** The proxy points at the real services, so submitting any form (a neighbour, a collection, a company, a SIGERSOL figure, a certificate, a staff account) writes to the production database. The Playwright tests that install the fake backend (`fakeBackend` in `e2e/backend.ts`) never do: it answers every `/svc/` request itself, including any route it doesn't simulate. The specs added with the full-coverage module (associations, neighbours, company registration, SIGERSOL, certificate issue, staff) also check that nothing went unanswered. The few warm-up tests that don't install it (in `a11y.spec.ts` and `flows.spec.ts`) only route the services' liveness checks.

The default port is 5200, not 4200, because Windows hosts with Hyper-V/WSL2 often reserve 4200 (`listen EACCES`).

## Check

```bash
npm test -- --watch=false   # Vitest unit tests
npx ng lint                 # angular-eslint, including template accessibility rules
npx ng build                # production build
npx playwright install chromium   # once per machine
npx playwright test         # browser flows + AXE (WCAG 2.1 AA); the backend is faked with page.route (e2e/backend.ts)
```

## Configuration

`src/environments/environment.ts` holds the production base URLs of the four services (public, not secrets). The development file points them at the proxy paths.

In production the browser calls the services directly, so each service needs the frontend's origin in its `CORS_ALLOWED_ORIGINS` (Render's `esg-shared` group).

The session JWT is kept in `sessionStorage`, never `localStorage`. Its lifetime comes from the token itself (`exp − iat`; auth-service issues one hour today), and there is no refresh token.

## Third-party assets

- IBM Plex Sans and Mono (`public/fonts/`), SIL Open Font License 1.1. Self-hosted Latin-1 subsets of the weights used.
- One Material Symbols icon, inlined as SVG, Apache License 2.0. See `public/ICONS-NOTICE.txt`.

No font, icon or script is loaded from a third-party server.

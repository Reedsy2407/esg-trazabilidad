# Frontend (frontend-demo): coverage, rules and limitations

The Angular app in `frontend-demo/` is the staff console for the four services. This page records which backend endpoint each screen uses, the rules that live only in the frontend, the known limitations and what the backend still lacks. How to run and test it is in `frontend-demo/README.md`.

## Endpoint → screen

27 of the 44 business endpoints have a screen (12 before "Frontend v2: full coverage").

| Service | Endpoint | Screen |
|---|---|---|
| auth | `POST /auth/login` | Iniciar sesión |
| auth | `GET /auth/me` | Mi sesión |
| auth | `POST /auth/staff-users` | Mi sesión → Crear una cuenta del personal |
| recycler | `GET /associations` | Asociaciones (list, state filter); pickers in Registrar recojo, Registrar empresa, SIGERSOL; names on Vecino's collections |
| recycler | `GET /associations/{id}` | Asociación; Emitir certificado (the draft's association) |
| recycler | `GET /associations/{id}/certifications` | Asociación (certification chips) |
| recycler | `POST /associations`, `PATCH …/suspend`, `PATCH …/activate` | — |
| recycler | `POST …/certifications`, `GET …/certifications/{id}`, `PATCH …/renew` | — |
| recycler | `/associations/{id}/recyclers` (create, list, detail, activate, deactivate) | — |
| collection | `GET /neighbors` | Vecinos; picker in Registrar recojo |
| collection | `POST /neighbors` | Registrar vecino |
| collection | `GET /neighbors/{id}` | Vecino |
| collection | `GET /neighbors/{id}/schedules` | Vecino; schedule picker in Registrar recojo |
| collection | `POST …/schedules`, `PATCH …/pause`, `…/cancel`, `…/reactivate` | Vecino (cancel asks first: it is final) |
| collection | `GET …/schedules/{id}` | — (the list carries the same fields) |
| collection | `POST /neighbors/{id}/collection-records` | Registrar recojo |
| collection | `GET /neighbors/{id}/collection-records` | Vecino (by date range) |
| collection | `GET …/collection-records/{id}` | — (the list carries the same fields) |
| collection | `/companies` (create, list, detail) | — deliberately unused (see "Overlap") |
| reporting | `GET /tracked-companies`, `GET /tracked-companies/{id}` | Empresas, Empresa |
| reporting | `POST /tracked-companies` | Registrar empresa |
| reporting | `GET …/certificates`, `GET …/certificates/{id}`, `…/pdf`, `…/csv` | Empresa, Certificado; Emitir certificado (overlap check) |
| reporting | `GET …/certificate-summary` | Empresa (period summary), Emitir certificado (draft) |
| reporting | `POST …/certificates` | Emitir certificado |
| reporting | `GET /sigersol-syncs`, `POST /sigersol-syncs` | Registros SIGERSOL, Registrar dato SIGERSOL |
| reporting | `GET /sigersol-syncs/{id}` | — (the list carries the same fields) |

## Rules that live only in the frontend

The backend doesn't enforce these; the screens do, and say so.

- **"Por vencer"** (association certifications): an unexpired certification that expires in **less than 30 days**, counted on Lima's calendar. It is not a backend state: the backend only says `expired`. "Vencido" follows that flag, plus any date already past in Lima (a clock-skew guard). The constant is `EXPIRING_SOON_DAYS` in `features/associations/certification-state.ts`. On screen it reads "vence en menos de 30 días (regla de esta aplicación…)". Green, amber and red are used only for these three chips.
- **Issuing a certificate** (irreversible, no endpoint voids one):
  - A period that hasn't ended (end date today or later in Lima) is refused, saying from which date it can be issued.
  - A period with 0 kg is refused.
  - A period without one SIGERSOL record covering all of it is refused before asking (the backend would answer RPT-005), with a link to register it.
  - A period overlapping a listed certificate is refused before asking (the backend would answer RPT-004).
  - An end before the start is refused (the backend has no code for it).
  - The confirmation is a checkbox plus a button that names the period.
- **SIGERSOL** figures are always labelled "ingreso manual": they are typed in from SIGERSOL; nothing integrates with it. The form keeps the columns' precision, and caps the note at 255 characters: a longer one would fail in the database and come back as RPT-006 ("overlap"), which would mislead.
- **Staff accounts:**
  - The initial password is always generated in the browser (16 characters, `crypto.getRandomValues`, no ambiguous characters). It is never typed.
  - It exists only in the page's memory: never in storage, the URL, router state, the console or a DOM attribute. It is dropped by "Ya la entregué", by a confirmed "Descartar", by leaving the page, or when an attempt is refused before ever creating the account. If the screen copied it, the clipboard is overwritten too.
  - After an unknown outcome it stays visible, and a retry reuses it.
  - Emails need a dot in the domain: `@Email` accepts `a@b`, which the domain then rejects with a 500.
- **Session:** "Tu sesión vence a las HH:MM (dura X y no se renueva)" takes the time from the token's `exp` (shown in Lima) and the length from `exp − iat`. auth-service issues no refresh token.
- **Unknown outcomes:** a write with no answer, a 408 or a 5xx (Render answers 502/504 while a service wakes) never claims it failed. The message says how to check before retrying.

## Known limitations

- **No roles.** Any signed-in staff member can issue certificates, create staff accounts, cancel schedules and register companies or SIGERSOL figures. The screens say so and ask for an explicit confirmation before the irreversible or sensitive actions.
- **Issuing to an inactive company is allowed**, as the backend allows it. Open question for the product owner.
- **Pickers load one page of 100** (the services' `max-page-size`). They say so when there are more. Neighbour search filters only what was loaded.
- **The district filter is exact.** It is case- and accent-sensitive, because the backend compares with equality.
- **The session log (bitácora)** lives in the tab's sessionStorage, tied to one sign-in. It is not a server record.
- **Neighbours and companies can't be edited, deactivated or deleted**, and associations can't be created from the app, because no screen covers those endpoints or they don't exist.

## Overlap: two kinds of "company"

reporting-service's **tracked company** (`/tracked-companies`) is the company certificates are issued to; it is the only one the frontend uses. collection-service also has `/companies` (name, RUC, contact data), unrelated to it: no event links the two. Until the backend settles which one is the company, `/companies` stays unused.

## Pending in the backend

- Edit, deactivate or delete: neighbours, tracked companies, SIGERSOL records, staff accounts.
- Change or reset a staff password. Today the initial password is permanent.
- Roles or permissions (who may issue certificates or create staff).
- Search neighbours by name (the list only filters by state and exact district).
- The association's name on the certificate (`EsgCertificateResponse` has only `associationId`).
- A JSON endpoint for a certificate's weighings (they travel only in the CSV).
- A validation code for a period whose end is before its start, at certificate issue.
- Decide between `/companies` (collection) and `/tracked-companies` (reporting).

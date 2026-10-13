# Frontend (frontend-demo): coverage, rules and limitations

The Angular app in `frontend-demo/` is the staff console for the four services. This page records which backend endpoint each screen uses, the rules that live only in the frontend, the known limitations and what the backend still lacks. How to run and test it is in `frontend-demo/README.md`.

## Endpoint → screen

32 of the 49 business endpoints have a screen (12 before "Frontend v2: full coverage"; 27 of 44 before M2 added the neighbour edit/deactivate/activate/delete/status-changes endpoints).

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
| collection | `GET /neighbors` | Vecinos (filtro por estado y por distrito, elegido de la lista INEI); picker in Registrar recojo |
| collection | `POST /neighbors` | Registrar vecino (distrito elegido de la lista INEI) |
| collection | `GET /neighbors/{id}` | Vecino |
| collection | `PATCH /neighbors/{id}` | Vecino → Editar |
| collection | `PATCH /neighbors/{id}/deactivate`, `…/activate` | Vecino (motivo obligatorio; reactivar ofrece los cronogramas pausados, D8) |
| collection | `DELETE /neighbors/{id}` | Vecino (motivo obligatorio; refused as COL-011 if it has collections) |
| collection | `GET /neighbors/{id}/status-changes` | Vecino (bitácora de cambios de estado) |
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
| reporting | `POST …/certificates/{id}/annul` | Certificado (Anular certificado) |
| reporting | `GET /sigersol-syncs`, `POST /sigersol-syncs` | Registros SIGERSOL, Registrar dato SIGERSOL |
| reporting | `GET /sigersol-syncs/{id}` | — (the list carries the same fields) |

## Rules that live only in the frontend

The backend doesn't enforce these; the screens do, and say so.

- **"Por vencer"** (association certifications): an unexpired certification that expires in **less than 30 days**, counted on Lima's calendar. It is not a backend state: the backend only says `expired`. "Vencido" follows that flag, plus any date already past in Lima (a clock-skew guard). The constant is `EXPIRING_SOON_DAYS` in `features/associations/certification-state.ts`. On screen it reads "vence en menos de 30 días (regla de esta aplicación…)". Green, amber and red are used only for these three chips.
- **Issuing a certificate** (it is never deleted; the only way back is to annul it, see below):
  - An **INACTIVE** company can't be issued any certificate (product owner's decision, 2026-10-10). The backend refuses it too (`RPT-015`); the issue screen shows "Esta empresa está inactiva: no se pueden emitir certificados" instead of the period form, with no issue button. The status comes from `TrackedCompanyResponse.status`.
  - A period that hasn't ended (end date today or later in Lima) is refused, saying from which date it can be issued.
  - A period with 0 kg is refused.
  - A period without one SIGERSOL record covering all of it is refused before asking (the backend would answer RPT-005), with a link to register it.
  - A period overlapping a listed **Emitido** certificate is refused before asking (the backend would answer RPT-004). An **Anulado** one frees its period and never counts.
  - An end before the start is refused before asking (the backend would answer RPT-009).
  - The confirmation is a checkbox plus a button that names the period.
- **Annulling a certificate** (M3): the certificate page offers "Anular certificado" while it is Emitido. It asks for a reason (500 characters at most, `shared/reason-confirm.ts`) and the same checkbox-and-named-button confirmation; who and when are never asked, the backend takes them from the JWT. An annulled certificate is never deleted: its page shows a heavy "ANULADO" stamp with the date, the staff member's email and the reason, its frozen numbers unchanged, and its PDF and CSV carry the same marks. It stays in the company's list tagged "Anulado", and it feeds neither the period summary nor the chart (they describe certified periods only). `RPT-019` (already annulled, e.g. by someone else a moment ago) says so and points to a reload; an unknown outcome never claims it failed. To correct wrong data the flow is: annul the certificate, deactivate the company, register the right one, issue again (the company-side steps arrived with M4).
- **Neighbour deactivate, reactivate and delete** (M2) all require a reason (`reason-confirm.ts`, max 500 characters) plus the same checkbox-and-named-button confirmation used for issuing a certificate; who does it is never asked, the backend takes it from the JWT. Reactivating offers the schedules that deactivation paused (D8) so staff can resume them one by one; deleting cancels the remaining `ACTIVE`/`PAUSED` schedules outright. A delete refused with `COL-011` (the neighbour has collection records) points at "Desactivar" instead.
- **Company registration and lifecycle** (M4):
  - The legal name is never typed: the backend asks SUNAT by RUC. Errors speak plainly: `RPT-010` bad RUC, `RPT-011` SUNAT did not answer (a definite 503: nothing was stored), `RPT-012` SUNAT does not know the RUC, `RPT-013` baja definitiva. A `NO HABIDO` taxpayer is registered and the company page shows SUNAT's warning.
  - After `RPT-011` the form offers "Registrar como no verificada" with a typed name. The company is **No verificada** and cannot receive certificates (`RPT-016`; the issue screen says so) until "Verificar con SUNAT" on its page succeeds. If SUNAT answers after all, `RPT-014` withdraws the offer.
  - The company page edits the **contact** only (RUC and association are API-only), deactivates/reactivates and deletes with a required reason (`reason-confirm.ts`). `RPT-018`: already in that state. `RPT-017`: it has certificates (annulled ones count), so it can only be deactivated. A deleted company disappears and its RUC is free again.
  - Legacy companies (typed by hand before M4) show as **No verificada** until verified; cleaning the demo data is M7.
- **SIGERSOL** figures are always labelled "ingreso manual": they are typed in from SIGERSOL; nothing integrates with it. The form keeps the columns' precision, and caps the note at 255 characters: a longer one would fail in the database and come back as RPT-006 ("overlap"), which would mislead.
- **Staff accounts:**
  - The initial password is always generated in the browser (16 characters, `crypto.getRandomValues`, no ambiguous characters). It is never typed.
  - It exists only in the page's memory: never in storage, the URL, router state, the console or a DOM attribute. It is dropped by "Ya la entregué", by a confirmed "Descartar", by leaving the page, or when an attempt is refused before ever creating the account. If the screen copied it, the clipboard is overwritten too.
  - After an unknown outcome it stays visible, and a retry reuses it.
  - Emails need a dot in the domain, checked before sending: `@Email` accepts `a@b`, which the service then refuses with AUTH-005.
- **Session:** "Tu sesión vence a las HH:MM (dura X y no se renueva)" takes the time from the token's `exp` (shown in Lima) and the length from `exp − iat`. auth-service issues no refresh token.
- **Unknown outcomes:** a write with no answer, a 408 or a 5xx (Render answers 502/504 while a service wakes) never claims it failed. The message says how to check before retrying.

## Known limitations

- **No roles.** Any signed-in staff member can issue certificates, create staff accounts, cancel schedules and register companies or SIGERSOL figures. The screens say so and ask for an explicit confirmation before the irreversible or sensitive actions.
- **Pickers load one page of 100** (the services' `max-page-size`). They say so when there are more. Neighbour search filters only what was loaded.
- **The district filter** is the same combobox used to register or edit a neighbour (M2): it picks one of the 50 districts by `districtUbigeo`, so it can't miss on case or accents. It still can't match free text, because the backend filters by the ubigeo code, not by name.
- **The session log (bitácora)** lives in the tab's sessionStorage, tied to one sign-in. It is not a server record.
- **Companies can't be edited, deactivated or deleted**, and associations can't be created from the app, because no screen covers those endpoints or they don't exist. Neighbours can (M2): edit, deactivate/reactivate with a reason, and delete with a reason (refused with COL-011 if they have collection records — deactivate instead).

## collection-service error catalog: COL-010..COL-013 (M2)

These four codes were added for the neighbour district, lifecycle and deletion work; `CollectionErrors.java` is the source of truth, `shared/error-message.ts` carries the Spanish text the frontend shows.

| Code | HTTP | When | Spanish message shown |
|---|---|---|---|
| COL-010 | 400 | `POST /neighbors` or `PATCH /neighbors/{id}` with a district that isn't one of the 50 INEI codes (Lima Metropolitana + Callao) — can't happen through the picker, only via a direct API call | El distrito no está en la lista de Lima Metropolitana y Callao. |
| COL-011 | 409 | `DELETE /neighbors/{id}` on a neighbour that has at least one collection record | El vecino tiene recojos registrados: no se puede eliminar, solo desactivar. |
| COL-012 | 409 | `PATCH /neighbors/{id}/deactivate` or `…/activate` when the neighbour is already in that state | El vecino ya está en ese estado. |
| COL-013 | 409 | A collection record, a new schedule, or reactivating a paused schedule, for an `INACTIVE` neighbour | El vecino está inactivo: no puede recibir recojos ni cronogramas activos. |

`COL-001` (`NEIGHBOR_NOT_FOUND`) also answers a status change or an edit that loses a race against a concurrent deletion — not just a missing id.

## Overlap: two kinds of "company"

reporting-service's **tracked company** (`/tracked-companies`) is the company certificates are issued to; it is the only one the frontend uses. collection-service also has `/companies` (name, RUC, contact data), unrelated to it: no event links the two. Until the backend settles which one is the company, `/companies` stays unused.

## Pending in the backend

- Edit, deactivate or delete: tracked companies, SIGERSOL records, staff accounts. (Neighbours got all three in M2.)
- Change or reset a staff password. Today the initial password is permanent.
- Roles or permissions (who may issue certificates or create staff).
- Search neighbours by name (the list only filters by state and exact district).
- The association's name on the certificate (`EsgCertificateResponse` has only `associationId`).
- A JSON endpoint for a certificate's weighings (they travel only in the CSV).
- Decide between `/companies` (collection) and `/tracked-companies` (reporting).

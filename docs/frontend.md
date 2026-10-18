# Frontend (frontend): coverage, rules and limitations

The Angular app in `frontend/` is the staff console for the four services. This page records which backend endpoint each screen uses, the rules that live only in the frontend, the known limitations and what the backend still lacks. How to run and test it is in `frontend/README.md`.

## Endpoint → screen

32 of the 49 business endpoints have a screen (12 before "Frontend v2: full coverage"; 27 of 44 before M2 added the neighbour edit/deactivate/activate/delete/status-changes endpoints).

| Service | Endpoint | Screen |
|---|---|---|
| auth | `POST /auth/login` | Iniciar sesión |
| auth | `GET /auth/me` | Mi sesión |
| auth | `POST /auth/password-reset/request` | Public, no session: Iniciar sesión → «¿Olvidaste tu contraseña?» (`/olvide-contrasena`, M9) |
| auth | `POST /auth/password-reset/verify` | Public: Revisa tu correo, el código (`/restablecer-contrasena`, M9c) |
| auth | `POST /auth/password-reset/confirm` | Public: Elige una contraseña nueva (`/nueva-contrasena`, M9c; needs the token in memory) |
| auth | `POST /auth/me/password` | Mi sesión → Cambiar mi contraseña (M7) |
| auth | `GET /auth/staff-users` | Mi sesión → Cuentas del personal (ADMIN only, M7) |
| auth | `POST /auth/staff-users` | Cuentas del personal → Crear una cuenta del personal (ADMIN only) |
| auth | `PATCH …/staff-users/{id}/deactivate`, `…/reactivate`, `POST …/staff-users/{id}/password` | Cuentas del personal (desactivar y reactivar con motivo; Restablecer una contraseña) (ADMIN only) |
| recycler | `GET /associations` | Asociaciones (list, state filter); pickers in Registrar recojo, Registrar empresa, SIGERSOL; names on Vecino's collections |
| recycler | `GET /associations/{id}` | Asociación; Emitir certificado (the draft's association) |
| recycler | `GET /associations/{id}/certifications` | Asociación (certification chips) |
| recycler | `POST /associations` | Registrar asociación (the RUC gets the same local check as the backend, D7; ASO-004 / ASO-002 answered on the screen) |
| recycler | `PATCH …/suspend`, `PATCH …/activate` | Asociación (asks first; ASO-003 reloads the current state) |
| recycler | `POST …/certifications`, `PATCH …/renew` | Asociación (Registrar certificación; Renovar asks for the new expiration date; CER-003 when the dates are out of order) |
| recycler | `GET …/certifications/{id}` | — |
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

- **A service that is down or waking (M10).** One wording everywhere, with no status codes and with the next step: «No se pudieron cargar los vecinos: el servicio de recojos no está disponible en este momento. Suele volver en menos de un minuto: vuelve a intentarlo.» It covers the three gateway statuses Render answers while a free service wakes (502, 503, 504) and the service's own `503` with code `SERVICE_UNAVAILABLE` (its database cannot be reached; `Retry-After: 10`). No answer at all (status 0) keeps «Comprueba tu conexión». Any other 5xx says «tuvo un problema inesperado». The login says the same for the auth service, and the PDF/CSV download for reporting. Writes that get no answer or a 5xx still say the outcome is unknown and how to check, never that it failed (the write may have gone through). Helpers: `isUnavailable` and `unavailableText` in `shared/error-message.ts`.

- **RUC of a new association** (`shared/ruc.ts`): the same local check as the backend (11 digits, prefix 10/15/16/17/20, modulo-11 digit) so the form stops a typo before the request; the backend still answers ASO-004 if it differs. It does not ask SUNAT.
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
- **M6b corrections.** The issue screen says issuing is final *for editing* (a certificate can still be annulled, with a reason). The certificate page's "Anular" section sits in the ticket's column. "Registrar recojo" has ONE neighbour control, an editable combobox ("Vecino": type a name or district, arrows and Enter to choose, Esc to close; editing the text withdraws the choice). The kilos chart's axis fits the data (a "nice" step, about three intervals), not a fixed 0-15,000. A company with certificates (even annulled) shows no "Eliminar empresa" button, only the reason; the server still answers `RPT-017` if one appears meanwhile. Rows from before M5 read "Dato anterior al registro de origen".
- **Certificate files** (M6b): the PDF is laid out like the ticket (title band, VIGENTE or ANULADO stamp, company wrapped inside the margins, kilos as the one big figure, striped table, district table), dates as dd/MM/yyyy and times in Lima, a pale ANULADO mark on every page of an annulled one, and the footer "Proyecto académico, sin validez legal" with "Página i de n". The CSV (M7b: comma, RFC 4180) is UTF-8 with a byte-order mark, CRLF and "," as separator (what Excel opens in columns where the decimal is a dot, as in Peru, with accents; fields with commas, quotes or line breaks are quoted; numbers use a dot decimal and no grouping; on a Windows with a decimal comma, like es-ES, it opens in one column: use Data > From Text/CSV and pick ","), with field names in one style: Estado (Vigente or Anulado), Anulado en (hora de Lima), Anulado por, Motivo de anulación, Disponible / No disponible.
- **District context** (M5): the certificate page and the draft list the kilos of each district with SIGERSOL Municipal's aggregated counts (associations and recyclers), their year and source, under "Contexto distrital". The note says they describe the district, not the company or its suppliers, and nothing is worded as a validation (the certificate never uses the word "verifica"). A district SIGERSOL has nothing for reads "Contexto de SIGERSOL Municipal no disponible."; kilos whose records carry no district read "Distrito desconocido". On a certificate the lines are frozen when it is issued; in the draft they are live. Only counts are shown: tonnes are not (unknown unit).
- **SIGERSOL origin** (M5, D1; reworded in M7): registering a figure asks "Origen del dato" (declarado por el operador, or con comprobante referenciado) and records the staff member's email from the session. The reference is **required** exactly with "Con comprobante referenciado" (`RPT-021`) and is "referenciado, no verificado por SIGERSOL". The wording never says SIGERSOL validated it. Older records read "Dato anterior al registro de origen". A company's summary and the certificate draft show the origin of the covering record, or "Sin dato declarado: ningún registro cubre el período."
- **The hierarchy figure is declared** (M7): screens, PDF and CSV label it «Cumplimiento de jerarquía (declarado)» and carry the note that it is declared by the association's operator, not an official SIGERSOL figure, and not verified by this platform.
- **SIGERSOL** figures are always labelled "ingreso manual": «Cifras declaradas por el operador, ingresadas a mano; esta aplicación no se conecta con SIGERSOL ni las verifica.» The form keeps the columns' precision, and caps the note at 255 characters: a longer one would fail in the database and come back as RPT-006 ("overlap"), which would mislead.
- **Staff accounts:**
  - The initial password is always generated in the browser (16 characters, `crypto.getRandomValues`, no ambiguous characters). It is never typed.
  - It exists only in the page's memory: never in storage, the URL, router state, the console or a DOM attribute. It is dropped by "Ya la entregué", by a confirmed "Descartar", by leaving the page, or when an attempt is refused before ever creating the account. If the screen copied it, the clipboard is overwritten too.
  - After an unknown outcome it stays visible, and a retry reuses it.
  - Emails need a dot in the domain, checked before sending: `@Email` accepts `a@b`, which the service then refuses with AUTH-005.
- **Roles and accounts (M7):** the role comes from the token and only steers the screens: «Cuentas del personal» and its actions show for an ADMIN, and the route guards send a STAFF member away from them; the server decides from the database (AUTH-006 for a non-admin). Passwords are 15 to 64 characters (NIST SP 800-63B); the form shows the rule and the server's `AUTH-007`/`AUTH-008` answers. After an account is created or reset, the person is sent to «Cambiar mi contraseña» before anything else (a guard, not a backend rule). Deactivate and reset say that a token already issued stays valid until it expires (at most one hour).
- **Session:** "Tu sesión vence a las HH:MM (dura X y no se renueva)" takes the time from the token's `exp` (shown in Lima) and the length from `exp − iat` (one hour, no revocation). auth-service issues no refresh token.
- **«¿Olvidaste tu contraseña?» (M9, en tres pasos desde M9c).** A link under the login opens three public screens (no top bar; a signed-in person is sent to Empresas). **Paso 1** (`/olvide-contrasena`): the email and «Enviar código»; it goes on to step 2 whatever the answer, so the screen never says whether the account exists. **Paso 2** (`/restablecer-contrasena`, «Revisa tu correo»): the email as **fixed text** with the link «¿No es tu correo? Cambiar» (back to step 1 with the email filled in), and the code (`inputmode="numeric"`, `autocomplete="one-time-code"`, pasted with spaces is fine: only the digits are sent) with «Verificar código». The code is checked **before** any password is asked for. The fixed message «Si el correo está registrado, te enviamos un código de 8 dígitos. Vence en 10 minutos. Revisa también la carpeta de spam.» reads the same for every account. «Enviar otro código» is disabled for 60 s with a visible countdown, outside the live region; only the end («Ya puedes pedir un código nuevo.») is announced, politely. Every code that does not work gets the same text («El código no es válido o venció. Pide uno nuevo.»), under the code field; after **five** the person goes back to step 1 with their email and a notice. **Paso 3** (`/nueva-contrasena`, «Elige una contraseña nueva»): the new password twice with the M7 rules and «Mostrar las contraseñas». The reset token the service hands back lives **only in memory** (`RecoverySession`): never in the URL, the history state, storage or the DOM, and a route guard sends anyone without it (a reload, a typed address) back to step 1. `AUTH-007` is said under the password and does not spend the token, so another password can be typed; when the token no longer works (`AUTH-011`, or 10 minutes passed in the browser) the person returns to step 2 with «La verificación venció (dura 10 minutos y sirve una vez). Pide un código nuevo.» and the same email. A `429` says «Demasiados intentos. Prueba de nuevo en N minutos.» (from `Retry-After`), and a bare 5xx never claims the code was not sent or the password not changed. On success the form is emptied, the token dropped, and the login shows «Contraseña cambiada. Inicia sesión con la nueva.» (no session starts). The email travels only in the browser's history state (never in the URL), and no password, code or token is stored, logged or put in a DOM attribute (a Playwright test checks the places). Each step moves focus to its title; errors use `role="alert"`; all three steps are tested at 360, 768 and 1280 px with AXE and by keyboard.
- **Combobox browsing (M7):** the district select and the neighbour combobox open with the full list on click (an empty search box), and leaving without choosing restores the previous choice.
- **Unknown outcomes:** a write with no answer, a 408 or a 5xx (Render answers 502/504 while a service wakes) never claims it failed. The message says how to check before retrying.

## Known limitations

- **Two roles only** (M7). Account administration is ADMIN-only; any signed-in staff member can still issue certificates, cancel schedules and register companies or SIGERSOL figures. The screens ask for an explicit confirmation before the irreversible or sensitive actions. See [Limitaciones conocidas](known-limitations.md).
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

- Edit, deactivate or delete: SIGERSOL records. (Neighbours got all three in M2, tracked companies in M4, staff accounts in M7: deactivate/reactivate, no delete.)
- Recovery codes as a backup for accounts with no real mailbox (cut 3 of the recovery design): not built. Changing your own password and an admin reset exist since M7, and the emailed code since M9.
- Finer permissions than ADMIN/STAFF (who may issue certificates).
- Search neighbours by name (the list only filters by state and exact district).
- The association's name on the certificate (`EsgCertificateResponse` has only `associationId`).
- A JSON endpoint for a certificate's weighings (they travel only in the CSV).
- Decide between `/companies` (collection) and `/tracked-companies` (reporting).

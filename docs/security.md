# Security

A light security review of the four services (`auth-service`, `recycler-service`, `collection-service`, `reporting-service`) and the Angular demo (`frontend-demo/`). The architecture didn't change for it. The table follows the project's 20-point checklist, in its order. Status is one of: **Meets**, **Partial**, **Not applicable**, **Omitted by design**. Controls outside the list are under [Additional controls](#additional-controls), and the decisions taken on purpose are explained after that.

## Checklist

| # | Point | Status | Why |
|---|-------|--------|-----|
| 1 | Hide API keys | Meets | The frontend holds no key or secret: its build only knows the four services' public URLs. Server-side secrets (`JWT_SECRET`, database and broker passwords) are environment variables, in Render's environment group in production and in a gitignored `.env.local` locally. |
| 2 | Remove secrets from git | Meets | gitleaks over the whole git history (309 commits) found 4 matches, all false positives: a UUID used as a test token's subject in three IT helpers, and the frontend's password alphabet constant. Each is allowlisted in `.gitleaks.toml` by commit, file and exact line, never by rule, and CI runs gitleaks over the whole history on every push and PR. `.env`, `.env.local` and the private architecture guide are gitignored; `.env.local.example` has every secret key empty in every version. |
| 3 | Public key for the database | Not applicable | The browser never talks to the database, so there is no public (anon) database key to restrict. Only the services connect, with credentials that never leave the server. |
| 4 | Enable Row Level Security | Omitted by design | See [No row-level security](#no-row-level-security). |
| 5 | Encrypt sensitive data | Partial | In transit: HTTPS to the browser and `sslmode=require` to the database. Passwords: BCrypt hashes. At rest: no column-level encryption, see [No column encryption](#no-column-encryption). |
| 6 | Enforce authentication on the server | Meets | Every service validates the JWT itself (HS256 signature and expiry, offline, against the shared secret). Every business endpoint needs a valid token; only login, liveness, `/actuator/info` and the API docs are public. The frontend's route guards are convenience only. |
| 7 | Restrict access to records | Partial | Anonymous callers reach no record. Any signed-in staff member reaches every record, see [No roles](#no-roles). Nested resources are checked against their parent in the path: a certificate under another company, a schedule under another neighbour or a recycler under another association answers 404, the same as one that doesn't exist. |
| 8 | Block field tampering | Meets | No write endpoint binds a persistence entity. Checked in the code: all 20 write mappings (`@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping`) take either one of 13 dedicated `*Request` records or path IDs only (7 state changes such as suspend or cancel). No `adapter/in/web` package references a JPA entity, repository or `jakarta.persistence` type. None of the 13 request records has a field for the id, status, timestamps, `active`, or computed values such as `kilosTrazados`: the server sets those. |
| 9 | Protect session cookies | Not applicable | There are no session cookies: the token travels in the `Authorization` header and is kept in `sessionStorage`. See [JWT in sessionStorage, with a CSP](#jwt-in-sessionstorage-with-a-csp). |
| 10 | Hash passwords | Meets | BCrypt. Initial staff passwords are generated in the browser (16 symbols, `crypto.getRandomValues`, about 92 bits) and never stored or logged by the frontend. The first admin's password is random. |
| 11 | Add rate limiting to login | Meets | `POST /auth/login`: 5 attempts per minute per client IP, answering `AUTH-004` (429). In memory, so per instance and reset on restart; enough against sustained brute force on a single instance. Wrong email and wrong password get the same `AUTH-001`. |
| 12 | Bot protection | Omitted by design | See [No CAPTCHA](#no-captcha). |
| 13 | Parameterize queries | Meets | Spring Data JPA derived queries and JPQL with bound parameters. No query is built by string concatenation, and there are no native queries. |
| 14 | Validate inputs | Meets | `jakarta.validation` on every request record, plus domain invariants, answered as 4xx with the service's own code. Two inputs used to end in a 500 and now don't: an email with no dot in its domain (`AUTH-005`, 400) and a certificate period whose end is before its start (`RPT-009`, 400, for both issue and draft summary). |
| 15 | Sanitize user content | Partial | Screens: Angular escapes every interpolation, and `frontend-demo/src` has no `innerHTML`, `bypassSecurityTrust*`, `eval` or `document.write` (the only `innerHTML` is a test reading the DOM); the CSP allows scripts from the site itself only. Gap: the certificate CSV export writes the company name as typed, without neutralizing a leading `=`, `+`, `-` or `@`, so a spreadsheet could read it as a formula. Only a signed-in staff member can set that name. |
| 16 | Restrict uploadable files | Not applicable | There are no uploads. See [No uploads](#no-uploads). |
| 17 | Return only the data needed in API responses | Meets | Every endpoint answers a dedicated `*Response` record built by a mapper, never an entity. Login returns only the access token, and the staff account response never includes the password hash. Error bodies carry a code and a fixed message only. Personal fields (DNI, phone, address) are returned only to signed-in staff, as part of the record they belong to. |
| 18 | Add security headers | Meets | APIs (Spring Security defaults, checked on the local stack and in production): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma: no-cache`, `Expires: 0`, `X-XSS-Protection: 0`, and HSTS in production. They send no CSP: they only return JSON, PDF and CSV. The site (`render.yaml`): a strict CSP (`default-src 'none'`, `script-src 'self'`, `connect-src` limited to the four APIs, `frame-ancestors 'none'`), `X-Content-Type-Options`, `Referrer-Policy: no-referrer` and a `Permissions-Policy` that turns off camera, microphone, geolocation and payment. |
| 19 | Force HTTPS | Meets | Render terminates TLS: plain HTTP answers `301` to HTTPS, and the four APIs send `Strict-Transport-Security: max-age=31536000 ; includeSubDomains` in production. The database URL uses `sslmode=require`. |
| 20 | Scan dependencies | Meets | Dependabot opens weekly PRs for Maven, GitHub Actions, the Docker base images and frontend-demo's npm packages, with minor and patch updates grouped. The frontend CI job fails on a known high or critical advisory in runtime dependencies (`npm audit --omit=dev --audit-level=high`). |

## Additional controls

- **No fallback value for a secret.** `JWT_SECRET`, `DB_PASSWORD` and `RABBITMQ_PASSWORD` are read as `${VAR}` with no default (`JwtSecurityConfig` uses `getRequiredProperty`). A service without them fails at startup instead of running with a predictable value.
- **CORS.** An explicit list of origins (`CORS_ALLOWED_ORIGINS`). Empty allows none; `*` is refused at startup.
- **CSRF.** Not applicable: the token travels in the `Authorization` header, never in a cookie, so a cross-site request can't carry it. The APIs are stateless and have CSRF protection off for that reason. The site's CSP also sets `form-action 'none'`.
- **Error handling.** Errors are `ProblemDetail` bodies with a code and a fixed message. No stack trace or exception text reaches the client.
- **Logging of sensitive data.** No password, token or secret is logged, with one deliberate exception: the first admin's random password is logged once at WARN when the staff table is empty, because there is no other channel to hand it over. The frontend doesn't log.
- **Token storage and lifetime.** The JWT lives in the tab's `sessionStorage`, expires after one hour and is never refreshed. See [JWT in sessionStorage, with a CSP](#jwt-in-sessionstorage-with-a-csp).

## Decisions taken on purpose

### No roles

Every signed-in staff member can do everything: issue certificates, create staff accounts, register companies and SIGERSOL figures. The demo has a handful of staff users who all do the same job, so roles would add a model nobody uses yet. What protects the data instead: every business endpoint needs a valid token, and the screens ask for an explicit confirmation before the irreversible or sensitive actions (issuing a certificate, creating an account). Roles are listed as pending in `docs/frontend.md`.

### JWT in sessionStorage, with a CSP

The frontend keeps the token in `sessionStorage`: it lives only in that tab and is gone when the tab closes. JavaScript can read it, so an XSS would expose it. The alternative, an HttpOnly cookie, would need the four APIs on one site (or `SameSite=None` cookies) plus CSRF protection, a different design for a static site that calls four origins. The risk is reduced instead:

- a CSP that allows scripts from the site itself only, with no inline scripts;
- no HTML-injection sinks in the code (checked in this review);
- tokens expire after one hour and are never refreshed.

### No row-level security

Row-level security matters when the browser talks to the database directly, as with Supabase's client libraries. Here the browser only talks to the services, each service reaches the database with its own credentials, and every access rule is applied in the service. There is a single tenant, so there are no rows to hide from one user to another.

### No column encryption

The most sensitive data stored is personal contact data: names, DNI, phone numbers, neighbours' addresses, staff emails. Passwords are stored as BCrypt hashes, never in clear. The rest is protected by access control and by TLS to the database, not by column-level encryption. That would need key management (where the key lives, how it rotates) that a free-tier demo can't host properly, and a key kept next to the data adds little.

### No CAPTCHA

There is no public sign-up or public form. The only public business endpoint is staff login, which is rate limited per IP, answers wrong email and wrong password with the same error (`AUTH-001`), and is backed by BCrypt. A CAPTCHA would add a third-party script and its domain to the CSP.

### No uploads

No endpoint accepts a file, and no screen offers one. SIGERSOL figures are typed in, and certificates are generated by the service (PDF and CSV downloads only). There is no file type to check, no size to cap, no storage to protect.

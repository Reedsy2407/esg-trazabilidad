# Performance: what was measured, what changed (M6c)

Render's free plan gives each API service 0.1 CPU and 512 MB, and puts it to sleep after 15 minutes
without traffic. Most of what looks like "the app is slow" is that plan, not the code. This page says
what was measured, what was changed because of it, and what was deliberately left alone.

## How to measure (one line)

Pull a service's log with the Render CLI (`docs/render-logs.md`) or copy it from the dashboard, and run `scripts/perf-summary.sh <file>`
(or `docker logs <container> 2>&1 | scripts/perf-summary.sh` locally): one row per service, method and
route template with count, p50, p95, max and the average database time.

## What the services now log

Fixed shape, logfmt, no personal data (no ids, e-mails, tokens, query strings or bind values):

| Event | Level | Fields |
|---|---|---|
| `event=http_request` | INFO; ERROR when the answer is 5xx or an exception escaped | `service method route status durationMs dbMs poolMs dbQueries` (`route` is the path template, `UNMATCHED` when no controller matched) |
| `event=slow_query` | INFO | `service durationMs sql` (statement shape only: literals and numbers become `?`, 160 characters at most). Threshold: `esg.observability.slow-query-ms`, 150 by default |
| `event=password_hash` | INFO | `service durationMs` (auth-service, the bcrypt step of a login) |
| `event=warmup` | INFO; ERROR when a step failed | `service step durationMs` |

Health probes (`/actuator/**`) are not logged. The code is `shared-kernel/.../observability` and `.../warmup`.

## Measurements (before / after)

Local, same limits as Render: `docker run --cpus=0.1 --memory=512m`, the production JVM flags, Postgres
in a local container (so database network latency is not in these numbers, see "Production" below).

| What | Before | After | Why |
|---|---|---|---|
| reporting: first `GET /tracked-companies` after the service starts | 2,317 ms (DB 90 ms) | 340 ms | start-up warm-up runs the request path, the JWT chain and the first Hibernate query once |
| reporting: the same request, warm | 70 to 170 ms | 70 to 170 ms | unchanged (2 queries, 2 to 3 ms each with 200 rows) |
| auth: first login after start | 2,071 ms | 565 ms | warm-up posts a login with an address that has no account, runs one bcrypt comparison and the e-mail lookup |
| auth: later logins | 680 ms | 600 ms | bcrypt, strength 10, is 500 to 600 ms at 0.1 CPU; the transaction that held a pooled connection during it is gone |
| start-up until liveness answers | 71 s (auth), 110 s (reporting) | 66 s with lazy initialisation, discarded | CPU-bound at 0.1 CPU; 7 % is not worth the risk |

The warm-up itself costs about 5 to 6 seconds after the service is ready, on its own thread, so readiness
never waits for it.

### Production (public endpoints only, from Lima)

| What | Time |
|---|---|
| Liveness of an awake service | 0.21 to 0.23 s (this is the network: Lima to Render, plus TLS) |
| Aggregate `/actuator/health` (includes a database check) | 0.25 to 0.31 s |
| Login with an unknown e-mail (one query), warm | 0.28 s |
| The same, first one after an idle spell | 2.1 s |
| A sleeping service | no answer in 90 s the first time, 15 to 35 s more the second: 105 to 125 s in total |

Reading: the database is not far (a query adds about 60 ms), so Render and Neon are not on different
continents. The 2.1 s first call after idling is the cold path (JIT, first pooled connection, and Neon
resuming its compute after 5 idle minutes); the start-up warm-up now covers the first two, and
`dbMs`/`poolMs` of the first request after an idle spell will say whether the third matters.

## Changed because the data said so

- Start-up warm-up (`esg.warmup.enabled`, on in the Docker images, off in tests): the service's own health,
  a request with a bogus bearer token (JWT decoder and security chain), and one task per service.
- auth-service `login` is no longer `@Transactional`: it was holding a pooled database connection
  during the bcrypt comparison.
- The login page pings the four services in the background and says "Despertando servicios…" if they
  take longer than 1.5 s. The form works meanwhile. (Landing on `/login` directly skipped the entry
  screen that wakes them.)

## Left alone on purpose

- **bcrypt strength 10.** Lowering it to 8 would make a login about 0.1 to 0.2 s at 0.1 CPU, but 10 is the
  floor OWASP recommends and the passwords protect certificate issuing.
- **Connection pool size, indexes, query shapes.** Warm queries take 2 to 3 ms; nothing in the data points
  at them. The `slow_query` event will show it if that changes with real volume.
- **Regions.** Render is in Oregon and the database is about 60 ms from it. A move to Virginia would save
  perhaps 80 ms per request for users in Lima but means recreating all four services and the database.
- **Lazy initialisation:** 7 % faster start-up, discarded.
- **Keeping services awake.** A scheduled ping would remove the 2 minute wake-up but the free plan's 750
  monthly hours do not cover four services around the clock. That is a decision about plan and hours, not code.

## Not measured

Authenticated production requests (company registration, the two lists): there are no credentials here.
With the logs above in place, run `scripts/perf-summary.sh` on a Render log after a normal session and
look at `p95` and `db avg` per route. Company registration also waits for SUNAT (Decolecta) when the RUC
is not in the 24 h cache; that time is outside this system.

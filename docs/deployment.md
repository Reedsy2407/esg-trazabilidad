# Deployment runbook

How to put the four services (`recycler-service`, `collection-service`, `reporting-service`, `auth-service`) on a public URL from zero, on free tiers only:

- **Render** runs the services: four free web services, built from each service's `Dockerfile`.
- **Neon** is the database: one free Postgres, shared by all four services.
- **CloudAMQP** is the message broker: one free "Little Lemur" RabbitMQ.

Everything below uses `<placeholders>`. **Never put a real host, user, password, token or connection string in this file, in `render.yaml`, or anywhere else in the repo.** Real values go in the Render dashboard only.

Once it is set up, deploying is automatic. Every push to `main` whose CI (`.github/workflows/ci.yml`, `mvn -B verify`) passes is deployed by Render (`autoDeployTrigger: checksPass`). A service is only rebuilt when its own module, `shared-kernel`, the root `pom.xml` or `.dockerignore` changed (`buildFilter` in `render.yaml`).

## 0. Choose one region for everything

The free Render region closest to Lima is in the US either way. The Blueprint uses **`oregon`**. Create Neon and CloudAMQP in the **same AWS US region** so that every database and broker round trip stays inside one region: for Oregon, that's AWS `us-west-2`.

The three providers name regions differently. At account creation, **confirm on each provider's own region list** that the one you pick is really the same AWS region. For Oregon, Neon lists it as "AWS US West 2 (Oregon)" and CloudAMQP as "Amazon Web Services US-West-2 (Oregon)". If Render's region changes, change `region:` in `render.yaml` for all four services, and pick Neon and CloudAMQP to match.

## 1. Postgres on Neon

1. Create a Neon project (free plan) in the region from step 0.
2. Use the database Neon creates by default, `neondb`. No need to create another one. Note its role (user) and password.
3. Copy the **direct** connection host, **not** the pooled one (whose host contains `-pooler`). In Neon's **Connect** panel, the **Connection pooling** toggle is **on by default**. Switch it off to see the direct host. Liquibase's lock and the services' session-level JDBC use don't belong behind a transaction-mode pooler.
4. Build the JDBC URL. Neon requires TLS:

   ```
   jdbc:postgresql://<neon-direct-host>/<database>?sslmode=require
   ```

   `<database>` is `neondb` unless you created another one.

No manual SQL is needed. Each service's Liquibase changelog creates its own tables on first start, including `reporting-service`'s `CREATE EXTENSION IF NOT EXISTS btree_gist`.

## 2. RabbitMQ on CloudAMQP

1. A new CloudAMQP account must create a **Team** first: a name, accepting the terms of service, and a GDPR question. Only then can it create an instance. Create one on the free **Little Lemur** plan, in the region from step 0.
2. From the instance details, note the host, user, password and **vhost**. On the shared plans the vhost is usually the same string as the user.
3. The services connect over TLS (`amqps`) on port **5671**.

Only `recycler-service`, `collection-service` and `reporting-service` use the broker; `auth-service` has none.

## 3. Render account and the `esg-shared` environment group

1. Create a Render account and connect it to the GitHub repository.
2. In the dashboard, create an **Environment Group** named exactly **`esg-shared`**. Create it **by hand, not in `render.yaml`**: Render ignores `sync: false` inside a Blueprint-declared group, so secrets there would be exposed to the Blueprint's syncs. All four services reference it with `fromGroup: esg-shared`, so each value is typed exactly once. That also means `JWT_SECRET` is one value, identical in every service.

| Key | Value |
|---|---|
| `JWT_SECRET` | A fresh random secret of **at least 32 bytes**, e.g. the output of `openssl rand -base64 48`. auth-service signs tokens with it and the other three validate them with it. Never reuse the local `.env.local` value. |
| `DB_URL` | `jdbc:postgresql://<neon-direct-host>/<database>?sslmode=require` |
| `DB_USERNAME` | `<neon-role>` |
| `DB_PASSWORD` | `<neon-password>` |
| `RABBITMQ_HOST` | `<cloudamqp-host>` |
| `RABBITMQ_PORT` | `5671` |
| `RABBITMQ_USER` | `<cloudamqp-user>` |
| `RABBITMQ_PASSWORD` | `<cloudamqp-password>` |
| `RABBITMQ_VHOST` | `<cloudamqp-vhost>` |
| `RABBITMQ_SSL_ENABLED` | `true` |

Tip: the group's creation screen has an **Add from .env** button. It accepts all ten keys pasted at once as `KEY=VALUE` lines, which is faster than typing them one by one. Paste straight from a password manager, and don't save that text in a file inside the repo.

`CORS_ALLOWED_ORIGINS` is optional and can be added later. It lists the browser origins allowed to call the APIs, comma separated, e.g. the frontend's `https://<frontend-host>`. Left unset, no origin is allowed. `*` makes every service fail at startup on purpose.

`PORT` is **not** set: Render injects it, and each service reads `server.port: ${PORT:808x}`.

A missing `JWT_SECRET` or `DB_PASSWORD` makes any service fail at startup on purpose, because neither has a default anywhere. A missing `RABBITMQ_PASSWORD` does the same to the three AMQP services; auth-service doesn't read it.

## 4. Apply the Blueprint

1. In Render, go to **New → Blueprint**, pick the repository and branch `main`, and give the Blueprint a **Blueprint Name**. That's any label for the Blueprint itself, not a service name, e.g. `esg-trazabilidad`. Render reads `render.yaml` and previews four web services: `esg-recycler-service`, `esg-collection-service`, `esg-reporting-service` and `esg-auth-service`. The preview is also the final validation of `render.yaml`.
2. When prompted for **`ADMIN_BOOTSTRAP_EMAIL`** (auth-service only, `sync: false`), enter the email of the first staff account. It is typed once here; later Blueprint syncs leave it alone.
3. Apply. Each service builds its image from the repo root (`dockerContext: .`) and starts. Render marks it live once `GET /actuator/health/liveness` answers 200.

**On the very first deploy, one service may fail its Liquibase step.** All four services share Neon's `public` schema, and with it Liquibase's `databasechangelog` and `databasechangeloglock` tables. The lock table itself (and its single row) has to be created **before** any service can take the lock. So when several services start at the same moment against an empty schema, they can race on creating it, and the loser fails to start. The fix is a **Manual Deploy** of just that service: the tables exist by then, so it waits on the lock like the others. This can only happen while the schema is empty, so it comes back only with a new or reset Neon database.

Why liveness and not `/actuator/health`: when RabbitMQ or Postgres is down, the aggregate health returns 503, and a restart can't fix a provider-side outage. Liveness only asks whether the JVM itself is healthy. The aggregate `/actuator/health` stays public for diagnosis.

## 5. The bootstrap password

On its first start against an empty `staff_user` table, auth-service creates the `ADMIN_BOOTSTRAP_EMAIL` account with a **random password, logged once at WARN level**, in a line of the form `email=<email> password=<password>`. Read it in **esg-auth-service → Logs** right after the first deploy, and store it in a password manager **immediately**. The window is narrow: in the real go-live, the line was no longer in the logs after a few restarts and redeploys, and the account had to be reset (below).

It is never logged again, and there is no change-password endpoint yet. The bootstrap only runs when `staff_user` is completely empty. So if the password is lost, recovering it means deleting every row of `staff_user` in Neon's SQL editor and restarting esg-auth-service, which logs a new password once. That also removes every other staff account created since.

## 5b. Verify a deploy

`scripts/verify-deploy.sh [sha]` (bash, needs `gh` logged in with read access to Actions) waits for CI on the commit, then for each service to serve the commit it should. It reads the deployed commit from the public `GET /actuator/info` (the static frontend: `GET /version.json`) (`{"commit": "<sha>"}`, from Render's `RENDER_GIT_COMMIT`). The expected commit per service is the newest one that touched its `buildFilter` paths, so a service a commit didn't touch isn't expected to redeploy. It allows up to 10 minutes for CI and up to 10 minutes per service, which covers the build and a cold start.

## 6. Smoke check

Run this against the public URLs, `https://<service>.onrender.com`. The commands are for **bash** (item 3's `read -s` isn't POSIX `sh`). Set these first, in your own shell only:

```bash
AUTH=https://<esg-auth-service>.onrender.com
RECYCLER=https://<esg-recycler-service>.onrender.com
COLLECTION=https://<esg-collection-service>.onrender.com
REPORTING=https://<esg-reporting-service>.onrender.com
```

The first call to a service that is asleep waits for its cold start (see Known limitations); that is not a failure.

1. **Public endpoints on all four answer without a token.** Liveness and Swagger UI should return 200:

   ```bash
   for u in $AUTH $RECYCLER $COLLECTION $REPORTING; do
     curl -s -o /dev/null -w "$u liveness=%{http_code} " $u/actuator/health/liveness
     curl -s -o /dev/null -w "swagger=%{http_code}\n" $u/swagger-ui/index.html
   done
   ```

2. **Business endpoints refuse a request without a token.** Expect `401` with `"code":"AUTH-000"`:

   ```bash
   curl -s -w " %{http_code}\n" $RECYCLER/associations
   curl -s -w " %{http_code}\n" $COLLECTION/companies
   curl -s -w " %{http_code}\n" $REPORTING/tracked-companies
   ```

3. **Log in with the bootstrap account.** Type the password at the prompt, so it stays out of shell history:

   ```bash
   read -rs -p "bootstrap password: " PW; echo
   TOKEN=$(curl -s -H 'Content-Type: application/json' \
     -d "{\"email\":\"<bootstrap-email>\",\"password\":\"$PW\"}" $AUTH/auth/login \
     | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'); unset PW
   ```

4. **The same token works, unmodified, on the other three services.** Expect 200 on each:

   ```bash
   for u in $RECYCLER/associations $COLLECTION/companies $REPORTING/tracked-companies; do
     curl -s -o /dev/null -w "$u %{http_code}\n" -H "Authorization: Bearer $TOKEN" $u
   done
   ```

5. **An event travels through CloudAMQP.** A collection registered on collection-service increases the association's kilos on recycler-service:

   ```bash
   H="Authorization: Bearer $TOKEN"
   ASSOC=$(curl -s -H "$H" -H 'Content-Type: application/json' \
     -d '{"name":"Asociacion Smoke","ruc":"<11-digit-ruc>"}' $RECYCLER/associations \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
   NEIGHBOR=$(curl -s -H "$H" -H 'Content-Type: application/json' \
     -d '{"fullName":"Vecino Smoke","address":"<address>"}' $COLLECTION/neighbors \
     | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
   curl -s -H "$H" -H 'Content-Type: application/json' \
     -d "{\"associationId\":\"$ASSOC\",\"collectionDate\":\"<yyyy-mm-dd>\",\"weightKg\":12.5}" \
     $COLLECTION/neighbors/$NEIGHBOR/collection-records
   echo "$ASSOC"
   ```

   Check the result in **Neon's SQL editor**. The association's running total isn't part of any HTTP response (`AssociationResponse` doesn't expose it). Within a few seconds (the outbox dispatcher polls every 5 s by default), it goes up by 12.5:

   ```sql
   select total_kilos_collected from association where id = '<association-id>';
   ```

6. **Login is rate limited.** The limit is 5 attempts per minute per client IP, and it counts **every** attempt, including the successful login of item 3. Tokens come back gradually, one about every 12 s, not all at once after a minute. So **wait at least a minute after item 3**, then run seven rapid wrong-password logins from one machine. Expect `401 AUTH-001` for the first five and `429 AUTH-004` with a `Retry-After` header from about the sixth. On a slow cold instance a token may be refilled mid-loop, which shifts the first 429 by one attempt.

   ```bash
   for i in 1 2 3 4 5 6 7; do
     curl -s -D /tmp/h -o /tmp/b -H 'Content-Type: application/json' \
       -d '{"email":"<bootstrap-email>","password":"wrong"}' $AUTH/auth/login
     echo "$i: $(head -1 /tmp/h | tr -d '\r') $(grep -o '"code":"[^"]*"' /tmp/b) $(grep -i '^retry-after' /tmp/h | tr -d '\r')"
   done
   ```

## 7. The frontend (static site) and CORS

`render.yaml` declares a fifth resource, **`esg-frontend`**, a static site built from `frontend-demo/` (`npm ci && npm run build`, published from `frontend-demo/dist/esg-frontend/browser`). It has no secrets and no environment group. It deploys only after CI passes (`autoDeployTrigger: checksPass`), and only when `frontend-demo/**` changes. Its build writes `/version.json` (`{"commit": "<sha>"}`) so that `scripts/verify-deploy.sh` checks it like the APIs.

The browser calls the four APIs directly, cross-origin, so they must allow the site's origin:

1. **Create the site.** The Blueprint creates it on its next sync after `render.yaml` reaches `main`: automatically if the Blueprint's Auto Sync is on, otherwise **Blueprints → esg-trazabilidad → Manual Sync**. The preview should show exactly one new resource, `esg-frontend` (static site), and no change to the four services.
2. **Read its URL** in **esg-frontend → Settings**. It is `https://esg-frontend-egcf.onrender.com`: the bare name was taken, so Render added the `-egcf` suffix. If the site is ever recreated with another suffix, update the `frontend` line in `scripts/services.txt` and the origin below.
3. **Allow that origin.** In **Environment Groups → esg-shared**, add `CORS_ALLOWED_ORIGINS` = `https://esg-frontend-egcf.onrender.com` (scheme and host only: no path, no trailing slash, never `*`, which fails startup on purpose). Saving the group redeploys the four services once.
4. **Verify.** Saving the group redeploys the services *at the same commit*, so `scripts/verify-deploy.sh` alone can't tell old instances from new ones. First wait until all four show **Deploy live** for that redeploy, then:
   - CORS, one preflight per service (each must print `access-control-allow-origin: https://esg-frontend-egcf.onrender.com`):
     ```bash
     for s in auth recycler collection reporting; do
       echo "$s: $(curl -si -X OPTIONS https://esg-$s-service.onrender.com/actuator/health \
         -H 'Origin: https://esg-frontend-egcf.onrender.com' -H 'Access-Control-Request-Method: GET' \
         -H 'Access-Control-Request-Headers: authorization' | grep -i '^access-control-allow-origin' | tr -d '\r')"
     done
     ```
   - The site's headers on the root, a deep link and a bundle (each must show the `content-security-policy` and `cache-control: no-cache`):
     ```bash
     F=https://esg-frontend-egcf.onrender.com
     for p in / /recojos/nuevo "/$(curl -s $F/ | grep -o 'main-[A-Z0-9]*\.js' | head -1)"; do
       echo "== $p"; curl -sI "$F$p" | grep -iE '^(HTTP|content-security-policy|cache-control|content-type)'
     done
     ```
   - `scripts/verify-deploy.sh`: five lines, all OK.
   - In the browser: open the site, wait for "Preparando el sistema" to finish, sign in, open a company and a certificate, download its PDF, and register a collection.

The site's response headers (in `render.yaml`) include a Content-Security-Policy: scripts only from the site itself, API calls only to the four services, no framing. Critical-CSS inlining is off in `angular.json` because its `onload=` handler would need inline scripts. A new API host has to be added to `connect-src` before the frontend can call it.

## Known limitations of the free tiers

- **Spin-down and cold start.** A free Render service sleeps after 15 minutes without inbound HTTP. The next request waits for a full container and Spring Boot start on 0.1 CPU. **Measured on Render (2026-09-28): 95–126 s** for that first request. Render's proxy holds the connection open for that whole time and then answers 200; it does not time out. Once a service is awake, `/actuator/health` answers in 0.27–0.57 s. Class Data Sharing (CDS) or AOT could shorten the cold start, but probably not below a minute; that's an unprioritized follow-up.
- **While a service sleeps, its background jobs don't run.** That means the outbox dispatcher, and recycler-service's certification-expiry scan. Events already published wait in CloudAMQP's durable queues until the consumer wakes. An outbox row written just before a service slept is dispatched when it next wakes. This is eventual consistency, not data loss.
- **750 free instance hours per workspace per month**, shared by all four services. A sleeping service uses none.
- **CloudAMQP deletes a queue that nobody has consumed from for 28 days.** The services redeclare their queues on the next start, but messages that were sitting in a deleted queue are lost. Acceptable for a demo; revisit before carrying real data.
- **One auth-service instance.** The login rate limit is in memory and resets when that instance restarts or sleeps.
- **The Swagger UI is public on purpose.** It is the demo's front door and only documents the API; every business call behind it still needs a token.

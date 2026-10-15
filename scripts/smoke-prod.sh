#!/usr/bin/env bash
# Production smoke test with a throwaway "smoke-" data set: logs in as a test staff user,
# registers a neighbor and one
# collection record, times each call, reads the services' own timing logs for that window with
# `render logs` + scripts/perf-summary.sh, and cleans up everything it created.
#
#   scripts/smoke-prod.sh
#
# Credentials: the DPAPI-encrypted file written by scripts/smoke-cred.ps1 (outside the repo), or
# SMOKE_EMAIL / SMOKE_PASSWORD when both are set. Never printed. The test staff user must already
# exist: scripts/smoke-create-user.mjs creates it through the real screen (a staff user cannot be
# deleted through the API, so it is reused; its e-mail must start with "smoke-").
# Optional: SMOKE_COMPANY=1 (also register a company: spends one provider lookup), SMOKE_ASSOCIATION_ID (default: the first active association), SMOKE_KEEP=1 (skip
# the clean-up, to look at the data), SMOKE_LOGS=0 (skip the Render log step).
#
# What stays behind and why: a collection record can never be edited or deleted, so the smoke
# neighbor that received one is DEACTIVATED (reason "smoke-cleanup"), not deleted. The company
# is deleted (it has no certificate). Everything is named "smoke-<run id>".
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
services_file="$here/services.txt"
# Credentials: SMOKE_EMAIL / SMOKE_PASSWORD if set, else the DPAPI-encrypted file read by
# scripts/smoke-cred.ps1 (%LOCALAPPDATA%\esg-smoke\cred.xml). Read into variables, never printed.
if [ -z "${SMOKE_EMAIL:-}" ] || [ -z "${SMOKE_PASSWORD:-}" ]; then
  cred_ps1="$(cygpath -w "$here/smoke-cred.ps1" 2>/dev/null || echo "$here/smoke-cred.ps1")"
  SMOKE_EMAIL="$(powershell -NoProfile -ExecutionPolicy Bypass -File "$cred_ps1" -Mode Get -Field Email </dev/null 2>/dev/null | tr -d '\r')"
  SMOKE_PASSWORD="$(powershell -NoProfile -ExecutionPolicy Bypass -File "$cred_ps1" -Mode Get -Field Password </dev/null 2>/dev/null | tr -d '\r')"
fi
[ -n "${SMOKE_EMAIL:-}" ] && [ -n "${SMOKE_PASSWORD:-}" ] || { echo "no smoke credentials: set SMOKE_EMAIL/SMOKE_PASSWORD or create the account (scripts/smoke-create-user.mjs)" >&2; exit 2; }
export SMOKE_EMAIL SMOKE_PASSWORD
case "$SMOKE_EMAIL" in smoke-*) ;; *) echo "SMOKE_EMAIL must start with smoke-" >&2; exit 2 ;; esac

base() { awk -v n="$1" '$1 == n { print $2 }' "$services_file"; }
AUTH="$(base auth)"; REC="$(base recycler)"; COL="$(base collection)"; REP="$(base reporting)"
run="smoke-$(date +%Y%m%d%H%M%S)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
t_start="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
failures=0
created_company=""; created_neighbor=""; made_record=0
token=""

json() { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const v=JSON.parse(s);const r=process.argv[1].split(".").reduce((a,k)=>a==null?a:a[k],v);process.stdout.write(r==null?"":String(r))}catch(e){}})' "$1"; }

# call <label> <method> <url> [json body]  -> prints "label: status in N ms"; body in $tmp/body
call() {
  local label="$1" method="$2" url="$3" body="${4:-}" out status t0 ms
  t0=$(date +%s%3N)
  if [ -n "$body" ]; then
    status="$(curl -s -m 150 -o "$tmp/body" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body")"
  else
    status="$(curl -s -m 150 -o "$tmp/body" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $token")"
  fi
  ms=$(( $(date +%s%3N) - t0 ))
  printf '%-34s %s  %5d ms\n' "$label" "$status" "$ms"
  last_status="$status"
}
need() { # expected-prefix
  case "$last_status" in $1*) ;; *) echo "  unexpected status $last_status: $(head -c 300 "$tmp/body")" >&2; failures=$((failures + 1)) ;; esac
}

# --- 0. wake the services (a sleeping free-plan service takes up to ~2 minutes) ----------------
echo "== waking services"
for u in "$AUTH" "$REC" "$COL" "$REP"; do
  for _ in $(seq 1 15); do
    code="$(curl -s -o /dev/null -m 20 -w '%{http_code}' "$u/actuator/health/liveness")"
    [ "$code" = 200 ] && break
    sleep 10
  done
  [ "$code" = 200 ] || { echo "service not awake: ${u##*//} (liveness $code)" >&2; exit 1; }
done

# --- 1. login --------------------------------------------------------------------------------
echo "== $run"
t0=$(date +%s%3N)
status="$(curl -s -m 150 -o "$tmp/body" -w '%{http_code}' -X POST "$AUTH/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(node -e 'process.stdout.write(JSON.stringify({email:process.env.SMOKE_EMAIL,password:process.env.SMOKE_PASSWORD}))')")"
printf '%-34s %s  %5d ms\n' "login" "$status" "$(( $(date +%s%3N) - t0 ))"
[ "$status" = 200 ] || { echo "login failed ($status)" >&2; exit 1; }
token="$(json accessToken <"$tmp/body")"
[ -n "$token" ] || token="$(json token <"$tmp/body")"
[ -n "$token" ] || { echo "login answered 200 without a token" >&2; exit 1; }

# --- 2. association (existing, active) --------------------------------------------------------
assoc="${SMOKE_ASSOCIATION_ID:-}"
if [ -z "$assoc" ]; then
  call "list associations" GET "$REC/associations?size=50"
  need 200
  assoc="$(node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const v=JSON.parse(s);const l=v.content||v;const a=l.find(x=>/ACTIVE|active/.test(String(x.status||x.state||"ACTIVE")))||l[0];process.stdout.write(a?a.id:"")}catch(e){}})' <"$tmp/body")"
fi
[ -n "$assoc" ] || { echo "no association to attach the company to (set SMOKE_ASSOCIATION_ID)" >&2; exit 1; }

# --- 3. company -------------------------------------------------------------------------------
# Off by default: the server only accepts the manual (unverified) path after asking SUNAT through
# the provider and getting no answer (RPT-014 otherwise), so even this path spends one lookup of
# the provider's quota. SMOKE_COMPANY=1 turns it on (it then works only while the provider is down).
if [ "${SMOKE_COMPANY:-0}" = 1 ]; then
  ruc="$(node -e '
const w=[5,4,3,2,7,6,5,4,3,2];let d="20"+String(Math.floor(Math.random()*1e8)).padStart(8,"0");
const s=d.split("").reduce((a,c,i)=>a+Number(c)*w[i],0);let c=11-(s%11);if(c===10)c=0;if(c===11)c=1;
process.stdout.write(d+c)')"
  call "register company (unverified)" POST "$REP/tracked-companies/unverified"     "$(node -e 'process.stdout.write(JSON.stringify({associationId:process.argv[1],ruc:process.argv[2],name:process.argv[3],contactName:"smoke",contactEmail:"smoke@example.org"}))' "$assoc" "$ruc" "$run")"
  need 2
  created_company="$(json id <"$tmp/body")"
fi
call "list companies" GET "$REP/tracked-companies?size=20"; need 200

# --- 4. neighbor + collection record ----------------------------------------------------------
call "register neighbor" POST "$COL/neighbors" \
  "$(node -e 'process.stdout.write(JSON.stringify({fullName:process.argv[1],address:"smoke test",district:"Lima",districtUbigeo:"150101"}))' "$run")"
need 2
created_neighbor="$(json id <"$tmp/body")"
call "list neighbors" GET "$COL/neighbors?size=20"; need 200
if [ -n "$created_neighbor" ]; then
  call "register collection record" POST "$COL/neighbors/$created_neighbor/collection-records" \
    "$(node -e 'process.stdout.write(JSON.stringify({associationId:process.argv[1],collectionDate:new Date().toISOString().slice(0,10),weightKg:1}))' "$assoc")"
  need 2
  [ "${last_status#2}" != "$last_status" ] && made_record=1
fi

# --- 5. clean up what carries the prefix ------------------------------------------------------
if [ "${SMOKE_KEEP:-0}" != 1 ]; then
  echo "== clean-up"
  if [ -n "$created_company" ]; then
    call "delete company" DELETE "$REP/tracked-companies/$created_company" '{"reason":"smoke-cleanup"}'; need 2
  fi
  if [ -n "$created_neighbor" ]; then
    if [ "$made_record" = 1 ]; then
      call "deactivate neighbor (has record)" PATCH "$COL/neighbors/$created_neighbor/deactivate" '{"reason":"smoke-cleanup"}'; need 2
      echo "  left behind: neighbor '$run' (inactive) with one 1 kg collection record, it cannot be deleted"
    else
      call "delete neighbor" DELETE "$COL/neighbors/$created_neighbor" '{"reason":"smoke-cleanup"}'; need 2
    fi
  fi
fi

# --- 6. the services' own timings for this window ---------------------------------------------
if [ "${SMOKE_LOGS:-1}" != 0 ]; then
  render_bin="$(command -v render || ls "$LOCALAPPDATA"/Microsoft/WinGet/Packages/Render.CLI_*/render.exe 2>/dev/null | head -1)"
  if [ -n "$render_bin" ]; then
    echo "== server-side timings (render logs + perf-summary)"
    sleep 20 # logs reach the Render API a few seconds late
    t_end="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    timeout 90 "$render_bin" logs --resources srv-dasr288473hc739jfjpg,srv-dasr288473hc739jfjtg,srv-dasr288473hc739jfju0,srv-dasr288473hc739jfjug \
      --start "$t_start" --end "$t_end" --limit 1000 --output json </dev/null >"$tmp/logs.json" 2>/dev/null
    grep '"message"' "$tmp/logs.json" | "$here/perf-summary.sh" || true
  else
    echo "(render CLI not found: skipping the server-side timings)"
  fi
fi

echo "== done: $failures unexpected status(es)"
[ "$failures" -eq 0 ]

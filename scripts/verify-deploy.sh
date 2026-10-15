#!/usr/bin/env bash
# Verifies a deploy end to end: CI on a commit, then each Render service
# actually serving the commit it should.
#
#   scripts/verify-deploy.sh [sha]      (default: origin/main)
#
# 0. Checks that scripts/services.txt's buildFilter paths still match
#    render.yaml, and refuses to run if they drifted.
# 1. Waits up to 10 min for every GitHub Actions run on the commit to finish
#    (read-only: `gh run list` / `gh run view`). A failed run fails the check
#    and names the failed jobs.
# 2. For each service in scripts/services.txt, in parallel: the expected commit
#    is the newest one up to <sha> that touched that service's buildFilter
#    paths, since Render doesn't redeploy a service a commit doesn't touch.
#    Polls GET /actuator/info every 10 s until its "commit" matches (by
#    prefix, or any commit whose buildFilter paths are identical to <sha>'s)
#    and liveness answers 200, up to 10 min per service: that covers Render's
#    build plus a cold start of up to ~2 min.
#
# Prints one line per service and a final CI line; exits non-zero if anything
# fails. When a service is still on an older commit that <sha> didn't touch,
# it suggests a Manual Deploy (see docs/deployment.md, 5b). It never prints
# tokens or environment variables.
set -uo pipefail

REPO="${VERIFY_REPO:-Reedsy2407/esg-trazabilidad}"
TOTAL_BUDGET=720   # the whole wait (CI + services) never goes past 12 minutes
CI_TIMEOUT=$TOTAL_BUDGET
SERVICE_TIMEOUT=$TOTAL_BUDGET
INTERVAL=10
PROGRESS_EVERY=60
script_start=$SECONDS

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"
services_file="$here/services.txt"
gh_bin="$(command -v gh || echo "/c/Program Files/GitHub CLI/gh.exe")"

# --- 0. services.txt vs render.yaml ------------------------------------------
# "<name> <sorted paths>" per service, from each source.
filters_from_services() {
  while read -r name _url paths; do
    case "$name" in ''|'#'*) continue ;; esac
    echo "$name $(tr ' ' '\n' <<<"$paths" | grep -v '^$' | sort | tr '\n' ' ')"
  done <"$services_file" | sort
}
filters_from_render_yaml() {
  awk '
    /^[[:space:]]*(- type:|name:)/ { name = ""; inpaths = 0 }
    /^[[:space:]]*name:[[:space:]]*esg-[a-z-]*[[:space:]]*$/ {
      name = $2; sub(/^esg-/, "", name); sub(/-service$/, "", name); next }
    /^[[:space:]]*paths:[[:space:]]*$/ { inpaths = 1; next }
    inpaths && name != "" && /^[[:space:]]*-[[:space:]]/ { p = $0; sub(/^[[:space:]]*-[[:space:]]*/, "", p); print name, p; next }
    { inpaths = 0 }
  ' "$root/render.yaml" | sort -k1,1 -k2,2 | awk '
    $1 != cur { if (cur != "") print cur, line; cur = $1; line = "" }
    { line = line $2 " " }
    END { if (cur != "") print cur, line }' | sort
}
if ! diff <(filters_from_services) <(filters_from_render_yaml) >/dev/null; then
  echo "FAIL: scripts/services.txt buildFilter paths no longer match render.yaml:" >&2
  diff <(filters_from_services) <(filters_from_render_yaml) | sed 's/^/  /' >&2
  exit 2
fi

if [ $# -ge 1 ]; then
  sha="$(git -C "$root" rev-parse --verify --quiet "$1^{commit}")" || { echo "unknown commit: $1" >&2; exit 2; }
else
  git -C "$root" fetch -q origin main 2>/dev/null || echo "warning: git fetch failed, using the local origin/main" >&2
  sha="$(git -C "$root" rev-parse origin/main)"
fi
short="${sha:0:7}"

# --- 1. CI --------------------------------------------------------------------
ci_line=""
ci_ok=1
ci_end_epoch=0
start=$SECONDS
last_progress=$SECONDS
while :; do
  runs="$("$gh_bin" run list -R "$REPO" --commit "$sha" \
    --json databaseId,status,conclusion,updatedAt --jq '.[] | "\(.databaseId) \(.status) \(.conclusion) \(.updatedAt)"' 2>/dev/null)"
  if [ -n "$runs" ] && ! grep -qv " completed " <<<"$runs"; then
    ci_end_epoch="$(awk '{print $4}' <<<"$runs" | sort | tail -1 | xargs -I{} date -d {} +%s 2>/dev/null || echo 0)"
    failed_ids="$(awk '$3 != "success" && $3 != "skipped" && $3 != "neutral" {print $1}' <<<"$runs")"
    if [ -z "$failed_ids" ]; then
      ci_line="CI   $short OK ($(wc -l <<<"$runs" | tr -d ' ') run(s) green, waited $((SECONDS - start))s)"
    else
      ci_ok=0
      jobs=""
      for id in $failed_ids; do
        j="$("$gh_bin" run view "$id" -R "$REPO" --json jobs \
          --jq '[.jobs[] | select(.conclusion != "success" and .conclusion != "skipped") | "\(.name) (\(.conclusion))"] | join(", ")' 2>/dev/null)"
        jobs="${jobs:+$jobs; }run $id: ${j:-unknown job}"
      done
      ci_line="CI   $short FAIL: $jobs"
    fi
    break
  fi
  if [ $((SECONDS - start)) -ge $CI_TIMEOUT ]; then
    ci_ok=0
    if [ -z "$runs" ]; then
      ci_line="CI   $short FAIL: no CI run found for this commit after ${CI_TIMEOUT}s"
    else
      ci_line="CI   $short FAIL: still running after ${CI_TIMEOUT}s"
    fi
    break
  fi
  if [ $((SECONDS - last_progress)) -ge $PROGRESS_EVERY ]; then
    echo "progress: waiting for CI on $short, $((SECONDS - script_start))s of ${TOTAL_BUDGET}s"
    last_progress=$SECONDS
  fi
  sleep "$INTERVAL"
done
# What is left of the 12 minutes is the services' wait.
SERVICE_TIMEOUT=$((TOTAL_BUDGET - (SECONDS - script_start)))
[ "$SERVICE_TIMEOUT" -ge 60 ] || SERVICE_TIMEOUT=60

# --- 2. Deployed commit + liveness ------------------------------------------------
same_commit() { # served expected -- prefix match either way, at least 7 chars
  [ -n "$2" ] && [ "${#1}" -ge 7 ] && { [[ "$2" == "$1"* ]] || [[ "$1" == "$2"* ]]; }
}

# Also fine: a served commit whose buildFilter paths are identical to <sha>'s,
# i.e. the same image (e.g. what Render's first Blueprint sync deployed).
same_image() { # served pathspecs...
  local served="$1"; shift
  git -C "$root" rev-parse --verify --quiet "$served^{commit}" >/dev/null &&
    git -C "$root" diff --quiet "$served" "$sha" -- "$@"
}

probe() { # name url expected outfile no_deploy_coming pathspecs...
  local name="$1" url="$2" expected="$3" out="$4" no_deploy="$5" t0=$SECONDS served="" live=""
  # The static frontend has no actuator: its build writes /version.json, and "alive" is its index.
  local info_path="/actuator/info" live_path="/actuator/health/liveness"
  if [ "$name" = frontend ]; then info_path="/version.json"; live_path="/"; fi
  shift 5
  while :; do
    served="$(curl -s -m 30 "$url$info_path" 2>/dev/null | sed -n 's/.*"commit":"\([0-9a-f]*\)".*/\1/p')"
    served="${served:-nothing}"
    if same_commit "$served" "$expected" || same_image "$served" "$@"; then
      live="$(curl -s -o /dev/null -m 30 -w '%{http_code}' "$url$live_path" 2>/dev/null)"
      if [ "$live" = "200" ]; then
        printf '%-10s OK   %4ss  serving %s\n' "$name" "$((SECONDS - t0))" "${served:0:7}" >"$out"
        return
      fi
    elif [ -n "$no_deploy" ] && [ "$served" != nothing ] &&
      git -C "$root" merge-base --is-ancestor "$served" "$expected" 2>/dev/null; then
      # Older than expected and no deploy can be under way: do not wait, say so now.
      printf '%-10s NEEDS DEPLOY  serving %s, expected %s (%s)\n' "$name" "${served:0:7}" "${expected:0:7}" "$no_deploy" >"$out"
      touch "$out.stale"
      return
    fi
    if [ $((SECONDS - t0)) -ge $SERVICE_TIMEOUT ]; then
      printf '%-10s FAIL %4ss  serving %s, expected %s%s\n' "$name" "$((SECONDS - t0))" \
        "${served:0:7}" "${expected:0:7}" "${live:+ (liveness $live)}" >"$out"
      # Still on an older commit, and <sha> itself didn't touch this service:
      # the multi-commit push case Render may skip (docs/deployment.md, 5b).
      if [ "$expected" != "$sha" ] &&
        git -C "$root" merge-base --is-ancestor "$served" "$expected" 2>/dev/null; then
        touch "$out.stale"
      fi
      return
    fi
    sleep "$INTERVAL"
  done
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
names=()
while read -r name url paths; do
  case "$name" in ''|'#'*) continue ;; esac
  names+=("$name")
  if [ "$ci_ok" != 1 ]; then
    printf '%-10s SKIP (CI failed, nothing new deployed)\n' "$name" >"$tmp/$name"
    continue
  fi
  # buildFilter globs as git pathspecs ("dir/**" -> "dir/"); read -a never globs.
  read -ra pathspecs <<<"$(sed 's#/\*\*#/#g' <<<"$paths")"
  expected="$(git -C "$root" log -1 --format=%H "$sha" -- "${pathspecs[@]}")"
  # A deploy can only be under way if the expected commit's own CI went green (Render deploys on
  # checksPass) and that was less than the whole wait ago. Otherwise nothing is coming.
  no_deploy=""
  if [ "$expected" != "$sha" ]; then
    eruns="$("$gh_bin" run list -R "$REPO" --commit "$expected" \
      --json status,conclusion,updatedAt --jq '.[] | "\(.status) \(.conclusion) \(.updatedAt)"' 2>/dev/null)"
    if grep -qE '^completed (failure|cancelled|timed_out|startup_failure)' <<<"$eruns"; then
      no_deploy="CI of ${expected:0:7} was red, so Render never deployed it"
    fi
  fi
  if [ -z "$no_deploy" ] && [ "$ci_end_epoch" -gt 0 ] && [ $(($(date +%s) - ci_end_epoch)) -gt "$TOTAL_BUDGET" ]; then
    no_deploy="CI finished over $((TOTAL_BUDGET / 60)) minutes ago and no deploy of it is under way"
  fi
  # VERIFY_DEPLOYING=1: a deploy was just started by hand, so waiting for it is right.
  [ -z "${VERIFY_DEPLOYING:-}" ] || no_deploy=""
  probe "$name" "$url" "$expected" "$tmp/$name" "$no_deploy" "${pathspecs[@]}" &
done <"$services_file"
# One progress line a minute while the probes run.
while [ -n "$(jobs -r)" ]; do
  for _ in $(seq 1 $((PROGRESS_EVERY / 2))); do
    [ -n "$(jobs -r)" ] || break
    sleep 2
  done
  [ -n "$(jobs -r)" ] && echo "progress: waiting for the services, $((SECONDS - script_start))s of ${TOTAL_BUDGET}s"
done
wait

status=0
[ "$ci_ok" = 1 ] || status=1
for name in "${names[@]}"; do
  cat "$tmp/$name"
  grep -q " OK " "$tmp/$name" || status=1
done
echo "$ci_line"
stale=()
for name in "${names[@]}"; do [ -e "$tmp/$name.stale" ] && stale+=("$name"); done
if [ "${#stale[@]}" -gt 0 ]; then
  echo "hint: ${stale[*]} need a deploy. ${sha:0:7} touches none of their buildFilter paths, and"
  echo "      Render skips the auto-deploy when the commit that did touch them had a red CI or the"
  echo "      push's last commit doesn't match. Deploy the latest commit of each (Render Manual"
  echo "      Deploy, or 'render deploys create <srv-id> --confirm -o text'), then run this script"
  echo "      again (docs/deployment.md, 5b)."
fi
exit $status

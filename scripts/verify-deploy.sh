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
CI_TIMEOUT=600
SERVICE_TIMEOUT=600
INTERVAL=10

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
start=$SECONDS
while :; do
  runs="$("$gh_bin" run list -R "$REPO" --commit "$sha" \
    --json databaseId,status,conclusion --jq '.[] | "\(.databaseId) \(.status) \(.conclusion)"' 2>/dev/null)"
  if [ -n "$runs" ] && ! grep -qv " completed " <<<"$runs"; then
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
  sleep "$INTERVAL"
done

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

probe() { # name url expected outfile pathspecs...
  local name="$1" url="$2" expected="$3" out="$4" t0=$SECONDS served="" live=""
  # The static frontend has no actuator: its build writes /version.json, and "alive" is its index.
  local info_path="/actuator/info" live_path="/actuator/health/liveness"
  if [ "$name" = frontend ]; then info_path="/version.json"; live_path="/"; fi
  shift 4
  while :; do
    served="$(curl -s -m 30 "$url$info_path" 2>/dev/null | sed -n 's/.*"commit":"\([0-9a-f]*\)".*/\1/p')"
    served="${served:-nothing}"
    if same_commit "$served" "$expected" || same_image "$served" "$@"; then
      live="$(curl -s -o /dev/null -m 30 -w '%{http_code}' "$url$live_path" 2>/dev/null)"
      if [ "$live" = "200" ]; then
        printf '%-10s OK   %4ss  serving %s\n' "$name" "$((SECONDS - t0))" "${served:0:7}" >"$out"
        return
      fi
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
  probe "$name" "$url" "$expected" "$tmp/$name" "${pathspecs[@]}" &
done <"$services_file"
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
  echo "hint: ${stale[*]} still on an older commit. ${sha:0:7} touches none of their buildFilter"
  echo "      paths, and Render may skip the auto-deploy when a push's last commit doesn't match,"
  echo "      even if earlier commits in it do. Use Manual Deploy > Deploy latest commit for them"
  echo "      in Render, then run this script again (docs/deployment.md, 5b)."
fi
exit $status

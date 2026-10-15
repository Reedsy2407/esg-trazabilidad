#!/usr/bin/env bash
# Squash-merges ONE Dependabot pull request, and only if it is a GitHub Actions bump
# (branch dependabot/github_actions/...), a patch or minor one (same major version in
# "from A to B"), and every CI check on it is green. Anything else: it says why and exits 1.
#
#   scripts/merge-dependabot-actions.sh <pr-number>
#
# This is the only merge the agent's permissions allow (see AGENT-KIT.md, point 15).
set -uo pipefail

pr="${1:?usage: merge-dependabot-actions.sh <pr-number>}"
case "$pr" in ''|*[!0-9]*) echo "not a PR number: $pr" >&2; exit 2 ;; esac
REPO="${VERIFY_REPO:-Reedsy2407/esg-trazabilidad}"
gh_bin="$(command -v gh || echo "/c/Program Files/GitHub CLI/gh.exe")"

info="$("$gh_bin" pr view "$pr" -R "$REPO" --json state,author,headRefName,title,statusCheckRollup </dev/null)" || exit 1

verdict="$(node -e '
const p = JSON.parse(process.argv[1]);
const fail = (m) => { console.log("NO " + m); process.exit(0); };
if (p.state !== "OPEN") fail("the PR is " + p.state);
if (!/^dependabot\/github_actions\//.test(p.headRefName)) fail("not a GitHub Actions bump (" + p.headRefName + ")");
if (!/dependabot/i.test((p.author || {}).login || "")) fail("not opened by Dependabot");
// The grouped PR (github-actions-minor-and-patch) holds only minor and patch updates by construction.
if (!/-minor-and-patch group/.test(p.title)) {
  const m = /from v?(\d+)(?:\.\d+)*\S* to v?(\d+)/.exec(p.title);
  if (!m) fail("cannot read the versions from the title: " + p.title);
  if (m[1] !== m[2]) fail("a major bump (" + m[1] + " to " + m[2] + "), needs a human look");
}
const checks = p.statusCheckRollup || [];
if (checks.length === 0) fail("no CI checks on the PR yet");
const bad = checks.filter((c) => !["SUCCESS", "SKIPPED", "NEUTRAL"].includes(c.conclusion || c.state));
if (bad.length) fail("CI not green: " + bad.map((c) => (c.name || c.context) + " " + (c.conclusion || c.state || c.status)).join(", "));
console.log("OK");
' "$info")"

if [ "$verdict" != "OK" ]; then
  echo "PR #$pr not merged: ${verdict#NO }" >&2
  exit 1
fi
timeout 120 "$gh_bin" pr merge "$pr" -R "$REPO" --squash </dev/null

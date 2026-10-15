# Reading production logs with the Render CLI

Claude reads the logs of the deployed services itself, read-only, with `render logs`. Nobody has to copy
them from the dashboard.

## Setup (once per machine)

- Installed with WinGet (`winget install -e --id Render.CLI --source winget`), which puts `render.exe`
  under `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Render.CLI_*` and on the user PATH. Open a new terminal
  after installing.
- `render login` (approved in the browser) saves a CLI token locally; then `render workspace set <id>` once.
  Workspace: `tea-cuo5t5l6l47c73ddnj60` ("My Workspace").
- `.claude/settings.local.json` (not in git) allows only `Bash(render logs:*)` and denies every other
  `render` subcommand (deploys, services, ssh, psql, env, jobs, restart, login, workspace...).

## Service ids

| Service | Id |
|---|---|
| esg-auth-service | `srv-dasr288473hc739jfjpg` |
| esg-collection-service | `srv-dasr288473hc739jfjtg` |
| esg-recycler-service | `srv-dasr288473hc739jfju0` |
| esg-reporting-service | `srv-dasr288473hc739jfjug` |

(`esg-frontend`, the static site, is `srv-db47vvu0tbcc73dff7bg`; it has no application logs worth reading.)

## Deploying (the only write allowed)

`timeout 60 render deploys create <srv-id> --confirm -o text < /dev/null` starts a deploy of the latest
commit of one of the four API services (ids above); it is allowed only for those ids in
`.claude/settings.local.json`. Used after a merge when `scripts/verify-deploy.sh` reports `NEEDS DEPLOY`
(see `docs/deployment.md`, 5b). Everything else in `render` stays denied.

## How to run it

```
timeout 60 render logs --resources <srv-ids> --level error --text "<text>" \
  --start 2026-10-14T14:00:00Z --end 2026-10-14T15:00:00Z --limit 500 \
  --output json < /dev/null > "$TEMP/render-logs.json"
```

Rules (same spirit as the shell rules in `CLAUDE.md`):

1. Always `--resources` (comma-separated ids; it is required in non-interactive mode).
2. Always `--output json`, always redirected to a file, always under `timeout`, stdin from `/dev/null`.
3. Never `--tail` and never the interactive mode: both wait forever.
4. Bound the query with `--start`/`--end` (ISO 8601, UTC) and `--limit` (default is 100). Other filters:
   `--level` (`error,warn,info`), `--text`, `--status-code`, `--method`, `--path`, `--direction`.
5. Read the file afterwards with Read or Grep, not by printing all of it.

The output is a stream of JSON objects (not an array), one per log line; the application line is in
`message`, the service in the `resource` label.

## Timing summary of a window

```
grep '"message"' "$TEMP/render-logs.json" | scripts/perf-summary.sh
```

gives p50/p95/max and database time per route (see `docs/performance.md`). The log lines carry no ids,
e-mails or tokens, so they are safe to read and quote.

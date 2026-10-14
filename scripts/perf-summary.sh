#!/usr/bin/env bash
# Summarises the services' timing log lines (shared-kernel observability) into one table per
# service and route: how many requests, their median and 95th percentile in ms, and how much of
# that was the database. Reads the lines on stdin or from the files given; it never prints
# anything but numbers and route templates (the log carries no ids, e-mails or tokens).
#
#   scripts/perf-summary.sh render-logs.txt            # a downloaded Render log
#   docker logs perf-auth 2>&1 | scripts/perf-summary.sh
#   scripts/perf-summary.sh render-logs.txt | sort -t'|' -k5 -n -r | head   # slowest p95 first
#
# Lines it understands (logfmt, level INFO, ERROR for 5xx):
#   event=http_request service=… method=… route=… status=… durationMs=… dbMs=… poolMs=… dbQueries=…
#   event=slow_query   service=… durationMs=… sql="…"      (listed at the end, by shape)
#   event=password_hash service=… durationMs=…              (the login's bcrypt step)
#   event=warmup       service=… step=… durationMs=…        (each start-up step)
set -uo pipefail

awk '
function field(line, name,   m) {
  if (match(line, name "=[^ ]+")) return substr(line, RSTART + length(name) + 1, RLENGTH - length(name) - 1)
  return ""
}
function pct(arr, n, p,   i) { i = int(n * p + 0.9999); if (i < 1) i = 1; return arr[i] }
/event=http_request/ {
  key = field($0, "service") " " field($0, "method") " " field($0, "route")
  n[key]++
  dur[key, n[key]] = field($0, "durationMs") + 0
  db[key] += field($0, "dbMs")
  if (field($0, "status") + 0 >= 500) errs[key]++
  keys[key] = 1
}
/event=slow_query/ {
  s = $0; sub(/^.*sql="/, "", s); sub(/"$/, "", s)
  slow[field($0, "service") " " s]++; slowMax[field($0, "service") " " s] = (field($0, "durationMs") + 0 > slowMax[field($0, "service") " " s]) ? field($0, "durationMs") + 0 : slowMax[field($0, "service") " " s]
}
/event=password_hash/ { hn++; hsum += field($0, "durationMs") + 0; if (field($0, "durationMs") + 0 > hmax) hmax = field($0, "durationMs") + 0 }
/event=warmup/ { warm[field($0, "service") " " field($0, "step")] = field($0, "durationMs") }
END {
  printf "service method route | n | p50 ms | p95 ms | max ms | db avg ms | 5xx\n"
  for (k in keys) {
    c = n[k]
    for (i = 1; i <= c; i++) a[i] = dur[k, i]
    # insertion sort: the logs are small
    for (i = 2; i <= c; i++) { v = a[i]; j = i - 1; while (j > 0 && a[j] > v) { a[j + 1] = a[j]; j-- } a[j + 1] = v }
    printf "%s | %d | %d | %d | %d | %.0f | %d\n", k, c, pct(a, c, 0.5), pct(a, c, 0.95), a[c], db[k] / c, errs[k]
  }
  if (hn) printf "\npassword_hash: %d, avg %.0f ms, max %d ms\n", hn, hsum / hn, hmax
  for (k in warm) printf "warmup %s: %s ms\n", k, warm[k]
  for (k in slow) printf "slow_query %s: %d times, max %d ms\n", k, slow[k], slowMax[k]
}' "$@"

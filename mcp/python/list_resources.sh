#!/usr/bin/env bash
#
# list_resources.sh — list the resources the MCP server exposes (resources/list).
#
# Resources are app-controlled, read-only DATA addressed by a URI (here: the Sherlock
# Holmes stories, sherlock:///<file>.md). This is the resources equivalent of
# list_tools.sh (which lists tools).
#
# Usage:
#   ./list_resources.sh        # list resources (uri + name + mimeType)
#   ./list_resources.sh -v     # also dump the raw JSON-RPC sent (→) and received (←)
#
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER="$DIR/server.py"

DUMP="${MCP_DUMP:-0}"
if [ "${1:-}" = "-v" ] || [ "${1:-}" = "--verbose" ]; then DUMP=1; fi
if [ -t 1 ]; then DIM=$'\033[2m'; RST=$'\033[0m'; else DIM=''; RST=''; fi

# initialize → initialized → resources/list
init='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}'
inited='{"jsonrpc":"2.0","method":"notifications/initialized"}'
list='{"jsonrpc":"2.0","id":2,"method":"resources/list"}'

if [ "$DUMP" = "1" ]; then
  printf "${DIM}→ %s${RST}\n" "$init" "$inited" "$list"
fi

resp="$(printf '%s\n' "$init" "$inited" "$list" | python3 "$SERVER" 2>/dev/null)"

if [ "$DUMP" = "1" ]; then
  while IFS= read -r rline; do
    [ -n "$rline" ] && printf "${DIM}← %s${RST}\n" "$rline"
  done <<< "$resp"
fi

printf '%s\n' "$resp" | python3 -c '
import sys, json
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    m = json.loads(line)
    if m.get("id") != 2:
        continue
    if "error" in m:
        print("error:", m["error"].get("message"), file=sys.stderr)
        sys.exit(2)
    rs = m.get("result", {}).get("resources", [])
    print(str(len(rs)) + " resource(s):")
    for r in rs:
        print("  - " + r["uri"])
        print("      " + r.get("name", "") + "  [" + r.get("mimeType", "") + "]")
'

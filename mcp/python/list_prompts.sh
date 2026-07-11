#!/usr/bin/env bash
#
# list_prompts.sh — list the prompts the MCP server exposes (prompts/list).
#
# Prompts are user-controlled reusable TEMPLATES (often surfaced as slash-commands).
# This is the prompts equivalent of list_tools.sh (which lists tools). It shows each
# prompt's name, description, and arguments.
#
# Usage:
#   ./list_prompts.sh        # list prompts (name + description + arguments)
#   ./list_prompts.sh -v     # also dump the raw JSON-RPC sent (→) and received (←)
#
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER="$DIR/server.py"

DUMP="${MCP_DUMP:-0}"
if [ "${1:-}" = "-v" ] || [ "${1:-}" = "--verbose" ]; then DUMP=1; fi
if [ -t 1 ]; then DIM=$'\033[2m'; RST=$'\033[0m'; else DIM=''; RST=''; fi

# initialize → initialized → prompts/list
init='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}'
inited='{"jsonrpc":"2.0","method":"notifications/initialized"}'
list='{"jsonrpc":"2.0","id":2,"method":"prompts/list"}'

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
    ps = m.get("result", {}).get("prompts", [])
    print(str(len(ps)) + " prompt(s):")
    for p in ps:
        print("  - " + p["name"] + ": " + p.get("description", ""))
        for a in p.get("arguments", []):
            kind = "required" if a.get("required") else "optional"
            print("      " + a["name"] + " (" + kind + ") - " + a.get("description", ""))
        if not p.get("arguments"):
            print("      (no arguments)")
'

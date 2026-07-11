#!/usr/bin/env bash
#
# list_tools.sh — ask the MCP server to describe itself: its identity (from initialize)
# and its tools with parameters (from tools/list).
#
# This is MCP **discovery** — how a client learns what a server offers WITHOUT knowing
# the tools in advance (exactly what the _18 / _19 client examples do at startup).
# To actually call a tool, use ./call_tools.sh.
#
# Usage:
#   ./list_tools.sh        # print server identity + tools with parameters
#   ./list_tools.sh -v     # also dump the raw JSON-RPC sent (→) and received (←)
#
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER="$DIR/server.py"

DUMP="${MCP_DUMP:-0}"
if [ "${1:-}" = "-v" ] || [ "${1:-}" = "--verbose" ]; then DUMP=1; fi
# Dim the dumped JSON only on a real terminal (no escape-code garbage when piped).
if [ -t 1 ]; then DIM=$'\033[2m'; RST=$'\033[0m'; else DIM=''; RST=''; fi

# The three JSON-RPC messages of a discovery round: initialize → initialized → tools/list.
init='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}'
inited='{"jsonrpc":"2.0","method":"notifications/initialized"}'
list='{"jsonrpc":"2.0","id":2,"method":"tools/list"}'

if [ "$DUMP" = "1" ]; then
  printf "${DIM}→ %s${RST}\n" "$init" "$inited" "$list"
fi

resp="$(printf '%s\n' "$init" "$inited" "$list" | python3 "$SERVER" 2>/dev/null)"

if [ "$DUMP" = "1" ]; then
  while IFS= read -r rline; do
    [ -n "$rline" ] && printf "${DIM}← %s${RST}\n" "$rline"
  done <<< "$resp"
fi

# Double-quotes-only Python (no f-strings): this block is inside a single-quoted bash
# string, so nested single quotes are impossible.
printf '%s\n' "$resp" | python3 -c '
import sys, json
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    msg = json.loads(line)
    if msg.get("id") == 1:                      # identity, from initialize
        info = msg.get("result", {}).get("serverInfo", {})
        print("Server: " + str(info.get("name")) + " v" + str(info.get("version")))
    elif msg.get("id") == 2:                    # capabilities, from tools/list
        if "error" in msg:
            print("error:", msg["error"].get("message"), file=sys.stderr)
            sys.exit(2)
        tools = msg.get("result", {}).get("tools", [])
        print(str(len(tools)) + " tool(s):")
        for t in tools:
            print("  - " + t["name"] + ": " + t.get("description", ""))
            schema = t.get("inputSchema", {}) or {}
            props = schema.get("properties", {}) or {}
            required = set(schema.get("required", []) or [])
            for pname, pdef in props.items():
                kind = "required" if pname in required else "optional"
                print("      " + pname + " (" + pdef.get("type", "?") + ", " + kind + ") - " + pdef.get("description", ""))
            if not props:
                print("      (no parameters)")
'

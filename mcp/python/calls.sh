#!/usr/bin/env bash
#
# calls.sh — call the MCP server's tools from the shell.
#
# An MCP server speaks JSON-RPC over stdin/stdout, NOT free text. So `echo "a question"
# | python3 server.py` won't work. This script builds the required handshake
# (initialize → initialized → tools/call) and pipes it to the server, then extracts the
# text result. To discover WHICH tools exist and their parameters, use ./describe.sh.
#
# Usage:
#   ./calls.sh                                    # run the built-in demo calls
#   ./calls.sh [-v] <tool_name> ['<json_args>']   # call one tool, e.g.:
#   ./calls.sh slugify   '{"text":"Hello World"}'
#   ./calls.sh roll_dice '{"sides":20,"count":3}'
#   ./calls.sh roll_dice                          # no args → tool defaults
#
#   -v / --verbose  (or MCP_DUMP=1)  dump the raw JSON-RPC sent (→) and received (←).
#
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER="$DIR/server.py"

DUMP="${MCP_DUMP:-0}"
if [ "${1:-}" = "-v" ] || [ "${1:-}" = "--verbose" ]; then DUMP=1; shift; fi
# Dim the dumped JSON only on a real terminal (no escape-code garbage when piped).
if [ -t 1 ]; then DIM=$'\033[2m'; RST=$'\033[0m'; else DIM=''; RST=''; fi

# Call one tool: mcp_call <name> <json-arguments>. Prints the tool's text result.
# With DUMP=1, first prints every JSON-RPC line sent (→) and received (←).
mcp_call() {
  local name="$1"
  # Default to an empty JSON object. NB: `${2:-{}}` does NOT work — bash parses the
  # first `}` as the end of the expansion, leaving a stray `}`. Use a plain default.
  local args="${2:-}"
  [ -n "$args" ] || args='{}'

  # The three JSON-RPC messages that make up one tool call over stdio.
  local init='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}'
  local inited='{"jsonrpc":"2.0","method":"notifications/initialized"}'
  local call="{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"$name\",\"arguments\":$args}}"

  if [ "$DUMP" = "1" ]; then
    # Each message is exactly one line on the wire — that's the stdio framing.
    printf "${DIM}→ %s${RST}\n" "$init" "$inited" "$call"
  fi

  local resp
  resp="$(printf '%s\n' "$init" "$inited" "$call" | python3 "$SERVER" 2>/dev/null)"

  if [ "$DUMP" = "1" ]; then
    while IFS= read -r rline; do
      [ -n "$rline" ] && printf "${DIM}← %s${RST}\n" "$rline"
    done <<< "$resp"
  fi

  # Extract the tools/call (id 2) result and print its text.
  printf '%s\n' "$resp" | python3 -c '
import sys, json
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    msg = json.loads(line)
    if msg.get("id") != 2:      # skip the initialize response, keep the tools/call one
        continue
    if "error" in msg:          # JSON-RPC error (e.g. unknown tool)
        print("error:", msg["error"].get("message"), file=sys.stderr)
        sys.exit(2)
    result = msg.get("result", {})
    for block in result.get("content", []):
        if block.get("type") == "text":
            print(block["text"])
    sys.exit(1 if result.get("isError") else 0)   # tool-level error (isError result)
'
}

if [ "$#" -ge 1 ]; then
  mcp_call "$1" "${2:-}"
else
  echo "# roll_dice  {\"sides\":20,\"count\":3}"
  mcp_call roll_dice '{"sides":20,"count":3}'
  echo
  echo "# slugify    {\"text\":\"The Adventures of Sherlock Holmes\"}"
  mcp_call slugify '{"text":"The Adventures of Sherlock Holmes"}'
  echo
  echo "# error case: unknown tool"
  mcp_call no_such_tool '{}' || echo "(exited non-zero, as expected for an error result)"
  echo
  echo "# tip: run ./describe.sh to discover the available tools and their parameters"
fi

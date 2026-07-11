# Demo MCP server (pure Python, stdio)

A minimal [Model Context Protocol](https://modelcontextprotocol.io) server used by the
Anthropic MCP-client example. **No dependencies, no build** — MCP's stdio transport is
just line-delimited JSON-RPC 2.0 over stdin/stdout, implemented by hand in
[`server.py`](server.py) with the Python 3 standard library only.

## Run

```sh
python3 mcp/python/server.py
```

Reads JSON-RPC from stdin, writes responses to stdout, runs until stdin closes (EOF).

> ⚠️ **stdout is the protocol channel** — the server never prints anything but JSON-RPC to
> it. Diagnostics go to stderr.

## Tools

| Tool | Parameters | Does |
|---|---|---|
| `roll_dice` | `sides` (int, default 6), `count` (int, default 1) | Rolls dice with **real randomness** — something a language model can't fake. |
| `slugify` | `text` (string, required) | Converts text to a URL-friendly slug. |

## Test without a client

```sh
printf '%s\n' \
 '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1.0"}}}' \
 '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
 '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
 '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"roll_dice","arguments":{"sides":20,"count":3}}}' \
 | python3 mcp/python/server.py 2>/dev/null
```

## Test from the shell — `describe.sh` & `calls.sh`

An MCP server speaks JSON-RPC, not free text, so `echo "a question" | python3 server.py`
won't work. Two small scripts wrap the proper handshake:

### `describe.sh` — discovery (what tools exist)

Issues `initialize` + `tools/list` and prints the server's identity and each tool with its
parameters. This is MCP **discovery** — exactly how a client (like the `_18`/`_19` examples)
learns what a server offers *without* hardcoding the tools:

```sh
./describe.sh        # server identity + tools with parameters
./describe.sh -v     # also dump the raw JSON-RPC sent/received
```

```
Server: demoniak-tools v1.0.0
2 tool(s):
  - roll_dice: Roll dice and return the individual results and their total. ...
      sides (integer, optional) - Faces per dice (default 6)
      count (integer, optional) - Number of dice to roll (default 1)
  - slugify: Convert a string into a URL-friendly slug (lowercase, hyphenated).
      text (string, required) - The text to slugify
```

### `calls.sh` — invocation (call a tool)

Builds `initialize` → `tools/call` and prints the tool's text result. Exits non-zero on an
error (unknown tool → 2, tool-level `isError` → 1):

```sh
./calls.sh                                   # built-in demo calls
./calls.sh slugify   '{"text":"Hello World"}'
./calls.sh roll_dice '{"sides":20,"count":3}'
./calls.sh roll_dice                         # no args → tool defaults
./calls.sh -v slugify '{"text":"Hi"}'        # -v: dump the raw JSON-RPC sent/received
```

**`-v` / `--verbose`** (or `MCP_DUMP=1`, on either script) dumps the actual protocol
traffic — each JSON-RPC message sent (`→`) and received (`←`):

```
→ {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}
→ {"jsonrpc":"2.0","method":"notifications/initialized"}
→ {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"slugify","arguments":{"text":"Hi"}}}
← {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18", ... ,"serverInfo":{"name":"demoniak-tools", ...}}}
← {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"hi"}]}}
hi
```

## Register with an MCP client

**Claude Code:**

```sh
claude mcp add demoniak-tools -- python3 mcp/python/server.py
```

**Claude Desktop** (`claude_desktop_config.json`) — use an absolute path:

```json
{
  "mcpServers": {
    "demoniak-tools": {
      "command": "python3",
      "args": ["/absolute/path/to/demoniak/mcp/python/server.py"]
    }
  }
}
```

## Adding a tool

1. Add an entry to `TOOLS` (name, description, JSON-Schema `inputSchema`).
2. Write a `tool_<name>(args)` function that returns a string.
3. Register it in `HANDLERS`.

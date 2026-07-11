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

## What this server provides — all three MCP primitives

An MCP server can expose more than tools. This one demonstrates the **three primitives**,
each distinguished by *who is in control*:

| Primitive | Controlled by | List / use methods | Here |
|---|---|---|---|
| **Tools** | the **model** (actions) | `tools/list` + `tools/call` | `roll_dice(sides, count)`, `slugify(text)` |
| **Resources** | the **app** (read-only data by URI) | `resources/list` + `resources/read` | the Sherlock Holmes stories in `assets/books/` as `sherlock:///<file>.md` |
| **Prompts** | the **user** (reusable templates) | `prompts/list` + `prompts/get` | `haiku(subject)` → a ready-made "write a haiku" request |

The server advertises which primitives it supports in the `initialize` handshake:
`"capabilities": {"tools": {}, "resources": {}, "prompts": {}}`.

**Tools** — actions the model calls:

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
 '{"jsonrpc":"2.0","id":4,"method":"resources/list"}' \
 '{"jsonrpc":"2.0","id":5,"method":"resources/read","params":{"uri":"sherlock:///01-a-scandal-in-bohemia.md"}}' \
 '{"jsonrpc":"2.0","id":6,"method":"prompts/list"}' \
 '{"jsonrpc":"2.0","id":7,"method":"prompts/get","params":{"name":"haiku","arguments":{"subject":"Baker Street fog"}}}' \
 | python3 mcp/python/server.py 2>/dev/null
```

(The shell helpers below cover each primitive: `list_tools.sh` / `call_tools.sh` for **tools**,
`list_resources.sh` for **resources**, `list_prompts.sh` for **prompts**.)

## Test from the shell — `list_tools.sh` & `call_tools.sh`

An MCP server speaks JSON-RPC, not free text, so `echo "a question" | python3 server.py`
won't work. Two small scripts wrap the proper handshake:

### `list_tools.sh` — discovery (what tools exist)

Issues `initialize` + `tools/list` and prints the server's identity and each tool with its
parameters. This is MCP **discovery** — exactly how a client (like the `_18`/`_19` examples)
learns what a server offers *without* hardcoding the tools:

```sh
./list_tools.sh        # server identity + tools with parameters
./list_tools.sh -v     # also dump the raw JSON-RPC sent/received
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

### `call_tools.sh` — invocation (call a tool)

Builds `initialize` → `tools/call` and prints the tool's text result. Exits non-zero on an
error (unknown tool → 2, tool-level `isError` → 1):

```sh
./call_tools.sh                                   # built-in demo calls
./call_tools.sh slugify   '{"text":"Hello World"}'
./call_tools.sh roll_dice '{"sides":20,"count":3}'
./call_tools.sh roll_dice                         # no args → tool defaults
./call_tools.sh -v slugify '{"text":"Hi"}'        # -v: dump the raw JSON-RPC sent/received
```

### `list_resources.sh` & `list_prompts.sh` — the other two primitives

Same idea as `list_tools.sh`, one per primitive — issue the `*/list` method and print what
the server exposes:

```sh
./list_resources.sh      # resources/list  → the Sherlock stories (uri + name + mimeType)
./list_prompts.sh        # prompts/list    → each prompt with its arguments
```

```
12 resource(s):
  - sherlock:///01-a-scandal-in-bohemia.md
      A Scandal in Bohemia  [text/markdown]
  ...

1 prompt(s):
  - haiku: Ask the assistant to write a haiku about a subject.
      subject (required) - What the haiku should be about
```

(To *read* a resource or *fill* a prompt — `resources/read` / `prompts/get` — use the raw
handshake snippet above, or the MCP Inspector.)

**`-v` / `--verbose`** (or `MCP_DUMP=1`, on any of these scripts) dumps the actual protocol
traffic — each JSON-RPC message sent (`→`) and received (`←`):

```
→ {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}
→ {"jsonrpc":"2.0","method":"notifications/initialized"}
→ {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"slugify","arguments":{"text":"Hi"}}}
← {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18", ... ,"serverInfo":{"name":"demoniak-tools", ...}}}
← {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"hi"}]}}
hi
```

## The official MCP Inspector (recommended)

The scripts above are handy for the shell and show the raw protocol, but the official,
interactive way to test any MCP server is the **[MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector)**
— a browser UI maintained by the Model Context Protocol project ([source](https://github.com/modelcontextprotocol/inspector),
[npm](https://www.npmjs.com/package/@modelcontextprotocol/inspector)). It lets you list and
call tools from a form, browse resources/prompts, and see the raw JSON-RPC. It supports all
transports (stdio, SSE, streamable-http).

Point it at this server (requires Node.js; nothing to add to this project — `npx` fetches it):

```sh
npx @modelcontextprotocol/inspector python3 mcp/python/server.py
```

It opens a UI at `http://localhost:6274`. There's also a scriptable `--cli` mode.

> It's essentially the GUI version of `list_tools.sh` (Tools tab = `tools/list`) and `call_tools.sh`
> (call a tool from a form): the shell scripts demystify the JSON-RPC underneath, the Inspector
> is what you'd reach for in day-to-day development.

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

#!/usr/bin/env python3
"""
A tiny MCP (Model Context Protocol) server over **stdio** — pure Python 3 stdlib.

No pip install, no SDK, no build: MCP's stdio transport is just line-delimited
JSON-RPC 2.0 (one JSON object per line) over stdin/stdout. This script implements
the few methods a tool server needs, by hand, so it stays dependency-free.

Run it directly:

    python3 mcp/python/server.py

⚠️ stdout is the protocol channel — never print anything but JSON-RPC to it.
   Diagnostics go to stderr (see log()).

Tools exposed (deliberately tiny, just to demonstrate parameters + results):
  - roll_dice(sides=6, count=1)  → real randomness the model can't fake
  - slugify(text)                → deterministic string transform
"""

import json
import random
import re
import sys

PROTOCOL_VERSION = "2025-06-18"
SERVER_INFO = {"name": "demoniak-tools", "version": "1.0.0"}


def log(*args):
    """Diagnostics to stderr — never stdout (that's the protocol)."""
    print("[server]", *args, file=sys.stderr, flush=True)


# ---- Tool definitions (JSON Schema for inputs) ---------------------------------

TOOLS = [
    {
        "name": "roll_dice",
        "description": "Roll dice and return the individual results and their total. "
                       "Uses real randomness (something a language model cannot do reliably).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "sides": {"type": "integer", "description": "Faces per die (default 6)"},
                "count": {"type": "integer", "description": "Number of dice to roll (default 1)"},
            },
            "required": [],
        },
    },
    {
        "name": "slugify",
        "description": "Convert a string into a URL-friendly slug (lowercase, hyphenated).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "text": {"type": "string", "description": "The text to slugify"},
            },
            "required": ["text"],
        },
    },
]


# ---- Tool implementations ------------------------------------------------------

def tool_roll_dice(args):
    sides = int(args.get("sides", 6))
    count = int(args.get("count", 1))
    if sides < 2:
        raise ValueError("sides must be >= 2")
    if not (1 <= count <= 100):
        raise ValueError("count must be between 1 and 100")
    rolls = [random.randint(1, sides) for _ in range(count)]
    return f"Rolled {count}d{sides}: {rolls} (total {sum(rolls)})"


def tool_slugify(args):
    text = args.get("text")
    if not isinstance(text, str) or not text.strip():
        raise ValueError("text is required")
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug or "(empty)"


HANDLERS = {"roll_dice": tool_roll_dice, "slugify": tool_slugify}


# ---- JSON-RPC plumbing ---------------------------------------------------------

def send(message):
    """Write one JSON-RPC message as a single line to stdout."""
    sys.stdout.write(json.dumps(message) + "\n")
    sys.stdout.flush()


def result(req_id, payload):
    send({"jsonrpc": "2.0", "id": req_id, "result": payload})


def error(req_id, code, message):
    send({"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}})


def handle(request):
    method = request.get("method")
    req_id = request.get("id")  # None for notifications

    if method == "initialize":
        # Echo the client's protocol version when given, else our default.
        version = request.get("params", {}).get("protocolVersion", PROTOCOL_VERSION)
        result(req_id, {
            "protocolVersion": version,
            "capabilities": {"tools": {}},
            "serverInfo": SERVER_INFO,
        })

    elif method == "notifications/initialized":
        pass  # notification: no response

    elif method == "tools/list":
        result(req_id, {"tools": TOOLS})

    elif method == "tools/call":
        params = request.get("params", {})
        name = params.get("name")
        args = params.get("arguments") or {}
        handler = HANDLERS.get(name)
        if handler is None:
            error(req_id, -32602, f"Unknown tool: {name}")
            return
        try:
            text = handler(args)
            # A successful tool result is a list of content blocks.
            result(req_id, {"content": [{"type": "text", "text": text}]})
        except Exception as exc:  # surface tool errors as isError results, not RPC errors
            result(req_id, {"content": [{"type": "text", "text": f"error: {exc}"}], "isError": True})

    elif req_id is not None:
        error(req_id, -32601, f"Method not found: {method}")
    # else: unknown notification → ignore


def main():
    log("demoniak-tools MCP server ready (stdio)")
    for line in sys.stdin:  # newline-delimited JSON-RPC
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            log("skipping non-JSON line:", line[:80])
            continue
        handle(request)
    log("stdin closed, exiting")


if __name__ == "__main__":
    main()

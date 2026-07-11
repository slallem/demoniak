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

This server demonstrates all THREE MCP primitives:

  - TOOLS      (model-controlled actions):
      roll_dice(sides=6, count=1)  → real randomness the model can't fake
      slugify(text)                → deterministic string transform
  - RESOURCES  (app-controlled, read-only data addressed by URI):
      the Sherlock Holmes stories under assets/books/ (sherlock:///<file>.md)
  - PROMPTS    (user-controlled reusable templates):
      haiku(subject)               → a ready-made "write a haiku" request

Each primitive has a list + a use method: tools/list + tools/call,
resources/list + resources/read, prompts/list + prompts/get.
"""

import json
import os
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


# ---- Resources: read-only data addressed by URI --------------------------------
#
# We expose the Sherlock Holmes stories from the project's assets/books/ folder. A
# resource is DATA (not an action): the client reads it and may put it in context.
# URI scheme here: sherlock:///<filename>.md

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS_DIR = os.path.normpath(os.path.join(HERE, "..", "..", "assets", "books"))
RESOURCE_SCHEME = "sherlock:///"


def _story_files():
    """Sorted list of story filenames in assets/books (empty if the folder is absent)."""
    if not os.path.isdir(DOCS_DIR):
        return []
    return sorted(f for f in os.listdir(DOCS_DIR) if f.endswith(".md") and f != "SOURCE.md")


def _title(path, fallback):
    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("# "):
                    return line[2:].strip()
    except OSError:
        pass
    return fallback


def list_resources():
    resources = []
    for name in _story_files():
        resources.append({
            "uri": RESOURCE_SCHEME + name,
            "name": _title(os.path.join(DOCS_DIR, name), name),
            "description": "A Sherlock Holmes story (public domain).",
            "mimeType": "text/markdown",
        })
    return resources


def read_resource(uri):
    """Return the contents block for a known resource URI, or None if unknown."""
    if not uri or not uri.startswith(RESOURCE_SCHEME):
        return None
    name = uri[len(RESOURCE_SCHEME):]
    if name not in _story_files():          # only serve listed files (no path traversal)
        return None
    with open(os.path.join(DOCS_DIR, name), encoding="utf-8") as fh:
        text = fh.read()
    return {"uri": uri, "mimeType": "text/markdown", "text": text}


# ---- Prompts: reusable, user-selected templates --------------------------------
#
# A prompt is a TEMPLATE the user picks (often shown as a slash-command). prompts/get
# fills in the arguments and returns ready-to-send messages.

PROMPTS = [
    {
        "name": "haiku",
        "description": "Ask the assistant to write a haiku about a subject.",
        "arguments": [
            {"name": "subject", "description": "What the haiku should be about", "required": True},
        ],
    },
]


def get_prompt(name, arguments):
    """Return a filled-in prompt (description + messages), or None if unknown."""
    if name != "haiku":
        return None
    subject = (arguments or {}).get("subject", "the sea")
    return {
        "description": "A haiku request",
        "messages": [
            {"role": "user", "content": {"type": "text", "text": f"Write a haiku about {subject}."}},
        ],
    }


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
            # Advertise every primitive this server supports. A client reads this to
            # know what to ask for (tools/list, resources/list, prompts/list).
            "capabilities": {"tools": {}, "resources": {}, "prompts": {}},
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

    elif method == "resources/list":
        result(req_id, {"resources": list_resources()})

    elif method == "resources/read":
        uri = request.get("params", {}).get("uri")
        contents = read_resource(uri)
        if contents is None:
            error(req_id, -32602, f"Unknown resource: {uri}")
        else:
            result(req_id, {"contents": [contents]})

    elif method == "prompts/list":
        result(req_id, {"prompts": PROMPTS})

    elif method == "prompts/get":
        params = request.get("params", {})
        got = get_prompt(params.get("name"), params.get("arguments"))
        if got is None:
            error(req_id, -32602, f"Unknown prompt: {params.get('name')}")
        else:
            result(req_id, got)

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

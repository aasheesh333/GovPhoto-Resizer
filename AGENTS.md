# GovPhoto Resizer — Agent Rules

## Hard constraints (apply to every session, every subagent)

1. **NEVER build or debug locally.** All Android builds (APK/AAB) and all test/debug verification must run via GitHub Actions. Do NOT invoke `./gradlew`, `gradle assemble*`, `gradle test*`, `adb`, emulator, or any local build/test tooling. Push commits to a branch and observe GitHub Actions runs via `gh run list` / `gh run view`.

2. **NEVER run heavy write commands in one shot.** Do work in small chunks / pieces. Avoid long-running file writes, large code generation in a single tool call, multi-hundred-line edits, or bulk recursive modifications. Break big work into many small tool calls. When in doubt, split it.

3. **NEVER run commands likely to crash the host** — no full-project greps across `build/`, `.gradle/`, or `node_modules/`; no unbounded `find`/`rg` output; no recursive globs over thousands of files. Use targeted paths and small scopes. Pipe heavy output through `| head -N` (small N) or `--max-count`.

## Practical effects

- Verification of any code change = push the commit, watch the GitHub Actions workflow (`.github/workflows/android-build.yml`) via `gh run watch` or `gh run list --branch <branch>`.
- Large code generation = one file per tool call, one tool call per message when files exceed ~200 lines.
- Refactors touching many files = iterate file-by-file; commit after each batch of a few files.

## Branch convention (current mega-PR)

- Working branch: `feat/pr2-monetization-mega`
- All work commits go there. Push often. CI runs on every push.

## Browser / CDP / noVNC stack (host-level, NOT part of the Android app)

A headless Chromium + noVNC stack is installed and auto-starts on boot so AI
agents can drive a visible browser and the human can watch from a phone.

- Display: `:99` (Xvfb, 1280x800x24) — `systemctl status xvfb.service`
- Window manager: openbox — `systemctl status openbox.service`
- VNC server: x11vnc on `0.0.0.0:5900` (NO password) — `x11vnc.service`
- Web client (phone): noVNC at `http://23.20.8.171:6080/vnc.html` — `novnc.service`
- Browser: Chromium snap on `:99`, profile at `~/.chromium-cdp-profile` — `chromium-cdp.service`
- CDP endpoint (for AI agents on this VPS): `ws://127.0.0.1:9222`
  - HTTP discovery: `curl http://127.0.0.1:9222/json/version`
  - List open tabs: `curl http://127.0.0.1:9222/json`
- CDP endpoint (external, e.g. for testing from a phone/laptop): `ws://23.20.8.171:9223`
  (socat forwarder `cdp-proxy.service` proxies public 9223 → local 9222; Chromium
   refuses to bind 9222 directly to 0.0.0.0 since v150)

Restart everything: `sudo systemctl restart xvfb openbox x11vnc novnc chromium-cdp cdp-proxy`

Security note: 6080 and 9223 are open to the internet with NO auth. Only keep
these services running when actively needed; consider stopping them when idle:
`sudo systemctl stop novnc cdp-proxy chromium-cdp`

## Driving the visible browser

Two control planes — always connect to the existing visible Chromium, never
launch a second browser (a second one won't be visible in noVNC).

### 1. Raw CDP (lightweight, no deps)
- `curl http://127.0.0.1:9222/json` — list open tabs
- `curl http://127.0.0.1:9222/json/version` — browser version
- `curl -X PUT 'http://127.0.0.1:9222/json/new?<url>'` — open a new tab at `<url>`
- WebSocket + JSON: see `/tmp/opencode/cdp-eval.py` (Runtime.evaluate helper,
  uses `python3-websocket` package) and `/tmp/opencode/cdp-upload.py`
  (DOM.setFileInputFiles for uploads).

### 2. Playwright (recommended for anything beyond one-shot eval)
Python 3 Playwright is installed (`playwright==1.61.0`, `sync_api` + `async_api`).
**Do NOT run `playwright install` — it would download a second, hidden Chromium.
Always `connect_over_cdp` to attach to the visible, noVNC-watchable browser.**

```python
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    browser = p.chromium.connect_over_cdp("http://127.0.0.1:9222")
    ctx = browser.contexts[0]            # reuse existing context (cookies/sessions)
    page = ctx.new_page()                # new tab — visible in noVNC
    page.goto("https://...")
    page.click("#sel"); page.fill("input", "text")
    page.screenshot(path="/tmp/shot.png")  # the human can also watch live in noVNC
    # DON'T call browser.close() — that kills the shared Chromium. Just page.close().
```

Quick aliases (any opencode shell on this VPS):
- `vb`      → start the full noVNC + Chromium stack
- `vb-stop`  → stop the public-facing half (noVNC + CDP proxy + Chromium)

These are defined in `~/.bash_aliases` (auto-sourced by Ubuntu's default
`~/.bashrc`). New bash sessions get them automatically; existing shells need
`source ~/.bash_aliases` once.

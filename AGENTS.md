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

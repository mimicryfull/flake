# Kilo starter for Flake (2026-11)

This is a **template**, not verified against the user's actual Flake repository. Kilo CLI v1+ expected. Kilo uses `kilo.jsonc`, `.kilo/agent/`, `.kilo/skills/`, and root `AGENTS.md`.

## Installation (Linux)
1. Back up your repository and check `git status --short`.
2. From repository root, extract the ZIP with `unzip -n /path/to/flake-kilo-starter.zip -d .` (`-n` means existing files are NOT overwritten). Merge your preexisting AGENTS.md / kilo.jsonc manually if any.
3. Install CLI if missing: `npm install -g @kilocode/cli`.
4. `python3 .kilo/tools/repo_map.py .` to generate path index (local CPU, no LLM calls).
5. `kilo` and `/connect` your Kilo account. Select `kilo-auto/efficient` with `/models` if needed. Confirm agents with `/agents` or `kilo agent list`.
6. Start with one task: `/flake-task Зроби ...`; use @flake-architect only after explicitly choosing its higher cost. Check costs using `kilo stats` and the Kilo dashboard.

## Role routing
- Default Code: `kilo-auto/efficient` — automatic cheap-to-sufficient per-turn routing.
- Built-in Explore: `z-ai/glm-5.3-flash` — inexpensive focused source lookup, read-only.
- Compaction: `kilo-auto/small` — low-cost summaries.
- `@flake-architect`: `z-ai/glm-5.3` — explicit, high-risk design only, read-only.
- `@flake-reviewer`: `kilo-auto/efficient` — read-only, on request or high-risk change.

These roles are not forced on each task: routing is guided by instructions; conditional delegation is not a guaranteed automatic cost controller. Auto Efficient may choose varying provider models. Before critical changes, inspect the actual session model/cost.

## Cost controls
Start with $5 paid credits, disable auto top-up, and monitor `kilo stats`. Individual Kilo credits stop paid queries when depleted; alerts are available in account spend settings. Don't use `kilo-auto/free` on confidential code without accepting its provider data handling terms. Do not enable semantic indexing automatically; start with this path map + rg + LSP. Add Kilo's optional semantic index only if navigation actually fails; indexing needs an embedding provider and may have separate costs.

## First-session test prompt
`Show the current Flake combat/VFX/UI subsystems using the code map and at most 8 directly relevant files. Do not edit anything. Report missing or stale paths briefly.`

## Important limitations
- No real Flake code was available during template authoring; many historical descriptions may be stale.
- The generated map indexes filenames and directory paths, not a semantic graph or AST.
- A Gradle build, Minecraft runtime test, and visual VFX verification still require the actual project and environment.

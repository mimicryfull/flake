# Flake: agent operating rules

Flake is a Minecraft Fabric PvP mod using Java, Gradle, and custom GLSL/VFX. **The repository source is authoritative**: documentation and these rules may be outdated. Confirm API and game version from build files. Never invent file paths.

## Before edits

- First check `git status --short`; preserve unrelated work, never reset or overwrite user changes.
- For a task, read `.kilo/context/repo-map.md` if present. It is a navigation hint, NOT authoritative source. Search with `rg`, glob, or LSP for identifiers; read only affected files and immediate dependencies. Do not reread the whole project.
- If the map is missing or stale, run `python3 .kilo/tools/repo_map.py .` once; this is deterministic, local, and uses no LLM tokens. Rerun after significant moves/refactors, not after every small edit.
- Treat screenshots and the user's latest instructions as desired behavior; existing UI/VFX may be an anti-reference.

## Task routing (do not create overhead)

- Tiny change (one class/constant/text): do directly in `code` with Auto Efficient. No subagent, no plan document.
- Medium change: do directly with Auto Efficient, load one relevant skill. Use `explore` only if locating code is genuinely hard.
- High-risk refactor affecting networking, saves, duel restore, or multiple subsystems: sketch a concise plan, use focused review. Use `@flake-architect` ONLY after explicit user approval of the extra model cost.
- Do NOT spawn multiple agents for a single-file change or call a reviewer for a cosmetic update.
- The `flake-reviewer` is read-only; invoke only for risky changes or suspected regression.
- Do not use Auto Frontier casually; the budget is $20/month total additional spend.

## Hard project boundaries

- Server owns damage, knockback, eligibility, cooldown, inventory state and authoritative decisions. Client owns rendering, input and visual feedback.
- A player has two class ability slots (default H and R); these are **not** the two selectable active item slots.
- Preserve previously good shaders/animations; prefer targeted improvement over wholesale rewriting.
- Avoid default vanilla particles as the main VFX. Low GPU overhead matters.
- Respect multiplayer, disconnect, duel cancel, inventory restoration and `/flake effects`.
- Confirm item/ability naming and implementation from source, not stale READMEs.

## Verification policy

- Small cosmetic changes: inspect diff; use build/shader validation only when appropriate.
- Java gameplay/data changes: run the smallest relevant Gradle check(s); broader tests only for affected server invariants.
- Shader/UI changes: compile where possible, and explicitly state if visual runtime inspection was not possible.
- Never create dozens of redundant test scripts or run the same broad tests unchanged.
- No invented benchmark or screenshot claims.

## Reporting

- Ukrainian, concise, friendly tone.
- Final: what changed, tests actually run, remaining limitations. No lengthy status essays.
- If blocked, report the exact blocker rather than pretending it is complete.

## Efficient Code Navigation

- Before a local task: (1) identify the subsystem, (2) check the matching section of `.kilo/context/architecture.md`, (3) locate files via `repo-map.md`, `rg`, LSP or `semantic_search`, (4) read only current, relevant code and immediate dependencies, (5) do not explore the whole repository, (6) trust live code over a stale architecture map.
- After a significant architectural change, update only the affected section of `architecture.md`; do not rewrite the whole document for small tasks.
- Never explore the entire repository for a localized task.
- First consult the repository map.
- Use exact symbol search (rg/LSP) for known names.
- Use semantic_search for conceptual code discovery.
- Read only relevant implementation files and dependencies.
- Do not repeatedly read unchanged files in the same session.
- Expand exploration only when necessary.
- After significant architectural changes, update the project map.

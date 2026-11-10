#!/usr/bin/env python3
"""No-LLM, low-overhead approximate Flake file locator (not code understanding)."""
import pathlib
import os
import sys
from collections import Counter

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
exclude = {'.git', '.gradle', '.idea', 'build', 'out', 'target', 'node_modules', '.kilo', '.opencode', '.kilocode'}
exts = {'.java', '.glsl', '.vert', '.frag', '.fsh', '.vsh', '.json', '.json5', '.jsonc', '.mcmeta', '.gradle', '.kts', '.properties'}
groups = {
    'Abilities and combat': ['abilit', 'skill', 'combat', 'damage', 'duel', 'cooldown', 'knockback'],
    'VFX and shaders': ['shader', 'render', 'effect', 'vfx', 'particle', 'lightning', 'earthquake', 'dissonance'],
    'Items and builds': ['item', 'equipment', 'loadout', 'inventory', 'build', 'accessor'],
    'UI and client': ['screen', 'hud', 'gui', 'widget', 'menu', 'client', 'preview'],
    'Arena and persistence': ['arena', 'restore', 'snapshot', 'persist', 'saved'],
    'Networking': ['network', 'packet', 'payload', 'sync'],
}
files = []
for dirname, dirs, names in os.walk(root):
    dirs[:] = sorted(d for d in dirs if d not in exclude and not d.startswith('.git'))
    base = pathlib.Path(dirname)
    for name in names:
        p = base / name
        if p.suffix.lower() in exts or name in {'gradlew', 'settings.gradle', 'build.gradle', 'gradle.properties', 'fabric.mod.json'}:
            files.append(p.relative_to(root).as_posix())
files.sort()
out = ['# Flake repository map (generated)', '', 'This is a PATH INDEX ONLY. Confirm implementations from live source; paths and identifiers can change.', '', f'- Indexed source/resource paths: {len(files)}', '']
for title, words in groups.items():
    found = [f for f in files if any(word in f.lower() for word in words)]
    out += [f'## {title}', f'Matches: {len(found)}']
    out += [f'- `{p}`' for p in found[:35]]
    if len(found) > 35:
        out += [f'- (+{len(found)-35} omitted; use `rg --files` and `rg` for live search)']
    out += ['']
for label, endings in [('Build/config entry points', ('build.gradle', 'build.gradle.kts','settings.gradle','settings.gradle.kts','gradle.properties','fabric.mod.json'))]:
    out += [f'## {label}'] + [f'- `{p}`' for p in files if p.endswith(endings)] + ['']
out += ["## Count by extension"]
counts = Counter(pathlib.Path(p).suffix or '(none)' for p in files)
out += [f'- `{k}`: {v}' for k,v in sorted(counts.items())]
out += ['', '## Update policy', 'Regenerate after moving/renaming many source files. Do not paste the whole map into each prompt; read the relevant section.']
dest = root / '.kilo' / 'context' / 'repo-map.md'
dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_text('\n'.join(out)+'\n', encoding='utf-8')
print(f'Wrote {dest.relative_to(root)} ({len(files)} paths, {len(out)} lines)')

---
name: flake-combat
description: Use when adding or editing Flake PvP abilities, weapons, active items, stun, damage, slow, knockback, cooldown, duels, networking, build validation, or inventory restore.
---
# Flake combat and inventory

Server-authoritative outcomes only. Keep client keybinds as class ability slot 1/2; distinguish them from two selectable active equipment items. Reuse existing registries, item and cooldown systems. Keep duel rules, immunity/eligibility, disconnect, death, dimension changes and inventory restoration invariant. Prefer small focused tests for cooldown, allowed targets, duplication and persistence over huge test suites. Do not rebalance items without a request.

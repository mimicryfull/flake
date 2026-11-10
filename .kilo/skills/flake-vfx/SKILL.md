---
name: flake-vfx
description: Use for Flake shader, GLSL, GPU rendering, particle, electro-earthquake, gravitational dissonance, visual distortion, postprocessing, FPS or animation tasks.
---
# Flake VFX

Locate the current effect renderer, shader resources, uniforms, animation state, and render lifecycle. Keep current correct animation and existing shader pipeline unless required otherwise. For ground lightning: follow terrain, create branching spatial bolts, keep effect within server AoE visual boundaries, avoid a flying sphere. For gravity: sphere charge/compression/blast, proper depth and targeted hit feedback. Distinguish screen-space feedback from world VFX. Minimize fill rate, allocations, draw calls and overdraw. Any performance claim needs measurement. Do not equate shader compilation with visual correctness. `/flake effects` must not change gameplay.

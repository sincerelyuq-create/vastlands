Vastlands — Architecture

Project Overview

Vastlands is a realistic world-generation mod for Minecraft Java Edition 1.21.8, using NeoForge.

The goal is to create large-scale, geographically believable landscapes while preserving Minecraft's block-based visual identity.

Core Requirements

- Minecraft version: 1.21.8
- Mod loader: NeoForge
- Java version: 21
- Target world height: Y=-256 to Y=511 (768 blocks total)
- No unnatural floating islands or disconnected terrain
- Performance-conscious, deterministic world generation
- Modular architecture with clearly separated generation systems

Development Modules

1. World Core

Responsible for project setup, world height configuration, generation infrastructure, shared interfaces, and integration.

2. Terrain

Responsible for mountain ranges, valleys, plains, plateaus, cliffs, and natural terrain continuity.

3. Hydrology

Responsible for rivers, tributaries, downhill drainage, waterfalls, and river valleys.

4. Caves

Responsible for large underground caverns, tunnels, vertical shafts, and diverse cave formations.

5. Forests

Responsible for large forests, tree diversity, giant trees, and natural vegetation distribution.

Integration Rules

- All generation systems must be deterministic for a given world seed.
- Neighboring chunks must connect without visible seams.
- Modules must use shared generation data where necessary.
- Avoid unnecessary chunk loading during world generation.
- Do not assume that world-height changes are trivial.
- Preserve compatibility with NeoForge 1.21.8.
- Prioritize actual working implementations over visual demonstrations or placeholders.

Development Workflow

Development is performed through separate, sequential Claude Cloud sessions.

Each agent must:

1. Read ARCHITECTURE.md and PROGRESS.md.
2. Inspect the existing code before making changes.
3. Implement only its assigned responsibilities.
4. Run available build checks and tests.
5. Update PROGRESS.md with completed work, remaining issues, and integration notes.
6. Create a pull request without automatically merging into main.

This document may be updated when architectural decisions are verified through implementation.

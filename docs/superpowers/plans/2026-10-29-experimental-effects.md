# Experimental SkSL Effects Implementation Plan

**Goal:** Add one default-off experimental switch below the fun-effects fold and a native fullscreen SkSL activation show with three chat-triggered effects

**Architecture:** Keep the existing settings card/switch/icon families, share the existing visible-message event subscription, and render a screen-owned effect after the panel transform has been restored. Capture only the current frame inside the active Skia context; never retain framebuffer images across frames or context resets

**Tech Stack:** Java 17-compatible shared code, Skija 0.143.18 RuntimeEffect, existing Forge/Fabric/NeoForge adapters

## Constraints

- One fold, one persisted switch, default false; no new preview controls
- Manual false-to-true transition plays a three-second ignition once; loading enabled config does not replay
- Global animation disabled, experimental switch disabled, screen removed or conversation visibility changed stops playback
- Experimental switch is independent of the existing Emoji fun switch
- Title glyph uses the existing 20-unit native path, size, stroke and centering
- Settings UI uses existing tokens; shader equations use normalized screen coordinates
- No game startup, commit, publication, personal-data edits or WIP reset

## Steps

- [x] Write regression tests for defaults, actual fold ordering/count, toggling/lifetime/cancellation, masked keyword matching, actual shader pixels and restored Canvas state
- [x] Observe feature-missing test failures
- [x] Implement small type/matcher/session/renderer classes; preserve the existing Emoji path
- [x] Wire both settings mappings and all three screen adapters; add bilingual resources and explicit semantic glyph mapping
- [x] Run native raster tests and inspect effect frames; measure actual hidden-window OpenGL playback, including first use and 1080p steady frames
- [x] Independently review the bounded change and resolve confirmed defects
- [x] Run three platform test/build and repository guards; verify and back up/deploy Windows x64 packages to the established instances
- [x] Record source/package hashes, actual measurements, remaining live-game limits and update STATUS

## Validation commands

Each platform: `gradlew.bat test build --offline --no-daemon --console=plain` with the existing JDK and Forge certificate flag

Repository: `python tools/verify_targets.py`, `python tools/twin_diff_report.py`, `python tools/bare_scan.py`, `git diff --check`

No extra workflow or commits are introduced by this plan

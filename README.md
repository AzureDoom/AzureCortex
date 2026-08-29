# AzureCortex

A modular Minecraft entity AI framework combining behavior trees, GOAP (Goal-Oriented Action Planning), blackboards,
interruptible actions, and custom navigation, including wall and ceiling crawling.

**AzureCortex is a framework, not a standalone mod.** It adds no blocks or items and doesn't change vanilla gameplay
on its own. Mod developers depend on it to build sophisticated, non-vanilla mob AI without re-implementing the
plumbing (ticking, preemption rules, replanning gates, pathfinding, and steering) from scratch. If you installed this
because another mod requires it, that's all it's for, there's nothing to configure as a player beyond what that
other mod tells you.

## Features

- **Behavior trees**: `PrioritySelector`, `Sequence`, `Condition`, and `CooldownGate` nodes for composing an agent's
  moment-to-moment decisions.
- **GOAP goal planning**: pluggable goal scoring, replanning gates, failure feedback, and emergency preemption, with
  no fixed goal-type list baked in.
- **Blackboards**: a per-agent state store shared between sensors, the planner, the tree, and actions.
- **Interruptible actions**: three-tier preemption (`NORMAL` / `LOCKED` / `EMERGENCY`) so a committed action can't be
  interrupted by accident, but a genuine emergency still gets through.
- **Custom navigation**: its own A* pathfinder with incremental/phased search to spread cost across ticks, plus an
  optional wall-and-ceiling-crawling movement model for creatures that shouldn't be bound to the floor.
- **Sensing**: target acquisition with last-seen tracking and velocity-based interception prediction, so a lost
  target is searched for intelligently rather than at its last exact block.

Every place a specific mod needs something domain-specific, what a goal is called, which blocks count as hazards,
how goals get scored, is a small interface your mod implements. AzureCortex only owns the generic machinery.

## Example entities

Three fully-wired example entities ship purely to demonstrate the framework in action, spawn one in via its spawn
egg to see it in the world:

- **Cortex Zombie**, melee, with an emergency healing branch (eat a golden apple when critically wounded).
- **Cortex Skeleton**, ranged, smoothly switching between drawing a bow and falling back to melee at point-blank
  range.
- **Cortex Spider**, climbs walls and ceilings in pursuit of a target, using the same wall/ceiling-crawling
  navigation model available to any mod built on AzureCortex.

## Supported versions & loaders

| Minecraft version | Forge          | Fabric           | NeoForge               |
|-------------------|----------------|------------------|------------------------|
| 1.18.2            | ✅ available   | ✅ available     | ❌No NeoForge version  |
| 1.20.1            | ✅ available   | ✅ available     | ❌No NeoForge version  |
| 1.21.1            | ❌ no support  | ✅ available     | ✅ available           |
| 26.2              | ❌ no support  | ✅ available     | ✅ available           |

## For mod developers

AzureCortex is published on AzureDoom's public Maven:

```
https://maven.azuredoom.com/mods
```

See the [wiki's Installation page](https://github.com/AzureDoom/AzureCortex/wiki/Installation) for exact Gradle
dependency coordinates per version and loader.

Full documentation lives on the [GitHub wiki](https://github.com/AzureDoom/AzureCortex/wiki), including:

- [Core Concepts](https://github.com/AzureDoom/AzureCortex/wiki/Core-Concepts), the runtime model and tick order.
- [Full Example Walkthrough](https://github.com/AzureDoom/AzureCortex/wiki/Full-Example-Walkthrough), the bundled
  zombie, traced end to end.
- [Ranged Example Walkthrough (Skeleton)](https://github.com/AzureDoom/AzureCortex/wiki/Ranged-Example-Walkthrough-Skeleton)
  and [Wall-Climbing Example (Spider)](<https://github.com/AzureDoom/AzureCortex/wiki/Wall-Climbing-Example-(Spider)>)
  , the other two bundled examples.
- Reference pages for [Behavior Trees](https://github.com/AzureDoom/AzureCortex/wiki/Behavior-Trees),
  [GOAP Planning](https://github.com/AzureDoom/AzureCortex/wiki/GOAP-Planning),
  [Sensing](https://github.com/AzureDoom/AzureCortex/wiki/Sensing),
  [Navigation and Pathfinding](https://github.com/AzureDoom/AzureCortex/wiki/Navigation-and-Pathfinding),
  [Wall Crawling](https://github.com/AzureDoom/AzureCortex/wiki/Wall-Crawling), and
  [Built-in Actions](https://github.com/AzureDoom/AzureCortex/wiki/Built-in-Actions).
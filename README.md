# Ping Mod

A CS2-style ping system for Minecraft 1.21.1 (NeoForge). Mark a location, block, item, or mob, and your nearby teammates can see it floating in 3D space with a color-coded label, an animated leader line down to the spot, and a rotating mini hologram of what was pinged.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft: 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)](https://www.minecraft.net/)
[![NeoForge: 21.1.176+](https://img.shields.io/badge/NeoForge-21.1.176%2B-DF8E1D)](https://neoforged.net/)

## Features

- **Press Z** (rebindable) to ping whatever you're looking at — block, dropped item, mob, player, or just a point in space.
- **Floating label widget** in HUD space with the target name, pinger name, skin-face icon, and distance. Renders cleanly with or without shader packs.
- **Rotating 3D hologram** of the pinged content (block model, item model, mob portrait, player face) anchored next to the label.
- **2D leader line** connecting the label to the pinged spot, length-scaled with distance so far pings stay visible.
- **Per-player ping color** — every player picks their own color in the in-game settings.
- **Hostile-mob indicator** — pinged hostile mobs are outlined in red regardless of your color, configurable.
- **Off-screen direction arrows** — pings outside your view show as edge-of-screen arrows pointing the right way.
- **Despawn detection** — if a pinged item is picked up or a mob is killed, the ping plays a quick close animation and fades.
- **Client-only fallback** — joining a server that doesn't have the mod still lets you ping; pings are visible only to you, with a one-time chat heads-up.

## Settings

Open the in-game config screen with **U** (rebindable) or via Mods → Ping Mod → Config. The screen is fully scrollable and includes a live preview that updates as you change settings.

- **Color** — RGB sliders + hex preview.
- **Display** — toggle the leader line; choose hologram position (None / Above / Left / Right); text-scale slider (0.5×–2.5×); hostile-mob indicator toggle.
- **Label Lines** — toggle the target name, pinger name, player head icon, and distance line independently.
- **Controls** — rebind Place Ping and Open Settings.

## Server Settings

Server administrators can tune the gameplay limits in `<world>/serverconfig/pingmod-server.toml`:

- `max_ping_distance` (16–4096, default 256) — block-distance cap from the pinger's eye to the ping target.
- `broadcast_radius_chunks` (-1 to 64, default -1) — chunk radius to broadcast to, or `-1` to use the server view distance.
- `cooldown_ticks` (0–600, default 10) — minimum ticks between pings from the same player.

## Installation

1. Install [NeoForge 21.1.176+](https://neoforged.net/) for Minecraft 1.21.1.
2. Drop `pingmod-<version>.jar` into your `mods/` folder.
3. (Optional) Drop the same jar on the server's `mods/` folder so pings broadcast to all nearby players. Without it on the server, the mod still works in client-only mode.

## Building from source

```bash
git clone https://github.com/Camyana/pingmod.git
cd pingmod
./gradlew build
```

The compiled jar lands in `build/libs/`.

To run a dev client:

```bash
./gradlew runClient
```

## License

[MIT](LICENSE)

# Glasscraft

Multiloader (Fabric + NeoForge) mod for Minecraft 26.2: a **Glasscutter** that safely
harvests glass, panes, glowstone and sea lanterns, plus **OptiFine-style connected
textures (CTM)**, optional clean glass, and Jade support.

## Features

- **Glasscutter** — cuts glass (every color), glass panes, glowstone and sea lanterns
  with guaranteed drops. Iron-tier durability; it never breaks — it wears out and
  becomes unusable until repaired with iron in an anvil.
- **Glasscutter Head** — the crafting core of the tool (iron, diamond and copper).
- **Connected textures** — glass, panes and sea lanterns placed together merge
  seamlessly, with correct outer corners, inner corners and cross junctions.
  Works with Sodium and without it.
- **Clean glass** (optional) — connected glass shows only its border, no inner
  texture shading.
- **Jade integration** (optional) — the overlay shows the Glasscutter as the
  effective tool for these blocks.

## Configuration

In-game config screen via **Mod Menu** (Fabric) or the mods list (NeoForge):
toggle the whole continuity system, per-material (glass, panes, glowstone, sea
lantern) and per-installed-mod.

CTM for other mods is **opt-in**: unlisted mods and blocks are left untouched
so their textures are not deformed. Enable a whole namespace with
`continuity.byMod: { "modid": true }`, or individual blocks with
`continuity.blockAllowlist: [ "modid:block" ]`. `blockDenylist` takes priority
over the allowlist, and the allowlist takes priority over `byMod`. Explicit
mod rules and block lists keep working; vanilla materials are unchanged.


optional integrations.

## Branches

- `main` — published releases
- `mc/26.2` — development line for Minecraft 26.2

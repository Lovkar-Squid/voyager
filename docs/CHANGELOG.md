# Changelog

## 0.3.1 - 2026-09-10 - hotfix: worlds without Exposure load again

- **Fixed: a world without Exposure would not load with 0.3.0** ("Errors in currently selected data
  packs prevented the world from loading"; the log says `Failed to parse thing: Unknown registry key
  ... exposure:camera`). The photographer's eleven crafter recipes all make Exposure items, and
  MineColonies throws its whole datapack away over one unknown item id. The recipes now live in a
  built-in datapack inside the jar that is only offered to the game when Exposure is installed, so
  without Exposure they are never seen - and the Departure Point works exactly as in 0.2.0.
  Thanks to GEN. WILL for the report, within the hour.

## 0.3.0 - 2026-09-10 - the Observatory and the Photo Booth (beta)

Two new professions built around Exposure and its Space and Expanded add-ons. Both are optional
buildings: without Exposure the Departure Point works exactly as in 0.2.0.

**The Observatory - the Astronomer**
- A night worker who lives at the building: out to the instrument at dusk, home at dawn with a
  plate; clouded out by rain. Five looks (Observatory, Stargazer's Keep, Sand Court, Skyward Station,
  Aperture Array), five levels each, every level of a look inside the same outline.
- The sky is Exposure: Space's 256 cosmic objects (or any datapack's), read as data - the mod links
  none of Exposure: Space's code. The building's lens tier grows with its level and its own research;
  what a night catches is weighted by the objects' own appearance chance, gated by the lens, and
  raised by tonight's sky event, which alone opens its exclusive objects. Rain clouds the watch out.
- The darkroom develops plates into prints: a first sighting or a composite becomes a genuine
  Exposure photograph of the night sky - stars, haze, the object large in the middle, the hills and
  the Observatory's own dome - that hangs in the study, files in albums and projects like any other.
  The colony is paid the objects' own rewards and keeps a sky book of everything it has recorded.
- Eleven studies (almanac, watch, optics, darkroom branches) paid for in nights of watching, some
  of which reach the Voyager's expeditions.
- The lookout: when the land within forty blocks offers a hill with a clear sky, the astronomer
  keeps the watch from there with the colony's camera and brings back a real photograph of the
  night sky with tonight's object in it; a setting sends one or two guards along as an escort.
- Star parties on event nights: the colony stays up, nobody is punished for the lost sleep.

**The Photo Booth - the Photographer**
- Crafts everything Exposure makes (film, cameras, stands, frames, albums, flashes) and, when the
  bench is quiet, photographs the colony: rendered on the server out of the world's own map
  colours, colonists in the picture, through whatever film, lens, filter and flash the colony
  fitted to its camera. The photographer lives at the studio.
- The colony chronicle: every building the builder finishes (and every build halfway up) is
  photographed for an album on the shelf, signed as a volume when it is full.
- From level 2 visitors come in for a portrait and, with Trade Post for MineColonies installed,
  pay for it in coins. Five looks, five levels, a gallery wall, a camera stand and a darkroom wing.
- The photographer says so, once a day, when there is no camera on the shelf.

**Both**
- Pictures go on the walls: every hut has photograph frames, and a finished picture goes into an
  empty one, or over the oldest, whose print goes to the shelf.
- Status lines and building descriptions for Colonist Errands 2.2.0, so the two can talk about
  their work with Talking Colonists.
- All fifty blueprints are audited for reachability (every worked block, every bed, every door, the
  darkroom's light) and were pasted through Structurize on a test server; both professions were
  run end to end on a headless colony before release.

## 0.2.0 - 2026-09-03 - first public beta

The Voyager profession for MineColonies: a colonist who leaves for the End and comes back with a haul.

**Building**
- *Departure Point* with two looks, **Launchpad** (a rocket on its pad) and **End Gate** (a ring gate
  of purpur and obsidian), levels 1-5 each, shipped as the *Voyager* structure pack. Both looks are
  alternatives of one building and every level has the same footprint, so upgrades never outgrow
  the outline you placed.
- The hut block has its own End-themed model; the Voyager wears an obsidian-and-purple suit.
- Storage in the control room / observatory at every level; the Rations tab picks the food that
  goes on expeditions.

**Expeditions**
- Launch windows every 3 days (level 1-2), 2 days (3-4) or daily (5); supplies per trip: 64
  cobblestone, 4 ender pearls, 16 torches, plus rations, a sword, a pickaxe and armor.
- Simulated expeditions with fights (endermen, shulkers, endermites - and the dragon with the right
  research), digging with the carried tool and a level-based haul of End loot; the Voyager eats
  rations to recover between fights.
- Better gear with every building level: iron (1), diamond (2), netherite (3), anything from 4.
- Everything is written to the hut's Expedition Log and to `latest.log` with a `[Voyager]` prefix.
- A Voyager lost in the End gets a grave by the Departure Point's console (the Undertaker can bury
  or resurrect them); their death shows in the Town Hall statistics.

**Effects and sounds**
- Launchpad: the crew boards through the hatch, engines ignite, the rocket lifts off and is really
  gone until it lands again (an empty rocket lands on its own if the crew is lost).
- End Gate: vortex, flash, a purple beam that carries the Voyager up (and down again before they
  return), a portal film that glows day and night and breathes End particles.
- Six own sounds: ignition, lift-off, landing approach, touchdown, gate charge, gate warp.

**Research** (University, Technology, after *Open the Nether*)
- *Reach for the Stars* unlocks the Departure Point, then: Void Insurance I/II, Starlight
  Navigation, Rapid Refit, Buddy System (two Voyagers per Departure Point), Ender Harvest, Shulker
  Whisperer, Dragon Hunt, Long-range Comms (reports in the colony chat), Return to Sender.

**Compatibility**
- NeoForge 21.1.x, Minecraft 1.21.1, MineColonies 1.1.1300+ (tested with 1.1.1368) and Structurize.
- Optional hook for Colonist Errands 2.1.0+ (Voyager lore and crew chatter with Talking Colonists).

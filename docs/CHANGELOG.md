# Changelog

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

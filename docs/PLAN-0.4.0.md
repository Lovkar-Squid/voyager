# Voyager 0.4.0 - "Trophies from the End": build plan

Your Voyagers now bring home something to remember each trip by: a souvenir named after a place in the
End, and at level 5 rare End-city treasure such as enchanted books and a map of the End. Server owners
get a config to tune expeditions. Before release, the paths nobody had exercised in play (Dragon Hunt,
Return to Sender, Rapid Refit, a full loop on a dedicated server) are run on a headless dedicated server.

## Owner decisions (16 Nov 2026) - binding

1. **Souvenir:** a NEW item with its own texture that can be PLACED (on a shelf or a table) as a
   decoration. The trip's name and story are in its tooltip.
2. **Names:** invented End place names from the approved list below. The tooltip also says who brought it,
   which expedition it was and what happened.
3. **Map of the End:** a lore item (a map with a drawn End and the trip's story), NOT a real explorer map.
   It must never generate or load End chunks.
4. **End-city books on level-5 trips:** a limited list WITHOUT Mending, about 5 % per trip, configurable.
5. **Death config:** the default stays as today (a Voyager can be lost). When an admin turns permanent
   death off, the Voyager comes home wounded and empty-handed, no grave, and keeps the gear.
6. **Observatory without Exposure: Space:** it stays VISIBLE (do not hide or unregister it), with a
   tooltip that the night sky needs Exposure: Space.
7. Fix the README header, which still says BETA 0.2.0.

### Approved place names (souvenir word in brackets)

| Place | Word |
|---|---|
| The Hollow Spires | Shard |
| Chorus Reach | Sprig |
| The Pale Shelf | Pebble |
| Purpur Steps | Fragment |
| Shulker Narrows | Shell |
| The Quiet Rim | Keepsake |
| Gatewatch Isle | Stone |
| The Listening Pillars | Shard |
| The Long Drop | Pebble |
| Obsidian Hook | Splinter |
| The Dragon's Wake | Scale (dragon trips only: only after a dragon fight the Voyager survived) |
| Elytra Point | Feather |
| The Broken Arches | Fragment |
| Lanternless Bay | Stone |
| The Far Isles | Keepsake |
| The Drifting Keel | Splinter |

The souvenir's name is "<Word> from <Place>", with a leading "The" lower-cased inside the sentence:
"Shard from the Hollow Spires", "Sprig from Chorus Reach", "Scale from the Dragon's Wake". Build it from
translatable parts (one lang key per place and per word, one pattern key) so it can be translated.

## Tooling (outside the repository, on the build PC)

- Build: `powershell -NoProfile -ExecutionPolicy Bypass -File D:/Claude/voyager-040/build-voyager.ps1`
  (plain javac + jar against the shared lib set; writes `voyager-<version>.jar` in the repo root, git-ignored).
- Headless dedicated server ("rig"): `powershell -NoProfile -ExecutionPolicy Bypass -File D:/Claude/voyager-040/run-rigtest.ps1 -Name <probe> [-Mode <word>] [-TimeoutSec <s>]`.
  NeoForge 21.1.249, flat world, MineColonies 1.1.1420 + Structurize + BlockUI + Domum Ornamentum
  (the owner's pack versions). Read the header of `run-rigtest.ps1` for the probe layout:
  `tools/<probe>/*.java`, `tools/<probe>/res/META-INF/neoforge.mods.toml`, optional `rigconfig/` and
  `rigconfig-<mode>/` (server config seeds), the mode word in `probe-mode.txt` in the server's working
  directory. A probe logs `[<probe>] CHECK <what>: OK|FAIL <detail>` lines and finally
  `[<probe>] RESULT <probe>: PASS|FAIL`, then halts the server (`server.halt(false)`). The runner exits
  0 only on PASS with no FAIL line and no ERROR/Exception line mentioning voyager or the probe; the full
  log is saved as `D:/Claude/voyager-040/riglogs/<probe>[-<mode>]-latest.log`. One run at a time.
- `tools/rigboot/` is the boot check (Voyager loads next to MineColonies on a dedicated server); it is the
  test command of every chunk.
- NeoForge 21.1 keeps SERVER configs in the server's `config/` folder (`config/voyager-server.toml`), not
  in `world/serverconfig`; a fresh run seeds it from `defaultconfigs/` (the probe's `rigconfig`).
- A working headless colony harness from 0.3.x exists: `tools/colonytest/ColonyTest.java` (Linux paths)
  and its Windows twin `D:/Claude/voyager-test/src/me/lovkar/voyagercolonytest/ColonyTest.java` (reads
  `colonytest-mode.txt` from the working directory, holds daylight, forces chunks, creates a colony with
  a fake-player owner, registers a town hall, pastes blueprints through Structurize, registers huts and
  hires citizens). Reuse its approach for colony probes.

## Chunks

### V1 - Server config, the death-off path, Observatory tooltip, README header, version 0.4.0

Goal: a NeoForge SERVER config (`ModConfigSpec`, `ModConfig.Type.SERVER`, file `voyager-server.toml`)
that tunes expeditions, with every default equal to today's behaviour except the new reward chances.

Keys (one section `expeditions` unless noted; exact names are the builder's, documented in README):
- trips per launch window per Voyager: the base of `BuildingVoyager.getMaxTripsPerPeriod` (default 1,
  range 1-10); the research bonuses still add on top.
- rations packed per trip and per crew member (default 16 = today's `RATIONS_TO_PACK`, range 1-64); used
  everywhere the 16 is used today (`EntityAIWorkVoyager`, `BuildingVoyager.rationStockWanted`) and in any
  UI text that states it.
- supply multiplier for the expedition plans (default 1.0, range 0.25-4.0): scales every input amount of
  the trip plan (rounded up, at least 1) for the requests, the "can this plan be fulfilled" check and what
  a launch consumes. At exactly 1.0 the plan's own recipe storage is used unchanged. The supply LIST
  itself stays in the datapack recipes `data/voyager/crafterrecipes/voyager/trip1..5.json`; README says
  admins replace those with a datapack to change it.
- trip length multiplier (default 1.0, range 0.1-10.0): scales the time away (the Rapid Refit and Star
  Charts reductions still apply; the existing floor of one find's time scales with it).
- dragon chance on level-5 trips with Dragon Hunt (default 0.10 = today's constant, range 0-1).
- permanent death (default true).
- section `rewards` (used by V2, defined now so V2 only reads them): souvenir chance per completed trip
  (default 0.10), End-city enchanted book chance per completed level-5 trip (default 0.05), map-of-the-End
  chance per completed level-5 trip (default 0.03), and the book enchantment list (a string list of
  enchantment ids; default: protection, fire_protection, blast_protection, projectile_protection,
  feather_falling, respiration, depth_strider, unbreaking, efficiency, fortune, silk_touch, sharpness,
  smite, looting, power, punch; never Mending, no other treasure enchantment, no curse in the default).

Permanent death off: in a fight, a blow that would drop the Voyager to 0 hp is not applied; instead the
Voyager is driven back: health set to a wounded value (a quarter of max health, at least 1 hp), the rest of
the itinerary and everything gathered on this trip are dropped (comes home empty-handed; the supplies
are spent), no grave, the death stat is not counted (count a separate retreat stat), Return to Sender
does not run, the citizen keeps all gear and remaining rations, the Expedition Log entry is not marked
killed, and the Voyager comes home through the normal return (rocket landing / gate arrival) and walks to
the hut. Log `[Voyager] <name> was driven back by a <mob> and comes home wounded, empty-handed`, and a
Long-range Comms line. The wounded state must survive a save and reload mid-trip (the travelling manager
respawns a traveller at full health: apply the wound on the live entity after the respawn, when the
fight is simulated, as fights are today). With permanent death on, nothing changes.

Observatory: stays registered and buildable exactly as today. Its hut item gets a tooltip line when the
mod `exposure_space` is not loaded (client side): "The night sky needs Exposure: Space - without it every
plate comes home blank." (wording may be tightened). No tooltip line when it is loaded.

README: replace the "BETA 0.2.0" header note with a short current one for 0.4.0 (what is tested where:
single-player and LAN since 0.2.0, the dedicated-server test of this release); add a "Server config"
section listing every key, its default and range. Version `0.4.0` in `resources/META-INF/neoforge.mods.toml`;
a `## 0.4.0 (unreleased)` entry at the top of `docs/CHANGELOG.md` (each chunk adds its lines there).

Tests: a probe `tools/configcheck/` (no colony needed) that reads the config on the rig and checks: with
its `rigconfig/` (non-default values for every key) the getters return those values; with mode `defaults`
(a `rigconfig-defaults/` folder that holds no file, or a mode that skips seeding) the getters return the
defaults above; the version string is 0.4.0; the en_us lang has the Observatory tooltip key; the
supply-multiplier helper scales a sample plan's inputs as specified (1.0 returns the same storage,
0.25 rounds up to at least 1); the wound helper (factor the "would this blow be fatal, what is the wounded
health" decision into a small pure method) gives the specified values.

Done when:
- the build passes;
- `run-rigtest.ps1 -Name rigboot` passes;
- `run-rigtest.ps1 -Name configcheck` and `run-rigtest.ps1 -Name configcheck -Mode defaults` pass.

Out of scope: souvenirs, books and map (V2; V1 only defines their config keys); the colony play-test (V3
exercises the death-off path end to end).

Builder: Opus. Reason: the config feeds the launch-window and trip-plan state and the death/grave/Return
to Sender path (core models and saves). Rigor: normal.

### V2 - Trophies: the souvenir, End-city books and the map of the End

Goal: the rewards decided above, rolled after the normal loot, so the existing loot tables stay as they are.

- Souvenir: one new block `voyager:souvenir` with its block item, a small non-full decoration (a keepsake
  on a little stand; facing property; placeable on any sturdy top face, so on shelves and tables), its
  own textures drawn by a new script `tools/gen_souvenir_textures.py` (own pixel art in the End palette:
  end stone, purpur, chorus; no copied third-party pixels; output committed). A flat item icon of its own
  is fine. It does not stack.
- The trip story is a registered data component (codec + stream codec, `DeferredRegister.DataComponents`):
  place id, Voyager name, expedition number, game day, the Departure Point look (Launchpad / End Gate) and
  what happened (e.g. "Fought 2 endermen and a shulker", "Met the dragon and lived", "A quiet crossing").
  The tooltip shows: "Brought home by <name>", "Expedition <n> - <look>, day <d>", and the event line.
  The name is set with the `item_name` component (anvil renaming still works).
- A placed souvenir keeps the story in its block entity and drops the same item with the same story
  when broken (loot table + implicit components); it survives a save and reload.
- Expedition number: a lifetime counter on the Departure Point (saved; existing buildings start from
  their completed-trip stat or 0).
- The events of a trip are tallied on the job while away and saved with it, so a reload mid-trip keeps them.
- Roll: when a trip completes successfully (the haul is packed at the end of the expedition), with the
  config chances: a souvenir (any level; the Dragon's Wake only and always when the Voyager beat the
  dragon on this trip, otherwise one of the other 15 places at random), and at level 5 an End-city
  enchanted book (one enchantment from the configured list at a random level from 1 to its max; unknown
  ids in the list are skipped with one warning) and the map of the End. A lost Voyager or a driven-back
  one (V1) gets none. The rewards go into the haul and through the citizen's inventory into the hut like
  every other find (never only in an AI field), appear in the Expedition Log entry, and with Long-range
  Comms in one chat line ("<name> brought home a Shard from the Hollow Spires").
- Map of the End: item `voyager:end_map` (not a vanilla map; no map data, no chunk loading), own texture
  from the same script, the same story component and tooltip, plus a line that it is "a hand-drawn chart
  of the outer islands".
- Lang for every new string; both new items in the Voyager creative tab.

Tests: a probe `tools/trophycheck/` on the rig: builds a souvenir for every place via the mod's own factory
method and checks the name and tooltip lines; the Dragon's Wake only for a dragon win; the default book pool
never yields Mending over many rolls and only yields listed enchantments; a stack round-trips through save
and parse with the registry access; places a souvenir block in the world, reads the block entity's story,
breaks the block and checks the drop carries the same story; the map item has the story and is not a
`MapItem`. A script `tools/check_trophies.py` checks the textures exist, are 16x16 RGBA PNGs, not empty and
referenced by the models, and every model, blockstate, loot table and lang key the new code names exists.

Done when:
- the build passes;
- `run-rigtest.ps1 -Name rigboot` passes;
- `run-rigtest.ps1 -Name trophycheck` passes;
- `python -I tools/check_trophies.py` prints OK;
- `run-rigtest.ps1 -Name configcheck` still passes.

Out of scope: real explorer maps, photographs (0.5.0), a shelf block of our own.

Builder: Opus. Reason: a new data component that must save, sync and survive placing and breaking (data
formats and saves). Rigor: normal.

### V3 - Play-test on the dedicated server: Dragon Hunt, Return to Sender, Rapid Refit, a full loop

Goal: exercise the never-tested paths on the headless dedicated server with a real colony, and fix every
bug found in the mod (each fix is a CHANGELOG line).

A probe `tools/expedition/` built on the colony harness's approach: a colony on the flat world, a level-5
Launchpad pasted from the bundled pack and registered (building level 5), a Voyager hired, gear (diamond
or better sword, pickaxe and armour) and rations and the plan's supplies in the hut, research effects
applied (Reach for the Stars, Rapid Refit, Long-range Comms, Dragon Hunt, Return to Sender as the mode
needs), and the config seeded per mode through `rigconfig-<mode>/` (short trips via the trip length
multiplier; forced chances where a mode needs them). Then the AI runs for real and the probe reads the
world, the building and the log. Modes, each its own run:
- `loop`: one complete trip on the Launchpad: boarding, departure, the rocket blocks stowed at lift-off
  and back after the landing, the Voyager away (travelling) and back visible, the haul unloaded into the
  hut, the trip counted; Rapid Refit's shorter time matches the formula in `launch()` (read from the
  departure log line); with forced reward chances, a souvenir, a book (not Mending) and a map arrive in
  the hut with the story component set.
- `dragon`: Dragon Hunt with the dragon chance forced to 1: the dragon is on the itinerary, the fight runs,
  and either the Voyager wins (dragon head and dragon's breath in the haul, a Dragon's Wake souvenir when
  the souvenir chance is forced) or is lost (then the `lost` checks apply). Both outcomes pass if the path
  is correct.
- `lost`: the Voyager must die (no armour, no sword, low health or a forced hard fight): Return to
  Sender puts the pack into the hut's racks, a grave is raised (or the "no room" warning), the rocket
  comes back on its own, nothing is left in an AI field.
- `wounded`: permanent death off and the same deadly setup: the Voyager comes home wounded (health at
  the wounded value after the return), keeps the gear, no grave, no death stat, an empty-handed haul.

Done when:
- the build passes;
- `run-rigtest.ps1 -Name rigboot` passes;
- `run-rigtest.ps1 -Name expedition -Mode loop -TimeoutSec 1500` passes;
- `run-rigtest.ps1 -Name expedition -Mode dragon -TimeoutSec 1500` passes;
- `run-rigtest.ps1 -Name expedition -Mode lost -TimeoutSec 1500` passes;
- `run-rigtest.ps1 -Name expedition -Mode wounded -TimeoutSec 1500` passes;
- `run-rigtest.ps1 -Name trophycheck` and `run-rigtest.ps1 -Name configcheck` still pass;
- `docs/IDEAS.md` "Verified in play" names what this release verified on a dedicated server, and the
  "Not yet exercised" line is updated.

Out of scope: screens and GUIs, the client-side effects' look (particles and sounds run but nobody sees
them headless), the End Gate look (verified in 0.2.0), the Observatory and Photo Booth.

Builder: Opus. Reason: drives MineColonies' AI state machine, travelling manager and save state headless,
and fixes land in the core expedition state machine. Rigor: normal.

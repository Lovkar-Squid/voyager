# The Observatory — build plan and mechanics

9 Sep 2026. The research, the licence and the five decisions are in `OBSERVATORY.md`; this is what
we actually build, in what order, and how each piece works.

One rule runs through all of it, and it comes from what Temno told us: **we read data, we do not
link code.** Their cosmic objects and events are JSON loaded by the ordinary datapack system, and
that is the only surface they support. Everything below is built on it.

---

## 0. Phase 0 — the spike: **DONE, and all four questions pass**

Run 9 Sep on a purpose-built headless server (`/root/skytest`) carrying only NeoForge, Exposure,
Exposure: Space, Exposure: Expanded, Architectury, Prism and a throwaway `skyspike` mod. No game
had to be launched by hand.

The spike reads `data/<any namespace>/cosmic_object/*.json` and `.../cosmic_event/*.json` with
vanilla's own `SimpleJsonResourceReloadListener`, registered through `AddReloadListenerEvent`, and
logs what it found on `ServerStartedEvent`.

**1. Is their JSON reachable as ordinary datapack data from another mod?** Yes.

```
256 cosmic object(s) from {exposure_space=256}, 3 event(s)
  by lens tier: {bad=12, normal=43, good=81, excellent=75, sculk=45}
  by type: {star=75, planet=36, nebula=36, galaxy=25, moon=17, asteroid=14, cluster=13,
            comet=13, anomaly=8, probe=7, pulsar=4, black_hole=4, station=3, quasar=1}
  a bad lens reaches 12 · normal 55 · good 136 · excellent 211 · sculk 256
  e.g. exposure_space:vega [star] tier=NORMAL chance=0.3 analysis=1500t sky=(255.0,-60.0) rewards=3
```

Every number matches the offline analysis exactly, and individual objects come through with their
tier, chance, analysis time, sky position, rewards and wiki link intact.

**2. Does our parser survive all 256 real files?** Yes — 256 read, none skipped, no exception.

**3. Does it pick up a third party's objects?** Yes. A datapack of ours dropped into the world with
one object gave:

```
257 cosmic object(s) from {exposure_space=256, voyager=1}
  ... sculk 45 -> 46, anomaly 8 -> 9
```

**4. Does it degrade with Exposure: Space absent?** Yes, and cleanly. With every sky mod removed:

```
no cosmic objects in any datapack - the Observatory stays shut
tonight: an ordinary night
  a bad lens reaches 0 ... a sculk lens reaches 0
```

**Zero errors in that run.** The feature turns itself off; nothing crashes, nothing complains.

### Two things the spike taught us that the offline reading could not

**A malformed datapack file costs a log line and nothing else.** A deliberately broken JSON file in
our test datapack never reached our code at all — vanilla's loader rejects it first
(`Couldn't parse data file voyager:broken_file`) and carries on. We do not have to defend against it.

**Their reader is stricter than ours, and that matters for phase 7.** The same object our reader
accepted was rejected by theirs:

```
[ExploredSpace/Cosmic] Failed to parse cosmic object voyager:end_gate_arch:
    No key catalog_texture in MapLike[...]
```

So the two readers are independent: an object can be visible to our Observatory and invisible to
their album. **If we want our own objects to appear in their catalogue too, every field their schema
requires must be present — `catalog_texture` above all — and the texture has to be a real PNG we
ship.** Worth knowing before we write eight of them.

### Where the code lives

The classes are already in Voyager and compiling — `me.lovkar.voyager.sky.SkyObject`, `SkyEvent`
and `SkyData` — plus an admin command `/voyagersky [lens]` that prints the summary and what a given
lens reaches. Only the `skyspike` entry point was throwaway. **Nothing about phase 0 has to be
written twice.**

Remaining known gap, written down rather than hidden: **we cannot read which event *their* mod
thinks is running tonight** — that lives in their saved data. `SkyData.tonight` rolls its own,
deterministically from the world seed and the day, using their weights. It will not always agree
with their Night Analyzer. That is the one thing worth asking the author to expose one day.

---

## 0b. What their data actually looks like

Measured offline over the 256 files, and confirmed in game by the spike above. **The schema is
completely uniform: every one of the eleven fields is present in every one of the 256 objects.**

| | |
|---|---|
| Objects / events | 256 / 3 |
| By lens tier | bad **12**, normal 43, good 81, excellent 75, sculk 45 |
| Types | star 75, planet 36, nebula 36, galaxy 25, moon 17, asteroid 14, cluster 13, comet 13, anomaly 8, probe 7, pulsar 4, black hole 4, station 3, **quasar 1** |
| appearance_chance | 0.018 – 0.95, median **0.05** |
| base_analysis_ticks | 700 – 7200 (0.6 – 6 minutes) |
| Rewards | 39 distinct items; by volume XP bottles 1187, diamond 178, amethyst 162, glowstone 115, echo shard 99 |
| Event-exclusive objects | 6, across the three events |

**Two things in there change the design.**

1. **A bad lens can see only 12 objects.** If building level hard-gated the tier, a level-1
   observatory would exhaust its whole sky in a week and then do nothing. So level must *not* be a
   tier gate on its own — L1 should reach **bad and normal** (55 objects), and the tiers above are
   opened by the Optics research rather than by masonry. Level then buys **watches per night** and
   analysis speed.
2. **The rewards are milder than I assumed.** I warned about netherite; the actual bulk is
   experience bottles and mid-tier gems. So the balance lever is not the reward table, it is **how
   often a night succeeds** — and with a median appearance chance of 0.05, most nights already find
   nothing. Pacing lives in the roll, not in the payout.

Also worth knowing: analysis is fast (six minutes at the very worst), so **the analyser is never the
bottleneck** — the night is. And with quasar 1, black hole 4 and pulsar 4, the type collections
idea has natural short sets to finish as well as a 75-star marathon.

## 1. Order of work

| Phase | What | Why here |
|---|---|---|
| **0** | The spike above | ✅ **done** — all four questions pass |
| **1** | **Observatory blueprints, L1–L5** | Longest lead time, and the thing you want to look at. Python voxels → your Blender → Structurize |
| **2** | The Observatory itself: job, building, AI, plates, analyser, colony catalogue | The core loop. Playable on its own |
| **3** | Research: Optics, The Night, The Plate | Turns the loop into progression |
| **4** | **Star Charts** — the Voyager link | The reason the two halves are one mod |
| **5** | The Space Gallery (Trade Post optional) | Needs prints to exist first |
| **6** | The Photo Studio | Independent of the sky; can slip without hurting anything |
| **7** | Our own cosmic objects, including the End-only one | Best last: by then we know the format cold |

Phases 2 and 5 are the ones with real unknowns. 1, 3, 6 and 7 are work, not risk.

---

## 2. The core loop, in detail

### The night

The Astronomer works from dusk to dawn. Each **watch** (one attempt, a few minutes of game time)
resolves like this:

1. **Can he see anything at all?** Rain or snow ends the watch. So does a solid roof — the
   telescope needs `canSeeSky` from the dome.
2. **How dark is it?** Three things dim the sky, and all three are free from vanilla:
   - **moon phase** (`level.getMoonPhase()`) — a full moon washes out faint objects; a new moon is
     the best night of the month;
   - **light pollution** — the light level around the building. A big bright colony hurts its own
     observatory, which is a genuinely interesting tension: a colony gets brighter as it grows;
   - **weather**, above.
3. **What is up tonight?** Read the events: tonight's event (if any) sets a luck multiplier, an
   analysis-speed multiplier, and unlocks its `exclusive_objects`.
4. **What can this telescope reach?** Filter the objects by `required_tier` against the lens the
   building has (research + building level decide which lenses the colony may fit).
5. **Roll.** Weight each candidate by `appearance_chance`, multiplied by luck, divided by the sky
   penalty from step 2. A miss is a normal outcome and the astronomer says so.
6. **A hit produces an undeveloped plate**, carrying: the object id, the lens tier used, the event
   (if any), the in-game night, and the citizen's name.

### The plate — and why it exists

Exposure renders a photograph on a **player's client**. There is no server-side renderer, so a
colonist alone in a loaded colony cannot make a real picture.

So the plate is our own item and it holds everything except the image. The colony's whole economy
runs on plates: they analyse, they catalogue, they pay out, they can even be sold unprinted at a
lower price. Nothing waits for you.

**Developing** is what needs a person. When a player is in the darkroom, the astronomer develops
plates into real Exposure photographs — through Exposure's own pipeline, so the result is a genuine
photograph item, not a lookalike. The limitation becomes the reason the building has a darkroom,
and coming home to a rack of undeveloped plates is a small pleasure rather than a compromise.

### The analyser

Our own block, our own progress, modelled on the honest parts of theirs: a plate goes in, it grinds
for `base_analysis_ticks` (adjusted by lens tier, research and tonight's event multiplier), and out
comes the identification plus the object's `rewards` into the colony's warehouse.

The object then enters **the colony's catalogue** — its own record, shown in the Observatory GUI,
with the object's name, type, the citizen who caught it, and the night.

**The player's own album fills the normal way**: take a developed photograph, put it in Exposure:
Space's own Photo Analyzer, and their mod does what it always does. We bypass nothing.

### What the building level means

Revised after measuring their data (section 0b): **level does not gate the lens tier** — with only
12 objects behind a bad lens, a tier-gated L1 would run out of sky in a week. Research opens the
tiers; the building buys time and quality.

| Level | What it buys |
|---|---|
| 1 | One watch a night. Bad + normal lenses (55 objects) |
| 2 | One watch, and the colony may fit whatever lens research has opened |
| 3 | **Two** watches. Analysis −15% |
| 4 | A dome that turns: the blocked arc of sky shrinks. Analysis −25% |
| 5 | **Three** watches, an apprentice, and the smallest sky penalty in the game |

---

## 3. Mechanics worth calling out

**Siting matters.** Every object has a fixed sky position (`sky: {yaw, pitch, spread}`). An
observatory with a mountain to the south genuinely cannot see southern objects. Placing the
building becomes a decision rather than a formality, and the build-tool preview can show which arc
of sky is blocked.

**Light pollution.** The colony's own lanterns are the astronomer's enemy. Put the observatory on
a hill outside the walls and it works better — which pulls the colony's shape in an interesting
direction and gives *Dark Skies* research something real to fix.

**The moon is a calendar.** Faint objects only on the darker phases. A colony learns to expect the
good nights, and *Ephemeris* research turns expectation into a forecast.

**A rare catch is a story, not a number.** When an object with a low `appearance_chance` is caught,
the colony records who caught it and on what night, the town is told, and the citizen gets a small
lasting happiness bonus. That is what the colony's catalogue is *for*.

**Failure is content.** Clouds, a full moon, a bright town, nothing up tonight — the astronomer
comes back with nothing and says why. A mechanic where every night pays is a mechanic where no
night matters.

---

## 3b. Three rules he asked for — and what the code says about each

Read out of `minecolonies-1.1.1368-1.21.1.jar`, not guessed.

### "Star-party colonists must not be punished for not sleeping"

Solvable cleanly. Not sleeping does not *apply* a penalty — **sleeping RESETS one**.
`EntityAISleep.sleep()` calls:

```java
citizenData.getCitizenHappinessHandler().resetModifier("slepttonight");
```

`slepttonight` is a time-based happiness modifier that grows the longer since it was last reset. So
at dawn after a star party we simply call the same thing for everyone who attended — one supported
API call on `ICitizenHappinessHandler`, which lives in MineColonies' **api** package. No mixin, no
coupling, nothing fought. The night out costs them nothing.

The same call is what keeps **the astronomer** happy on his permanent night shift: his shift ends at
dawn, and ending it resets the modifier.

### "The astronomer lives at the observatory and is not counted as awake"

Half of this is easy and half of it is a wall, so here it is straight.

**Easy:** he gets a bed in the observatory blueprint and sleeps there **by day**, the way a guard
lives at their tower. He never competes for a citizen bed in the residences, and with the reset
above he is never unhappy about the hours.

**The wall:** the "All citizens are tucked into bed" message. `CitizenManager.onCitizenSleep()` is:

```java
for (ICitizenData c : citizens.values()) {
    if (!c.isAsleep() && !(c.getJob() instanceof AbstractJobGuard)) return;
}
// ... "All citizens are tucked into bed."
```

The exemption is a **hard `instanceof AbstractJobGuard`**. No interface, no flag, no registry — a job
is either a guard job or it blocks the line. Three ways out, and I would take the third:

1. **Make the Astronomer a guard job.** It works and it is one word. It also drags in combat AI,
   guard-tower assumptions and the guard research. Wrong shape for a man with a telescope.
2. **A mixin into `CitizenManager`.** Works, and it is exactly the coupling we just decided against
   for Exposure — a core mixin that breaks on their next refactor, on our users' machines.
3. **Accept it, because it costs one chat line and nothing else.** I traced the flag:
   `areCitizensSleeping` is set by `updateCitizenSleep(boolean)` and read in exactly one place — the
   latch that stops the message repeating. `Colony` clears it each morning. **It gates no gameplay
   whatsoever.** So the astronomer being up at 2 a.m. means the message does not appear that night,
   and that is the entire consequence.

My recommendation is 3, with 2 available behind a config toggle if the missing line annoys you in
play. What you actually asked for — he lives there, keeps no bed in town, and is never punished for
the hours — is fully delivered either way.

### "The observatory must never run out of work"

A real risk: 256 objects is finite, and a colony that catalogues them all would leave a worker
standing in a dome doing nothing. So the building has **three kinds of work, and only the first can
run out.**

**1. Discovery — finite.** New objects. 256 of theirs, plus ours. This is the headline and it ends.

**2. Production — endless, demand-driven.** The Gallery wants stock; postcards sell by the handful;
prints wear out of display cases when sold. A fully-catalogued colony still photographs every clear
night, because the shop has empty frames. Their own mod already blesses this: re-analysing an
object with a *different* photograph pays a share (`repeatRewardPercent`), so a second plate of Vega
was never waste.

**3. Programme work — endless, procedurally generated.** This is the part that makes an observatory
feel like an observatory rather than a collection game:

- **Monitoring series.** Pick a variable star or a comet; a series of plates of the same object
  across several nights completes a *study*, which pays and then starts a new one. Real astronomy is
  re-observation, and this is endless by construction.
- **Sky surveys.** A standing assignment: sweep one arc of sky (the `sky.yaw` bands make this
  natural). Completion pays and a new arc is assigned.
- **Seeing records.** Even a night that finds nothing produces a record of the conditions — and
  those records are what feed *Ephemeris* and the Voyager launch-window forecast. **A blank night
  still has an output**, which is the neatest answer to the whole question.
- **The Almanac.** Each in-game month consumes the month's observations into a book.
- **Better plates.** *Comparative Astronomy* combines two plates of one object into a better print,
  so a duplicate is an upgrade rather than a duplicate.

The AI picks work in that order: anything undiscovered first, then whatever the Gallery is short of,
then the standing programme. **The dome is never dark and the worker is never idle**, and none of it
depends on us inventing filler.

## 4. More ideas — what else this makes possible

These all fall out of data we already have. Roughly ordered by how much they add per hour of work.

### Cheap and strong

1. **Type collections.** Fifteen object types. "All the nebulae", "all the planets" — completing a
   type is a colony achievement with a reward and a line in the album.
2. **The Almanac.** Each in-game month the astronomer writes a real book item: what was seen, by
   whom, which nights were clear. A keepsake that costs almost nothing to generate and that players
   will screenshot.
3. **Postcards.** Cheap prints run off in bulk from a catalogued object. Low value each, sold to
   visitors by the handful — the Gallery's bread and butter under the rare pieces.
4. **A lit dome.** The observatory glows at night. Colonists nearby get a small happiness bonus:
   the town is proud of it.
5. **Naming rights.** First catch of an uncatalogued object lets the colony name it. The name shows
   in the colony album and on the print, for that world, for ever.

### Medium, and distinctive

6. **Star parties.** On an event night — meteor shower, aurora — off-shift colonists gather at the
   observatory instead of going to bed. Colony-wide happiness, one night, announced in advance once
   *Ephemeris* is researched. This is the mechanic I would build first after the core loop; it is
   the one that makes a colony feel like it has a culture.
7. **Wildlife and place photography.** Exposure's `Frame` records the biome, the structures and the
   entities in shot. A photograph of a warden, or one taken in an ancient city, is worth real money
   at the Gallery. Suddenly the player has a reason to carry a camera, and the Studio has stock that
   did not come from the sky.
8. **Photo requests.** A visitor asks the Gallery for a print of a particular type, or the Studio
   for a portrait. A small errand with a coin reward — and it plugs straight into Colonist Errands.
9. **Comet returns.** Some objects are once-in-a-lifetime. The observatory can *predict* the return
   and put it in the almanac, and the colony waits for it.
10. **Apprenticeship.** A second astronomer at L4+, trained by the first, the way Voyager's Buddy
    System already works. Two watches in parallel.

### The Voyager link — the reason this is one mod

11. **Star Charts.** Each catalogued object shortens expeditions slightly and improves finds. The
    Observatory is how you make the Departure Point good.
12. **Expedition photography.** A Voyager takes a camera. What comes back is a photograph *of the
    End* — different biome, different structures in the frame — and the Gallery pays accordingly.
13. **An object only reachable from an expedition.** Our own cosmic object, in their format, with a
    sky position and a tier that no overworld telescope can resolve. The only way to catch it is to
    send somebody. Now the two halves of the mod genuinely need each other, which is decision 5
    turned into a mechanic.
14. **Launch windows from the sky.** The astronomer's forecast decides which mornings the rocket
    may go. Weather and moon feed the launch schedule — one system, two buildings.

### Bigger, later

15. **The Gallery's reputation.** The rarer the pieces on display, the more visitors the town draws
    and the better the prices. A reason to keep a masterpiece rather than sell it.
16. **Travelling exhibition.** Trade Post's Station ships a crate of prints to another colony for a
    run, and it comes back with coins and a bump in reputation.
17. **An astronomer visitor type.** A colony famous for its sky attracts visiting astronomers, who
    buy prints, bring a rare plate to trade, or teach — a small research-speed bonus while they stay.
18. **Colony-to-colony catalogue.** Two of your colonies each keep a catalogue; the Station carries
    copies. Completing the album becomes a thing you do across a world rather than in one town.

---

## 5. What I would *not* build

- **Anything that writes into their player catalogue.** Decided, and their answer confirms it.
- **A second camera.** Exposure's camera is excellent and it is MIT; ours would only be worse.
- **Energy for the analyser.** Theirs has optional energy and AE2 support. A MineColonies building
  that needs power is a MineColonies building most players will not use.
- **Deep coupling to Trade Post.** Same lesson as Exposure, applied before it costs us: the Gallery
  should be **our** building with **our** AI, using Trade Post only for the things that are stable —
  its coin *item id* and MineColonies' own visitor system. Subclassing their Marketplace would tie
  our release schedule to theirs for very little gain. **This is a decision I would like you to
  confirm**, because it is a change from the first draft.

---

## 6. Open questions

1. **Trade Post coupling** — our own Gallery building using their coins (my recommendation), or
   built on their Marketplace?
2. **The six rarity band names** — Common / Notable / Rare / Remarkable / Extraordinary / Once in a
   Lifetime, or something with more character?
3. **Studio: its own hut, or a room in the Gallery?** Its own hut is more work and more identity.
4. **Light pollution** — good tension, or annoying? It is the one mechanic here that tells a player
   their colony is in the way.
5. **How generous should the rewards be?** Their objects pay netherite scrap and diamonds. A
   colonist producing those overnight needs a long clock on it, and I would rather start stingy.

---

## 7. Phase 1 — the blueprints: **DONE** (9 Sep 2026)

**Five looks, five levels each, twenty-five blueprints.** The Departure Point ships a Launchpad and an
End Gate and lets you pick in the build tool; the Observatory does the same with four. Generated by
`tools/observatory.py`, validated by `tools/check_observatory.py`, written by
`tools/build_observatory.py` into `resources/blueprints/voyager/voyager/observatory/`.

| Look | File | What it is |
|---|---|---|
| **Copper Dome** | `observatory1-5` | pale stone, a verdigris copper dome on a drum, a refractor on a fork. The default. |
| **Stargazer's Keep** | `keep1-5` | medieval: a stone tower with battlements and a red-tiled spire, a shuttered opening facing north, and an **armillary sphere** in the yard instead of a lens. |
| **Sand Court** | `sandcourt1-5` | a desert court with **no dome and no roof over the instrument at all** — a great stone gnomon (a Samrat Yantra) with its dial in the paving, an arcade with glazed tilework, and domed pavilions. |
| **Skyward Station** | `station1-5` | modern: a white dome on a quartz block, iron rails, and **radio dishes** on masts. The look that sits next to the Launchpad's purpur. |
| **Aperture Array** | `array1-5` | futuristic: no dome and no tube - a **great ring** on two pylons with a tinted lens in it and a glowing inner rim, on a dark deepslate block seamed with light. Purpur, so it belongs to the same mod as the Launchpad. |

Every look uses the same box `((-11, 0, -12), (11, 25, 12))` and puts its hut block at `(0, 2, -8)`
once aligned, so an upgrade never moves the building and a player can even switch look on an
upgrade. Levels run roughly 900 blocks at L1 to 2300 at L5 including the pad.

### Level by level (the same story in five voices)

1. a room to keep the plates dry, and the instrument out in the open
2. the darkroom arrives — `exposure:lightroom` — and the instrument gets a plinth
3. the dome / the tower / the full gnomon and arcade appears, with the night console under it
4. a dome you can work in, two lightrooms, the analysing bench outside
5. the thing the colony is known for: the great dome or tower, the meridian circle, three
   lightrooms, and the gallery wall of the colony's own plates

### The palette, and why (Copper Dome; the other four swap the stone, not the plan)

- **Body:** `domum_ornamentum:beige_stone_bricks`, quoins and plinth `cream_stone_bricks`. Domum
  Ornamentum is a hard dependency of MineColonies, so these blocks are in every colony already and
  the building sits next to the rest of the town instead of looking like a guest. The Keep is
  `stone_bricks` and red tile, the Sand Court smooth sandstone on a red apron with glazed tile, the
  Station quartz and glass.
- **Dome:** `waxed_oxidized_copper` on a `waxed_oxidized_cut_copper` turning ring. Verdigris is what
  an observatory looks like and it is the one green in Minecraft that reads as *old metal*.
- **Darkroom:** `black_brick_extra` with a `tinted_glass` ceiling. A darkroom should be dark.
- **Dome floor:** `blue_brick_extra` with a copper compass rose and an amethyst centre — the sky,
  laid into the floor.
- **Terrace:** deepslate paving with a compass rose, a slab kerb, and `squarepillar` columns.

### Exposure, in the building

`exposure_space:analyzer` is the astronomer's desk — the block's own model file calls it that.
`night_analyzer` is the console in the dome. `exposure:lightroom` is what the darkroom is *for*
(one at L2, two at L4, three at L5). Photograph frames go up on the study walls from L3, the
colony's own plates. The Observatory is only registered when Exposure and Exposure: Space are
present (see OBSERVATORY.md §5), so the blueprint is free to depend on them.

### Domum Ornamentum's mix-and-match blocks — how they are written

This needed a change to `voxel.py`. A DO shingle/timber-frame/panel/pillar/vanilla-shape block does
not carry its material in the block state: the block is one id and a **block entity** holds a map of
texture slot to block id. MineColonies' own blueprints are full of them (caledonia/university4 has
352). The shape is exactly:

```
{x, y, z, id: "domum_ornamentum:materially_retexturable",
 textureData: {"minecraft:block/oak_planks": "domum_ornamentum:beige_stone_bricks"}}
```

The slot names are the placeholder textures in DO's models, so they are per block, not per material
— `voxel.DO_SLOTS` lists them, read off real MineColonies blueprints. `Structure.mixed(x, y, z,
state, *materials)` places one and records its materials; `to_blueprint` writes the block entities.

**A material outside DO's tag for that slot renders as missing, silently** — the same trap as a
block id that does not exist. The tags are in the DO jar under
`data/domum_ornamentum/tags/block/`: `stairs_materials`, `slab_materials`, `wall_materials` take
`#domum_ornamentum:bricks` (so DO's own bricks work); `pillar_materials` and `shingles_roof` do
**not** — they take `#domum_ornamentum:default`, which is vanilla stone, so the columns are
`polished_deepslate` and `calcite` and the shingles are `deepslate_tiles`. `check_observatory.py`
now also fails a DO block that has no material at all.

### What the renders taught us

Round after round of Blender renders, and each one killed a design that looked fine in code:

1. **A 45-degree tube of full blocks is not a telescope**, it is a lump of copper. Nor is a vertical
   one — that is a chimney. What reads instantly is the long, near-horizontal tube of a refractor on
   a fork. Two colours of copper in the same tube read as a barber pole; one metal only.
2. **`cone()` is not a dome.** A dome needs each course's radius off a circle. And the radius has to
   be a *whole* number of blocks: taking it straight off the circle gave courses whose radii
   differed by a tenth of a block, single cells flicked in and out of the shell, and the dome looked
   chewed. The profile is now a half-ellipse a third taller than a hemisphere, radius never stepping
   in by more than one block a course.
3. **A hollow roof of stairs on a 45-degree slope is a floating shell** — two courses touch only at
   their corners. Solid under the tiles; the stairs are the surface.
4. **A top-half stair used as an eave leaves its lower half open** — a black slot right round the
   building at roof level. Use a slab.
5. **Copper is an accent.** A full copper course plus copper quoins plus a copper cornice on a small
   building is most of the elevation, and the first render came back almost entirely orange.
6. **The dome slit must stop below the crown**, or it takes the apex and both tip blocks with it and
   the finial stands on air.
7. **A circle sampled by angle is a ring of floating blocks.** `int(round(cos*r))` gives cells that
   meet only at their corners. Step along one axis and fill the gap in the other — `circle()` in
   observatory.py — and every cell shares a face with the next. The armillary and the dish rim both
   needed it.
8. **A dome that steps in by one block a course all the way to the top is a mitre, not a dome.**
   Stop the profile at radius three and cap it flat.
9. **A railing round all four sides of a paved court is a fence across the yard.** Rail the open
   edge only.
10. **The instrument has to be the second thing you see, and only the second.** The Sand Court's
    first version flanked its gnomon with two tall quadrant walls that read as archways and buried
    the one shape that makes the look. They went on the ground as a dial.

### Still to do before this is testable in game

The hut block `voyager:blockhutobservatory` and the building type `voyager:observatory` do not exist
yet — that is Phase 2. The blueprints reference them, which is the right way round: the pack is
ready for the Java side to catch up.

---

## 8. Phase 2a — the Java side, first pass: **DONE** (9 Sep 2026, `voyager-0.3.0-alpha.1.jar`)

The blueprints referenced a hut block and a building type that did not exist. They do now, so the
Observatory can be placed, built and staffed in game — which is what makes every later phase
testable instead of theoretical.

| File | What it does |
|---|---|
| `block/BlockHutObservatory.java` | the hut block. Same two overrides as the Departure Point's: `AbstractColonyBlock` hardcodes the "minecolonies" namespace in `getRegistryName()`, and MineColonies' block-entity type only accepts MineColonies huts. One `voyager:colonybuilding` type is now registered for **both** hut blocks, so either building's blueprints carry the same tile entity. |
| `colony/BuildingObservatory.java` | max level 5, `getSchematicName()` picks the look out of the blueprint path (the Departure Point's trick, with five names instead of two), remembers nights worked and plates waiting, and reads the `scope` tag the blueprints now carry. |
| `colony/JobAstronomer.java` | the job, with a one-word `Status` for Colonist Errands to read. |
| `colony/ObservatoryModules.java` | one worker; **Knowledge** decides what they can identify, **Focus** how long they hold a watch. |
| `ai/EntityAIWorkAstronomer.java` | the night: decide → walk to the instrument → watch → carry the plate in. Its states are **our own enum implementing `IAIState`** — that interface is public, so an addon does not have to borrow ids that mean something else (the Voyager had to take the Nether Miner's). |

**The blueprints tag their instrument.** Every look now writes a `scope` tag at the pier of the
telescope, the plinth of the armillary, the foot of the gnomon or the mast of the ring, and the AI
walks to it. Missing tag falls back to the hut block, so the AI can never stall on an old blueprint.

**Voice lines.** `ModSoundEvents.CITIZEN_SOUND_EVENTS` gets an entry for `astronomer` (the
Researcher's lines) at common setup. A job with no entry there is an NPE in the server tick — that
was the 0.1.0 crash and it is the first thing to get wrong twice.

### Still to do

1. **Conditional registration.** Exposure and Exposure: Space are declared *optional* in
   `neoforge.mods.toml`, but the hut registers regardless. Without them the blueprints lose the
   analyser, the lightroom and the frames (they become air). Gate the building entry on
   `ModList.get().isLoaded("exposure_space")`.
2. **Plates as real items.** `nightWorked()` counts them; nothing carries one yet. The plate should
   become an Exposure photograph, created by registry id rather than by linking their classes.
3. The research tree, the Star Charts, the Gallery and the Studio — sections 1-4 above.
4. The astronomer's own outfit; right now they wear the Voyager's suit.

---

## 9. Phase 2b — the plates: **DONE** (9 Sep 2026, `voyager-0.3.0-alpha.2.jar`)

The night now produces something. This is the core loop of section 2, in its first honest form.

**`sky/SkyRoll.java`** — what the astronomer caught. The roll is weighted by the objects' own
`appearance_chance` and gated by the lens the colony has ground (`level - 1` on the tier ladder
BAD → NORMAL → GOOD → EXCELLENT → SCULK). An event night multiplies the odds by its own `luck` and
opens its exclusive objects — the only nights some of them can ever be caught, so an event is
worth staying up for. The roll is deterministic from the world seed, the day and the citizen id,
so it does not reshuffle on a reload.

**The six rarity bands are derived, not hand-sorted** across 256 objects: the tier you need to see
it, plus how shy it is once you have the lens. Event-exclusive is its own top band, because "you
had to be there" is the rarest thing a sky can offer.

| Band | Colour | Roughly |
|---|---|---|
| Common | grey | bad/normal lens, shows up often — the Moon, the bright planets |
| Notable | white | normal lens, middling chance |
| Rare | aqua | good lens, or a shy object with a normal one |
| Remarkable | blue | excellent lens territory |
| Extraordinary | light purple | sculk lens, and shy with it |
| Once in a Lifetime | gold | only exists while tonight's event runs |

**Two items, one record.** `voyager:exposed_plate` is what comes home at dawn and shows nothing;
`voyager:star_plate` is the developed print, and its tooltip names the object, its type, its band
and the night it was taken. What the plate records lives in vanilla's `minecraft:custom_data`
rather than a component type of our own, because the object it names is a `ResourceLocation` that
may belong to Exposure: Space, to us, or to a datapack nobody has written yet. **Nothing is looked
up until a tooltip asks**, so a plate of an object whose datapack has since been removed is a plate
of "something", not a crash.

**The darkroom is now real work.** The blueprints tag their lightroom (`darkroom`); by day, an
astronomer carrying exposed plates walks there and develops them one at a time, which is what gives
the job a day shift and the darkroom a reason to be in the building. Level 1 has no darkroom, so
the plates simply keep until the colony builds one.

**A blank plate is a real result.** If the lens cannot resolve anything, the plate still comes home
blank - and that is the colony's cue to grind a better one.

### Still to do

1. **Conditional registration** on Exposure: Space (unchanged from Phase 2a).
2. **The Gallery and the Studio** — the plates have nowhere to go yet but a rack.
3. The research tree, Star Charts, the colony catalogue.
4. The astronomer's own outfit.

---

## 10. Phase 2c — the research branch: **DONE** (9 Sep 2026, `voyager-0.3.0-alpha.3.jar`)

Nine researches under the University's Technology branch, generated by
`tools/gen_observatory_research.py`. The root, **The Long Night**, hangs off MineColonies'
`memoryaid` at level 3, asks for a Library 3, and unlocks the hut.

| Research | Level | Needs | What it does |
|---|---|---|---|
| **The Long Night** | 3 | Library 3 | unlocks the Observatory |
| **Lens Grinding** | 4 | Observatory 2 | +1 lens tier — bought with `exposure_space:normal_telescopic_lens` |
| **Lens Grinding II** | 5 | Observatory 4 | +2 — bought with their `good_telescopic_lens` |
| **Star Party** | 4 | Observatory 3 | on an event night the whole colony stays up **and nobody takes the sleep penalty** |
| **Ephemeris** | 5 | Observatory 4 | tomorrow night's event announced a day early |
| **Darkroom Discipline** | 4 | Observatory 3 | a second plate through the darkroom in the same step |
| **Second Exposure** | 5 | Observatory 4 | a re-shot object is still worth something |
| **Comparative Astronomy** | 6 | Observatory 5 | two plates of one object combine |
| **Apprentice Astronomer** | 4 | Observatory 4 | a second astronomer |

**The lenses are the honest coupling.** Exposure: Space's own telescopic lenses buy our lens
researches — their progression items, through the item registry, with none of their code linked.
In a pack without them those researches cannot be bought, and without their datapack the sky is
empty anyway: `SkyData.summary()` says so in the log and every plate comes home blank.

### Marko's three rules, in code

1. **Star parties must not penalise sleep.** `EntityAISleep` clears the `slepttonight` happiness
   modifier and anybody who did not sleep keeps it. The astronomer now clears it themselves at the
   end of every watch — unconditionally, because a job whose whole point is the dark should never
   have been punished for it — and Star Party extends the same clearing to **every citizen** on an
   event night.
2. **The astronomer lives at the observatory and is not counted in "all colonists are tucked in
   bed".** Half done: the penalty is gone (above). The count itself is `CitizenManager.onCitizenSleep`
   with a hard `instanceof AbstractJobGuard`, which needs a mixin — this build uses plain `javac`, so
   that part is still open.
3. **The Observatory must never run out of work.** Held: a blank plate is a real result, so a
   clear night always produces something, and the day shift always has the darkroom.

### Live effects

`lens_grinding` feeds `BuildingObservatory.lensTier()`, `apprentice` feeds the worker module's crew
size, `star_party` and `darkroom_discipline` are read by the AI, `ephemeris` announces the night.
`second_exposure` and `comparative_astronomy` are data waiting on the colony catalogue.

---

## 11. Phase 2d — research *in* the Observatory, and research that reaches the Voyager: **DONE** (9 Sep 2026, `voyager-0.3.0-alpha.4.jar`)

Two things Marko asked for: that the Observatory be a place you can *do* research, and that some of
those researches change how the Voyager works.

### 11.1 A second research building

The research tab is `BuildingModules.UNIVERSITY_RESEARCH`, an ordinary view-only module producer —
any building can carry it, and the Observatory now does. The tab alone is not enough, though:

- `TryResearchMessage extends AbstractBuildingServerMessage<BuildingUniversity>`, and its synthetic
  bridge method does a real `checkcast` to `BuildingUniversity` before the handler runs. A building
  that carries the tab but is not a University crashes the moment you click "research".
- `ILocalResearchTree.attemptBeginResearch(Player, IColony, BuildingUniversity, IGlobalResearch)`
  takes the same type.

So `BuildingObservatory extends BuildingUniversity`. What that inherits is exactly what we want:
the tab, `onColonyTick` advancing whatever is in progress, `onSuccess` (which re-applies effects to
every citizen and announces it), and `processOfflineTime` at level 3 and up. It also inherits one
thing we do not want.

**The researcher-shaped hole.** `BuildingUniversity.onColonyTick` does

```java
getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == ModJobs.researcher.get())
```

and uses that module's crew size to decide how many researches may advance this tick. An Observatory
is staffed by astronomers, so the lookup finds nothing — and `getModuleMatching` **throws** on null,
once per colony tick, forever.

The fix is a narrow override of `getModule(Class, Predicate)` in `BuildingObservatory`: do exactly
what the interface default does, and if nothing matched, ask whether the filter *would have* taken a
researcher module — by testing it against a throwaway probe module built with `ModJobs.researcher` —
and if so, hand it the astronomers instead. Any other filter still gets the honest answer, including
`null`. The astronomers are then the crew whose size decides how fast the colony's research moves,
which is the same bargain the University makes; an Observatory with nobody on the roof advances
nothing.

Worth knowing: because `IGlobalResearch.canResearch(IBuilding, tree)` is handed *this* building, the
Observatory gates research on the **Observatory's** level, and it can start anything in the tree, not
only its own branch. That is deliberate — it is a second university with a different staff, and a
colony that would rather train astronomers than researchers can.

### 11.2 Three researches that cross to the Voyager

Bought at the Observatory, spent at the Departure Point. This is the join Marko wanted between the
two professions: the colony that watches the sky crosses it better.

| Research | Level | Needs | Parent | What it does |
|---|---|---|---|---|
| **Star Charts** | 5 | Observatory 3 | Lens Grinding | expeditions take 20 % less time, **on top of** Rapid Refit |
| **Deep Field** | 6 | Observatory 5 | Star Charts | every expedition brings back one find more |
| **Launch Window** | 6 | Observatory 5 | Ephemeris | grants `starlight_navigation` **level 2** — a third trip per launch window |

- `star_charts` and `deep_field` are new effects, declared in the Observatory's generator and read
  by `VoyagerResearch` (that is where the Voyager code looks, so that is where they live).
- `EntityAIWorkVoyager` sums Rapid Refit and Star Charts and clamps the total at 80 %, so the two
  branches stack without ever collapsing the trip to nothing.
- `deepField()` copies one **non-token** find onto the itinerary: the astronomer found a place, not
  a monster, so Deep Field never adds a fight.
- Launch Window needed `starlight_navigation` to grow a second level (`[1.0, 2.0]`);
  `BuildingVoyager.getMaxTripsPerPeriod()` already reads the strength, so nothing else changed.
- Deep Field costs Exposure: Space's **sculk** telescopic lens — the top of their lens ladder buys
  the top of ours, same coupling as the Lens Grinding line.

### 11.3 Still open

- Conditional registration when Exposure: Space is absent (today it only degrades: empty sky, blank
  plates, one log line).
- The Space Gallery / Photo Studio — the plates still have nowhere to go but the rack — and the
  colony catalogue that `second_exposure` and `comparative_astronomy` wait on.
- Rule 2's other half (the "everyone is in bed" count) still needs a mixin.
- The astronomer wears the Voyager's suit.

---

## 12. Phase 2e — the astronomer's own coat: **DONE** (9 Sep 2026, `voyager-0.3.0-alpha.5.jar`)

Until now the astronomer wore the Voyager's space suit, because `ASTRONOMER_MODEL_ID` was just
`MODEL_ID`. Two people in one mod should not share a wardrobe, and you should be able to tell at
fifty blocks which of them is on the roof.

`client/AstronomerModel.java` + `tools/gen_astronomer_skin.py`, registered as its own model type
(`voyager:astronomer`) with its own layer. `SimpleModelType.getTextureBase()` is just the model
id's path, so the textures are `minecolonies:textures/entity/citizen/default/astronomer{male,female}1{_a,_b,_d,_w}.png`
and the icons go in every style folder, exactly like the suit's.

**The outfit.** Somebody who works outdoors, at night, in winter: a hood with a flip-up brass and
amethyst lens parked on the temple, a cream wool scarf wound twice, a heavy indigo coat with a
brass button placket and the colony's star badge, leather gloves and boots, a cloak down the back
(a shade lighter than the coat, with folds, a clasp bar and a worn hem), and a satchel of glass
plates on the left hip. The satchel is `isWorking()`-gated the way the Voyager's life-support pack
is: the plates come out when the watch starts. The faces come from MineColonies' **Student** skins
— there is no researcher skin in the jar, and the University's own people are the closest thing a
colony has to an astronomer.

**`tools/preview_skin.py`** is new and worth keeping: a flat front/back paper-doll of a skin at
10x, pasted from the box faces the model actually uses. It is not a render, but it is enough to
catch what pixels hide — the first cloak was the same colour as the coat and read as a flat slab,
and the first hood lens sat where half the skin variants keep an eye.

```bash
cd tools
python3 gen_astronomer_skin.py
python3 preview_skin.py astronomer voyager   # writes tools/out/skin_<who>_<gender>.png
```

---

## 13. Phase 2f — the colony's catalogue: **DONE** (9 Sep 2026, `voyager-0.3.0-alpha.6.jar`)

Second Exposure and Comparative Astronomy shipped in alpha.3 as data waiting on a record that did
not exist. This is that record.

**`sky/SkyCatalogue.java`** — one line per object: how many plates the colony has of it, the best
band it was ever caught in, the nights it bookends, and whether its two best have already been
combined. Stored on each `BuildingObservatory` (a building already serializes itself; an addon has
no clean per-colony store without a mixin) and **merged across every Observatory in the colony** on
the way out, so a second Observatory does not start a second book. It never looks an object up: an
id whose datapack has gone stays in the book as an id, which is what real records do.

Reading it is server-side — `getServerBuildingManager()` — so a client asking gets an empty list
instead of an exception.

**Where it bites: `EntityAIWorkAstronomer.readTheBook()`.** The darkroom now checks the book before
printing:

| The colony has seen it | Research | The print |
|---|---|---|
| no | — | plain, full XP |
| yes | none | duplicate, **half** XP, tonight's band |
| yes | Second Exposure | duplicate, full XP, **the better of the two nights** on the print |
| yes | Comparative Astronomy | **composite**: one band brighter than either night, triple XP, a line in the colony chat |

A composite marks the object's entry `combined`, colony-wide, so it can happen once per object and
never be farmed. `SkyPlate` gained a `sky_mark` tag (`FIRST` / `DUPLICATE` / `COMPOSITE`) and shows
it in the tooltip.

**Degrading without Exposure: Space.** Already coherent, now also audible: the server log warns
rather than informs when no datapack ships cosmic objects, and the astronomer tells the colony's
managers once — the watch still runs, the darkroom still works, every plate comes home blank, and
now somebody knows why.

**`/voyagersky catalogue`** prints the book of whichever colony you are standing in: objects,
plates, best band, first and last night, and which have been combined. Added because "look at the
rack and count" is not a way to test this.

### Still open

- The Space Gallery / Photo Studio: the plates have a book now, but still nowhere to hang.
- Rule 2's other half (the "everyone is in bed" count) still needs a mixin.

---

## 14. Phase 3 — five things Marko caught in game, and the Observatory's own research (9 Sep 2026, `alpha.7` and `alpha.8`)

First play-test of the Observatory. Five findings, all real.

### 14.1 Twenty-five levels instead of five looks (`alpha.7`)

The build tool listed one "Observatory" with **levels 1 to 25**. The cause was one line: `build()`
tagged every blueprint `name=Observatory`.

Structurize groups the blueprints in a folder by `getCustomName(blueprint, fallback)`, which reads
a `name=` tag on the **anchor block** at `BlockPos.ZERO` and only falls back to the file name with
the level digits stripped. One name for twenty-five files means one alternative with twenty-five
"levels". The Departure Point had this right from the start (`name=Launchpad` / `name=End Gate`);
the Observatory did not. Now: **Copper Dome, Stargazer's Keep, Sand Court, Skyward Station,
Aperture Array**, five alternatives of five levels, chosen in the build tool.

Worth writing down, because it is not documented anywhere: the build tool's four lists are
folders → blueprints (labelled by the hut's `INamedBlueprintAnchorBlock.getBlueprintDisplayName()`)
→ **alternatives** (the `name=` tag) → levels (`ILeveledBlueprintAnchorBlock.getLevel`, which for a
MineColonies hut is the digits in `blueprintDataProvider.schematicName`).

Marko also asked for the mod to sit higher in the Switch Pack window. It cannot: Structurize keys
that window's categories on a `TreeMap` of the pack's **owner**, and for a pack inside a mod jar
the owner comes from `modFile.findResource("blueprints", modId)` — the mod id, alphabetical, always
after `minecolonies`. With the looks inside one pack he does not need that window at all.

### 14.2 A hut block wearing the missing texture (`alpha.7`)

Four texture references in `blockhutobservatory.json` pointed at files that do not exist:
`deepslate_brick_wall` and three `waxed_*_copper`. Two whole families of vanilla blocks have no
texture of their own — a **wall** is drawn with the texture of the block it is cut from, and every
**waxed** copper reuses the unwaxed one — and Minecraft says nothing about it. That was the desk's
legs and the whole telescope. `tools/check_models.py` now walks every model in `resources/assets`
and resolves every texture against the client assets jar: 31 references, 0 missing.

### 14.3 The camera stand (`alpha.7`)

`exposure:camera_stand` is an **entity**, not a block, so `voxel.py` gained an `entities` list
(the same one MineColonies uses for its armour stands and item frames) and `Structure.entity()`.
`observatory.place_camera()` finds a free square with something solid under it near the tagged
instrument, so every look at every level gets a real tripod beside the telescope without five
hand-picked coordinates going stale. The builder will ask for one camera stand in the materials.

### 14.4 Iron doors (`alpha.7`)

Colonists cannot open them. Skyward Station → `warped_door`, Aperture Array → `mangrove_door`.

### 14.5 The Observatory's own research (`alpha.8`)

Marko: *"za research sem mislil bolj da ima observatori svoj research sistem in ne istega kot
university"*. Fair, and it turned out to be the better design.

**The idea that makes it its own system: a study is paid for in nights, not hours.** The University
buys research with a timer and a researcher standing in a room. An Observatory buys it with nights
the astronomer actually kept the watch — a clouded night buys nothing, an Observatory with nobody
on the roof buys nothing. The currency is the thing the building already produces, which is not
something MineColonies' timer can express.

| Piece | What it is |
|---|---|
| `sky/SkyStudy.java` | one study, read from `data/voyager/sky_study/*.json` |
| `sky/SkyStudies.java` | the datapack reload listener, same shape as `SkyData` |
| `colony/SkyStudyModule.java` | the book: what is finished, what is running, how many nights in |
| `colony/SkyStudyModuleView.java` | the client's copy and the hut tab |
| `client/SkyStudyWindow.java` + `assets/voyager/gui/skystudy.xml` | the GUI |
| `network/StartStudyMessage.java` | our own packet, re-checked end to end on the server |
| `tools/gen_sky_studies.py` | eleven studies in four branches |

Eleven studies: **Optics** (Lens Grinding I/II, Deep Field), **The Almanac** (Star Party, Star
Charts, Ephemeris, Launch Window), **The Darkroom** (Darkroom Discipline, Second Exposure,
Comparative Astronomy) and **The Watch** (Apprentice Astronomer). One at a time, gated on the
Observatory's level and on its parent.

Consequences, all good:

- `BuildingObservatory extends AbstractBuilding` again. The whole `BuildingUniversity` inheritance
  and the researcher-shaped `getModule` redirect from 11.1 are **gone** — they existed only to
  satisfy `TryResearchMessage`'s cast, and we no longer send that packet.
- `ObservatoryResearch.strength()` reads the buildings' own books, merged across the colony, and
  every existing call site is unchanged.
- Star Charts, Deep Field and Launch Window moved out of `VoyagerResearch` into
  `ObservatoryResearch`, so the Departure Point reads them from the Observatory's book.
- The University's Technology tree keeps exactly one Voyager entry for the Observatory: the hut
  **unlock**, because that is where a player looks for a hut they cannot place yet.
- Datapack contents do not reach clients on their own, so the whole book travels in the module's
  `serializeToView` — a few hundred bytes, and it means a dedicated server's packs decide what the
  GUI shows.

---

## 15. Phase 3b — the night shift, and the astronomer's own roof (9 Sep 2026, `alpha.9`)

Marko, watching the first astronomer: *"zdaj gre delavec ki tam dela spat ponoči (ravno takrat ko
bi naj delal)"*. The whole building did nothing, because dusk fell and the one colonist whose job
is the dark walked off to a bed.

### 15.1 Why this needed a mixin — the only one in the mod

`CitizenAI.calculateNextState` is where MineColonies decides what a citizen does next, and the
decision to sleep is written straight into that method with exactly one exemption:

```java
if (job instanceof AbstractJobGuard guardJob) { ...never reaches the sleep branch... }
```

Guards work at night because their branch returns before the sleep logic. There is no hook on the
job, none on the building, none on a schedule — `IJob.canAIBeInterrupted()` is consulted for
sickness, not for bedtime. Making `JobAstronomer` extend the guard job would take the exemption
and drag in combat AI, guard-tower requirements and a citizen the colony counts as defence: a much
bigger lie than a five-line injection.

So: `mixin/CitizenAINightShiftMixin.java`, `@Inject(at = HEAD, cancellable = true)`, plus
`voyager.mixins.json` and a `[[mixins]]` block in `neoforge.mods.toml`. No Gradle needed —
`sponge-mixin.jar` compiles it and NeoForge 1.21.1 runs on official names, so there is no refmap.

The injection only ever **forces work**, only for a `JobAstronomer`, only at night, and it stands
aside for everything MineColonies would rightly interrupt work for: hunger (`shouldEat()`),
illness, and a raid. In daylight the astronomer sleeps like anybody else.

**And it stands aside once the watch is kept.** `JobAstronomer` now remembers the night it last
worked (`keptWatchTonight`), so as soon as the plate is exposed the astronomer is allowed to go to
bed rather than stand on the roof until dawn. That also stops the one failure mode this kind of
injection invites — a citizen ping-ponging between "go to sleep" and "no, work".

### 15.2 The Observatory is the astronomer's home

Asked directly, the answer was no, so now it is: `ObservatoryModules.WORK` is a
**`WorkAtHomeBuildingModule`** instead of a plain worker module (assigning the job assigns the
home), the building carries `BuildingModules.BED`, and the study has a bed from level 1.

That was Marko's rule from the very first plan and it is the arrangement that makes sense:
somebody who works nights should not be walking home across a sleeping town at dawn, nor holding a
bed in a house they are never in at night. It also gives the colony a bed back.

The bed is placed by **searching**, not by coordinates: `place_bed()` looks for two free squares
side by side with a floor under them, air above and a ceiling within a few blocks — which is what
tells an indoor room from the open terrace. The fixed spot tried first did not survive, because
the Stargazer's Keep's tower cuts through the study's back wall from level 3 up and a bed with a
wall where its head should be drops on placement. A generated check over all five looks and five
levels caught it before it shipped, and now guards it.

### 15.3 Still open

- The "everyone is tucked in bed" count (`CitizenManager.onCitizenSleep`) still has its hard
  `instanceof AbstractJobGuard`. Exempting the astronomer there would mean re-implementing that
  loop in a mixin, and what it drives is a chat line - not worth the breakage risk yet.
- The Space Gallery: the plates have a book and a catalogue, still nowhere to hang.

---

## 16. Phase 4 — automation, real photographs, and the Photo Booth (9 Sep 2026, `alpha.10` – `alpha.12`)

Marko: *"probajva naredit čim več stvari da bo automatable da lahko oni delajo sami in craftajo
stvari ki jih rabijo sami"*, and then the one that changed the whole design: *"kaj pa če probava
nekak po svoje naredit da lahko kolonisti slikajo?"*

### 16.1 The sky pays for being looked at (`alpha.10`)

Every cosmic object in Exposure: Space carries its own `rewards` list — amethyst, diamonds,
netherite scrap, film, bottles of experience. `SkyObject` had been parsing that list and throwing
it away since the spike. Now the darkroom pays it out:

| The study | What the colony is paid |
|---|---|
| first sighting of an object | the full reward |
| a composite (Comparative Astronomy) | the full reward again — two nights are a new result |
| a repeat, with Second Exposure, 8+ nights later | a quarter |
| a repeat otherwise | nothing |

The cooldown and the share are the same bargain Exposure: Space makes with a player, and a colony
should not get a better deal than the person who wrote the sky.

### 16.2 The colony buys its own studies (`alpha.11`)

Studies used to come out of the **player's pockets**, which is the exact opposite of what a colony
is for. Now the GUI only *chooses*: the module records the study as `wanted`, and the astronomer's
`stockTheStudy()` looks in the Observatory's own racks, puts in a request for whatever is missing
so a courier brings it, and starts the study the moment it is all there. Nobody carries a
telescopic lens up a hill by hand any more.

### 16.3 A colonist takes a photograph (`alpha.11`, `alpha.12`)

The received wisdom is that this cannot be done: Exposure renders an image from the camera's
viewpoint **on the client** and uploads the pixels, and a server-side NPC has no renderer. Both
halves of that are true. The conclusion is not.

**Two ways in, and we use both.**

1. **Point at a picture that already exists.** A photograph's `photograph_frame` component holds an
   `ExposureIdentifier`, and an identifier is either a stored exposure *or a texture* — which is
   how Exposure projects images that were never photographed. Every cosmic object ships a
   `catalog_texture`, so when the Observatory develops a plate of the Crab Nebula it prints a
   genuine Exposure photograph of Exposure: Space's own picture of the Crab Nebula. Only for a
   first sighting or a composite, or the gallery fills with the same nebula five times.
   (`compat/ExposurePhotographs`)

2. **Render it ourselves, on the server.** An exposure is nothing but `width, height, byte[] pixels`
   and a palette id — and Exposure ships a palette that *is* Minecraft's map colours. Minecraft has
   been drawing maps server-side since 2011 by asking a block for its `MapColor`. So the
   photographer casts **one ray per pixel** out of their own eye through a pinhole camera, asks
   whatever it hits for its map colour, shades it by which face was hit and how far away it was,
   and writes the packed palette index straight into the byte array. `ExposureRepository.save()`
   stores it; the photograph points at the id. It is a real photograph of whatever the colonist was
   pointing at, taken on the server, in Exposure's own palette.
   (`compat/ExposureCamera`)

96×96, drawn **eight rows per AI step** — ten thousand raycasts do not belong in one tick, and a
photograph that takes six seconds while the photographer stands still with the camera up is a
better thing to watch than one that appears instantly. The camera goes into their main hand, the
arm comes up (`startUsingItem`), end-rod particles tick, and Exposure's own shutter sound plays
when the last row is drawn.

**The wall against their code.** Exposure is an optional dependency, so both classes above are the
only ones that touch it, and nothing reaches them except through `sky/SkyPhotograph` and
`photo/ColonyCamera` — each checks `ModList.isLoaded("exposure")` before the JVM has a reason to
resolve anything, and turns any surprise into "no photograph today" plus one line in the log.

### 16.4 The Photo Booth and the Photographer (`alpha.12`)

A crafter profession, because a crafter is what the colony actually needs: **twelve Exposure
recipes** (film of four kinds, cameras, camera stands, lightrooms, frames of two kinds, albums,
flashes, the interplanar projector), so nobody hand-crafts an Exposure item again. The building is
a studio with the colony's camera stand in it and one or two darkrooms behind it, in the same five
looks as the Observatory — `photobooth.py` is 150 lines because it is built out of the
Observatory's own pieces, and it validates through the same gates.

When the bench is quiet the photographer walks into the studio, picks the nearest citizen as a
subject, and takes the photograph described above. One every five minutes, and only if the colony
has a camera — which the photographer can, of course, make.

### 16.5 Still open

- The **Planetarium** (see `docs/PLANETARIUM-AND-PHOTOBOOTH.md` §3): the analyzer loop, the framing
  wall and the showing. The reward payout in §16.1 already does the part that mattered most.
- **Exposure: Expanded's filters** as the photographer's study tree, mirroring the Observatory's.
- **Portraits from a citizen's own skin**: `ExposureData` takes any pixels, so a proper portrait
  composed from the sitter's skin PNG is possible and would beat a raycast for a close-up.
- The "everyone is tucked in bed" count still has its hard `instanceof AbstractJobGuard`.

## 17. Phase 4b — people in the picture, the camera's fittings, and a photographer you can recognise (10 Sep 2026, `alpha.13`)

Marko has not tested any of the `0.3.0` alphas yet, so this morning's pass was about making the
first test land well: what would look wrong in the first five minutes, and what would make a
photograph worth keeping.

### 17.1 What would have broken

- **Recipe JSON.** MineColonies' custom recipes take `"id"` keys (`inputs: [{id, count}]`,
  `result: {id, count}`), not `"item"`. All twelve photographer recipes were silently invalid.
- **Registry lookups.** `BuiltInRegistries.ITEM.get(id)` hands back *air* for a missing key, never
  null, so every `== null` guard around it was dead. They are `getOptional(id).orElse(null)` now.
- **The shoot never started.** It hung off an `IDLE` `AITarget` that the crafting AI does not
  reliably pass through. The photographer now overrides `decide()`: the bench first, and only when
  the crafting AI itself says `IDLE` does the camera come off the shelf.
- **`startUsingItem` on a camera shows nothing** — the item has no use duration. Replaced by a real
  arm pose (§17.4).

### 17.2 People in the picture

The raycast in §16.3 only knew about blocks. `ExposureCamera.open()` now freezes the entities in
front of the lens (within 80 blocks, as `Sitter` boxes — the whole box and the top 28 % as a head)
and each pixel's ray is clipped against them as well as the world; the nearest hit wins. Entities
are painted two-tone by type (a villager's robe and face, a cow's hide and head, a citizen's coat),
so a portrait of a colonist is recognisably a person and not a brown smear. Up to four of them go
into the frame as `EntityInFrame`, which is what makes Exposure's own tooltip say who is in it. The
print is named for what it shows: *Portrait of Mira* if someone sat for it, otherwise the place.

### 17.3 Light, time of day, and the fittings on the colony's camera

Every ray is shaded by which face it hit and how far it travelled, then dimmed by the light level
where it landed (`getMaxLocalRawBrightness`): a dark room comes out dark, unless the camera has a
**flash** fitted and the subject is within fourteen blocks, or the film is high-sensitivity, which
lowers the threshold. The sky is painted by the world clock — pale blue by day, orange and violet at
the golden hour, a dark blue with hashed stars at night, grey in rain.

The photographer uses **the colony's actual camera item**, so whatever the colony fitted to it is
what the picture gets (`Fittings`, read from the item's data components):

| Fitting | Effect on the picture |
|---|---|
| Black-and-white or Game Boy film | greys, and the exposure is typed `BLACK_AND_WHITE` |
| High-sensitivity film | shoots in lower light |
| Telescopic lens | narrow field of view, 0.25–0.5 by tier — a real zoom |
| Panoramic lens | wide field, 1.6 |
| Exposure: Expanded filters | applied to the developed pixels: flip, desaturate, blur, pencil (Sobel edges), a tint by colour word |
| Flash, mode ON/AUTO | lights the subject, the frame carries `FLASH` |
| Shutter speed dial | stops difference from default brightens or darkens |
| Zoom dial | the field of view, when there is no lens deciding it |

Film is handled the way a photographer would: a full roll is ejected onto the shelf for the
darkroom, a fresh roll comes off the shelf and goes in (a request goes out if there is none), and
each exposure is written **onto the roll** (`FilmRollItem.addFrame`) as well as printed — so the
colony's film actually fills up and the darkroom recipes have something to develop.

### 17.4 A photographer you can recognise

- **Outfit** (`gen_photographer_skin.py`): dark cap, cream shirt, brown vest, a strap across the
  chest with a brass buckle, dark trousers, boots, and a satchel on the back — eight textures
  (both genders × the four MineColonies suffixes) plus the citizen icons, 128×64 like the astronomer.
- **Model** (`client/PhotographerModel`): the cap gets a peak and the back a bag, both drawn from
  the right half of the 128-wide sheet. When the job's render meta contains `camera` both arms
  come up to the face and follow the head — the viewfinder pose. The AI sets the meta while it
  aims and exposes and clears it when the camera goes back, so you can see who is taking a picture
  from across the colony.

### 17.5 Housekeeping

- `stockTheStudy()` (the Observatory buying its chosen study) runs every fifth decision tick, and
  the photographer counts the racks for a camera every hundred game ticks instead of every decision.
- The raycast remains bounded: 96×96 pixels, eight rows a step, one `level.clip` plus the frozen
  sitter boxes per pixel.

### 17.6 Next (`alpha.14`, promised)

- **The lookout.** The astronomer picks a spot with a clear sky near the Observatory — a blueprint
  `lookout` tag if there is one, else the highest open block within reach — takes the colony's
  camera with them, and shoots a real night-sky photograph there for a better plate.
- **A night escort.** An Observatory setting that names a guard tower; its guard walks out with the
  astronomer and stands watch until dawn (`setRallyLocation` / guard task settings, to investigate).

## 18. Phase 5 — the Photo Booth earns its keep: sitters who pay, and the colony chronicle (10 Sep 2026, `alpha.14`)

Marko, this morning: *the photographer works by day and can photograph other colonists and, say,
document the colony's development; and visitors could come in to be photographed, and you get
revenue for the Trade Post.* The design doc had both under "Visitor Vanity" and "Portraiture"
(`docs/OBSERVATORY.md` §4); this is them built, with one change of mind about coupling.

### 18.1 Trade Post without linking Trade Post

Trade Post keeps the colony's balance as **one statistic on MineColonies' own statistics
manager** — `current_balance`, in value units, a Trade Coin being `tradeCoinValue` of them (1000
by default). Its Marketplace, Resort and thrift shop all read and write that number, and its own
vacation income is credited exactly the way we now do it:

```java
colony.getStatisticsManager().incrementBy("current_balance", amount, colony.getDay());
```

So paying the colony is a MineColonies API call, guarded by `ModList.isLoaded("mctradepost")`, and
the only thing borrowed from Trade Post is the coin value, read by reflection from `MCTPConfig`
with 1000 as the fallback (`compat/TradePostLedger`). Nothing of theirs is on our classpath, the
Space Gallery question in §6 answers itself, and a pack without Trade Post loses nothing but the
money: the visitor still sits, and the print goes on the shelf instead.

### 18.2 Visitors come in for a portrait (`photo/VisitorSitting`)

MineColonies' visitors are a small state machine on the entity (IDLE, WANDERING, SITTING, COMBAT)
that MineColonies leaves open — Trade Post sends them shopping by adding transitions to it. We do
the same. Every colony tick the Photo Booth (level 2 and up, with a photographer and a camera on
the shelf) gives each visitor who does not have it yet the idea of a portrait: from IDLE or
WANDERING, by day, roughly once in ten thoughts, if nobody else has the chair, the visitor **books
the sitting**, walks to the **sitter's mark** in front of the gallery wall, stands there looking at
the photographer, and waits — a minute at most. A booked sitter outranks the bench: the
photographer drops what they were about to craft, takes the camera to the **photographer's mark**
behind the tripod, and shoots. The visitor pays and leaves with the print (the negative stays on
the roll for the darkroom); the colony's balance goes up; `portraits_sold` ticks in the building's
statistics; the chat says who bought what for how much. One sitting per visit, one sitter at a
time, a booking nobody turned up for is dropped after a couple of minutes, and a photographer who
cannot come (no camera, night without a flash) lets the visitor go rather than keep them standing.

Price: half a coin plus a quarter per level, half again for colour — one coin for a
black-and-white portrait at level 2, nearly two for colour at level 5.

**The studio got marks.** Three tagged squares in a line: `sitter` (in front of the gallery wall,
which is the backdrop), `studio` (the tripod), `photographer` (behind it). The validator now
insists on all three being free, in line, with nothing standing between sitter and photographer;
the bed search keeps two squares either side of that line clear (one, in the smallest booth), and
the level-1 studio grew a block wider to make room. The tripod itself is excluded from the ray
cast, so it is never in the picture even though the photographer shoots through its square.

### 18.3 The colony chronicle (`colony/ChronicleHook`, the album)

MineColonies posts `BuildingConstructionModEvent` on its own event bus when a builder finishes a
work order. For a BUILD or an UPGRADE, every Photo Booth in that colony is told, and the building
joins the **chronicle queue** (persisted). When the bench is quiet the photographer picks a
**viewpoint**: from the building's corners, the eight compass points at a distance that takes the
whole facade in, on standable ground within a few blocks of the building's own level, scored by
climb and by the walk from where they stand. They walk out, turn to the building's centre exactly
(body and head — MineColonies' look control turns gradually and only the head, and a photograph
needs the whole citizen pointed the right way this tick), and expose with the field of view widened
just enough to fit the building (`ColonyCamera.open(..., fovScale)` — the lens is overruled for
this one shot, never narrowed). The print is titled *Town Hall, level 3 (day 12)* and goes into
**the album**: an Exposure album on the shelf, first free page, with the same line as the note.
When the sixteenth page fills, the album is **signed** — *Chronicle of Riverbend, vol. II*, by the
photographer — which makes it Exposure's finished, un-editable volume, and the next chronicle
photograph asks for a new album (which the photographer can, of course, make). A building torn
down before its turn is dropped from the queue; one that cannot be reached is skipped rather than
stall the album.

### 18.4 Order of the day

The photographer's `decide()` now runs: a sitter at the mark → the shoot, before anything; else
the bench (`AbstractEntityAICrafting.decide()`); else, if that came back IDLE, the chronicle (at
most one building a minute) or, failing that, an idle portrait of whoever is nearby (at most one
every five minutes).

### 18.5 Still open

- The lookout and the night escort for the astronomer (§17.6), next.
- A stat line for the money itself (`earned` is persisted; the townhall stats window shows counts).
- Group portraits, portraits at home for happiness (§4 of the design doc).

## 19. Phase 5b — the lookout and the night escort (10 Sep 2026, `alpha.15`)

Marko: *maybe the one who works in the Observatory could go up a hill, or somewhere the sky is very
clear at night, to photograph better, and take the camera along - and you could assign a guard to
protect them at night.*

### 19.1 Finding the lookout (`BuildingObservatory.getLookout`)

A blueprint may tag one (`lookout`); none of ours do, so the building looks for it in the land:
every third column within forty blocks of the instrument, inside the colony, at the surface
(`MOTION_BLOCKING_NO_LEAVES`), at least three blocks above the instrument, standable, under open
sky, not inside any building's footprint, and open all round - at least five of the eight compass
points five blocks out are no higher than it. Score = rise × 2 + openness × 1.5 − walk × 0.15;
the best wins; a flat colony has none, and then the astronomer keeps the watch at the instrument
exactly as before. Looked for again every three days, cached in the building's NBT.

### 19.2 The night from the hill (`EntityAIWorkAstronomer`)

At dusk, if the *Watch from the lookout* setting is on and there is a lookout, `chooseTheWatch()`
takes the colony's camera off the Observatory's shelf **into the astronomer's own pack** (not a
field: a server that stops halfway up the hill must not lose the camera), loads a roll from the
shelf if the one in it is full, asks for a camera once a night if there is none (the Photo Booth
makes them), sends for the escort, and walks out with the camera in hand and the arms up. The watch
is kept at the lookout - the astronomer turns to where tonight's object stands in the sky, because
Exposure: Space gives every object a yaw and a pitch - and the plate comes home **one band
brighter** (a dark sky), rolled with the better of the Observatory's lens and the camera's own
telescopic lens, since Exposure: Space grades its lenses the same way.

Then the photograph: a real Exposure exposure of the night sky, drawn band by band like the
photographer's, with the **moon** where the moon is tonight in tonight's phase (a four-degree disc
with the phase cut out of it by an offset shadow disc), the stars, and **the object itself pressed
into the frame** out of Exposure: Space's own 64×64 catalogue picture, read straight out of the mod
jar on the server and pressed into map colours - large through a telescopic lens, a smudge of the
right colours through a plain one. Titled *Andromeda Galaxy, from the lookout*; filed on the shelf
with the plates when the astronomer is home.

Sleep moved: MineColonies is allowed to put the astronomer to bed only once they are **home**
(`filePlate`), not the moment the night counts, or it would put them to bed on the hill with the
photograph half taken.

### 19.3 The escort (`BuildingObservatory.callEscort`)

The rally banner's own mechanism, without the banner: the nearest guard towers with a guard in
them get `setRallyLocation(new StaticLocation(lookout, dimension))`. The guard's own AI does the
rest - walks there, glows, fights anything within thirty blocks - until `releaseEscort()` at the
end of the watch, and as a safety, at the first colony tick after dawn. The towers are remembered
in NBT so a restart releases them too. *Night escort:* one guard (default), two, or none.
A rallied guard costs what a rally costs in MineColonies (a hungry one comes back hungrier).

### 19.4 Settings

The Observatory gained a settings tab: `voyager:lookout` (on) and `voyager:escort` (one guard) -
plain `BoolSetting`/`StringSetting`, so nothing new had to be taught to MineColonies' factories.

### 18.6 Progress pictures (`alpha.16`)

Marko: *and maybe document during construction too - photograph the progress halfway?* The
builder's hut keeps its place in the blueprint - a blueprint-local position and a stage (clear,
solid blocks, non-solids, decoration, spawn). Every colony tick the Photo Booth looks at every
claimed build or upgrade in the colony's work manager: once the solid stage has climbed past half
the blueprint's height, or any later stage has begun, the walls are up and the roof is not, and
that is the picture worth having. The work order is remembered (persisted) so each build gets one
progress picture, titled *Town Hall, level 3 - halfway up (day 11)*, taken from the same kind of
viewpoint as the finished one and pasted into the same album; the finished picture follows when
the builder is done. A finished building supersedes its own halfway picture if that was never taken.

### 19.5 The review before the first test (`alpha.17`)

An independent read of every class written today against the decompiled MineColonies sources,
before Marko runs any of it. What it caught, all fixed in `alpha.17`:

- **The photographer would never have taken a picture.** `AbstractEntityAICrafting` leaves IDLE
  only when `hasWorkToDo()` - a crafting request - so the `decide()` override that starts a shoot
  was unreachable while idle (the same trap the Voyager fell into in 0.1.6, forgotten). Now
  `hasWorkToDo()` also counts a sitter at the mark, a chronicle job that is due, and time for a
  portrait, without counting any inventory.
- **The camera could vanish.** It lived in a field and the hand slot; MineColonies resets a
  worker's AI for sleep, rain and raids without asking, and the reset would have dropped the
  colony's camera, lens, flash and film. Both AIs now keep the camera in the worker's own pack and
  look it up by slot; `decide()` carries a stray camera back to the shelf first. The shoot's own
  states are no longer interruptible for a meal, and the day's shooting stops at 10500, before the
  10600 bedtime.
- Full rolls of film were being reloaded from the shelf (any `FilmRollItem` matched) - now only
  rolls with a frame free; an ejected full roll is never dropped when the shelf is full.
- A first-time build is level 0 until it is finished, so every halfway picture of a new building
  was being discarded; the render meta was being wiped every second by the base AI (no viewfinder
  pose); the credited night was not persisted (a restart mid-night paid twice); a zenith object
  gave a one-colour frame (pitch clamped to −85°); an empty `catalog_texture` tried to open a
  directory; visitors were painted grey in their own portraits (`instanceof AbstractEntityCitizen`);
  the escort was not re-rallied after a restart; a blueprint-tagged lookout outside the colony would
  have been refused by the guards; a camera in a colleague's hands was being re-requested.

## 20. Phase 5c — every room reachable, and the photographer moves in (10 Sep 2026, `alpha.18` – `alpha.19`)

Marko's first walk through the buildings found the photographer with "a wall in front of his
door", rooms with no way into them, and a lightroom that could not print. All fifty blueprints were
redrawn from one plan and every one of them is now checked by a machine before it is written.

- **The plan** (`tools/observatory.py`, `tools/photobooth.py`): a study/studio with the work row
  against the north wall, the door in the middle of the south wall and the bed in a corner placed
  by plan (the search used to drop it outside under the eave, or across the door's inside square);
  the darkroom as a **wing sharing the study's wall and entered from inside** (it used to stand a
  block apart with its door opening into the study's outer wall); the dome and the Keep's tower
  **on the study's flat roof**, reached by a ladder through a hatch, doors facing the hatch (they
  used to be dropped through the study from the ground and opened south off the eave into the
  air); instruments off the door approach and the gate; camera stands on a whole block the design
  owns, never on the kerb or the ground past it.
- **Light**: Exposure's lightroom refuses to print below light 13 at the block above it. Every
  lightroom now has a full-strength lamp set into the outer wall beside that square (14), under a
  tinted-glass ceiling, so a plate develops in the dark.
- **The judge** (`tools/access.py`): from the ground outside, a two-block-tall citizen walks the
  design (step up 1, drop 3, doors, ladders) and must be able to stand next to every worked block
  and on every floor mark; walled-off rooms, doors into walls, bed halves and the light above each
  lightroom are reported too. `build_observatory.py` and `build_photobooth.py` still refuse floating
  blocks, unattached ladders and lanterns, unknown block ids and a box overrun.
- **Proved in a real world** (`tools/pastetest/`, `tools/worldcheck.py`): a throwaway server mod
  pastes all fifty blueprints through Structurize itself on a headless NeoForge server with
  MineColonies and Exposure; the world is read back block for block and audited again — 0
  differences, 155 photograph frames and 50 camera stands placed, block light 14 above all 80
  lightrooms.
- **Photograph frames are entities, not blocks.** `exposure:photograph_frame_small/medium/large`
  exist only as entity models; the first blueprints named them as blocks and Structurize placed
  air. They are now `exposure:photograph_frame` hanging entities with `Facing`, `Size` and
  `TileX/Y/Z` (Structurize rotates and re-anchors them itself), so the builder asks for one
  photograph frame per picture, the way it asks for an item frame.
- **The photographer lives at the Photo Booth** (`colony/WorkAtHomeCraftingModule.java`): the
  crafting AI and the request resolvers cast a crafter's module to `CraftingWorkerBuildingModule`,
  so the Booth could not use MineColonies' `WorkAtHomeBuildingModule` the way the Observatory does.
  The new module is the crafting one with the two work-at-home steps added: hired here makes the
  Booth the citizen's home (and frees their bed in town), fired or the building lost makes them
  homeless again. The bed in the studio is theirs through `BuildingModules.BED`.
- Leftover University researches for the studies that moved into the Observatory's own book
  (`apprentice`, `lens_grinding`, `star_party`, …) were still in the repository and would have
  shipped from a clean checkout; `gen_observatory_research.py` removes them and they are gone.
- Not changed: the AI, the tags it walks to (`darkroom`, `scope`, `studio`, `sitter`,
  `photographer`), the Departure Point.

## 21. Phase 5d — the darkroom's print shows the sky, and the two professions can talk about their work (10 Sep 2026, `alpha.20`)

- **The print from a plate is a photograph of the night** (`compat/SkyPrint.java`). It used to point
  at Exposure: Space's catalogue picture, which has a transparent ground - so it hung on the wall as
  an icon on white paper ("a bit boring because you can't see the night sky"). Now it is drawn in
  Minecraft's map colours like the lookout photograph: a sky black at the zenith and blue below, a
  scatter of stars with a few bright enough to cross, a band of haze, a glow round the object, the
  object as big as a telescope makes it, and along the bottom the hills, a few spruces and the
  Observatory's own dome with a lamp lit in it. The catalogue picture is the fallback if the
  drawing cannot be made. `compose()` is pure and can be looked at outside the game.
- **Status lines for Colonist Errands.** `JobAstronomer.getStatusLine()` and the new
  `JobPhotographer.Status`/`getStatusLine()` say in a sentence what the colonist is doing
  ("keeping the watch from the lookout under the open sky, camera in hand; caught the Crab Nebula
  tonight - a first for the colony", "camera up in the studio, photographing Anna"), and
  `BuildingObservatory.describeForChat()` / `BuildingPhotoBooth.describeForChat()` put the building
  into one English paragraph (look, level, lens, nights kept, the sky book, tonight's event; portraits
  sold, prices, the chronicle's volumes). Read by Colonist Errands 2.2.0 through reflection, the
  way it reads the Voyager's status, so a Voyager without Errands loses nothing.

## 22. Phase 5e — pictures on the walls, and both professions proven end to end (10 Sep 2026, `alpha.21`)

- **The frames fill.** Every hut had Exposure photograph frames on its walls and every one hung
  empty, because the pictures went on the shelf. Now a finished picture goes on the wall first
  (`compat/ExposureFrames.java`, reached through `ColonyCamera.hang`): into an empty frame inside
  the building's box, or, when all are full, into the frame with the oldest picture, whose print
  comes down onto the shelf - the walls show the newest work and nothing is lost. The astronomer
  hangs the darkroom print of a first sighting or a composite, and the lookout photograph once
  they are back inside (`hangWhatWasCarried()` when the plate is filed); the photographer hangs
  the idle portraits ("took a photograph and hung it in the studio") and a chronicle photograph
  that has no album to go in. Paid sittings still leave with the visitor. The study has a frame
  from level 1 now (over the work row; three from level 3, five at level 5 - it used to start at
  level 3), so the astronomer has somewhere to hang the first print. `describeForChat()` on both
  buildings says how many frames hold a picture, for Colonist Errands.
- **"The Photo Booth has no camera"** is now actually said. The lang key existed since alpha.14 and
  nothing sent it: a photographer with no camera on the shelf put in a request and otherwise
  looked simply idle, forever ("the photographer doesn't photograph visitors"). Now the colony's
  managers are told once a day, and the log says so, the way the Observatory does for the lookout.
- **The astronomer's idle line** after filing the plate no longer keeps the "photographing the
  night sky" text through the sleep that follows.
- **Verified as played, not just as pasted** (`tools/colonytest/`, a second throwaway server mod):
  on a flat headless world it creates a colony with a fake owner, pastes `observatory1` and
  `photobooth2`, registers both huts, hires an astronomer and a photographer into them, stocks
  each shelf with a camera and film, builds a stepped hill for the lookout and spawns a bystander
  and a visitor; the console then sets day and night. The photographer took a portrait in the
  studio within thirty seconds of daylight and it hung in a frame on the gallery wall; the
  astronomer found the hill, walked up with the camera, photographed the sky from the lookout,
  filed a plate of Mizar, and next morning developed it and printed the photograph. All four
  exposures were read out of `data/exposures/` and rendered: the studio, the star field, and the
  night-sky print exactly as the composer draws it. So the answer to "do both really make
  pictures" is yes, on a server with no player in it.

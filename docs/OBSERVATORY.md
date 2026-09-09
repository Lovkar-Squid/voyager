# The Observatory, the Gallery and the Studio

Design notes, 9 Sep 2026. Everything technical here was read out of the actual jars in your
instance — `exposure-neoforge-1.21.1-1.9.18.jar`, `exposure-space-neoforge-1.2.8.jar` and
`mctradepost-1.06.012-1.21.1.jar` — not from a wiki.

Three professions, one chain: **the Observatory makes the pictures, the Gallery sells them,
the Studio photographs people.**

---

## 1. What we are building on

### Exposure (MIT, mortuusars)

A full camera: attachments (lens, filter, film), film rolls, a Lightroom to develop them,
photographs, albums, photo frames, a camera stand with redstone control.

### Exposure: Space (**MIT**, Temno)

Astrophotography, and it is already most of an observatory:

- Camera + **Space Filter** + **Telescopic Lens** — five tiers: bad, normal, good, excellent,
  **sculk**.
- Scan the night sky, capture, feed the photograph to a **Photo Analyzer**.
- First study of an object pays **real reward items** and enters your **catalog**;
  the **Cosmic Album** shows what you have.
- **256 cosmic objects** as JSON — required lens tier, appearance chance, size,
  `base_analysis_ticks`, a catalog texture, reward items, a fixed sky position, a Wikipedia link.
  15 types: planet, star, nebula, galaxy, cluster, comet, asteroid, moon, probe, debris,
  anomaly, black hole, pulsar, quasar, unknown.
- Three **cosmic events** — meteor shower, aurora storm, deep sky night — each with a weight,
  a luck multiplier, an analysis-speed multiplier, and objects that appear *only* then.

All of it is **data**, which is the only surface the author supports (section 8): objects at
`data/<ns>/cosmic_object/*.json`, events at `data/<ns>/cosmic_event/*.json`, plus live external
config folders for adding our own. We read those files with vanilla's `ResourceManager` and never
link against their classes.

### Trade Post for MineColonies (GPLv3, Ayar & Alysar)

Already has the whole selling machine:

- **Marketplace** + **Shopkeeper**, with **display cases** at tagged blueprint positions
  (`DisplayCase`: a position, a stack, a frame id, a sale state).
- An **advertising** system that pulls `IVisitorData` — the marketplace *attracts visitors*.
- Its own currency (`mctradepost:mctp_coin`, gold, diamond), minting and depositing.
- A **Resort** with **Guest Services** — guests who stay.
- A **Station** and Station Master for shipping between colonies.
- Its own **economic research tree** (antique shop, consignment shop, criers, buyers,
  capitalism, five-star vacation…).
- Item values as **data files with `"replace": false`** — so our mod can ship values for its own
  items without conflicting.

---

## 2. Three facts that shape the whole design

### (a) A photograph is rendered on a **player's client**

Exposure captures by rendering on somebody's client and uploading (`client/capture/…`,
`ExposureUploader`). Even the tripod proves it — `CameraStandEntity implements CameraHolder`,
and `CameraHolder` is entirely about finding a person:

```java
Optional<Player> getPlayerExecutingExposure();
Optional<? extends Player> getClosestPlayerInRange();
boolean isInRangeForPhoto(Player);
```

**No player in range, no image.** There is no server-side renderer to borrow.

**The answer, and I think it is better than the thing it replaces: the undeveloped plate.**

Our own item. The astronomer works all night whether you are there or not and comes home with a
plate that knows *what* it caught — which object, which lens, which event, which night, who
took it. The studio's plates know *who sat*. Nothing about the colony's work depends on you.

Then the plate has to be **developed**, and developing needs a person in the darkroom — which is
exactly when Exposure can render a real photograph. So you come home from the Nether to a rack
of undeveloped plates and an afternoon's work turning them into pictures. The limitation becomes
the reason the buildings have a darkroom at all.

If you are never there, the plates still analyse, still fill the catalogue, still pay out, and
still sell — as *unprinted* stock at a lower price. Nothing is ever blocked.

### (b) The catalog belongs to a **player**, not a colony

`PlayerCatalog` is a per-player record through data attachments; the Analyzer keeps an
`ownerUuid`. There is no colony-level anything — and it is internal, so after the author's reply
(section 8) we do not touch it at all. The colony keeps **its own** catalogue, shown in the
Observatory GUI; a player's album fills the normal way, by putting a photograph the colony produced
into Exposure: Space's own Analyzer.

### (c) A price is per **item**, not per stack

`ItemValueManager.get(Item)` — one value per item type. So out of the box a print of a quasar and
a print of the Moon are worth the same. The fix is simple and also better for display: prints
come in **rarity bands as separate items** — *common / notable / rare / extraordinary* — with the
actual object kept in data components for the picture, the label and the tooltip.

### Licences

- Exposure is **MIT**.
- Trade Post is **GPLv3**: fine, Voyager is GPLv3 already, and we would be compiling against it.
- **Exposure: Space is MIT too**, confirmed by the author — see section 8. Its jar metadata says
  otherwise and that is a mistake in the jar, not the licence.

**The build plan and the mechanics live in `OBSERVATORY-PLAN.md`.**

---

## 3. The three buildings

### The Observatory — *Astronomer*

Works at night. Needs a camera, film, a space filter and a telescopic lens; requests them
through the colony like any other worker. Each clear night he finds what his lens tier allows
(read from `required_tier` in the object data), weighted by `appearance_chance` and by whatever
tonight's event says the sky is doing, and comes back with an **undeveloped plate**. The analyser in the building grinds
through `base_analysis_ticks` and identifies the object: reward items to the colony, a new line
in the colony's catalogue.

Building level decides how much sky he can reach — L1 barely more than the Moon, L5 with a sculk
lens the things almost nothing else sees.

### The Space Gallery — *Curator*  (Trade Post addon)

Takes developed prints from the Observatory, frames them, puts them in display cases, and sells
them to visitors and to you for Trade Post coins. Rarity band sets the price; a framed print is
worth more than a loose one; a print of something **nobody else has catalogued** is worth most.

Trade Post's marketplace already advertises to visitors, so the gallery inherits a reason for
people to turn up. The Station can ship prints to another colony — an exhibition on tour.

### The Photo Studio — *Photographer*  (Trade Post addon)

Colonists and visitors come and sit. The photographer takes a **portrait plate** of them, and
this is where Exposure gives us something genuinely good: a `Frame` records `entitiesInFrame`
(`EntityInFrame`), so a developed portrait really does contain that citizen — we are not faking
a name on a card.

- A colonist who has a portrait at home is happier.
- A visitor buys their portrait before they leave, in coins.
- Group portraits: everyone who works in one building, taken together.

---

## 4. Research — the part you asked for ideas on

Six branches. Prerequisites in brackets. These are all things the two mods actually expose, so
none of it is decoration.

**Optics — what he can see at all**

| Research | What it does |
|---|---|
| **Ground Glass** | Unlocks the Astronomer. He can work a *bad* lens. |
| **Achromat** [Ground Glass] | *Normal* tier. Analysis −10%. |
| **Silvered Mirror** [Achromat] | *Good* tier. |
| **Deep Field** [Silvered Mirror] | *Excellent* tier. Expensive. |
| **Sculk Optics** [Deep Field] | *Sculk* tier — the objects almost nothing else reaches. Echo shards. |

**The night — when he works and how often he wastes it**

| Research | What it does |
|---|---|
| **Night Watch** | He works the whole night instead of part of it. |
| **Weather Eye** [Night Watch] | Light rain no longer ends the night's work. |
| **Ephemeris** [Weather Eye] | The colony is told a day ahead which sky event is coming — reads `CosmicEventManager`. |
| **Rotating Dome** [Night Watch] | L4+ gets a dome; analysis speed up. |

**The plate — turning a night into something**

| Research | What it does |
|---|---|
| **Darkroom Discipline** | Analysis ticks −20%. |
| **Second Exposure** [Darkroom Discipline] | A re-analysed object still pays a share — their own `repeatRewardPercent`, but ours. |
| **Comparative Astronomy** [Second Exposure] | Two plates of one object combine into a better print. |
| **Spectroscopy** [Comparative Astronomy] | The analyser also yields a *spectral note*, which the Gallery prices as a premium. |

**The colony — why anybody cares**

| Research | What it does |
|---|---|
| **Star Charts** [Deep Field] | **The Voyager link.** Every catalogued object shortens expeditions and improves finds. |
| **Public Lecture** [Night Watch] | Colony happiness rises the night after a discovery. |
| **Almanac** [Ephemeris] | Farmers get a small bonus — the observatory earns its keep. |
| **Naming Rights** [Star Charts] | The colony may name an object it catalogued first. The name shows in the gallery and the album. |

**The gallery — Trade Post side**

| Research | What it does |
|---|---|
| **Framing** | Prints can be framed. Higher value. |
| **Limited Edition** [Framing] | A print may be sold several times at a falling price — a print run. |
| **Patrons** [Framing] | Visitors drawn by the gallery arrive richer and stay longer. |
| **Travelling Exhibition** [Limited Edition] | The Station ships prints to another colony at a premium. |

**The studio — people**

| Research | What it does |
|---|---|
| **Portraiture** | Colonists may sit. A portrait at home makes a citizen happier. |
| **Family Album** [Portraiture] | The happiness bonus grows with a whole household photographed. |
| **Visitor Vanity** [Portraiture] | Visitors buy their own portrait before leaving. |
| **Guild Portraits** [Family Album] | Group portraits of everyone who works in one building. |

Twenty-two researches. That is more than enough for a tree with real choices in it — if it is
too much for a first release, *Optics*, *The night* and *Star Charts* alone make a complete mod.

---

## 5. What it would cost

Registration is cheap — `Voyager.java` already registers jobs and buildings through
`DeferredRegister`s, so three more `JobEntry` + `BuildingEntry` pairs are a copy of what is
there. The real work, biggest first:

1. **Blueprints.** Three buildings × 5 levels = 15, through the pipeline you already have
   (Python voxels → your Blender → Structurize `.blueprint`). This is the long pole and the part
   you will want to look at before anything else exists.
2. **Three AIs.** `EntityAIWorkVoyager` is ~1000 lines; the Astronomer is the biggest of the
   three, the Curator and Photographer are lighter because Trade Post already has the selling.
3. Research JSON + lang (`tools/gen_research.py` already generates these), three worker outfits,
   sounds, and a `ModSoundEvents.CITIZEN_SOUND_EVENTS` entry per job — **that was the 0.1.0
   crash**: MineColonies NPEs on a job with no voice-line map.
4. Optional dependencies done properly: without Exposure: Space the Observatory hut and its
   researches simply are not there; without Trade Post the Gallery and Studio are not there.

---

## 6. Decided — 9 Sep 2026

He answered all five. **Nothing is being built yet**; this is the record.

1. **One mod.** Everything goes into Voyager. It becomes the colony that looks up as well as the
   one that leaves — Observatory, Space Gallery and Photo Studio all ship inside it, with the
   outside mods as optional dependencies.
2. **The colony keeps the catalogue.** *Amended after the author's reply (section 8): the colony
   keeps its own record, and a player's album fills the normal way — by putting a photograph the
   colony produced into their own Photo Analyzer. We never write into their `PlayerCatalog`.*
3. **At least six rarity bands.** Proposed, and derived from their own data rather than hand-sorted
   across 256 objects — `required_tier` and `appearance_chance`, with event-exclusivity as its own
   top band:

   | Band | What lands in it |
   |---|---|
   | Common | bad/normal lens, high appearance chance — the Moon, bright planets |
   | Notable | normal lens, middling chance |
   | Rare | good lens |
   | Remarkable | excellent lens, ordinary chance |
   | Extraordinary | excellent/sculk lens, low chance |
   | Once in a Lifetime | objects that appear **only** during a sky event |

   Six items, six prices, and the actual object kept in components for the picture and the label.
4. **The Observatory does not require Trade Post.** It works alone; if Trade Post is installed the
   Gallery and the Studio switch on and the selling works as designed.
5. **Yes to our own cosmic objects**, written into their external folder — including at least one
   that can only be caught from an End expedition, so the two halves of the mod need each other.

## 7. Still open

- The six band names above are a proposal, not a decision.
- Whether the Studio is worth its own hut or lives as a room inside the Gallery.
- Exposure: Space's licence — see below.

## 8. The licence, settled — and what the author actually said

**The licence.** I first read `license = "ARR"` out of the jar's `neoforge.mods.toml` and reported
the mod as All Rights Reserved. Both places the author publishes it say **MIT** — CurseForge
project 1580159 renders the full MIT text, Modrinth's sidebar agrees — and **Temno confirmed it
in writing on 9 Sep**:

> "Regarding the ARR thing, I forgot about that - you can go with MIT."

So: Exposure MIT, Exposure: Space MIT, Trade Post GPLv3. Nothing is in the way.
**Lesson: never take a licence from a jar's `mods.toml`.** It is often an unedited template
default; read the page the author publishes on.

**The part that changes the design.** In the same message:

> "One nuance about the API: it only lets you interact with space objects - adding, removing, etc.
> - for datapacks and configs, and that's the extent of it. I might remove the \"library\" tag in
> the future."

That is the author saying, politely, that **there is no supported Java API**. The classes I found
by reading the jar — `CosmicObjectManager.detectableBy`, `CosmicEventManager.get`, `PlayerCatalog`,
`AnalyzerBlockEntity` — are internals. MIT means we are *allowed* to call them; it does not mean
they will still exist next release, and he has no reason to keep them stable. A mod of ours built
on those would break on somebody else's update schedule, and the breakage would land on our users.

What he *does* support is the data layer: cosmic objects and events as JSON, in datapacks and in
the live external folders. So the plan changes, and for the better.

### The rule from here: read the data, never link the code

Their objects live at `data/<ns>/cosmic_object/*.json` and events at `data/<ns>/cosmic_event/*.json`,
loaded by the ordinary datapack system — `CosmicObjectManager` is a plain
`SimpleJsonResourceReloadListener`. **We can read exactly the same files with vanilla's own
`ResourceManager`**, in our own reload listener, and touch none of their classes.

That gets us everything the Observatory needs: the objects, their `required_tier`, their
`appearance_chance`, `base_analysis_ticks`, the reward lists, the sky positions, and the events
with their multipliers. A JSON schema is far more stable than internal Java, a change to it is easy
to detect, and we can validate on load and simply do less rather than crash.

It also has a nice side effect: our Observatory would see **every** cosmic object registered by
anybody — theirs, ours, and any third datapack — instead of only theirs.

### What this costs, honestly

One thing genuinely goes away: **we cannot read or write a player's own `PlayerCatalog`.** That was
decision 2 — "the colony keeps the catalogue, a visiting player's is topped up from it". The
top-up needs their internals.

The replacement is better anyway: **the colony gives you the photograph; you feed it to their Photo
Analyzer yourself, and your album fills through their own mod, the normal way.** We bypass nothing,
their progression stays intact, and the colony's catalogue is simply the colony's own record of
what its astronomer has found. Decision 2 stands with that one amendment.

And decision 5 — shipping our own cosmic objects in their format — turns out to be the *supported*
path rather than the risky one. It is now the backbone of the integration, not a flourish.

## 9. The message — sent, and answered

Sent 9 Sep on the project Discord (**https://discord.gg/RwYz28Nfs7** — the same invite on
CurseForge and Modrinth; there is no public source repository or issue tracker). Temno replied the
same evening confirming MIT and adding the API nuance quoted in section 8.

Worth a short thank-you, and one question if we want it answered: whether they would consider the
studied-objects catalogue readable from a datapack-level hook one day. Not a blocker — the design
above does not need it.

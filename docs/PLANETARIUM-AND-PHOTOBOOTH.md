# The Planetarium and the Photo Booth — what the Exposure mods give us to work with

A survey of **Exposure**, **Exposure: Space** and **Exposure: Expanded** as they sit in Lovkar's
MineColonies Ultimate, and a design for the two buildings that would use them: a **Planetarium**
that turns the Observatory's plates into something the colony can see and spend, and a **Photo
Booth** with a **Photographer** who runs the colony's darkroom and print shop.

Written the same way the Observatory was: read their data and drive their blocks through the
interfaces Minecraft already gives us, never link their classes.

---

## 1. The one finding that shapes everything

**A colonist cannot take a photograph.**

Exposure captures an image by rendering the world from the camera's viewpoint **on the client** and
uploading it to the server — that is what its `Direct Capture`, `Background Capture`, "Requesting"
and "Exporting" machinery is for. There is no server-side render, so an NPC has no way to produce a
real photograph. Any design that has a colonist walk up and press the shutter is a design that
cannot be built.

Everything *after* the shutter is server-side and fully automatable, and that turns out to be the
better game anyway: **the player shoots, the colony refines.** The player brings back exposed film;
the colony develops it, prints it, ages it, copies it, frames it and binds it into albums. That is
exactly the shape of every good MineColonies profession.

The Observatory sidesteps the problem entirely, because its plates are our own item and its sky is
read from datapacks. The Planetarium inherits that freedom.

---

## 2. What each mod actually contains

### 2.1 Exposure — the camera and the darkroom

| Thing | What it is | Can a colonist drive it? |
|---|---|---|
| **Camera** | Item with attachments: film, filter, shutter speed, self-timer, viewfinder, flash, focal length | No (capture is client-side) — but craftable |
| **Camera Stand** | An **entity** tripod that holds a camera; can malfunction and be fixed; redstone-controllable | Placeable in blueprints (done in the Observatory), fixable |
| **Film** | B&W, Colour, High-Sensitivity of each; a roll holds several frames | Craftable |
| **Film developing** | `exposure:film_developing` recipe — film roll + water bottle → developed film | Yes, a plain recipe |
| **Lightroom** | Block that prints frames off a developed film onto photo paper using dyes, over `print_time` ticks, storing experience | **Yes** — it is a `BaseContainerBlockEntity implements WorldlyContainer`, so it can be loaded and emptied exactly the way MineColonies' Smelter drives a furnace |
| **Printing modes** | Black & white, Colour, and **Chromatic** (three channel prints + tinted glass on the Lightroom) | Yes |
| **Photograph** | The printed image | — |
| **Photograph aging** | `exposure:photograph_aging` — photograph + ager + brush → `aged_photograph` | Yes, a recipe |
| **Photograph copying** | `exposure:photograph_copying` — photograph + paper + dyes → a copy | Yes, a recipe |
| **Photo Album / Signed Album** | A book of photographs with a title and an author; readable on a lectern | Craftable; signing is a GUI action |
| **Frames** | `photograph_frame`, `glass_photograph_frame`, `glow_photograph_frame` | Craftable and placeable |
| **Stacked Photographs** | Several photographs in one item | — |
| **Interplanar Projector** | Projects an image from another world; has a broken form and a fixing recipe | Craftable, fixable |
| **Flash** | A block | Craftable |

### 2.2 Exposure: Space — the sky, and the part we already use

| Thing | What it is | Where we stand |
|---|---|---|
| **Cosmic objects** | 256 datapack files: name, type, `required_tier`, `appearance_chance`, `size`, `base_analysis_ticks`, catalog texture, **`rewards`**, sky position, Wikipedia link | `SkyData` reads all of it. **`rewards` is read and not yet used** — see §3 |
| **Cosmic events** | Meteor Shower, Aurora Storm, Deep Sky Night — luck, analysis speed, exclusive objects | Read; our own nightly roll |
| **Telescopic lenses** | bad → normal → good → excellent → sculk | The Observatory's lens progression is bought with them |
| **Space Filter** | Required on the camera to capture cosmic objects at all | Not used yet |
| **Photo Analyzer** | Block that analyses a captured cosmic photo; first study grants that object's rewards and fills the album | **The whole Planetarium** |
| **Night Analyzer** | Tells you tonight's event | In the Observatory's blueprints |
| **Cosmic Album** | A book of every object studied, with a per-object reward that resets to 100% after a cooldown | The model for our colony catalogue |
| **Cosmic Leftover** | Eating it reveals the whole catalogue | A trophy |

### 2.3 Exposure: Expanded — style, and only style

70 items, all of them camera attachments: **~24 filters** (art, bits, blur, blobs, bumpy, creeper,
deconverge, desaturate, flip, green, notch, ntsc, outline, pencil, phosphor, scan pincushion,
sobel, spider, wobble, antialias, colour convolve, fxaa…), **high-capacity / high-resolution /
high-sensitivity film** in every combination, four **vanity films** (Commodore 64, CGA, Gameboy,
NES), instant slides, a **panoramic lens** and a **telescopic lens**.

None of it is a mechanic — it is all *taste*. Which makes it perfect for a progression: every
filter is a thing the Photographer can be taught, and the colony's photographs get better-looking
rather than more numerous.

---

## 3. The Planetarium

**What it is for.** The Observatory catches the sky. The Planetarium is where the colony *reads*
what it caught: a domed room with a projector, a wall of framed plates, and the desk where an
object is finally identified and the colony is paid for it.

### 3.1 The loop

1. Star plates arrive from the Observatory (the rack, or the courier).
2. The **Curator** — the Planetarium's worker — takes a plate to an analyzer desk and studies it.
   Studying takes `base_analysis_ticks` from the object's own file, divided by the colony's lens
   tier and by the night's `analysis_speed` if it was an event night. All of that is already in
   `SkyObject` and `SkyEvent`.
3. **First study of an object pays out its `rewards`** — the item list in its own JSON, straight
   into the building. This is the single biggest thing the Observatory is currently missing: it
   catches objects and the colony gets nothing but a picture.
4. The entry goes into the colony catalogue (`SkyCatalogue`, already built). A repeat study of an
   object pays a fraction, and the full reward comes back after a cooldown — the same bargain
   Exposure: Space makes with a player, so a colony that keeps looking keeps earning.
5. The best plate of each object is **framed on the wall**: the Curator places an
   `exposure:photograph_frame` in a tagged spot, so the gallery physically fills up as the
   catalogue does. A player walking in can see what the colony knows.

### 3.2 The showing

When the catalogue passes a threshold, or on an event night, the Planetarium can hold a **showing**:
the dome lights, particles, a sound, and every citizen who was inside gets a happiness bump for a
day. It costs nothing but the time, and it gives Star Party somewhere to happen. This is the piece
that makes the building worth walking into rather than worth walking past.

### 3.3 What it needs from us

- A worker (**Curator**) and a building, both cut from the Observatory's pattern.
- Reading `rewards` in `SkyObject` (already parsed) and paying them out.
- The repeat-reward cooldown — `SkyCatalogue.Entry` already carries `firstNight` and `lastNight`.
- Framing: a blueprint tag per frame slot, and the Curator placing frames.
- One new item or none: the colony's own **Cosmic Album**, which could simply be
  `exposure_space:cosmic_album` handed over when the catalogue is complete.

---

## 4. The Photo Booth and the Photographer

**What it is for.** The colony's darkroom and print shop. The player shoots; the Photographer
develops, prints, ages, copies, frames and binds.

### 4.1 The loop

1. The player drops **exposed film** into the Photo Booth (or a courier brings it).
2. The Photographer **develops** it (`exposure:film_developing`: film + water bottle).
3. They load the **Lightroom** with the developed film, photo paper and dyes, wait out the print
   time and take the photographs out. This is a `WorldlyContainer`, so it is driven the same way
   the Smelter drives a furnace — a real integration with none of their code linked.
4. Printed photographs go on the rack, or into the requests that asked for them.

### 4.2 What the profession is, in MineColonies terms

A **crafter**, with a crafting module that teaches the whole Exposure catalogue: film of every
kind, cameras, camera stands, lightrooms, albums, frames of all three sorts, projectors, flashes.
That single decision makes the building useful from the first day, because every other Exposure
item in the pack stops being a hand-craft.

On top of the crafting module, three things only this worker does:

- **Developing and printing** (above), which no recipe book can express.
- **Aging** and **copying** photographs, both real Exposure recipes with an item cost.
- **Binding**: assembling the colony's photographs into a **signed Photo Album** titled after the
  colony and the year. A colony heirloom, and a genuinely nice thing to carry out of a world.

### 4.3 Where Expanded comes in

Every filter is a **style the Photographer has been taught** — bought the way the Observatory's
studies are bought, with materials and time. A colony with a taught Photographer prints
photographs with that filter applied on request; an untaught one prints plain. The four vanity
films (Gameboy, NES, C64, CGA) are the joke at the end of the tree, and they should be expensive.

### 4.4 Portraits — what is and is not possible

A colonist cannot photograph another colonist (§1). What *is* possible and worth doing:

- A citizen can **ask** for a photograph as a MineColonies interaction, and be satisfied by the
  Photographer handing over any printed photograph. The colony wants pictures; the player supplies
  the shots. That is a request the player fulfils by going out and taking one, which is a much
  better hook than an NPC pressing a shutter nobody sees.
- The Photo Booth can hold a **camera stand** with the colony's camera on it, exactly as the
  Observatory does — so the *player* walks in, sits, and takes their own portrait in the booth.
  The building is the studio; the player is the photographer of record.

---

## 5. Suggested order

1. **Rewards in the Observatory first.** The `rewards` list is already parsed and ignored. Paying
   it out is a small change with the largest effect on whether the Observatory feels worth having,
   and it does not need a new building at all.
2. **The Photo Booth**, because the Photographer-as-crafter is useful on day one and the Lightroom
   loop is a known pattern (the Smelter).
3. **The Planetarium**, which is the bigger build: a new worker, the framing wall, the showing.
4. **Expanded's filters** as the Photographer's study tree, mirroring the Observatory's.

## 6. What to be careful about

- **Never link their classes.** Everything above works through datapack JSON, item ids, recipes and
  `WorldlyContainer`. That is the contract their author asked for and it is why an update of theirs
  has never broken us.
- **All three are optional dependencies.** Exposure: Expanded especially — the filter tree has to
  degrade to "no filters offered" when it is absent, the way the sky degrades to blank plates.
- **The Lightroom needs light level** (its own config) and dyes per print. The Photographer's
  building has to actually satisfy those, or the loop silently stalls.

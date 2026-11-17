# Changelog

## 0.4.0 (unreleased)

- **Trophies from the End.** A completed expedition may bring home something to remember it by,
  on top of the haul (the existing loot tables are untouched):
  - **Souvenirs** (any level, 10 % per completed trip by default), named after one of sixteen
    places in the End - "Shard from the Hollow Spires", "Sprig from Chorus Reach". The tooltip
    tells who brought it home, which expedition of the Departure Point it was (Launchpad or End
    Gate, colony day) and what happened ("Fought 2 endermen and a shulker", "A quiet crossing").
  - A souvenir can be placed on a shelf, a table or any block with a firm top: a keepsake on a
    little purpur stand. Broken, pushed or knocked off, it drops as the same souvenir with the same
    story and name; an anvil can rename it.
  - The **Dragon's Wake** ("Scale from the Dragon's Wake") only and always after the Voyager beat
    the dragon on that trip.
  - From a level 5 Departure Point: an **End-city enchanted book** (5 %; one enchantment from the
    configured list - never Mending by default - at a random level) and a **map of the End** (3 %),
    a hand-drawn chart with the trip's story. It is a lore item, not a vanilla map: no map data,
    no chunks.
  - Trophies are packed first and come home into the hut like every other find, appear in the
    Expedition Log and, with Long-range Comms, in one chat line. A lost or driven-back Voyager
    brings none. The trip's story survives a reload mid-trip.
  - Every Departure Point now counts its expeditions (an older one starts from its completed-trip
    stat); the number is in the souvenir's story and in the departure log line.
  - A haul stack that does not fit in the Voyager's pack is now logged instead of vanishing
    without a word.
- **A server config, `voyager-server.toml`.** Server owners can tune expeditions: trips per launch
  window, rations per Voyager, a supply multiplier for the expedition plans, a trip length
  multiplier, the dragon's odds on Dragon Hunt trips and whether a Voyager can be lost at all; a
  `rewards` section holds the chances of the new trophies. Every expedition default plays exactly
  like 0.3.9. The README lists every key with its default and range.
  - The supply multiplier scales what the hut requests, what must be in stock before a launch and
    what a launch uses up (rounded up, at least 1 of each). The supply list itself stays in the
    datapack recipes `trip1..5.json`, so a datapack can still replace it.
  - The trip length multiplier scales the whole crossing; Rapid Refit and Star Charts still shorten it.
- **Permanent death can be turned off.** With `permanentDeath = false` the blow that would have
  killed a Voyager is never dealt: they are driven back with a quarter of their health and come home
  through the normal landing with their gear, but empty-handed - the rest of the itinerary and the
  trip's finds are lost, the cobblestone and torches the launch used up stay used up, and the plan's
  ender pearls come home in the pack, as after every trip. They step out of the rocket (or the gate)
  with that quarter, whatever they healed out of sight; on the walk to the hut they heal as any hurt
  colonist does. No grave, no death on the record (a "driven back" count instead), and Return to
  Sender has nothing to do. With Long-range Comms the colony hears about it. A reload on the way
  home keeps the wound until they step out; from there on it heals like any colonist's.
  The log says at how much health the Voyager turned back and how hard the next blow was feared to be.
- **The Observatory says what it needs.** Without Exposure: Space its hut item has a tooltip line:
  the night sky needs Exposure: Space, and without it every plate comes home blank. The building
  itself stays exactly as it was - buildable, with or without the mod.
- **README:** the header no longer says BETA 0.2.0, and there is a section on the server config.
  It now says what a trip does with its supplies: the 64 cobblestone and 16 torches are used up by
  every launch; the 4 ender pearls only have to be in the Voyager's pack and come home again - after
  a retreat too (MineColonies hands back a plan input that the trip can also turn up, as it does a
  tool; unchanged since 0.2.0, the datapack recipes are as they were). It listed the pearls with
  what every trip uses up, and said a retreat's supplies were spent.
- **Fixed, found by playing expeditions on a dedicated server:**
  - **Rations are eaten.** Since 0.2.0 a Voyager could eat any food except the rations on the hut's
    menu - which is all they pack - so between fights they patched up with nothing and went into the
    next fight as hurt as they were. They now eat the menu's food (any food when the menu is empty).
    Voyagers win more fights than before.
  - A blow the game refuses to deal is worked out by Voyager itself: MineColonies' helper for it
    broke off the fight with an error as soon as the Voyager wore protection-enchanted armour. If
    such a blow is fatal all the same, the Voyager now dies the normal way; before, they were left at
    0 hp and their colonist could come back later as if nothing had happened.
  - The Expedition Log, the log line and the Long-range Comms line name only the trophies that were
    really packed; one that found no room in a full pack stays in the End and is no longer listed.
  - An empty loot roll (an enderman's pearl rolled as zero) is no longer carried or put in the
    Expedition Log; the new "no room in the pack" warning fired for it ("0x Air").
  - A Voyager lost in the End leaves no trip in progress behind on the job. With no room for a
    grave, what they still wore is dropped by the hut along with the pack, and the warning counts it.
  - A Voyager back on the Launchpad walks on to the hut when their AI restarts on the way - after a
    reload, or when MineColonies' care for a colonist below 6 hp takes them over, as it does a
    driven-back Voyager. Before, the restarted AI found them still away, hid them in the End again and
    brought the rocket in to land a second time. A meal or leisure no longer interrupts that walk.
  - "Finished the expedition and is heading home" is logged once per trip: an AI restarted on the way
    home logged it again, and a driven-back Voyager, who finished nothing, got it too.
- **Fixed, found by a review of this release:**
  - **A driven-back Voyager at an End Gate never got home.** They stepped out of the gate below
    MineColonies' 6 hp mark, its care for a hurt colonist took them over and restarted their AI, and
    the restarted AI - with no rocket on a pad to tell it the trip was over - sent them through the
    gate's arrival again, wounded them again at the end of it, and so on for good. The job now
    remembers that the Voyager has stepped out (saved with it), and a restarted AI walks on to the
    hut at both looks. The wound of a retreat is dealt once: nothing wounds them again after the
    landing.
  - A trip saved by 0.3.9 after its fights (still away, nothing left on the itinerary) and loaded in
    0.4.0 comes home with its haul and no trophy. Its fights were never counted, so a souvenir would
    have told a false story ("A quiet crossing", never the Dragon's Wake). A 0.3.9 trip with finds
    still ahead counts the fights it has left.
  - The Long-range Comms lines and the log name a mob by its name, the way vanilla's death messages
    do: "driven back by Ender Dragon", "beat Enderman out there", no longer "driven back by a
    ender_dragon".
  - A reload on the way home no longer logs "finished the expedition and is heading home" a second
    time: the job saves that the trip has turned for home.
  - Buddy System: the Departure Point has one Expedition Log, and it belongs to the Voyager who set
    off last. A Voyager driven back while a crew mate who left after them was still out reset the
    crew mate's entry; their fights, death or trophies could end up in it too. Now only the log's own
    Voyager writes it.
- **Tested on a dedicated server.** A headless NeoForge 21.1.249 server with MineColonies 1.1.1420
  boots Voyager and reads its config: once seeded with a non-default value for every key, once from
  the mod's own defaults (`tools/configcheck`). The trophies are checked there too
  (`tools/trophycheck`): all sixteen souvenirs with their names and tooltips, the Dragon's Wake
  rule, 4,000 End-city books without a single Mending, the story through saving, loading and the
  network, and a souvenir placed, saved, broken and knocked off its shelf, keeping its story every
  time. `tools/check_trophies.py` checks the textures, models, loot table and lang keys.
  - **A play-test with a real colony** (`tools/expedition`): a colony on the flat world, a level-5
    Launchpad pasted from the bundled pack, a hired Voyager, the plan's supplies and rations in the
    hut, and then the AI runs for real. The research is injected as effects (the University is not
    part of the test), trips are short (trip length 0.25) and every trophy chance is 1.0.
  - `loop`: the Voyager boards, departs, the rocket (1,375 blocks) is stowed at lift-off and stands
    on the pad again after the landing as it left, the Voyager is away out of sight for the computed
    time (the departure line gives Rapid Refit's shorter time to the second), comes back, carries the
    haul into the hut and the trip is counted. The souvenir, the End-city book (not Mending) and the
    map of the End arrive in the hut with their story (name, expedition 1, Launchpad, the fights of
    the trip). The job and the building keep the trip, its tally and the expedition number through
    saving and loading, and the rocket's stash survives a reload while it is away. (The test colony
    has no restaurant, so the probe feeds the Voyager once home.)
  - `dragon`: Dragon Hunt with the dragon certain. The Voyager (netherite, Protection IV, Void
    Insurance) ate a ration, beat her and brought home a dragon head, dragon's breath and a *Scale
    from the Dragon's Wake*. The win branch ran; a lost dragon fight is the lost path below.
  - `lost`: a Voyager without sword, armour or rations was lost in the End: Return to Sender put the
    pack into the hut's racks, a grave was raised by the hut, the rocket came home on its own, no
    trophies. (MineColonies takes the job off a colonist who dies, so the grave keeps no trip.)
  - `wounded`: a deadly trip (no rations, half health, the dragon at the end) with permanent death
    off: the Voyager turned back from a fight (a shulker in one run, the dragon in another) and was
    left with a quarter of their health, was taken out of the world on the way home and came back
    still wounded, landed once and stepped out at that quarter (below MineColonies' 6 hp mark,
    whatever they had healed meanwhile), then walked home with all their gear, empty-handed: no
    grave, no death on the record, a retreat counted. Their own Expedition Log entry kept the fights
    won and lost the loot.
  - `wounded-gate`: the same retreat from a level-5 End Gate. The Voyager turned back from the dragon,
    was taken out of the world on the way home and came back still wounded, appeared at the gate
    exactly once, stepped out at a quarter of their health and walked home from below the 6 hp mark
    with all their gear, empty-handed: no grave, no death, a retreat counted. While they were out the
    probe handed the Expedition Log to a crew mate who "set off later"; the retreat left that entry
    as it was.
  - The modes in which the Voyager comes home also read the job's saves the way an older world would
    have them: a 0.3.9 trip saved after its last find gets no trophy, one with finds ahead and a
    0.4.0 save still roll theirs. `tools/trophycheck` renders the Long-range Comms lines that name a
    mob.

## 0.3.9 - 2026-10-06 - the photographer walks round town

- **The photographer no longer photographs his own studio wall.**
  - Between other work he used to take "a portrait" in the studio of whoever stood within eight blocks of him, through the studio's walls if need be.
  - With nobody there he took the picture anyway: a dark photograph of the wall, named after the colony.
- **Now he walks round town for it.** Now and then, in daylight, he goes out for one of two pictures:
  - **A portrait** of a colonist who is out of doors and awake. He takes it from a spot three to five blocks in front of them, with nothing between the lens and their face.
  - **A view** of one of the colony's buildings within 64 blocks of the booth. It is named after the building and the colony, for example "Observatory, SquidVile".
  - If the colonist walks off, he follows them up to three times. With nobody in clear sight he takes no picture and spends no film.
  - Both kinds go on the studio's gallery wall, as before.
- **Paid portraits are unchanged.** A visitor at the studio's mark is photographed there and pays in Trade Post coins.
- The log line of every photograph gives its title.
- **Tested headless**, with the Photo Booth, the Observatory and the public pack's server mods:
  - In 15 minutes the photographer took four portraits of a colonist out of doors, up to 31 blocks from the booth, and two views of the Observatory.
  - None of the pictures was named after the colony alone.
  - A visitor at the mark bought their portrait for 1,000 coins, as with 0.3.8.

## 0.3.8 - 2026-09-27 - sharper pictures on high-resolution film, and full rolls used up

- **High-resolution film makes sharper photographs.** Exposure: Expanded's high-resolution rolls
  have twice the frame of an ordinary roll, and the colonists' pictures now follow the roll: 192
  pixels a side on a high-resolution roll instead of 96 - four times the detail. Until now every
  picture was 96 pixels whatever was loaded. It goes for the astronomer's photograph from the
  lookout too. A 192-pixel picture is exposed four times as long (about 24 seconds instead of 6),
  so the server does no more work in any one moment than before: measured headless, an ordinary
  picture costs the server about 0.1-0.2 s all told and a high-resolution one about 0.3-0.5 s.
- **The Film setting picks a kind, not one item.** *Black and white* takes any black-and-white roll
  that is not a fast one - Exposure's own, and Expanded's high-capacity and high-resolution rolls -
  and the other choices the same way. In 0.3.7 it took only Exposure's plain roll: Expanded's rolls
  on the shelf were ignored, and one in the camera was even taken out. When several rolls would do,
  the photographer uses the plainest first, so a high-resolution or fast roll is only spent when
  the setting asks for it or nothing else is left (on *whatever is on the shelf* too).
- **New film choices: High-resolution B&W and High-resolution colour**, when Exposure: Expanded is
  installed. Without such a roll he asks the colony for one.
- **Full rolls are used up instead of piling up on the shelf.** Every picture on the roll was
  developed the moment it was taken and is already in an album or a frame, so a full roll of the
  colony's own pictures now simply goes when it comes out of the camera - at the Photo Booth and at
  the Observatory - and the full rolls earlier versions left on the two shelves are cleared the
  next time the camera is loaded. A roll with anybody else's pictures on it is never used up: a
  player's pictures exist only as negatives until the roll is developed, so that one still goes on
  the shelf for the darkroom.
- Expanded's vanity films (Game Boy, NES, C64, CGA) are only used on *whatever is on the shelf*:
  the colonists' pictures are drawn in the map's colours, not in their palettes.
- The log line for every photograph gives its size and how long the server spent drawing it.
- Tested headless: a high-resolution colour roll chosen over the black-and-white roll next to it
  (192 pixels); *Black and white* with only a high-resolution black-and-white roll on the shelf
  (used, 192 pixels) and with an ordinary one beside it (the ordinary one used, 96 pixels);
  Expanded's pencil filter on a 192-pixel picture; a missing high-resolution roll asked for with no
  outing; a full roll of the colony's own pictures used up and the spare loaded, a full roll with
  somebody else's pictures put on the shelf, a full roll and no spare (a roll asked for, no
  outing), and old full rolls cleared off the shelf while a player's stayed; and the astronomer's
  lookout photograph on ordinary and on high-resolution film. The whole-pack gate ran with
  Exposure: Space in it for the first time: 75 of 75 checks, and no warning from the mod at all.

## 0.3.7 - 2026-09-27 - pick the film and the filter

- **New: the Photo Booth's settings tab has Film and Filter**, so you decide what the colony's
  photographs look like.
  - **Film:** *whatever is on the shelf* (the default - what the booth has always done), black and
    white, colour, or one of the two high-sensitivity rolls, which see in the dark. If the camera
    holds a different kind, the photographer takes that roll out - its pictures stay on it - and puts
    it on the shelf for later. With no roll of the chosen kind he asks the colony for one, and the
    colony is told once a day which film is missing.
  - **Filter:** *as fitted in the camera* (the default - nothing changes until you pick one), no
    filter, red, orange, yellow, green, blue or sepia (brown glass); with Exposure: Expanded also
    pencil sketch, outline, soft focus, mirror, faded and vivid. He fits it before every picture
    from his pack or the shelf, and the filter that comes off goes on the shelf. One the booth has
    not got is asked for, and he keeps taking pictures with what is fitted in the meantime.
  - It is all real items in the real camera: take the camera off the shelf and the chosen roll and
    filter are in it.
- **Colour filters on black-and-white film work like real ones now.** The glass passes its own
  colour and holds back the rest, so the greys shift while the picture keeps its brightness: red
  darkens a blue sky against the land and lightens red brick, orange and yellow do the same more
  gently, green lightens grass and leaves, and blue does the opposite of red. Before, the frame was
  tinted and then turned grey, which mostly just made the whole picture lighter or darker - through
  a red pane the sky and the grass came out the same grey. On colour film the glass tints the
  picture, as before.
- The log line for every photograph names the film and the filter it was taken on.
- The recipe mode stays in the same tab. The booth has its own settings module for all three now
  (MineColonies' crafter settings module could not hold the other two); a booth built before 0.3.7
  keeps the recipe mode it had. The film and filter are saved by name, so a later version adding
  choices, or Exposure: Expanded being removed, cannot quietly swap one filter for its neighbour -
  a choice that is no longer offered goes back to the default.
- Tested headless on the test server: colour film through red glass (the black-and-white roll next
  to it left alone), black-and-white through Expanded's pencil filter (the red pane someone had
  fitted taken off and put on the shelf), colour film chosen with only black and white on the shelf
  (a roll asked for, no outing), and black-and-white through red and through yellow - the same view
  photographed each time, so the greys could be compared: through red the sky now comes out
  clearly darker than the grass. Then the settings across a restart: kept as they were set, the
  recipe mode carried over from a booth saved by 0.3.6, and a filter from Expanded back to the
  default once Expanded is gone.

## 0.3.6 - 2026-09-27 - the photographer loads the film he was given

- **Fixed: the photographer stopped taking pictures for good once his first roll was used up.** When
  he runs out of film he asks the colony for a roll, and a courier brings it - but MineColonies hands
  a delivered request to the worker himself, so the roll lands in the photographer's own pack, not on
  the Photo Booth's shelf. He only ever looked on the shelf. So he set out - to the studio mark, or
  towards a new building for the chronicle - took the camera up, found no film, put it back and
  walked home again, over and over, without a word. And because he was already carrying a roll, the
  colony never asked for another one. He now loads film from his own pack first and the shelf
  second. (Until you update, a roll of film put on the Photo Booth's shelf gets him going again.)
- **He checks for film before he sets out** - a camera *and* a roll to put in it - instead of finding
  out at the viewpoint. With no film anywhere he asks for a roll, and the colony is told once a day,
  the same way it is told about a missing camera.
- When an outing is called off, the log says why (once a day for each reason), so a photographer who
  never shoots is no longer a silent mystery.
- The startup line in the log now reads the real version from the mod (it had said 0.3.2 ever since
  0.3.2).
- Built against MineColonies 1.1.1399, Structurize 1.0.833, BlockUI 1.0.212, Exposure 1.9.19 and
  Trade Post 1.06.013, and every call into them was checked against those and the previous versions.
  The headless test colony now hands the photographer his film the way a courier does: 0.3.5 walked
  out and back all afternoon without a single picture, 0.3.6 photographed the new building for the
  chronicle and then took a portrait; with no film at all it asks for a roll and stays home.

## 0.3.5 - 2026-09-23 - the photographer gets his day back

- **Fixed: at level 2 the studio ate the whole day.** Every visitor in the colony thinks about a
  portrait every fifteen seconds and takes one about one time in ten; a sitter standing at the mark
  outranks everything else the photographer could be doing; and nothing anywhere said *enough for
  today*. With a tavern full of visitors that adds up to a man who never leaves the studio - so the
  colony chronicle, which is the photographer's actual work and what the album on the shelf is made
  of, stood still. The studio now has a day's quota by level (**2** sittings at level 2, then 3, 5
  and 8), a quiet spell after each print while it is developed (2.5 in-game hours at level 2, down
  to 1 hour at level 5, where there is a darkroom hand), and it closes to new sitters while the
  chronicle is behind - two buildings owed a photograph, or one that has been waiting two in-game
  hours. A visitor who books and never turns up now leaves a short quiet spell instead of a free
  chair, so the next one does not walk straight in.
- The hut says so itself: the Photo Booth's description - what a talking colonist reads out when you
  ask about the studio - now names the day's quota and how many sittings it has taken today.
- Checked headless on the test server (`voygate`, 75 checks): the quota, the quiet spell, the day
  rolling over, the no-show, and the chronicle closing and re-opening the door.

## 0.3.4 - 2026-09-19 - the Observatory and the Photo Booth can be upgraded

- **Fixed: both huts were stuck at level 1.** Their unlock research was worth `1.0`, and MineColonies
  reads that number twice - below 1 the hut cannot be built at all, and a hut whose level has caught
  up with it cannot be raised any further (`AbstractBuilding.requestUpgrade`). So the research
  unlocked the building and locked it in the same breath, and the Builder answered *"You have to
  unlock the research to upgrade this building"* with no research left to do. Both effects are worth
  **5.0** now, like every hut in MineColonies itself and like the Departure Point. The blueprints for
  levels 2-5 were in the mod all along.
- **Fixed: the Photo Booth did not count as housing.** A worker who sleeps at their workplace gives
  up their bed in a house and MineColonies raises the colony's ceiling by one to make up for it -
  but only for a building that has beds **and** a `WorkAtHomeBuildingModule`. The photographer's
  module has to extend `CraftingWorkerBuildingModule` (the crafting AI casts to it), and Java has no
  second parent, so the Photo Booth had the bed and the sleeper but not the marker: hiring a
  photographer quietly cost the colony one citizen, and it reported no room for anybody new while a
  house had a free bed. It now carries `PhotoBoothHomeModule`, a work-at-home module that hires
  nobody, creates no resolvers and exists to be counted. The Observatory was never affected - the
  astronomer is not a crafter, so it uses MineColonies' own class.
- **Existing colonies:** nothing to do. The research you have finished is worth five levels the
  moment the world loads.
- Verified headless before release (`voygate`, 55 checks): a colony from nothing, both researches
  finished, every hut walked from level 1 to 5 and refused at 6, and the colony's ceiling read before
  and after each worker is hired.

## 0.3.3 - 2026-09-16 - the Observatory and the Photo Booth can be crafted

- **Fixed: neither hut block had a crafting recipe** - the only way to get one was the creative tab.
  They are made like the Departure Point now: eight planks around a gold building scepter, with a
  **spyglass** in the middle for the Observatory and an **item frame** for the Photo Booth.
## 0.3.2 - 2026-09-13 - the Photographer sticks to photography

- **Fixed: the Photographer would learn any recipe at all** - planks, stone bricks, walls, chests,
  anything a player cared to teach him. His crafting module was MineColonies' *general* crafter, the
  kind meant for a hut with no trade of its own, so the Photo Booth quietly stood in for the Sawmill
  and the Stonemason. He now learns only recipes whose **product** is in the item tag
  `voyager:photographer_product`: cameras, film, photograph frames, albums, the lightroom, the
  camera stand and the interplanar projector. Wood goes back to the Sawmill and stone to the
  Stonemason.
- The list is a **tag**, not code, so a pack that adds another camera mod can hand the Photographer
  its film by adding to it - no jar edit.
- The test is on the product rather than the ingredients on purpose: a photograph frame is sticks
  and glass, and judged by what goes in it would belong to the Sawmill.
- **Existing colonies:** the check runs when a recipe is *taught*, so anything your Photographer
  learned before this update stays on his list until you remove it in his Recipes tab.

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

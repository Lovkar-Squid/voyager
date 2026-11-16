package me.lovkar.voyagertrophycheck;

import io.netty.buffer.Unpooled;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.VoyagerConfig;
import me.lovkar.voyager.block.SouvenirBlockEntity;
import me.lovkar.voyager.trophy.EndPlace;
import me.lovkar.voyager.trophy.TripStory;
import me.lovkar.voyager.trophy.TripTally;
import me.lovkar.voyager.trophy.Trophies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Headless check of 0.4.0's trophies on the dedicated test server, through the mod's own factories:
 * a souvenir for every one of the sixteen places with its name and tooltip, what happened on a trip
 * in words, the Dragon's Wake only after a dragon win, the roll by building level, the End-city book
 * pool (never Mending, only listed enchantments, unknown ids left out), the map of the End (the
 * story, and no vanilla map), the story through NBT save and parse and through the network codec,
 * the job's trip tally through NBT, and a souvenir placed in the world, saved, broken and knocked off
 * its support - the drop must carry the same story every time.
 *
 * <p>The expected English is written out here by hand from the approved names, so a wrong lang
 * value or a wrong place cannot agree with itself. The rig seeds no config: the mod's defaults are
 * live. Prints "[trophycheck] CHECK ..." lines and "[trophycheck] RESULT trophycheck: PASS|FAIL",
 * then halts.</p>
 */
@Mod("voyagertrophycheck")
public class TrophyCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger("voyagertrophycheck");
    private static final String VOYAGER = "Mira Tallow";

    /** The approved names (docs/PLAN-0.4.0.md), in EndPlace's order. */
    private static final String[][] APPROVED = {
            {"hollow_spires", "Shard from the Hollow Spires"},
            {"chorus_reach", "Sprig from Chorus Reach"},
            {"pale_shelf", "Pebble from the Pale Shelf"},
            {"purpur_steps", "Fragment from Purpur Steps"},
            {"shulker_narrows", "Shell from Shulker Narrows"},
            {"quiet_rim", "Keepsake from the Quiet Rim"},
            {"gatewatch_isle", "Stone from Gatewatch Isle"},
            {"listening_pillars", "Shard from the Listening Pillars"},
            {"long_drop", "Pebble from the Long Drop"},
            {"obsidian_hook", "Splinter from Obsidian Hook"},
            {"dragons_wake", "Scale from the Dragon's Wake"},
            {"elytra_point", "Feather from Elytra Point"},
            {"broken_arches", "Fragment from the Broken Arches"},
            {"lanternless_bay", "Stone from Lanternless Bay"},
            {"far_isles", "Keepsake from the Far Isles"},
            {"drifting_keel", "Splinter from the Drifting Keel"},
    };

    private static final ResourceLocation ENDERMAN = id("enderman");
    private static final ResourceLocation ENDERMITE = id("endermite");
    private static final ResourceLocation SHULKER = id("shulker");
    private static final ResourceLocation DRAGON = id("ender_dragon");

    private int ticks = 0;
    private boolean failed = false;

    public TrophyCheck() {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private static ResourceLocation id(final String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private void check(final String what, final boolean ok, final String detail) {
        LOGGER.info("[trophycheck] CHECK {}: {} {}", what, ok ? "OK" : "FAIL", detail);
        failed |= !ok;
    }

    private void checkEq(final String what, final Object got, final Object want) {
        check(what, Objects.equals(got, want), "got " + got + ", want " + want);
    }

    private void onTick(final ServerTickEvent.Post event) {
        if (++ticks != 100) {
            return;
        }
        final MinecraftServer server = event.getServer();
        final Runnable[] parts = {
                this::checkRegistered,
                this::checkNamesAndTooltips,
                this::checkEventLines,
                this::checkPlaces,
                this::checkRoll,
                () -> checkBooks(server),
                this::checkTally,
                () -> checkRoundTrips(server),
                () -> checkMap(server),
                () -> checkComms(server),
                () -> checkPlacedSouvenir(server.overworld()),
        };
        for (final Runnable part : parts) {
            try {
                part.run();
            } catch (final Throwable t) {
                LOGGER.error("[trophycheck] a check part threw", t);
                check("check part ran to the end", false, t.toString());
            }
        }
        LOGGER.info("[trophycheck] RESULT trophycheck: {}", failed ? "FAIL" : "PASS");
        server.halt(false);
    }

    private static TripStory story(final EndPlace place, final List<TripStory.Fight> fights) {
        return new TripStory(place.id(), VOYAGER, 12, 34, TripStory.LOOK_LAUNCHPAD, fights);
    }

    private static List<TripStory.Fight> fought(final Object... mobAndCount) {
        final List<TripStory.Fight> out = new ArrayList<>();
        for (int i = 0; i < mobAndCount.length; i += 2) {
            out.add(new TripStory.Fight((ResourceLocation) mobAndCount[i], (Integer) mobAndCount[i + 1]));
        }
        return out;
    }

    private static List<String> tooltip(final ItemStack stack) {
        final List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        final List<String> out = new ArrayList<>();
        for (final Component line : lines) {
            out.add(line.getString());
        }
        return out;
    }

    // ---------------------------------------------------------------- registrations

    private void checkRegistered() {
        check("block voyager:souvenir", BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "souvenir")), "");
        check("item voyager:souvenir is the block's item", Voyager.SOUVENIR_ITEM.get() instanceof BlockItem bi && bi.getBlock() == Voyager.SOUVENIR.get(), "");
        check("item voyager:end_map", BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "end_map")), "");
        check("block entity voyager:souvenir", BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "souvenir")), "");
        check("data component voyager:trip_story", BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "trip_story")),
                "persistent " + !Voyager.TRIP_STORY.get().isTransient());
        checkEq("souvenir does not stack", new ItemStack(Voyager.SOUVENIR_ITEM.get()).getMaxStackSize(), 1);
        checkEq("map does not stack", new ItemStack(Voyager.END_MAP.get()).getMaxStackSize(), 1);
        check("souvenir block has a facing", Voyager.SOUVENIR.get().defaultBlockState().hasProperty(HorizontalDirectionalBlock.FACING), "");
        checkEq("plain souvenir name", new ItemStack(Voyager.SOUVENIR_ITEM.get()).getHoverName().getString(), "Souvenir");
    }

    // ---------------------------------------------------------------- the sixteen souvenirs

    private void checkNamesAndTooltips() {
        checkEq("sixteen places", EndPlace.values().length, APPROVED.length);
        final List<TripStory.Fight> fights = fought(ENDERMAN, 2, SHULKER, 1);
        for (int i = 0; i < APPROVED.length && i < EndPlace.values().length; i++) {
            final EndPlace place = EndPlace.values()[i];
            final TripStory story = story(place, fights);
            final ItemStack stack = Trophies.souvenir(story);
            checkEq("place " + i + " id", place.id(), APPROVED[i][0]);
            checkEq("souvenir name " + place.id(), stack.getHoverName().getString(), APPROVED[i][1]);
            check("souvenir " + place.id() + " carries its story", story.equals(stack.get(Voyager.TRIP_STORY.get())), "");
            check("name is item_name, not custom_name", stack.has(DataComponents.ITEM_NAME) && !stack.has(DataComponents.CUSTOM_NAME), place.id());
            checkEq("souvenir tooltip " + place.id(), tooltip(stack),
                    List.of("Brought home by " + VOYAGER, "Expedition 12 - Launchpad, day 34", "Fought 2 endermen and a shulker"));
        }
        // an anvil name wins over the place name, and the story stays
        final ItemStack renamed = Trophies.souvenir(story(EndPlace.QUIET_RIM, List.of()));
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Grandmother's stone"));
        checkEq("renamed souvenir", renamed.getHoverName().getString(), "Grandmother's stone");
        checkEq("renamed souvenir keeps its tooltip", tooltip(renamed),
                List.of("Brought home by " + VOYAGER, "Expedition 12 - Launchpad, day 34", "A quiet crossing"));
    }

    private void checkEventLines() {
        final TripStory gate = new TripStory(EndPlace.FAR_ISLES.id(), "Ott", 3, 7, TripStory.LOOK_END_GATE, List.of());
        checkEq("End Gate expedition line", gate.expeditionLine().getString(), "Expedition 3 - End Gate, day 7");
        checkEq("brought-by line", gate.broughtBy().getString(), "Brought home by Ott");
        checkEq("no fights", gate.eventLine().getString(), "A quiet crossing");
        checkEq("one shulker", story(EndPlace.FAR_ISLES, fought(SHULKER, 1)).eventLine().getString(), "Fought a shulker");
        checkEq("three kinds", story(EndPlace.FAR_ISLES, fought(ENDERMAN, 2, ENDERMITE, 1, SHULKER, 3)).eventLine().getString(),
                "Fought 2 endermen, an endermite and 3 shulkers");
        checkEq("a mob without words of its own", story(EndPlace.FAR_ISLES, fought(id("phantom"), 3, ENDERMAN, 1)).eventLine().getString(),
                "Fought 3 Phantom and an enderman");
        final TripStory dragon = story(EndPlace.DRAGONS_WAKE, fought(ENDERMAN, 1, DRAGON, 1));
        check("dragon story knows the dragon", dragon.beatTheDragon(), "");
        checkEq("dragon line", dragon.eventLine().getString(), "Met the dragon and lived");
    }

    // ---------------------------------------------------------------- the roll

    private void checkPlaces() {
        final RandomSource random = RandomSource.create(20261116L);
        boolean alwaysWake = true;
        for (int i = 0; i < 500; i++) {
            alwaysWake &= Trophies.place(true, random) == EndPlace.DRAGONS_WAKE;
        }
        check("a dragon win: always the Dragon's Wake", alwaysWake, "500 rolls");
        final Set<EndPlace> seen = new HashSet<>();
        boolean neverWake = true;
        for (int i = 0; i < 5000; i++) {
            final EndPlace place = Trophies.place(false, random);
            neverWake &= place != EndPlace.DRAGONS_WAKE;
            seen.add(place);
        }
        check("no dragon win: never the Dragon's Wake", neverWake, "5000 rolls");
        checkEq("no dragon win: all fifteen other places come up", seen.size(), 15);
        final TripTally tally = new TripTally();
        tally.recordWin(ENDERMAN);
        check("an ordinary trip did not beat the dragon", !tally.beatTheDragon(), "");
        tally.recordWin(DRAGON);
        check("a dragon win is in the tally", tally.beatTheDragon(), "");
    }

    private void checkRoll() {
        final RandomSource random = RandomSource.create(42L);
        checkEq("level 4, every chance 1: a souvenir only", Trophies.decide(4, 1.0, 1.0, 1.0, random), new Trophies.Roll(true, false, false));
        checkEq("level 5, every chance 1: all three", Trophies.decide(5, 1.0, 1.0, 1.0, random), new Trophies.Roll(true, true, true));
        checkEq("level 5, every chance 0: nothing", Trophies.decide(5, 0.0, 0.0, 0.0, random), new Trophies.Roll(false, false, false));
        int souvenirs = 0, books = 0, maps = 0;
        final int n = 40000;
        for (int i = 0; i < n; i++) {
            final Trophies.Roll roll = Trophies.decide(5, VoyagerConfig.souvenirChance(), VoyagerConfig.endCityBookChance(),
                    VoyagerConfig.endMapChance(), random);
            souvenirs += roll.souvenir() ? 1 : 0;
            books += roll.book() ? 1 : 0;
            maps += roll.map() ? 1 : 0;
        }
        check("default chances: about 10 % souvenirs", Math.abs(souvenirs / (double) n - 0.10) < 0.01, souvenirs + " of " + n);
        check("default chances: about 5 % books", Math.abs(books / (double) n - 0.05) < 0.007, books + " of " + n);
        check("default chances: about 3 % maps", Math.abs(maps / (double) n - 0.03) < 0.006, maps + " of " + n);
    }

    // ---------------------------------------------------------------- End-city books

    private void checkBooks(final MinecraftServer server) {
        final HolderLookup.Provider registries = server.registryAccess();
        final List<String> pool = VoyagerConfig.DEFAULT_BOOK_ENCHANTMENTS;
        checkEq("the rig runs the default book list", VoyagerConfig.bookEnchantments(), pool);
        checkEq("default pool resolves fully", Trophies.bookPool(registries, pool).size(), pool.size());
        final RandomSource random = RandomSource.create(5L);
        final Set<String> seen = new HashSet<>();
        int bad = 0;
        String firstBad = "";
        for (int i = 0; i < 4000; i++) {
            final ItemStack book = Trophies.book(registries, random, VoyagerConfig.bookEnchantments());
            final ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            boolean ok = book.is(Items.ENCHANTED_BOOK) && stored.size() == 1;
            for (final var entry : stored.entrySet()) {
                final String key = entry.getKey().unwrapKey().map(k -> k.location().toString()).orElse("?");
                final int level = entry.getIntValue();
                ok &= pool.contains(key) && !"minecraft:mending".equals(key)
                        && level >= 1 && level <= entry.getKey().value().getMaxLevel();
                seen.add(key);
                if (!ok && firstBad.isEmpty()) {
                    firstBad = key + " " + level;
                }
            }
            bad += ok ? 0 : 1;
        }
        check("4000 default books: one listed enchantment each, never Mending, level 1..max", bad == 0,
                bad + " bad" + (firstBad.isEmpty() ? "" : ", first " + firstBad));
        check("4000 default books: no Mending", !seen.contains("minecraft:mending"), seen.size() + " kinds seen");
        checkEq("4000 default books: every listed enchantment comes up", seen.size(), pool.size());

        final List<String> withUnknown = List.of("voyager:no_such_enchantment", "minecraft:sharpness", "not an id at all");
        checkEq("unknown ids are left out of the pool", Trophies.bookPool(registries, withUnknown).size(), 1);
        boolean allSharpness = true;
        for (int i = 0; i < 50; i++) {
            final ItemStack book = Trophies.book(registries, random, withUnknown);
            allSharpness &= book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream()
                    .allMatch(h -> h.is(ResourceKey.create(Registries.ENCHANTMENT, id("sharpness"))));
        }
        check("with unknown ids: only the listed one that exists", allSharpness, "50 books");
        check("nothing valid listed: no book at all", Trophies.book(registries, random, List.of("voyager:nothing")).isEmpty(), "");
    }

    // ---------------------------------------------------------------- the job's tally

    private void checkTally() {
        final TripTally tally = new TripTally();
        tally.recordWin(ENDERMAN);
        tally.recordWin(SHULKER);
        tally.recordWin(ENDERMAN);
        final ListTag saved = tally.save();
        final CompoundTag job = new CompoundTag();
        job.put("trip_fights", saved);
        final TripTally back = new TripTally();
        back.load(job.getList("trip_fights", TripTally.listType()));
        checkEq("tally through NBT", back.fights(), fought(ENDERMAN, 2, SHULKER, 1));
        checkEq("tally tells the story", story(EndPlace.LONG_DROP, back.fights()).eventLine().getString(), "Fought 2 endermen and a shulker");
        back.clear();
        checkEq("a new trip starts with nothing", back.fights(), List.of());
    }

    // ---------------------------------------------------------------- save, parse, network

    private void checkRoundTrips(final MinecraftServer server) {
        final HolderLookup.Provider registries = server.registryAccess();
        final TripStory story = story(EndPlace.SHULKER_NARROWS, fought(SHULKER, 4, ENDERMAN, 1));
        final ItemStack souvenir = Trophies.souvenir(story);
        final ItemStack renamed = souvenir.copy();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("From the narrows"));
        for (final ItemStack stack : List.of(souvenir, renamed, Trophies.endMap(story))) {
            final String what = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + (stack.has(DataComponents.CUSTOM_NAME) ? " (renamed)" : "");
            final Tag saved = stack.save(registries);
            final ItemStack parsed = ItemStack.parse(registries, saved).orElse(ItemStack.EMPTY);
            check("NBT save and parse: " + what, ItemStack.isSameItemSameComponents(stack, parsed) && story.equals(parsed.get(Voyager.TRIP_STORY.get())),
                    saved.toString());
            final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            ItemStack.STREAM_CODEC.encode(buf, stack);
            final ItemStack sent = ItemStack.STREAM_CODEC.decode(buf);
            check("network codec: " + what, ItemStack.isSameItemSameComponents(stack, sent) && buf.readableBytes() == 0,
                    sent.getHoverName().getString());
        }
    }

    // ---------------------------------------------------------------- the map of the End

    private void checkMap(final MinecraftServer server) {
        final Item map = Voyager.END_MAP.get();
        check("map of the End is not a vanilla map", !(map instanceof MapItem), map.getClass().getName());
        final TripStory story = story(EndPlace.ELYTRA_POINT, fought(ENDERMITE, 2));
        final ItemStack stack = Trophies.endMap(story);
        check("map carries the story", story.equals(stack.get(Voyager.TRIP_STORY.get())), "");
        check("map has no map data", !stack.has(DataComponents.MAP_ID) && !stack.has(DataComponents.MAP_DECORATIONS), "");
        checkEq("map name", stack.getHoverName().getString(), "Map of the End");
        checkEq("map tooltip", tooltip(stack), List.of("A hand-drawn chart of the outer islands",
                "Brought home by " + VOYAGER, "Expedition 12 - Launchpad, day 34", "Fought 2 endermites"));
        checkEq("plain map tooltip", tooltip(new ItemStack(map)), List.of("A hand-drawn chart of the outer islands"));
    }

    // ---------------------------------------------------------------- Long-range Comms

    private void checkComms(final MinecraftServer server) {
        final TripStory story = story(EndPlace.HOLLOW_SPIRES, List.of());
        final ItemStack souvenir = Trophies.souvenir(story);
        final Holder<Enchantment> sharpness = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id("sharpness")));
        final ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(sharpness, 3));
        final ItemStack map = Trophies.endMap(story);
        checkEq("comms, a souvenir", Component.translatable(Trophies.KEY_COMMS, VOYAGER,
                Trophies.join(List.of(Trophies.describe(souvenir)))).getString(), VOYAGER + " brought home a Shard from the Hollow Spires");
        checkEq("comms, all three", Component.translatable(Trophies.KEY_COMMS, VOYAGER, Trophies.join(List.of(
                Trophies.describe(souvenir), Trophies.describe(book), Trophies.describe(map)))).getString(),
                VOYAGER + " brought home a Shard from the Hollow Spires, an enchanted book (Sharpness III) and a map of the End");
    }

    // ---------------------------------------------------------------- in the world

    private BlockPos place(final ServerLevel level, final BlockPos on, final ItemStack stack) {
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(on).add(0, 0.5, 0), Direction.UP, on, false);
        final BlockPlaceContext context = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack, hit) {
        };
        ((BlockItem) stack.getItem()).place(context);
        return on.above();
    }

    private List<ItemStack> dropsAround(final ServerLevel level, final BlockPos at) {
        final List<ItemStack> out = new ArrayList<>();
        for (final ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3))) {
            out.add(entity.getItem().copy());
            entity.discard();
        }
        return out;
    }

    private void checkPlacedSouvenir(final ServerLevel level) {
        final BlockPos base = level.getSharedSpawnPos().offset(4, 12, 4);
        final TripStory story = story(EndPlace.BROKEN_ARCHES, fought(ENDERMAN, 3));
        final String name = "Fragment from the Broken Arches";

        level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        final ItemStack inAir = Trophies.souvenir(story);
        place(level, base, inAir);
        check("cannot stand on thin air", !level.getBlockState(base).is(Voyager.SOUVENIR.get())
                && !level.getBlockState(base.above()).is(Voyager.SOUVENIR.get()) && inAir.getCount() == 1, "");

        level.setBlock(base, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        final ItemStack stack = Trophies.souvenir(story);
        final BlockPos at = place(level, base, stack);
        final BlockState state = level.getBlockState(at);
        check("souvenir placed on stone", state.is(Voyager.SOUVENIR.get()) && stack.isEmpty(), state.toString());
        final BlockEntity be = level.getBlockEntity(at);
        check("placed souvenir has its block entity", be instanceof SouvenirBlockEntity, String.valueOf(be));
        if (!(be instanceof SouvenirBlockEntity souvenir)) {
            return;
        }
        checkEq("block entity keeps the story", souvenir.getStory(), story);

        final CompoundTag saved = souvenir.saveWithFullMetadata(level.registryAccess());
        final BlockEntity loaded = BlockEntity.loadStatic(at, state, saved, level.registryAccess());
        check("block entity through save and load", loaded instanceof SouvenirBlockEntity s && story.equals(s.getStory()), saved.toString());
        check("the name survives save and load too", loaded != null
                && Objects.equals(loaded.collectComponents().get(DataComponents.ITEM_NAME), Trophies.souvenir(story).get(DataComponents.ITEM_NAME)), "");

        final List<ItemStack> drops = Block.getDrops(state, level, at, souvenir);
        check("loot table drops one souvenir", drops.size() == 1 && drops.get(0).is(Voyager.SOUVENIR_ITEM.get()), drops.toString());
        if (drops.size() == 1) {
            checkEq("loot drop carries the story", drops.get(0).get(Voyager.TRIP_STORY.get()), story);
            checkEq("loot drop keeps the name", drops.get(0).getHoverName().getString(), name);
        }

        level.destroyBlock(at, true);
        final List<ItemStack> broken = dropsAround(level, at);
        check("broken: the block is gone", level.getBlockState(at).isAir(), "");
        check("broken: one souvenir dropped", broken.size() == 1 && broken.get(0).is(Voyager.SOUVENIR_ITEM.get()), broken.toString());
        if (broken.size() == 1) {
            checkEq("broken: the drop carries the same story", broken.get(0).get(Voyager.TRIP_STORY.get()), story);
            checkEq("broken: the drop keeps the name", broken.get(0).getHoverName().getString(), name);
            check("broken: the drop is the same item as the one placed",
                    ItemStack.isSameItemSameComponents(broken.get(0), Trophies.souvenir(story)), "");
        }

        // an anvil-named souvenir set on a shelf, and the shelf taken away under it
        final ItemStack named = Trophies.souvenir(story);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("The one from the arches"));
        place(level, base, named);
        check("renamed souvenir placed", level.getBlockState(at).is(Voyager.SOUVENIR.get()), "");
        level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        final List<ItemStack> fell = dropsAround(level, at);
        check("support gone: the souvenir pops off", level.getBlockState(at).isAir() && fell.size() == 1, fell.toString());
        if (fell.size() == 1) {
            checkEq("support gone: the drop carries the story", fell.get(0).get(Voyager.TRIP_STORY.get()), story);
            checkEq("support gone: the drop keeps its anvil name", fell.get(0).getHoverName().getString(), "The one from the arches");
        }
        level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check("dragon mob id", EntityType.getKey(EntityType.ENDER_DRAGON).equals(DRAGON), DRAGON.toString());
    }
}

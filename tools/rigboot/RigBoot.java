package me.lovkar.voyagerrigboot;

import me.lovkar.voyager.Voyager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless boot check on the dedicated test server: Voyager loaded next to MineColonies, its hut
 * blocks and items, the three jobs and buildings and the End damage type are registered, and the
 * server ticks. Prints "[rigboot] CHECK ..." lines and "[rigboot] RESULT rigboot: PASS|FAIL", then halts.
 */
@Mod("voyagerrigboot")
public class RigBoot {
    private static final Logger LOGGER = LoggerFactory.getLogger("voyagerrigboot");
    private int ticks = 0;
    private boolean failed = false;

    public RigBoot() {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void check(final String what, final boolean ok, final String detail) {
        LOGGER.info("[rigboot] CHECK {}: {} {}", what, ok ? "OK" : "FAIL", detail);
        failed |= !ok;
    }

    private void onTick(final ServerTickEvent.Post event) {
        if (++ticks != 100) {
            return;
        }
        final MinecraftServer server = event.getServer();
        for (final String id : new String[] {Voyager.HUT_NAME, Voyager.OBSERVATORY_HUT_NAME, Voyager.PHOTOBOOTH_HUT_NAME}) {
            final ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Voyager.MODID, id);
            check("block " + id, BuiltInRegistries.BLOCK.containsKey(rl), rl.toString());
            check("item " + id, BuiltInRegistries.ITEM.containsKey(rl), rl.toString());
        }
        check("jobs", Voyager.JOB.isBound() && Voyager.ASTRONOMER_JOB.isBound() && Voyager.PHOTOGRAPHER_JOB.isBound(), "voyager, astronomer, photographer");
        check("buildings", Voyager.BUILDING.isBound() && Voyager.OBSERVATORY.isBound() && Voyager.PHOTOBOOTH.isBound(), "departure point, observatory, photo booth");
        final ResourceLocation end = ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "the_end");
        check("damage type", server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).containsKey(end), end.toString());
        check("server ticks", true, ticks + " ticks");
        LOGGER.info("[rigboot] RESULT rigboot: {}", failed ? "FAIL" : "PASS");
        server.halt(false);
    }
}

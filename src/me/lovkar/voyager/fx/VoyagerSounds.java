package me.lovkar.voyager.fx;

import me.lovkar.voyager.Voyager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Our own send-off and homecoming sounds (synthesized, see tools/gen_sounds.py). */
public final class VoyagerSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Voyager.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_IGNITION = sound("rocket_ignition");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_LAUNCH = sound("rocket_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_LANDING = sound("rocket_landing");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_TOUCHDOWN = sound("rocket_touchdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_CHARGE = sound("gate_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_WARP = sound("gate_warp");

    private VoyagerSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(final String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Voyager.MODID, name)));
    }
}

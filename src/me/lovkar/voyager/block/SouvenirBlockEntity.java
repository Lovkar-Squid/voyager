package me.lovkar.voyager.block;

import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.trophy.TripStory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds a placed souvenir's trip story.
 *
 * <p>The story is an implicit component: taken off the item when the souvenir is placed, saved with
 * the chunk, and handed back to the item the loot table drops ({@code copy_components} from the
 * block entity) - and to a creative pick-block. The souvenir's name and an anvil name are not ours
 * to handle: vanilla keeps every component the block entity did not take in the block entity's
 * own component map, saves it and hands it back the same way.</p>
 *
 * <p>Clients get the same data with the chunk (the update tag), so a creative pick-block of a placed
 * souvenir copies the story and the name along with it.</p>
 */
public class SouvenirBlockEntity extends BlockEntity {

    private static final String TAG_STORY = "story";

    private @Nullable TripStory story;

    public SouvenirBlockEntity(final BlockPos pos, final BlockState state) {
        super(Voyager.SOUVENIR_BE.get(), pos, state);
    }

    /** The trip this souvenir is from, or null for one without a story (a creative-tab souvenir). */
    public @Nullable TripStory getStory() {
        return story;
    }

    @Override
    protected void applyImplicitComponents(final @NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        story = input.get(Voyager.TRIP_STORY.get());
    }

    @Override
    protected void collectImplicitComponents(final DataComponentMap.@NotNull Builder components) {
        super.collectImplicitComponents(components);
        if (story != null) {
            components.set(Voyager.TRIP_STORY.get(), story);
        }
    }

    /**
     * The story travels as a component; it must not also ride along as block-entity data on a picked
     * item. Vanilla marks the hook deprecated (it is meant for block entities, not for callers) and
     * overrides it in its own block entities exactly like this.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(final @NotNull CompoundTag tag) {
        tag.remove(TAG_STORY);
    }

    @Override
    protected void saveAdditional(final @NotNull CompoundTag tag, final HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        if (story != null) {
            TripStory.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), story)
                    .resultOrPartial(error -> Voyager.LOGGER.warn("[Voyager] could not save a souvenir's story at {}: {}", worldPosition, error))
                    .ifPresent(saved -> tag.put(TAG_STORY, saved));
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(final HolderLookup.@NotNull Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(final @NotNull CompoundTag tag, final HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        story = !tag.contains(TAG_STORY) ? null
                : TripStory.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get(TAG_STORY))
                .resultOrPartial(error -> Voyager.LOGGER.warn("[Voyager] a souvenir at {} lost its story: {}", worldPosition, error))
                .orElse(null);
    }
}

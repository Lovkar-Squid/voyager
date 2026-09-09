package me.lovkar.voyager.network;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.MessageUtils;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.colony.BuildingObservatory;
import me.lovkar.voyager.colony.ObservatoryModules;
import me.lovkar.voyager.colony.SkyStudyModule;
import me.lovkar.voyager.sky.SkyStudies;
import me.lovkar.voyager.sky.SkyStudy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * "Start this study at that Observatory."
 *
 * <p>Our own packet rather than MineColonies' {@code TryResearchMessage}, because that one casts
 * the building it was sent from to the University and because the Observatory's studies are not
 * in MineColonies' tree at all. Everything is re-checked on the server: the packet carries a
 * position and a study id and no authority whatsoever.</p>
 */
public record StartStudyMessage(BlockPos building, ResourceLocation study) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StartStudyMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "start_study"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartStudyMessage> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
                buf.writeBlockPos(msg.building());
                buf.writeResourceLocation(msg.study());
            }, buf -> new StartStudyMessage(buf.readBlockPos(), buf.readResourceLocation()));

    @Override
    public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final StartStudyMessage message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            final IColony colony = IColonyManager.getInstance()
                    .getColonyByPosFromWorld(player.serverLevel(), message.building());
            if (colony == null || !colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.MANAGE_HUTS)) {
                return;
            }
            final IBuilding building = colony.getServerBuildingManager().getBuilding(message.building());
            if (!(building instanceof BuildingObservatory observatory)) {
                return;
            }
            // Standing next to it. A study is begun at the Observatory, not from across the map.
            if (player.distanceToSqr(message.building().getX() + 0.5, message.building().getY() + 0.5,
                    message.building().getZ() + 0.5) > 64 * 64 && !player.isCreative()) {
                return;
            }
            final SkyStudy study = SkyStudies.byId(message.study());
            if (study == null) {
                return;
            }
            final SkyStudyModule module = observatory.getModule(ObservatoryModules.STUDY);
            if (module == null) {
                return;
            }
            final String refused = module.start(study, player);
            if (refused != null) {
                MessageUtils.format(Component.translatable(refused)).sendTo(player);
            }
        });
    }
}

package me.lovkar.voyager.colony;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.client.SkyStudyWindow;
import me.lovkar.voyager.sky.SkyStudies;
import me.lovkar.voyager.sky.SkyStudy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The client's copy of the Observatory's book of studies, and the tab that shows it. */
public class SkyStudyModuleView extends AbstractBuildingModuleView {

    private final Map<ResourceLocation, Double> done = new LinkedHashMap<>();
    private @Nullable ResourceLocation current;
    private @Nullable ResourceLocation wanted;
    private int nightsDone;
    private int level;

    @Override
    public void deserialize(final @NotNull RegistryFriendlyByteBuf buf) {
        final int bookSize = buf.readVarInt();
        final List<SkyStudy> book = new ArrayList<>(bookSize);
        for (int i = 0; i < bookSize; i++) {
            book.add(SkyStudy.read(buf));
        }
        SkyStudies.receive(book);
        done.clear();
        final int finished = buf.readVarInt();
        for (int i = 0; i < finished; i++) {
            done.put(buf.readResourceLocation(), buf.readDouble());
        }
        current = buf.readBoolean() ? buf.readResourceLocation() : null;
        wanted = buf.readBoolean() ? buf.readResourceLocation() : null;
        nightsDone = buf.readVarInt();
        level = buf.readVarInt();
    }

    public boolean isDone(final ResourceLocation id) {
        return done.containsKey(id);
    }

    public @Nullable ResourceLocation current() {
        return current;
    }

    public @Nullable ResourceLocation wanted() {
        return wanted;
    }

    public int nightsDone() {
        return nightsDone;
    }

    public int observatoryLevel() {
        return level;
    }

    /** The same rules the server applies, so the button says what the click will do. */
    public SkyStudyModule.Status statusOf(final SkyStudy study) {
        if (study == null) {
            return SkyStudyModule.Status.NEEDS_LEVEL;
        }
        if (done.containsKey(study.id())) {
            return SkyStudyModule.Status.DONE;
        }
        if (study.id().equals(current)) {
            return SkyStudyModule.Status.IN_PROGRESS;
        }
        if (study.id().equals(wanted)) {
            return SkyStudyModule.Status.GATHERING;
        }
        if (level < study.tier()) {
            return SkyStudyModule.Status.NEEDS_LEVEL;
        }
        if (study.parent() != null && !done.containsKey(study.parent())) {
            return SkyStudyModule.Status.NEEDS_PARENT;
        }
        return current != null || wanted != null ? SkyStudyModule.Status.BUSY : SkyStudyModule.Status.AVAILABLE;
    }

    @Override
    public BOWindow getWindow() {
        return new SkyStudyWindow(this);
    }

    @Override
    public ResourceLocation getIconResourceLocation() {
        return ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "textures/gui/study.png");
    }

    @Override
    public Component getDesc() {
        return Component.translatable("com.voyager.gui.study.tab");
    }
}

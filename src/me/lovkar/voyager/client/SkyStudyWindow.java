package me.lovkar.voyager.client;

import java.util.List;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import me.lovkar.voyager.Voyager;
import me.lovkar.voyager.colony.SkyStudyModule;
import me.lovkar.voyager.colony.SkyStudyModuleView;
import me.lovkar.voyager.network.StartStudyMessage;
import me.lovkar.voyager.sky.SkyStudies;
import me.lovkar.voyager.sky.SkyStudy;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Observatory's book of studies: what the colony has learned about the sky, what it is
 * working on tonight, and what it could start next.
 *
 * <p>Deliberately plain. A study is a line with its name, what it costs and where it stands;
 * clicking a line that is ready begins it. Everything the button says is re-checked on the
 * server - the window is a view, not an authority.</p>
 */
public class SkyStudyWindow extends AbstractModuleWindow<SkyStudyModuleView> {

    private static final ResourceLocation LAYOUT =
            ResourceLocation.fromNamespaceAndPath(Voyager.MODID, "gui/skystudy.xml");

    private final List<SkyStudy> studies;

    public SkyStudyWindow(final SkyStudyModuleView view) {
        super(view, LAYOUT);
        this.studies = SkyStudies.ordered();
        final ScrollingList list = findPaneOfTypeByID("studies", ScrollingList.class);
        if (list != null) {
            list.setDataProvider(new ScrollingList.DataProvider() {
                @Override
                public int getElementCount() {
                    return studies.size();
                }

                @Override
                public void updateElement(final int index, final Pane row) {
                    fillRow(studies.get(index), row);
                }
            });
        }
        updateHeader();
    }

    /** The line at the top: what is on the bench tonight, or how far the colony has got. */
    private void updateHeader() {
        final Text header = findPaneOfTypeByID("progress", Text.class);
        if (header == null) {
            return;
        }
        final SkyStudy gathering = SkyStudies.byId(moduleView.wanted());
        if (gathering != null) {
            header.setText(Component.translatable("com.voyager.gui.study.gathering",
                    Component.translatable(gathering.nameKey())));
            return;
        }
        final SkyStudy running = SkyStudies.byId(moduleView.current());
        if (running != null) {
            header.setText(Component.translatable("com.voyager.gui.study.working",
                    Component.translatable(running.nameKey()), moduleView.nightsDone(), running.nights()));
            return;
        }
        int finished = 0;
        for (final SkyStudy study : studies) {
            if (moduleView.isDone(study.id())) {
                finished++;
            }
        }
        header.setText(Component.translatable("com.voyager.gui.study.idle", finished, studies.size()));
    }

    private void fillRow(final SkyStudy study, final Pane row) {
        final SkyStudyModule.Status status = moduleView.statusOf(study);
        final ButtonImage button = row.findPaneOfTypeByID("start", ButtonImage.class);
        if (button != null) {
            button.setText(Component.translatable(study.nameKey()));
            button.setEnabled(status.startable());
            button.setHandler(clicked -> begin(study));
        }
        final Text note = row.findPaneOfTypeByID("status", Text.class);
        if (note != null) {
            note.setText(describe(study, status));
        }
    }

    /** One line under the name: what it costs, or how far along it is, or why it is not ready. */
    private MutableComponent describe(final SkyStudy study, final SkyStudyModule.Status status) {
        return switch (status) {
            case DONE -> Component.translatable("com.voyager.study.status.done")
                    .withStyle(ChatFormatting.DARK_GREEN);
            case IN_PROGRESS -> Component.translatable("com.voyager.gui.study.nights",
                    moduleView.nightsDone(), study.nights()).withStyle(ChatFormatting.DARK_AQUA);
            case GATHERING -> costLine(study).withStyle(ChatFormatting.GOLD);
            case NEEDS_LEVEL -> Component.translatable("com.voyager.study.status.needs_level", study.tier())
                    .withStyle(ChatFormatting.DARK_GRAY);
            case NEEDS_PARENT -> Component.translatable("com.voyager.study.status.needs_parent",
                    Component.translatable(parentName(study))).withStyle(ChatFormatting.DARK_GRAY);
            case BUSY -> Component.translatable("com.voyager.study.status.busy")
                    .withStyle(ChatFormatting.DARK_GRAY);
            case AVAILABLE -> costLine(study);
        };
    }

    private static String parentName(final SkyStudy study) {
        final SkyStudy parent = SkyStudies.byId(study.parent());
        return parent == null ? "com.voyager.study.status.needs_parent.unknown" : parent.nameKey();
    }

    private MutableComponent costLine(final SkyStudy study) {
        final MutableComponent line = Component.translatable("com.voyager.gui.study.cost", study.nights());
        for (final ItemStack cost : study.costs()) {
            line.append(Component.literal("  " + cost.getCount() + "x ")).append(cost.getHoverName());
        }
        return line;
    }

    private void begin(final SkyStudy study) {
        if (!moduleView.statusOf(study).startable()) {
            return;
        }
        PacketDistributor.sendToServer(
                new StartStudyMessage(moduleView.getBuildingView().getPosition(), study.id()));
        // The server answers with a fresh building packet; close so the next open shows the truth.
        close();
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("com.voyager.gui.study.begun",
                    Component.translatable(study.nameKey())), true);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        updateHeader();
    }

    /** Unused, but blockui expects buttons it does not know about to be handled. */
    @Override
    public void onButtonClicked(final Button button) {
        super.onButtonClicked(button);
    }
}

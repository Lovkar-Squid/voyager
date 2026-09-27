package me.lovkar.voyager.colony;

import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.settings.CrafterRecipeSetting;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import me.lovkar.voyager.photo.StudioSettings;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * The Photo Booth's settings tab: the crafter's recipe mode, as every MineColonies crafter has it,
 * and the studio's own two - which film goes in the camera and which filter over the lens
 * ({@link StudioSettings}).
 *
 * <p>A plain {@link SettingsModule} would do the job; this one only makes loading kinder:</p>
 * <ul>
 *   <li>A booth saved before 0.3.7 used MineColonies' own crafter settings module
 *       ({@code craft_settings}); its recipe mode is carried over once instead of resetting.</li>
 *   <li>MineColonies stores a choice of a string setting as a position in its list. The filter list
 *       is longer when Exposure: Expanded is installed, and a later version may add more, so the
 *       film and filter are restored <em>by name</em>. A choice that is no longer offered (Expanded
 *       removed, say) goes back to the default rather than landing on its neighbour.</li>
 * </ul>
 */
public class PhotoBoothSettingsModule extends SettingsModule {

    /** Where MineColonies kept a crafter's settings - the booth's own up to 0.3.6. */
    private static final String OLD_KEY = "craft_settings";

    public static PhotoBoothSettingsModule create() {
        final PhotoBoothSettingsModule module = new PhotoBoothSettingsModule();
        module.with(AbstractCraftingBuildingModule.RECIPE_MODE, new CrafterRecipeSetting())
                .with(StudioSettings.FILM, StudioSettings.filmSetting())
                .with(StudioSettings.FILTER, StudioSettings.filterSetting());
        return module;
    }

    @Override
    public void deserializeNBT(final HolderLookup.Provider provider, final CompoundTag compound) {
        CompoundTag own = compound;
        if (!own.contains("settingslist") && !own.contains("settings")) {
            // Nothing saved under this module yet, so MineColonies handed over the whole building:
            // a booth from before 0.3.7 has its recipe mode under the crafter settings module.
            final CompoundTag modules = compound.getCompound("building_modules");
            if (modules.contains(OLD_KEY)) {
                own = modules.getCompound(OLD_KEY);
            }
        }
        final String film = savedChoice(provider, own, StudioSettings.FILM);
        final String filter = savedChoice(provider, own, StudioSettings.FILTER);
        super.deserializeNBT(provider, own);
        restore(StudioSettings.FILM, film, StudioSettings.FILM_ANY);
        restore(StudioSettings.FILTER, filter, StudioSettings.FILTER_CAMERA);
    }

    /** The saved choice of a string setting, as its value (a lang key), or null if there is none. */
    @Nullable
    private static String savedChoice(final HolderLookup.Provider provider, final CompoundTag own,
            final ISettingKey<StringSetting> key) {
        try {
            final CompoundTag settings = own.contains("settings") ? own.getCompound("settings") : own;
            final ListTag list = settings.getList("settingslist", Tag.TAG_COMPOUND);
            final String id = key.getUniqueId().toString();
            for (int i = 0; i < list.size(); i++) {
                final CompoundTag entry = list.getCompound(i);
                if (id.equals(entry.getString("key"))) {
                    final Object saved = StandardFactoryController.getInstance()
                            .deserializeTag(provider, entry.getCompound("value"));
                    return saved instanceof StringSetting setting ? setting.getValue() : null;
                }
            }
        } catch (final RuntimeException unreadable) {
            // Fall back to what MineColonies restores on its own.
        }
        return null;
    }

    private void restore(final ISettingKey<StringSetting> key, @Nullable final String choice, final String fallback) {
        if (choice == null) {
            return;
        }
        try {
            final StringSetting setting = getSetting(key);
            if (setting != null) {
                setting.set(setting.getSettings().contains(choice) ? choice : fallback);
            }
        } catch (final RuntimeException notThere) {
            // Keep what MineColonies restored.
        }
    }
}

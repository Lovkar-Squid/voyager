package me.lovkar.voyager.colony;

import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import me.lovkar.voyager.ai.ExpeditionRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Expedition plans with the server config's supply multiplier applied.
 *
 * <p>A plan is a MineColonies crafter recipe (supplies in, a loot-table roll out); the datapack
 * recipes {@code trip1..5.json} decide what is on the list. The multiplier only scales the amounts,
 * and it does so on a copy: the colony's own recipe storage is shared by MineColonies' recipe
 * manager and must never change under it. Everything that reads amounts - the requests, the
 * "can this plan be fulfilled" check, the launch that uses the supplies up, the hut keeping them -
 * goes through here, so they all agree on one number.</p>
 */
public final class TripPlans {

    private TripPlans() {
    }

    /**
     * The plan with every input amount scaled (rounded up, at least 1). At a multiplier of 1.0 it
     * is the very same storage object, so a server on the default plays exactly as 0.3.9 did.
     * The copy keeps everything else of the plan - its loot table, its outputs, its type.
     */
    public static @Nullable IRecipeStorage scaled(final @Nullable IRecipeStorage plan, final double multiplier) {
        if (plan == null || !ExpeditionRules.scales(multiplier)) {
            return plan;
        }
        final List<ItemStorage> inputs = new ArrayList<>();
        for (final ItemStorage input : plan.getInput()) {
            final ItemStorage scaled = input.copy();
            scaled.setAmount(ExpeditionRules.scaledAmount(input.getAmount(), multiplier));
            inputs.add(scaled);
        }
        return RecipeStorage.builder(plan).withInputs(inputs).build();
    }

    /** How many of {@code item} the plan asks for in all (0 when it is not on the list). */
    public static int amountOf(final @Nullable IRecipeStorage plan, final ItemStorage item) {
        if (plan == null) {
            return 0;
        }
        int amount = 0;
        for (final ItemStorage input : plan.getCleanedInput()) {
            if (input.equals(item)) {
                amount += input.getAmount();
            }
        }
        return amount;
    }
}

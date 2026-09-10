package me.lovkar.voyager.compat;

import com.minecolonies.api.colony.IColony;
import me.lovkar.voyager.Voyager;
import net.neoforged.fml.ModList;

/**
 * The colony's purse, as Trade Post for MineColonies keeps it.
 *
 * <p>Trade Post stores the colony's balance as one statistic on the colony's own statistics
 * manager - {@code current_balance}, in value units where a Trade Coin is worth
 * {@code tradeCoinValue} (1000 by default). Its Marketplace, Resort and thrift shop all read and
 * write that same number, so paying the colony is one increment on a MineColonies API that exists
 * whether or not Trade Post does. Not one Trade Post class is linked: the only thing borrowed from
 * it is the coin value, read by reflection from its config, with 1000 as the answer if that fails.</p>
 *
 * <p>Without Trade Post there is no economy to pay into, and {@link #credit} says so by returning
 * false - the Photo Booth then hands over the print instead of the money.</p>
 */
public final class TradePostLedger {

    public static final String MOD_ID = "mctradepost";
    /** Trade Post's own statistic key for the colony balance. */
    public static final String BALANCE = "current_balance";
    /** Trade Post's currency sign, used wherever a price is shown. */
    public static final String SIGN = "‡";
    private static final int DEFAULT_COIN_VALUE = 1000;

    private static Boolean loaded;
    private static int coinValue = -1;

    private TradePostLedger() {
    }

    /** Whether there is a purse to pay into at all. */
    public static boolean available() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    /** What one Trade Coin is worth in balance units - Trade Post's {@code tradeCoinValue}. */
    public static int coinValue() {
        if (coinValue > 0) {
            return coinValue;
        }
        int value = DEFAULT_COIN_VALUE;
        if (available()) {
            try {
                final Class<?> config = Class.forName("com.deathfrog.mctradepost.MCTPConfig");
                final Object holder = config.getField("tradeCoinValue").get(null);
                final Object got = holder.getClass().getMethod("get").invoke(holder);
                if (got instanceof Integer i && i > 0) {
                    value = i;
                }
            } catch (final Throwable t) {
                Voyager.LOGGER.info("[ledger] could not read Trade Post's coin value ({}), assuming {}",
                        t.toString(), DEFAULT_COIN_VALUE);
            }
        }
        coinValue = value;
        return value;
    }

    /** The colony's balance, in value units. Zero without Trade Post. */
    public static int balance(final IColony colony) {
        if (!available() || colony == null) {
            return 0;
        }
        return colony.getStatisticsManager().getStatTotal(BALANCE);
    }

    /**
     * Pay the colony.
     *
     * @return true if there was an economy to pay into and the amount was positive
     */
    public static boolean credit(final IColony colony, final int amount) {
        if (!available() || colony == null || amount <= 0) {
            return false;
        }
        final long total = (long) balance(colony) + amount;
        final int safe = total > Integer.MAX_VALUE ? (int) (Integer.MAX_VALUE - (long) balance(colony)) : amount;
        if (safe <= 0) {
            return false;
        }
        colony.getStatisticsManager().incrementBy(BALANCE, safe, colony.getDay());
        return true;
    }

    /** "1,250‡" - the way Trade Post's own windows would print it. */
    public static String format(final long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value) + SIGN;
    }
}

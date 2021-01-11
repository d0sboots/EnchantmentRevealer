/* Copyright 2016 David Walker

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package io.github.d0sboots.enchantmentrevealer;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.IRegistry;

/**
 * Represents a single set of observed information gleaned from the vanilla enchanting process.
 */
public class Observation {
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

    /**
     * Truncated enchanting RNG seed. Minecraft masks the lower four bits, as well. If power ==
     * RESET_POWER, then this is the index of the slot that was clicked on, instead.
     */
    public short truncatedSeed;
    /** The min level value for each slot. 0 means no enchantment. */
    public final int[] levels = { 0, 0, 0 };
    /** The enchantment in each slot, encoded as an int. -1 means no enchantment. */
    public final int[] enchants = { -1, -1, -1 };
    /**
     * The level of each enchantment, not to be confused with the level for the slot. -1 means no
     * enchantment. 0 is technically valid, but not really, since all enchants start at 1. (Protection
     * I, Sharpness I, etc.)
     */
    public final int[] enchantLevels = { -1, -1, -1 };
    /**
     * The power level of the enchanting table at the time this observation was reported. If this is
     * RESET_POWER, then this is observation of a true enchanting result (the slot was clicked), and
     * certain fields have different meanings.
     */
    public int power = -2;
    /** When the observation happened, in milliseconds. */
    public long now = -1; // Not used in equals()/hashCode()
    /**
     * What tick the observation happened in. Tick counting (re)starts from 0 when the GUI is opened.
     */
    public long tick = -1; // Not used in equals()/hashCode()
    /** The item being enchanted. */
    @Nullable
    public ItemStack item;

    /**
     * Special flag value for {@link power} that indicates this is an observation of a true enchanting
     * result which will reset EnchantmentWorker to its default state.
     */
    public static final int RESET_POWER = -1;

    /** They took this function away, but we still need it. This is a convenient central place. */
    @SuppressWarnings("deprecation")
    static int getEnchantmentID(Enchantment enchantment) { return IRegistry.field_212628_q.getId(enchantment); }

    public boolean hasEnchants() { return item != null && levels[0] != 0; }

    public boolean isUnenchantable() { return levels[0] == 0 && levels[1] == 0 && levels[2] == 0; }

    public void merge(Observation other) {
        truncatedSeed = other.truncatedSeed;
        System.arraycopy(other.levels, 0, levels, 0, 3);
        System.arraycopy(other.enchants, 0, enchants, 0, 3);
        System.arraycopy(other.enchantLevels, 0, enchantLevels, 0, 3);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(enchants);
        result = prime * result + (item == null ? 0 : Item.getIdFromItem(item.getItem()));
        result = prime * result + Arrays.hashCode(levels);
        result = prime * result + Arrays.hashCode(enchantLevels);
        result = prime * result + power;
        result = prime * result + truncatedSeed;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Observation))
            return false;
        Observation other = (Observation) obj;
        if (!Arrays.equals(enchants, other.enchants))
            return false;
        if (!Arrays.equals(enchantLevels, other.enchantLevels))
            return false;
        if (item == null)
            return other.item == null;
        if (other.item == null)
            return false;
        if (Item.getIdFromItem(item.getItem()) != Item.getIdFromItem(other.item.getItem()))
            return false;
        if (!Arrays.equals(levels, other.levels))
            return false;
        if (power != other.power)
            return false;
        if (truncatedSeed != other.truncatedSeed)
            return false;
        return true;
    }

    private String formatTail(int[] enchantList, int[] enchantLevels) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < enchantList.length; ++i) {
            int entry = enchantList[i];
            int level = enchantLevels[i];
            if (entry == -1) {
                builder.append("-1, ");
            } else {
                builder.append('"');
                Enchantment enchant = Enchantment.getEnchantmentByID(entry);
                builder.append(enchant == null ? "unknown" : enchant.func_200305_d(level).getString());
                builder.append("\" (0x");
                builder.append(Integer.toHexString(entry));
                builder.append(" ");
                builder.append(level);
                builder.append("), ");
            }
        }
        builder.replace(builder.length() - 2, builder.length(), "]");
        builder.append(", levels: ");
        builder.append(Arrays.toString(levels));
        builder.append(", item: ");
        builder.append(item);
        builder.append(", tick: ");
        builder.append(tick);
        builder.append(", now: ");
        builder.append(dateFormat.format(new Date(now)));
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String toString() {
        if (power == RESET_POWER) {
            Map<Enchantment, Integer> enchantMap = EnchantmentHelper.getEnchantments(item);
            int[] enchantList = new int[enchantMap.size()];
            int[] enchantLevel = new int[enchantMap.size()];
            int i = 0;
            for (Entry<Enchantment, Integer> entry : enchantMap.entrySet()) {
                enchantList[i] = getEnchantmentID(entry.getKey());
                enchantLevel[i] = entry.getValue();
                i++;
            }
            return "EnchantObservation(chosenSlot: " + truncatedSeed
                    + ", observedEnchants: " + formatTail(enchantList, enchantLevel);
        }

        return String.format(
                "Observation(seed: 0x%04X, power: %d, enchants: %s",
                truncatedSeed, power, formatTail(enchants, enchantLevels));
    }
}

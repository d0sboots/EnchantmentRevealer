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

import javax.annotation.Nullable;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Represents a single set of observed information gleaned from the vanilla enchanting process.
 */
public class Observation {
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

    /**
     * Truncated enchanting RNG seed. Minecraft masks the lower four bits, as well.
     * If power == RESET_POWER, then this is the index of the slot that was clicked on, instead.
     */
    public short truncatedSeed;
    /** The min level value for each slot. 0 means no enchantment. */
    public final int[] levels = {0, 0, 0};
    /** The enchantment in each slot, encoded as an int. -1 means no enchantment. */
    public final int[] enchants = {-1, -1, -1};
    /**
     * The power level of the enchanting table at the time this observation was reported.
     * If this is RESET_POWER, then this is observation of a true enchanting result (the slot
     * was clicked), and certain fields have different meanings.
     */
    public int power = -2;
    /** When the observation happened, in milliseconds. */
    public long now = -1;  // Not used in equals()/hashCode()
    /** The item being enchanted. */
    @Nullable
    public ItemStack item;

    /**
     * Special flag value for {@link power} that indicates this is an observation of a true
     * enchanting result which will reset EnchantmentWorker to its default state.
     */
    public static final int RESET_POWER = -1;

    public static String getEnchantName(int data) {
        return Enchantment.getEnchantmentById(data & 0xFF).getTranslatedName(data >>> 8);
    }

    public boolean hasEnchants() {
        return item != null && levels[0] != 0;
    }

    public void merge(Observation other) {
        truncatedSeed = other.truncatedSeed;
        System.arraycopy(other.levels, 0, levels, 0, 3);
        System.arraycopy(other.enchants, 0, enchants, 0, 3);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(enchants);
        result = prime * result + (item == null ? 0 : Item.getIdFromItem(item.getItem()));
        result = prime * result + Arrays.hashCode(levels);
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

    private String formatTail(int[] enchantList) {
        StringBuilder builder = new StringBuilder("[");
        for (int entry : enchantList) {
            if (entry == -1) {
                builder.append("-1, ");
            } else {
                builder.append('"');
                builder.append(getEnchantName(entry));
                builder.append("\" (0x");
                builder.append(Integer.toHexString(entry));
                builder.append("), ");
            }
        }
        builder.replace(builder.length() - 2, builder.length(), "]");
        builder.append(", levels: ");
        builder.append(Arrays.toString(levels));
        builder.append(", item: ");
        builder.append(item);
        builder.append(", now: ");
        builder.append(dateFormat.format(new Date(now)));
        return builder.toString();
    }

    @Override
    public String toString() {
        if (power == RESET_POWER) {
            Map<Integer, Integer> enchantMap = EnchantmentHelper.getEnchantments(item);
            int[] enchantList = new int[enchantMap.size()];
            int i = 0;
            for (Map.Entry<Integer, Integer> entry : enchantMap.entrySet()) {
                enchantList[i++] = (entry.getValue() << 8) | entry.getKey();
            }
            return "EnchantObservation(chosenSlot: " + truncatedSeed
                    + ", observedEnchants: " + formatTail(enchantList);
        }

        return String.format(
                "Observation(seed: 0x%04X, power: %d, enchants: %s",
                truncatedSeed, power, formatTail(enchants));
    }
}

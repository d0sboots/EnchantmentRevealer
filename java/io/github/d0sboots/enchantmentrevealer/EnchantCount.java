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

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;

public class EnchantCount implements Comparable<EnchantCount> {
    public int count;
    public final EnchantmentData enchant;

    public EnchantCount(int count, EnchantmentData enchant) {
        this.count = count;
        this.enchant = enchant;
    }

    public static int hashCode(EnchantmentData data) {
        return data.enchantmentobj.hashCode() * 5 + data.enchantmentLevel;
    }

    public static boolean equals(EnchantmentData first, EnchantmentData second) {
        return first.enchantmentLevel == second.enchantmentLevel &&
                first.enchantmentobj == second.enchantmentobj;
    }

    public static String toString(EnchantmentData data) {
        return "\"" + data.enchantmentobj.getTranslatedName(data.enchantmentLevel) +
                "\" (0x" + Integer.toHexString(Enchantment.getEnchantmentID(data.enchantmentobj)) +
                " " + data.enchantmentLevel + ")";
    }

    @Override
    public int hashCode() {
        return count * 119 + hashCode(enchant);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof EnchantCount))
            return false;
        EnchantCount other = (EnchantCount) obj;
        return count == other.count && equals(enchant, other.enchant);
    }

    @Override
    public int compareTo(EnchantCount other) {
        if (count != other.count)
            return count - other.count;
        if (enchant.enchantmentLevel != other.enchant.enchantmentLevel) {
            return enchant.enchantmentLevel - other.enchant.enchantmentLevel;
        }
        return Enchantment.getEnchantmentID(enchant.enchantmentobj)
                - Enchantment.getEnchantmentID(other.enchant.enchantmentobj);
    }

    @Override
    public String toString() {
        return "EnchantCount(" + count + ", " + toString(enchant) + ")";
    }
}

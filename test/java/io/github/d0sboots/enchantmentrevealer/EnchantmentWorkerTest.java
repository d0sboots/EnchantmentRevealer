/* Copyright 2019 David Walker

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

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class EnchantmentWorkerTest {

    @Before
    public void setupTestCase() {
        Bootstrap.register();
        Locale locale = new Locale();
        try {
            Method method = I18n.class.getDeclaredMethod("setLocale", Locale.class);
            method.setAccessible(true);
            method.invoke(null, locale);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static Observation getTestObservation() {
        Observation observation = new Observation();
        observation.truncatedSeed = 0x2340;
        observation.power = 6;
        observation.levels[0] = 4;
        observation.levels[1] = 11;
        observation.levels[2] = 14;
        observation.enchants[0] = 0x22; // Unbreaking
        observation.enchantLevels[0] = 1;
        observation.enchants[1] = 0; // Protection
        observation.enchantLevels[1] = 2;
        observation.enchants[2] = 1; // Fire Protection
        observation.enchantLevels[2] = 2;
        observation.item = new ItemStack(Item.getByNameOrId("diamond_leggings"));
        return observation;
    }
    
    @Test
    public void testTestLevels() {
        Random rand = new Random(0);
        Observation observation = getTestObservation();
        int count = 0;
        for (int i = 0; i < 10000000; ++i) {
            boolean expected = EnchantmentWorker.testLevels(rand, i, observation);
            boolean actual = EnchantmentWorker.testLevelsFast(rand, i, observation);
            assertEquals(expected, actual);
            if (expected) {
                count++;
            }
        }
        assertTrue(count >= 10000);
    }

    @Test
    public void testFullRun() throws InterruptedException {
        Observation observation = getTestObservation();
        EnchantmentWorker worker = new EnchantmentWorker(/*useSeed=*/ "always");
        worker.addObservation(observation);
        // Wait for worker to finish
        EnchantmentWorker.State state = worker.state;
        while (state.enchants == EnchantmentWorker.NO_STRINGS) {
            Thread.sleep(50);
            state = worker.state;
        }
        assertEquals("enchantmentrevealer.status.possibles", state.statusMessage);
        assertEquals(12, state.counts[0][0]);
    }
}

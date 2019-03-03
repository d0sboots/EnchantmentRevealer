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
import java.util.List;
import java.util.Random;

import org.junit.Test;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public class EnchantmentWorkerTest {

    static {
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

    // A generic test observation
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
        observation.item = new ItemStack(Items.DIAMOND_LEGGINGS);
        return observation;
    }

    // An observation at low power that has two empty slots, which has exposed bugs
    // in the past.
    private static Observation getWeakObservation() {
        Observation observation = new Observation();
        observation.truncatedSeed = 0x08e0;
        observation.power = 0;
        observation.levels[0] = 1;
        observation.levels[1] = 3;
        observation.levels[2] = 4;
        observation.enchants[0] = -1;
        observation.enchantLevels[0] = 0;
        observation.enchants[1] = -1;
        observation.enchantLevels[1] = 0;
        observation.enchants[2] = 0x22; // Unbreaking
        observation.enchantLevels[2] = 1;
        observation.item = new ItemStack(Items.FISHING_ROD);
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

    private static EnchantmentWorker runWorkerLoop(Observation observation, String useSeed, int expectedCandidates) throws InterruptedException {
        Enchantment[] targets = new Enchantment[3];
        for (int i = 0; i < 3; ++i) {
            targets[i] = Enchantment.getEnchantmentByID(observation.enchants[i]);
        }
        EnchantmentWorker worker = new EnchantmentWorker(useSeed);
        worker.addObservation(observation);
        // Wait for worker to finish
        EnchantmentWorker.State state = worker.state;
        while (state.enchants == EnchantmentWorker.NO_STRINGS) {
            Thread.sleep(50);
            state = worker.state;
        }
        assertEquals("enchantmentrevealer.status.possibles", state.statusMessage);
        assertEquals(worker.candidatesLength, state.counts[2][0]);
        assertEquals("Found the wrong number of candidates!", expectedCandidates, state.counts[2][0]);
        return worker;
    }

    private static void runFastWorkerTest(Observation observation, int seed, int expectedCandidates)
            throws InterruptedException {
        EnchantmentWorker worker = runWorkerLoop(observation, "always", expectedCandidates);
        int i = 0;
        while (i < worker.candidatesLength && worker.candidates[i] != seed) {
            ++i;
        }
        assertNotEquals("The correct seed was not among the candidates!", worker.candidatesLength, i);
        Random rand = new Random(0);
        ItemStack item = observation.item;
        List<List<EnchantmentData>> cachedEnchantmentList = EnchantmentWorker.buildEnchantListCache(item);
        Enchantment[] targets = new Enchantment[3];
        for (i = 0; i < 3; ++i) {
            targets[i] = Enchantment.getEnchantmentByID(observation.enchants[i]);
        }
        @SuppressWarnings("unchecked") List<EnchantmentData>[] tempEnchantmentData = new List[3];
        int enchantability = item.getItem().getItemEnchantability(item);
        for (i = 0; i < worker.candidatesLength; ++i) {
            for (int j = 0; j < 3; ++j) {
                assertTrue("Failure for " + j + " at i=" + i,
                        EnchantmentWorker.testEnchantFast(rand, worker.candidates[i], observation, false,
                                cachedEnchantmentList, tempEnchantmentData, targets[j], enchantability, j));
            }
        }
    }

    @Test
    public void testFastFullRun() throws InterruptedException {
        runFastWorkerTest(getTestObservation(), 0x12347, 12);
    }

    @Test
    public void testFastWeakRun() throws InterruptedException {
        runFastWorkerTest(getWeakObservation(), 0x249e08e4, 18202);
    }

    // These tests take >1 minute to run.
    private static void runSlowWorkerTest(Observation observation, int seed, int expectedCandidates)
            throws InterruptedException {
        EnchantmentWorker worker = runWorkerLoop(observation, "never", expectedCandidates);
        int i = 0;
        while (i < worker.candidatesLength && worker.candidates[i] != seed) {
            ++i;
        }
        assertNotEquals("The correct seed was not among the candidates!", worker.candidatesLength, i);
        Random rand = new Random(0);
        @SuppressWarnings("unchecked") List<EnchantmentData>[] tempEnchantmentData = new List[3];
        for (i = 0; i < worker.candidatesLength; ++i) {
            assertTrue("Failure at i=" + i,
                    EnchantmentWorker.testEnchants(
                            rand, worker.candidates[i], observation, tempEnchantmentData));
        }
    }

    @Test
    public void testSlowFullRun() throws InterruptedException {
        runSlowWorkerTest(getTestObservation(), 0x12347, 38722);
    }

    @Test
    public void testSlowWeakRun() throws InterruptedException {
        runSlowWorkerTest(getWeakObservation(), 0x249e08e4, 75093865);
    }
}

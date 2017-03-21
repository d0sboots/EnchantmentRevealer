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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

/**
 * This does all the heavy lifting for figuring out enchantments.
 *
 * It can either run on its own thread, or the caller's thread (if the amount of work is small
 * enough).
 */
public class EnchantmentWorker implements Runnable {
    /**
     * Data class that holds a UI state to be communicated to the GUI.
     */
    public static class State {
        /** Message to be shown in the place where "Enchanting" normally shows up. */
        public final String statusMessage;
        /**
         * First dimension is slot (3 entries), second dimension is the number of unique
         * enchants for that slot. The string values are the pre-translated names, with power
         * level. These are sorted so that the "observed" enchantment (the one that would be
         * shown in the vanilla UI) is always first, and then in order of increasing rarity.
         */
        public final String[][] enchants;
        /**
         * Sliced the same direction as "enchants", this has the number of times that
         * enchantment is a valid possibility. Because of the sorting order, the first element
         * (per slot) also serves as the denominator. If the field has been narrowed to a single
         * seed, then all counts will be 1.
         */
        public final int[][] counts;
        /**
         * The observation this state applies to. If this doesn't match the current observation,
         * then the state won't be displayed.
         */
        @Nullable
        public final Observation observation;

        public State(String statusMessage, String[][] enchants, int[][] counts,
                @Nullable Observation observation) {
            this.statusMessage = statusMessage;
            this.enchants = enchants;
            this.counts = counts;
            this.observation = observation;
        }

        public boolean isError() {
            return statusMessage.startsWith(TextFormatting.RED.toString());
        }
    }

    public static final String DEFAULT_STATUS = I18n.format("enchantmentrevealer.version",
            EnchantmentRevealer.MODID, EnchantmentRevealer.VERSION);
    // Default size of the psuedo-ArrayList "candidates".
    private static final int INITIAL_SIZE = 128;
    // The number of seeds to work in a batch, before reporting progress to the UI
    private static final int BATCH_SIZE = 512;
    // No strings, my friend, no strings!
    private static final String[][] NO_STRINGS = { new String[0], new String[0], new String[0] };
    private static final int[][] NO_INTS = { new int[0], new int[0], new int[0] };
    private static final State DEFAULT_STATE = new State(DEFAULT_STATUS, NO_STRINGS, NO_INTS, null);

    public volatile State state = DEFAULT_STATE;

    @GuardedBy("this")
    private final Deque<Observation> queue = new ArrayDeque<Observation>();
    @GuardedBy("this")
    private Thread thread = null;
    @GuardedBy("this")
    private Observation pendingEnchant;

    // The following are only accessed from the worker thread, or when the worker thread is
    // guaranteed to be stopped.

    // A psuedo-ArrayList (we grow and shrink it ourselves) that tracks the possible seed
    // candidates. This is not an actual ArrayList because unboxing. (Even though it probably
    // doesn't matter.)
    private int[] candidates = new int[INITIAL_SIZE];
    private int candidatesLength = 0;

    @SuppressWarnings("unchecked")
    final ArrayList<EnchantCount>[] enchantCounts = new ArrayList[3];
    {
        for (int i = 0; i < 3; ++i)
            enchantCounts[i] = new ArrayList<EnchantCount>();
    }
    private final Random rand = new Random(0);
    @SuppressWarnings("unchecked")
    private final List<EnchantmentData>[] tempEnchantmentData = new ArrayList[3];
    private final ArrayList<Observation> observations = new ArrayList<Observation>();

    @Override
    public void run() {
        EnchantmentRevealer.out.println("Worker " + this + " starting");
        try {
            mainLoop();
        } catch (RuntimeException e) {
            // This isn't thread-safe, but it's better than the thread never starting again.
            thread = null;
            throw e;
        } finally {
            EnchantmentRevealer.out.println("Worker " + this + " exiting");
        }
    }

    private void mainLoop() {
        while (true) {
            synchronized (this) {
                if (checkDone()) {
                    return;
                }
            }

            Observation observation = observations.get(observations.size() - 1);
            EnchantmentRevealer.out.println("Working observation " + observation);
            if (observation.hasEnchants()) {
                for (int i = 0; i < 3; ++i) {
                    enchantCounts[i].clear();
                }
                Observation prevObservation = null;
                for (int i = observations.size() - 2; i >= 0; --i) {
                    Observation o = observations.get(i);
                    if (o.hasEnchants()) {
                        prevObservation = o;
                        break;
                    }
                }
                if (prevObservation != null
                        && prevObservation.truncatedSeed != observation.truncatedSeed) {
                    if (prevObservation.power != observation.power) {
                        refineWithNewPower(observation);
                    } else {
                        dumpError("seedmismatch");
                        // Put the observation back so it is processed next time
                        observations.add(observation);
                        return;
                    }
                } else {
                    if (candidatesLength == 0) {
                        doInitial(observation);
                    } else {
                        refine(observation);
                    }
                }
                if (candidatesLength == 0) {
                    dumpError("exhausted");
                    // Put the observation back so it is processed next time
                    observations.add(observation);
                    return;
                }
            } else {
                // Keep the message around, but update the observation
                state = new State(state.statusMessage, NO_STRINGS, NO_INTS, observation);
                continue;
            }
            state = generateRestingState(observation);
        }
    }

    public synchronized void addObservation(Observation observation) {
        queue.add(observation);

        if (thread != null) {
            // Worker will handle it.
            return;
        }
        if (observation.hasEnchants() && (candidatesLength == 0 || candidatesLength > 100)) {
            thread = new Thread(this, "EnchantmentWorker");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        } else {
            mainLoop();
        }
    }

    private void doInitial(Observation observation) {
        int initial = observation.truncatedSeed & 0xFFFF;
        int i = initial;
        do {
            state = new State(I18n.format("enchantmentrevealer.calculating.percent",
                    (((i & 0xFFFF0000L) * 100L + (1L << 31)) >>> 32)), NO_STRINGS, NO_INTS,
                    observation);
            int localLimit = i + (BATCH_SIZE << 12);

            for (; i != localLimit; i += (1 << 16)) {
                for (int j = 0; j < 16; ++j) {
                    int merged = i | j;
                    if (testLevels(merged, observation)) {
                        testEnchantsAndAdd(merged, observation);
                    }
                }
            }
        } while (i != initial);
    }

    private void refine(Observation observation) {
        int limit = candidatesLength;
        candidatesLength = 0;
        for (int i = 0; i < limit; ++i) {
            testEnchantsAndAdd(candidates[i], observation);
        }
    }

    private void refineWithNewPower(Observation observation) {
        int limit = candidatesLength;
        candidatesLength = 0;
        for (int i = 0; i < limit; ++i) {
            int test = candidates[i];
            if (testLevels(test, observation)) {
                testEnchantsAndAdd(test, observation);
            }
        }
    }

    private boolean testLevels(int seed, Observation observation) {
        rand.setSeed(seed);

        for (int i = 0; i < 3; ++i) {
            int level = EnchantmentHelper.calcItemStackEnchantability(rand, i, observation.power,
                    observation.item);
            if (level < i + 1) {
                level = 0;
            }
            if (level != observation.levels[i]) {
                return false;
            }
        }
        return true;
    }

    private void testEnchantsAndAdd(int seed, Observation observation) {
        for (int i = 0; i < 3; ++i) {
            int level = observation.levels[i];

            if (level == 0) {
                tempEnchantmentData[i] = null;
                continue;
            }
            List<EnchantmentData> list = buildEnchantmentList(seed, observation, i);
            tempEnchantmentData[i] = list;
            if (!list.isEmpty()) {
                EnchantmentData data = list.get(rand.nextInt(list.size()));
                if (Enchantment.getEnchantmentByID(observation.enchants[i]) != data.enchantmentobj ||
                        observation.enchantLevels[i] != data.enchantmentLevel) {
                    return;
                }
            } else {
                // Real enchant has something for this slot, but we found nothing.
                return;
            }
        }
        addCandidate(seed);
        tallyEnchants();
    }

    private List<EnchantmentData> buildEnchantmentList(int seed, Observation observation, int id) {
        // Do not be deceived: There is a cast to long inside setSeed() in the code this is copied
        // from, but it happens *after* the addition, meaning it does absolutely nothing.
        rand.setSeed(seed + id);
        ItemStack item = observation.item;
        if (item.getItem() == Items.enchanted_book) {
            item = new ItemStack(Items.book);
        }
        List<EnchantmentData> list = EnchantmentHelper.buildEnchantmentList(rand, item,
                observation.levels[id], false);
        if (item.getItem() == Items.book && list.size() > 1) {
            list.remove(rand.nextInt(list.size()));
        }
        return list;
    }

    @GuardedBy("this")
    private boolean checkDone() {
        while (true) {
            if (queue.isEmpty()) {
                thread = null;
                shrink();
                return true;
            }
            Observation observation = queue.poll();
            if (observation.power == Observation.RESET_POWER) {
                if (!isEnchantConsistent(observation)) {
                    // Add for error reporting
                    observations.add(observation);
                    dumpError("inconsistent");
                    return true;
                }
                observations.clear();
                candidatesLength = 0;
                state = DEFAULT_STATE;
                continue;
            }
            observations.add(observation);
            return false;
        }
    }

    private boolean isEnchantConsistent(Observation observation) {
        if (candidatesLength != 1)
            return true;
        int id = observation.truncatedSeed;
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(observation.item);
        List<EnchantmentData> list = buildEnchantmentList(candidates[0], observation, id);
        if (enchants.size() != list.size())
            return false;
        for (EnchantmentData data : list) {
            if (enchants.get(data.enchantmentobj) != data.enchantmentLevel)
                return false;
        }
        return true;
    }

    private State generateRestingState(Observation observation) {
        String[][] enchants = new String[3][];
        int[][] counts = new int[3][];
        for (int i = 0; i < 3; ++i) {
            final EnchantmentData target = new EnchantmentData(
                    Enchantment.getEnchantmentByID(observation.enchants[i]), observation.enchantLevels[i]);
            final ArrayList<EnchantCount> list = enchantCounts[i];
            Collections.sort(list);
            Collections.reverse(list);

            // Move the observed enchant to the top
            if (!list.isEmpty()) {
                int j;
                for (j = 0; j < list.size() && !EnchantCount.equals(list.get(j).enchant, target); ++j)
                    ;
                if (j == list.size()) {
                    throw new RuntimeException("Failed to find " + target + " for " + i
                            + " in list " + Arrays.toString(list.toArray()));
                }
                EnchantCount targetPair = list.get(j);
                for (; j > 0; --j) {
                    list.set(j, list.get(j - 1));
                }
                list.set(0, targetPair);
            }

            String[] enchantTarget = new String[list.size()];
            int[] countTarget = new int[list.size()];
            for (int j = 0; j < list.size(); ++j) {
                EnchantCount item = list.get(j);
                enchantTarget[j] = item.enchant.enchantmentobj.getTranslatedName(item.enchant.enchantmentLevel);
                countTarget[j] = item.count;
            }
            enchants[i] = enchantTarget;
            counts[i] = countTarget;
        }
        String message;
        switch (candidatesLength) {
        case 0:
            message = DEFAULT_STATUS;
            break;
        case 1:
            message = I18n.format("enchantmentrevealer.status.seed", candidates[0]);
            break;
        default:
            message = I18n.format("enchantmentrevealer.status.possibles", candidatesLength);
        }
        return new State(message, enchants, counts, observation);
    }

    private void addCandidate(int v) {
        if (candidatesLength >= candidates.length) {
            candidates = Arrays.copyOf(candidates, candidates.length << 1);
        }
        candidates[candidatesLength++] = v;
    }

    private void shrink() {
        int newSize = Math.max(candidatesLength, INITIAL_SIZE);
        if (newSize != candidates.length) {
            candidates = Arrays.copyOf(candidates, newSize);
        }
    }

    private synchronized void dumpError(String tag) {
        state = new State(TextFormatting.RED
                + I18n.format("enchantmentrevealer.error.mainmessage"), NO_STRINGS, NO_INTS,
                observations.get(observations.size() - 1));
        GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
        chat.printChatMessage(new TextComponentTranslation("enchantmentrevealer.error.part1",
                new TextComponentTranslation("enchantmentrevealer.error." + tag), "d0sboots",
                "gmai", "l.com").setChatStyle(new Style().setColor(TextFormatting.RED)
                .setBold(true)));
        chat.printChatMessage(new TextComponentTranslation("enchantmentrevealer.error.part2")
                .setChatStyle(new Style().setColor(TextFormatting.YELLOW)));
        for (Observation observation : observations) {
            chat.printChatMessage(new TextComponentString(observation.toString())
                    .setChatStyle(new Style().setColor(TextFormatting.YELLOW)));
        }
        observations.clear();
        candidatesLength = 0;
        thread = null;
        shrink();
    }

    private void tallyEnchants() {
        for (int i = 0; i < 3; ++i) {
            ArrayList<EnchantCount> list = enchantCounts[i];
            List<EnchantmentData> enchantData = tempEnchantmentData[i];
            if (enchantData == null) {
                continue;
            }
            outer: for (EnchantmentData data : enchantData) {
                final int size = list.size();
                for (int j = 0; j < size; ++j) {
                    EnchantCount pair = list.get(j);
                    if (EnchantCount.equals(pair.enchant, data)) {
                        pair.count++;
                        continue outer;
                    }
                }
                list.add(new EnchantCount(1, data));
            }
        }
    }

    public synchronized void reportEnchantBegin(Observation observation) {
        pendingEnchant = observation;
    }

    public synchronized void reportEnchantFinished(ItemStack stack) {
        if (pendingEnchant == null)
            return;
        pendingEnchant.item = stack;
        addObservation(pendingEnchant);
        pendingEnchant = null;
    }
}

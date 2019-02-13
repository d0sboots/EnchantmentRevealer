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
            EnchantmentRevealer.VERSION);
    // Default size of the psuedo-ArrayList "candidates".
    private static final int INITIAL_SIZE = 128;
    // The number of seeds to work in a batch, before reporting progress to the UI
    private static final int BATCH_SIZE = 1024;
    // No strings, my friend, no strings!
    public static final String[][] NO_STRINGS = { new String[0], new String[0], new String[0] };
    private static final int[][] NO_INTS = { new int[0], new int[0], new int[0] };
    private static final State DEFAULT_STATE = new State(DEFAULT_STATUS, NO_STRINGS, NO_INTS, null);

    public volatile State state = DEFAULT_STATE;

    @GuardedBy("this")
    private final Deque<Observation> queue = new ArrayDeque<Observation>();
    @GuardedBy("this")
    Thread thread = null; // Visible for testing
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
    private final String useSeedHint;
    // Are we re-doing the calculations assuming bad seed data?
    private boolean didFallback = false;

    public EnchantmentWorker(String useSeedHint) {
        this.useSeedHint = useSeedHint;
    }

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
            if (!observation.hasEnchants()) {
                // Keep the message around, but update the observation
                state = new State(state.statusMessage, NO_STRINGS, NO_INTS, observation);
                continue;
            }
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
            if (prevObservation != null && prevObservation.truncatedSeed != observation.truncatedSeed) {
                dumpError("seedmismatch");
                // Put the observation back so it is processed next time
                observations.add(observation);
                return;
            }
            if (candidatesLength == 0) {
                if (didFallback || useSeedHint.equalsIgnoreCase("never")) {
                    doInitialFull(observation);
                } else {
                    doInitial(observation);
                }
            } else {
                refine(observation);
            }

            if (candidatesLength == 0) {
                if (didFallback || useSeedHint.equalsIgnoreCase("always")) {
                    dumpError("exhausted");
                    // Put the observation back so it is processed next time
                    observations.add(observation);
                    return;
                }

                didFallback = true;
                // Put all the observations back on the queue, so we re-process them.
                synchronized (this) {
                    for (int i = observations.size() - 1; i >= 0; --i) {
                        queue.addFirst(observations.get(i));
                    }
                }
                observations.clear();
                continue; // Immediately start re-processing
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
        thread = new Thread(this, "EnchantmentWorker");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void setPartialProgress(Observation observation, long percent) {
        state = new State(I18n.format("enchantmentrevealer.calculating.percent", percent),
                NO_STRINGS, NO_INTS, observation);
    }

    private void doInitial(Observation observation) {
        int initial = observation.truncatedSeed & 0xFFF0;
        int i = initial;
        do {
            setPartialProgress(observation, Math.round((i & 0xFFFFFFFFL) * 0x64P-32));  // 100/2^32
            int localLimit = i + (BATCH_SIZE << 12);

            for (; i != localLimit; i += (1 << 16)) {
                for (int j = 0; j < 16; ++j) {
                    int merged = i | j;
                    if (testLevels(rand, merged, observation)
                            && testEnchants(rand, merged, observation, tempEnchantmentData)) {
                        addAndTallyEnchants(merged, tempEnchantmentData);
                    }
                }
            }
        } while (i != initial);
    }

    /** Scan the entire space */
    private void doInitialFull(final Observation observation) {
        final int THREAD_POOL_SIZE = 4;
        Thread[] threads = new Thread[THREAD_POOL_SIZE];
        final int batch[] = new int[1];  // Loop counter passed as one-element array

        for (int j = 0; j < THREAD_POOL_SIZE; ++j) {
            threads[j] = new Thread("EnchantmentWorker-doInitialFull-" + j) {
                @Override public void run() {
                    class Observed {
                        public int seed;
                        @SuppressWarnings("unchecked")
                        public List<EnchantmentData>[] tempData = new List[3];
                        {
                            for (int i = 0; i < 3; ++i) {
                                tempData[i] = new ArrayList<EnchantmentData>();
                            }
                        }
                    };

                    // We never shrink seen, so that it holds on to all the Observed instances.
                    // So we track seenLength separately, and length() becomes capacity.
                    // This is so that we can re-use all the objects involved without re-allocating
                    // or re-initializing them.
                    List<Observed> seen = new ArrayList<Observed>();
                    Random rng = new Random(0);
                    seen.add(new Observed());
                    int seenLength = 0;
                    do {
                        int i;
                        int localLimit;
                        synchronized (batch) {
                            // We've been saving work thread-locally, now deal with it.
                            for (int j = 0; j < seenLength; ++j) {
                                Observed o = seen.get(j);
                                addAndTallyEnchants(o.seed, o.tempData);
                            }
                            seenLength = 0;
                            i = batch[0];
                            if (i == 1) { // Sentinel value
                                return;
                            }
                            // Higher batch size, because of the larger space.
                            localLimit = i + (BATCH_SIZE << 4);
                            batch[0] = localLimit;
                            if (localLimit == 0) {
                                batch[0] = 1;
                            }
                            setPartialProgress(observation, Math.round((i & 0xFFFFFFFFL) * 0x64P-32));  // 100/2^32
                        }

                        // The inner loop: Everything else can be slow, but this must be fast.
                        for (; i != localLimit; i++) {
                            if (testLevelsFast(rng, i, observation)
                                    && testEnchants(rng, i, observation, seen.get(seenLength).tempData)) {
                                seen.get(seenLength).seed = i;
                                seenLength++;
                                if (seenLength >= seen.size()) {
                                    seen.add(new Observed());
                                }
                            }
                        }
                    } while (true);
                }
            };
            threads[j].setDaemon(true);
            threads[j].setPriority(Thread.MIN_PRIORITY);
            threads[j].start();
        }
        for (int j = 0; j < THREAD_POOL_SIZE; ++j) {
            try {
                threads[j].join();
            } catch (InterruptedException ex) {
                j--;
            }
        }
    }

    private void refine(Observation observation) {
        int limit = candidatesLength;
        candidatesLength = 0;
        int i = 0;
        final double dRatio = 100.0 / limit;
        while (i < limit) {
            setPartialProgress(observation, Math.round(i * dRatio));
            int localLimit = i + BATCH_SIZE;
            if (localLimit > limit) {
                localLimit = limit;
            }
            for (; i != localLimit; i++) {
                if (testEnchants(rand, candidates[i], observation, tempEnchantmentData)) {
                    addAndTallyEnchants(candidates[i], tempEnchantmentData);
                }
            }
        }
    }

    static boolean testLevels(Random rand, int seed, Observation observation) {
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

    // This should always return the same result as testLevels(). We keep both around, because
    // testLevels() is less likely to break, and isn't that much slower. It's suitable for use
    // in everything except doInitialFull().
    static boolean testLevelsFast(Random rand, int seed, Observation observation) {
        int[] levels = observation.levels;
        int power = observation.power;
        if (power > 15) {
            power = 15;
        }
        int p1 = 1 + (power >> 1);
        int p2 = power + 1;
        rand.setSeed(seed);
        int j = rand.nextInt(8) + p1 + rand.nextInt(p2);
        int level = j / 3;
        if (level < 1) {
            level = 1;
        }
        if (level != levels[0]) {
            return false;
        }
        j = rand.nextInt(8) + p1 + rand.nextInt(p2);
        level = j * 2 / 3 + 1;
        if (level < 2) {
            level = 0;
        }
        if (level != levels[1]) {
            return false;
        }
        j = rand.nextInt(8) + p1 + rand.nextInt(p2);
        level = power * 2;
        if (level < j) {
            level = j;
        }
        if (level < 3) {
            level = 0;
        }
        return level == levels[2];
    }

    private static boolean testEnchants(Random rand, int seed, Observation observation,
            List<EnchantmentData>[] tempEnchantmentData) {
        for (int i = 0; i < 3; ++i) {
            int level = observation.levels[i];

            if (level == 0) {
                tempEnchantmentData[i] = null;
                continue;
            }
            List<EnchantmentData> list = buildEnchantmentList(rand, seed, observation, i);
            tempEnchantmentData[i] = list;
            if (!list.isEmpty()) {
                EnchantmentData data = list.get(rand.nextInt(list.size()));
                if (Enchantment.getEnchantmentByID(observation.enchants[i]) != data.enchantmentobj ||
                        observation.enchantLevels[i] != data.enchantmentLevel) {
                    return false;
                }
            } else {
                // Real enchant has something for this slot, but we found nothing.
                return false;
            }
        }
        return true;
    }

    private static List<EnchantmentData> buildEnchantmentList(
            Random rand, int seed, Observation observation, int id) {
        // Do not be deceived: There is a cast to long inside setSeed() in the code this is copied
        // from, but it happens *after* the addition, meaning it does absolutely nothing.
        rand.setSeed(seed + id);
        ItemStack item = observation.item;
        if (item.getItem() == Items.ENCHANTED_BOOK) {
            item = new ItemStack(Items.BOOK);
        }
        List<EnchantmentData> list = EnchantmentHelper.buildEnchantmentList(rand, item,
                observation.levels[id], false);
        if (item.getItem() == Items.BOOK && list.size() > 1) {
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
                didFallback = false;
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
        List<EnchantmentData> list = buildEnchantmentList(rand, candidates[0], observation, id);
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
            final ArrayList<EnchantCount> list = enchantCounts[i];
            Collections.sort(list);
            Collections.reverse(list);

            // Move the observed enchant to the top
            if (!list.isEmpty()) {
                final EnchantmentData target = new EnchantmentData(
                        Enchantment.getEnchantmentByID(observation.enchants[i]), observation.enchantLevels[i]);
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
                "gmai", "l.com").setStyle(new Style().setColor(TextFormatting.RED)
                .setBold(true)));
        chat.printChatMessage(new TextComponentTranslation("enchantmentrevealer.error.part2")
                .setStyle(new Style().setColor(TextFormatting.YELLOW)));
        for (Observation observation : observations) {
            chat.printChatMessage(new TextComponentString(observation.toString())
                    .setStyle(new Style().setColor(TextFormatting.YELLOW)));
        }
        observations.clear();
        candidatesLength = 0;
        thread = null;
        didFallback = false;
        shrink();
    }

    private void addAndTallyEnchants(int v, List<EnchantmentData>[] tempEnchantData) {
        if (candidatesLength >= candidates.length) {
            candidates = Arrays.copyOf(candidates, candidates.length << 1);
        }
        candidates[candidatesLength++] = v;

        for (int i = 0; i < 3; ++i) {
            ArrayList<EnchantCount> list = enchantCounts[i];
            List<EnchantmentData> enchantData = tempEnchantData[i];
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

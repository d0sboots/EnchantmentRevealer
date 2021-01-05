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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Preconditions;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ContainerEnchantmentWrapper extends ContainerEnchantment {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final long SYNC_DELAY_MS = 100;

    private final EnchantmentWorker worker;
    private final World world;
    private final BlockPos position;
    private final ArrayDeque<Observation> observations = new ArrayDeque<Observation>();
    private ItemStack lastStack = new ItemStack((Item) null);
    private Observation newSeedObservation = new Observation();
    private Observation lastSeedObservation;
    public Observation lastObservation;

    public ContainerEnchantmentWrapper(
            InventoryPlayer playerInv, World worldIn, EnchantmentWorker worker, BlockPos pos) {
        super(playerInv, worldIn);
        this.worker = Preconditions.checkNotNull(worker);
        this.world = worldIn;
        this.position = pos;
    }

    @Override
    public boolean enchantItem(EntityPlayer playerIn, int id) {
        boolean val = super.enchantItem(playerIn, id);
        if (!worker.isDisabled() && val && lastObservation != null) {
            Observation observation = new Observation();
            System.arraycopy(lastObservation.levels, 0, observation.levels, 0, 3);
            observation.truncatedSeed = (short) id;
            observation.power = Observation.RESET_POWER;
            worker.reportEnchantBegin(observation);
        }
        return val;
    }

    @Override
    public void onCraftMatrixChanged(IInventory inventoryIn) {
        super.onCraftMatrixChanged(inventoryIn);
        if (worker.isDisabled()) {
            return;
        }
        if (inventoryIn != tableInventory) {
            return;
        }
        ItemStack stack = inventoryIn.getStackInSlot(0);
        if (ItemStack.areItemStacksEqual(stack, lastStack)) {
            return;
        }
        LOGGER.debug("onCraftMatrixChanged {}", stack);
        lastStack = stack == null ? null : stack.copy();
        if (lastStack != null && lastStack.hasEffect()) {
            worker.reportEnchantFinished(lastStack);
        }
        Observation newObservation = new Observation();
        newObservation.now = System.currentTimeMillis();
        newObservation.item = lastStack;
        setPower(newObservation);
        observations.add(newObservation);
    }

    @Override
    public void updateProgressBar(int id, int data) {
        super.updateProgressBar(id, data);
        // This is called once per tick, since EntityPlayerMP.onUpdate() calls
        // Container.detectAndSendChanges(). This means matching up the enchanting item with the
        // data from the server is inherently racy. We combat this by paying attention to changes
        // in the data, with a fallback based on time.
        if (!worker.isDisabled() && id == 9) {
            System.arraycopy(enchantClue, 0, newSeedObservation.enchants, 0, 3);
            System.arraycopy(worldClue, 0, newSeedObservation.enchantLevels, 0, 3);
            System.arraycopy(enchantLevels, 0, newSeedObservation.levels, 0, 3);
            newSeedObservation.truncatedSeed = (short) (xpSeed & -16);

            Observation itemObservation = observations.peek();
            if (!newSeedObservation.equals(lastSeedObservation)) {
                lastSeedObservation = newSeedObservation;
                newSeedObservation = new Observation();
                LOGGER.debug("New seed observation {}", lastSeedObservation);

                if (itemObservation == null) {
                    LOGGER.debug("Nothing to match new observation to!");
                    return;
                }
            } else if (itemObservation != null
                    && itemObservation.now + SYNC_DELAY_MS < System.currentTimeMillis()) {
                LOGGER.debug("Time's up waiting for {}", lastSeedObservation);
            } else {
                // We haven't reached the fallback time yet, so wait for a change.
                return;
            }
            observations.poll();
            itemObservation.merge(lastSeedObservation);
            worker.addObservation(itemObservation);
            lastObservation = itemObservation;
        }
    }

    private float getPower(BlockPos pos) { return world.getBlockState(pos).getEnchantPowerBonus(world, pos); }

    private void setPower(Observation observation) {
        float power = 0;
        for (int j = -1; j <= 1; ++j) {
            for (int k = -1; k <= 1; ++k) {
                if ((j != 0 || k != 0) && world.isAirBlock(position.add(k, 0, j))
                        && world.isAirBlock(position.add(k, 1, j))) {
                    power += getPower(position.add(k * 2, 0, j * 2));
                    power += getPower(position.add(k * 2, 1, j * 2));
                    if (k != 0 && j != 0) {
                        power += getPower(position.add(k * 2, 0, j));
                        power += getPower(position.add(k * 2, 1, j));
                        power += getPower(position.add(k, 0, j * 2));
                        power += getPower(position.add(k, 1, j * 2));
                    }
                }
            }
        }
        observation.power = (int) power;
    }
}

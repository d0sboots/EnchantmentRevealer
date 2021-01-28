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

import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class Events {
    private EnchantmentWorker worker;
    private BlockPos lastInteractPos;

    @SubscribeEvent
    public void onGui(GuiOpenEvent event) {
        if (event.getGui() == null || !GuiEnchantment.class.equals(event.getGui().getClass())) {
            // Only hook the enchantment GUI. We don't use instanceof, because we *only* want to
            // hook the unmodified GUI.
            return;
        }
        event.setGui(GuiEnchantmentWrapper.wrap((GuiEnchantment) event.getGui(), worker, lastInteractPos));
    }

    @SubscribeEvent
    public void onInteract(RightClickBlock event) {
        lastInteractPos = event.getPos();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld() instanceof WorldClient) {
            worker = new EnchantmentWorker();
        }
    }
}

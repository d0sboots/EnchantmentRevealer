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

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class SlotWrapper extends Slot {
    private final Slot base;

    private final ContainerEnchantmentWrapper container;

    public SlotWrapper(Slot base, ContainerEnchantmentWrapper container) {
        super(base.inventory, 0, base.xDisplayPosition, base.yDisplayPosition);
        this.base = base;
        this.container = container;
    }

    @Override
    public void onSlotChanged() {
        base.onSlotChanged();
        container.onSlotChanged(getStack());
    }

    @Override
    public void onPickupFromSlot(EntityPlayer playerIn, ItemStack stack) {
        base.onPickupFromSlot(playerIn, stack);
        container.onSlotChanged(getStack());
    }

    @Override
    public void putStack(ItemStack stack) {
        base.putStack(stack);
        container.onSlotChanged(getStack());
    }

    // The methods below are pure proxy

    @Override
    public void onSlotChange(ItemStack p_75220_1_, ItemStack p_75220_2_) {
        base.onSlotChange(p_75220_1_, p_75220_2_);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return base.isItemValid(stack);
    }

    @Override
    public ItemStack getStack() {
        return base.getStack();
    }

    @Override
    public ItemStack decrStackSize(int amount) {
        return base.decrStackSize(amount);
    }

    @Override
    public boolean canTakeStack(EntityPlayer playerIn) {
        return base.canTakeStack(playerIn);
    }

    @Override
    public boolean canBeHovered() {
        return base.canBeHovered();
    }

    @Override
    public boolean equals(Object arg0) {
        return base.equals(arg0);
    }

    @Override
    public boolean getHasStack() {
        return base.getHasStack();
    }

    @Override
    public int getSlotStackLimit() {
        return base.getSlotStackLimit();
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return base.getItemStackLimit(stack);
    }

    @Override
    public String getSlotTexture() {
        return base.getSlotTexture();
    }

    @Override
    public boolean isHere(IInventory inv, int slotIn) {
        return base.isHere(inv, slotIn);
    }

    @Override
    public ResourceLocation getBackgroundLocation() {
        return base.getBackgroundLocation();
    }

    @Override
    public void setBackgroundLocation(ResourceLocation texture) {
        base.setBackgroundLocation(texture);
    }

    @Override
    public void setBackgroundName(String name) {
        base.setBackgroundName(name);
    }

    @Override
    public TextureAtlasSprite getBackgroundSprite() {
        return base.getBackgroundSprite();
    }

    @Override
    public int getSlotIndex() {
        return base.getSlotIndex();
    }

    @Override
    public int hashCode() {
        return base.hashCode();
    }

    @Override
    public String toString() {
        return base.toString();
    }
}

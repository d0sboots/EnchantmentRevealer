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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.fonts.FontResourceManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.util.INameable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

public class GuiEnchantmentWrapper extends GuiEnchantment {
    private static final FieldHelper<InventoryPlayer, GuiEnchantment> inventoryField =
            FieldHelper.from(InventoryPlayer.class, GuiEnchantment.class);
    private static final FieldHelper<World, ContainerEnchantment> worldField =
            FieldHelper.from(World.class, ContainerEnchantment.class);
    private static final FieldHelper<ContainerEnchantment, GuiEnchantment> containerField =
            FieldHelper.from(ContainerEnchantment.class, GuiEnchantment.class);
    @SuppressWarnings("unchecked")
    private static final FieldHelper<Map<ResourceLocation, FontRenderer>, FontResourceManager> mapField =
            FieldHelper.from((Class<Map<ResourceLocation, FontRenderer>>) (Class<?>) Map.class,
                    FontResourceManager.class);
    private static final FieldHelper<INameable, GuiEnchantment> nameableField =
            FieldHelper.from(INameable.class, GuiEnchantment.class);
    private static final FontRenderer dummyFontRenderer = new DummyFontRenderer();
    private static final long SCROLL_PERIOD_MS = 5000;
    private static final long SCROLL_PAUSE_MS = 1000;
    // These two are in pixels
    private static final int TITLE_OFFSET_X = 8;
    private static final int TITLE_OFFSET_Y = 4;

    private final EnchantmentWorker worker;
    private final InventoryPlayer inventory;
    private final INameable nameableShadow;
    private EnchantmentWorker.State lastState;
    private long scrollBaseMs = System.currentTimeMillis();
    @SuppressWarnings("unchecked")
    private final ArrayList<String>[] tooltipText = new ArrayList[3];

    public GuiEnchantmentWrapper(
            InventoryPlayer inventory, World worldIn, EnchantmentWorker worker, BlockPos pos, INameable nameable) {
        super(inventory, worldIn, nameable);
        try {
            ContainerEnchantment containerWrapper =
                    new ContainerEnchantmentWrapper(inventory, worldIn, worker, pos);
            inventorySlots = containerWrapper;
            containerField.set(this, containerWrapper);
            nameableShadow = nameable;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < 3; ++i) {
            tooltipText[i] = new ArrayList<String>();
        }
        this.worker = worker;
        this.inventory = inventory;
    }

    public static GuiEnchantmentWrapper wrap(
            GuiEnchantment base, EnchantmentWorker worker, BlockPos pos) {

        return new GuiEnchantmentWrapper(
                inventoryField.get(base),
                worldField.get(containerField.get(base)), worker, pos, nameableField.get(base));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        if (worker.isDisabled()) {
            super.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
            return;
        }
        // We don't want the gibberish text to render, but we want the rest of the standard GUI
        // stuff, so we replace the renderer before delegating and then put it back after.
        FontRenderer saved = mc.getFontResourceManager().getFontRenderer(Minecraft.standardGalacticFontRenderer);
        Map<ResourceLocation, FontRenderer> renderers = mapField.get(mc.getFontResourceManager());
        renderers.put(Minecraft.standardGalacticFontRenderer, dummyFontRenderer);
        super.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
        renderers.put(Minecraft.standardGalacticFontRenderer, saved);

        if (lastState.observation != ((ContainerEnchantmentWrapper) inventorySlots).lastObservation) {
            return; // Out-of-sync, happens when the GUI is closed with an item still present
        }
        FontRenderer renderer = mc.fontRenderer;

        // Use the scissor test to clip scrolling/oversize text to within the GUI. We have to
        // rescale to get screen coordinates from GUI (scaled) coordinates.
        int midX = (width - xSize) / 2;
        int midY = (height - ySize) / 2;
        final int leftBound = 78;
        final int rightBound = 166;
        int clipMinX = (midX + leftBound) * mc.mainWindow.getWidth() / width;
        int scissorWidth = (rightBound - leftBound) * mc.mainWindow.getWidth() / width;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(clipMinX, 0, scissorWidth, mc.mainWindow.getHeight());

        float scrollFraction = getScrollFraction();
        for (int i = 0; i < 3; ++i) {
            String[] enchants = lastState.enchants[i];
            if (enchants.length == 0) {
                continue;
            }

            String ench = enchants[0];
            int stringWidth = renderer.getStringWidth(ench);
            float adjust = (stringWidth <= rightBound - leftBound ? 0.5F : scrollFraction)
                    * (rightBound - leftBound - stringWidth);
            renderer.drawString(ench, midX + leftBound + adjust, midY + 15 + 19 * i, 0x222222);

            int[] enchantCounts = lastState.counts[i];
            int ecLen = enchantCounts.length;
            if (ecLen == 1) {
                continue; // Don't do "+0 more"
            }

            final String plusX;
            if (enchantCounts[ecLen - 1] == enchantCounts[0]) {
                plusX = "" + (ecLen - 1);
            } else {
                int acc = 0;
                for (int j = 1; j < ecLen; ++j) {
                    acc += enchantCounts[j];
                }
                int total = (int) (((acc * 200L + 1) / enchantCounts[0]) >>> 1);
                plusX = String.format("%d.%02d", total / 100, total % 100);
            }
            renderer.drawString(I18n.format("enchantmentrevealer.text.plusx", plusX),
                    midX + leftBound + 2, midY + 24 + 19 * i, 0x222222);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private float getScrollFraction() {
        long elapsed = System.currentTimeMillis() - scrollBaseMs;
        while (elapsed > SCROLL_PERIOD_MS) {
            elapsed -= SCROLL_PERIOD_MS;
            scrollBaseMs += SCROLL_PERIOD_MS;
        }
        boolean flag = elapsed < SCROLL_PERIOD_MS / 2;
        if (!flag) {
            elapsed -= SCROLL_PERIOD_MS / 2;
        }
        final float frac = (elapsed < SCROLL_PAUSE_MS)
                ? 0F
                : (elapsed - SCROLL_PAUSE_MS) / (float) (SCROLL_PERIOD_MS / 2 - SCROLL_PAUSE_MS);
        return flag ? 1F - frac : frac;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        EnchantmentWorker.State newState = worker.state;
        if (!worker.isDisabled() && newState != lastState) {
            lastState = newState;
            calculateTooltipText();
            scrollBaseMs = System.currentTimeMillis();
        }
        super.render(mouseX, mouseY, partialTicks);
    }

    private void calculateTooltipText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 3; ++i) {
            ArrayList<String> text = tooltipText[i];
            String[] enchants = lastState.enchants[i];
            int[] counts = lastState.counts[i];

            boolean hidePercent = counts.length == 0 || counts[0] == counts[counts.length - 1];
            text.clear();
            for (int j = 0; j < enchants.length; ++j) {
                if (hidePercent) {
                    // Duplicate the highlighting of the normal GUI
                    builder.append(TextFormatting.WHITE);
                    builder.append(TextFormatting.ITALIC);
                } else {
                    builder.append(TextFormatting.YELLOW);
                }
                builder.append(enchants[j]);
                builder.append(TextFormatting.RESET);
                String styled = builder.toString();
                String which = EnchantmentRevealer.CONFIG.verboseDebug.get()
                        ? "verbose"
                        : "normal";
                if (hidePercent) {
                    text.add(I18n.format("enchantmentrevealer.tooltip." + which,
                            styled, counts[j]));
                } else {
                    text.add(I18n.format("enchantmentrevealer.tooltip.percent." + which,
                            styled, percentage(counts[j], counts[0]), counts[j]));
                }
                builder.setLength(0);
            }
        }
    }

    private static String percentage(int numerator, int denominator) {
        if (numerator * 2000L >= 199L * denominator) {
            return ((numerator * 200L / denominator + 1L) >>> 1) + "";
        }
        if (numerator * 20000L >= 199L * denominator) {
            int foo = (int) ((numerator * 2000L / denominator + 1L) >>> 1);
            return foo / 10 + "." + foo % 10;
        }
        return String.format("0.%02d", (numerator * 20000L / denominator + 1L) >>> 1);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String message = EnchantmentWorker.DEFAULT_STATUS;
        if (isPointInRegion(TITLE_OFFSET_X, TITLE_OFFSET_Y, 160, 9, mouseX, mouseY)) {
            message = I18n.format(
                    worker.isDisabled() ? "enchantmentrevealer.status.enable" : "enchantmentrevealer.status.disable");
        } else if (worker.isDisabled()) {
            message = nameableShadow.getDisplayName().getFormattedText();
        } else if (lastState != null) {
            message = lastState.statusMessage;
        }
        fontRenderer.drawString(message, TITLE_OFFSET_X, TITLE_OFFSET_Y, 0x404040);
        fontRenderer.drawString(inventory.getDisplayName().getFormattedText(),
                8f, ySize - 96 + 2, 0x404040);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (isPointInRegion(TITLE_OFFSET_X, TITLE_OFFSET_Y, 160, 9, mouseX, mouseY)) {
            worker.setDisabled(!worker.isDisabled());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawHoveringText(List<String> textLines, int x, int y) {
        if (worker.isDisabled()) {
            super.drawHoveringText(textLines, x, y);
            return;
        }
        // Rather than overriding all of drawScreen, it's easier to grab the tooltip
        // before it is rendered and tweak it to suit us.
        Observation lastObservation =
                ((ContainerEnchantmentWrapper) inventorySlots).lastObservation;
        if (lastObservation != lastState.observation ||
                lastState.enchants == EnchantmentWorker.NO_STRINGS) {
            // We don't have a new result yet, pass through.
            super.drawHoveringText(textLines, x, y);
            return;
        }
        for (int i = 0; i < 3; ++i) {
            if (isPointInRegion(60, 14 + 19 * i, 108, 17, x, y)) {
                ArrayList<String> newLines = tooltipText[i];
                int originalSize = newLines.size();
                if (!textLines.isEmpty()) {
                    newLines.addAll(textLines.subList(1, textLines.size()));
                }
                super.drawHoveringText(newLines, x, y);
                // Restore the list
                newLines.subList(originalSize, newLines.size()).clear();
                return;
            }
        }
        System.out.println(
                "Couldn't figure out where the mouse was pointing! Coords (" + x + "," + y + ")");
        super.drawHoveringText(textLines, x, y);
    }
}

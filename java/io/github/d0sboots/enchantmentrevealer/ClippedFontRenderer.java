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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * ClippedFontRenderer extends the normal FontRenderer to allow specifying a clipping rectangle.
 *
 * Italic rendering may slightly overflow the clipping bounds, because doing otherwise would be
 * tricky and I don't need it anyway.
 */
public class ClippedFontRenderer extends FontRenderer {
    private static final FieldHelper<ResourceLocation, FontRenderer> textureField =
            FieldHelper.from(ResourceLocation.class, FontRenderer.class);
    private static final FieldHelper<TextureManager, FontRenderer> engineField =
            FieldHelper.from(TextureManager.class, FontRenderer.class);
    private static final ResourceLocation[] unicodePageLocations =
            FieldHelper.from(ResourceLocation[].class, FontRenderer.class).get(null);

    // Too lazy for getters/setters, access these directly.
    public float clipMinX = -1f;
    public float clipMaxX = Float.MAX_VALUE;
    public float clipMinY = -1f;
    public float clipMaxY = Float.MAX_VALUE;

    /**
     * The new renderer will clone the settings (such as font) of the base renderer, but does not
     * keep a reference. (Changes to the base renderer won't apply to the ClippedFontRenderer.)
     *
     * @param base The renderer to clone from
     */
    ClippedFontRenderer(FontRenderer base) {
        super(Minecraft.getMinecraft().gameSettings, textureField.get(base),
                engineField.get(base), base.getUnicodeFlag());
        setBidiFlag(base.getBidiFlag());
        super.onResourceManagerReload(null);
    }

    /**
     * Logic common to rendering a unicode or non-unicode font.
     *
     * @param ch The character to render
     * @param xStart The x-coordinate to begin font layout from
     * @param totalWidth The width of this character, including the padding space
     * @param italic Whether to render in italics
     */
    private void renderCommon(int ch, int xStart, float totalWidth, boolean italic) {
        float texX = (ch & 0x0F) / 16F + xStart / 256F;
        float texY = (ch & 0xF0) / 256F;
        float width = totalWidth - 1.01F;
        float italicOffset = italic ? 1F : 0F;

        float startX = posX;
        float endX = posX + width;
        float startY = posY;
        float endY = posY + 7.99F;
        // Abort if this character is completely outside the clipping bounds
        if (startX > clipMaxX || endX < clipMinX || startY > clipMaxY || endY < clipMinY) {
            return;
        }

        float startTexX = texX;
        float endTexX = texX + width / 128F;
        float startTexY = texY;
        float endTexY = texY + 7.99F / 128F;
        if (startX < clipMinX) {
            startTexX += (clipMinX - startX) / 128F;
            startX = clipMinX;
        }
        if (endX > clipMaxX) {
            endTexX += (clipMaxX - endX) / 128F;
            endX = clipMaxX;
        }
        if (startY < clipMinY) {
            startTexY += (clipMinY - startY) / 128F;
            startY = clipMinY;
        }
        if (endY > clipMaxY) {
            endTexY += (clipMaxY - endY) / 128F;
            endY = clipMaxY;
        }

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        GL11.glTexCoord2f(startTexX, startTexY);
        GL11.glVertex3f(startX + italicOffset, startY, 0.0F);
        GL11.glTexCoord2f(startTexX, endTexY);
        GL11.glVertex3f(startX - italicOffset, endY, 0.0F);
        GL11.glTexCoord2f(endTexX, startTexY);
        GL11.glVertex3f(endX + italicOffset, startY, 0.0F);
        GL11.glTexCoord2f(endTexX, endTexY);
        GL11.glVertex3f(endX - italicOffset, endY, 0.0F);
        GL11.glEnd();
    }

    @Override
    protected float renderDefaultChar(int ch, boolean italic)
    {
        float totalWidth = charWidth[ch];
        bindTexture(locationFontTexture);
        renderCommon(ch, 0, totalWidth, italic);
        return totalWidth;
    }

    private ResourceLocation getUnicodePageLocation(int page) {
        // Note that unicodePageLocations aliases the static field in ResourceLocation, so this
        // makes use of (and updates) the main cache.
        ResourceLocation loc = unicodePageLocations[page];
        if (loc == null) {
            loc = new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", page));
            unicodePageLocations[page] = loc;
        }
        return loc;
    }

    @Override
    protected float renderUnicodeChar(char ch, boolean italic)
    {
        int glyphLocation = glyphWidth[ch];
        if (glyphLocation == 0)
            return 0F;
        int xStart = glyphLocation >>> 4;
        int xEnd = glyphLocation & 15;
        int page = ch >> 8;
        bindTexture(getUnicodePageLocation(page));
        // Not sure what's up with this value, but it's what makes the math work out.
        float totalWidth = (xEnd + 3 - xStart) / 2F;
        renderCommon(ch, xStart, totalWidth, italic);
        return totalWidth;
    }
}
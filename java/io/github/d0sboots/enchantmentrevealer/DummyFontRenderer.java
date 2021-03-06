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

import net.minecraft.client.gui.FontRenderer;

/**
 * Stub that doesn't render anything.
 *
 * Only overrides what I need it to.
 */
public class DummyFontRenderer extends FontRenderer {
    DummyFontRenderer() {
        super(null, null);
    }

    @Override
    public void drawSplitString(String str, int x, int y, int wrapWidth, int textColor) {
    }
}

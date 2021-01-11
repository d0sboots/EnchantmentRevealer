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

import java.io.File;

import org.apache.logging.log4j.LogManager;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = EnchantmentRevealer.MODID, version = EnchantmentRevealer.VERSION, updateJSON = "https://raw.githubusercontent.com/d0sboots/EnchantmentRevealer/master/update.json", canBeDeactivated = true, clientSideOnly = true)
public class EnchantmentRevealer {
    public static final String MODID = "enchantment_revealer";
    public static final String NAME = "EnchantmentRevealer";
    public static final String VERSION = "1.3";
    public static boolean verbose = false;

    private Events events;
    private boolean enableCommand = false;

    @EventHandler
    public void init(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(new File(event.getModConfigurationDirectory(), NAME + ".cfg"));
        config.load();

        Property prop = config.get(Configuration.CATEGORY_CLIENT, "verboseDebug", false,
                "If true, enchanting information will be more verbose.");
        verbose = prop.getBoolean();
        prop = config.get(Configuration.CATEGORY_GENERAL, "enableCommand", false,
                "If true, the /xpseed command will be enabled, allowing you to retrieve "
                        + "and set players' enchantment seeds directly.");
        enableCommand = prop.getBoolean();
        prop = config.get(Configuration.CATEGORY_CLIENT, "useSeedHint", "sometimes",
                "How to handle seed \"hint\" values from the server. On a vanilla server, these provide "
                        + "useful information that greatly speeds up the deduction process, but other servers "
                        + "may send garbage data instead. Setting this to \"always\" says to always trust the "
                        + "server provided value, while \"never\" means to ignore it. The default is \"sometimes\", "
                        + "which means try to use the seed, but recalculate as if \"never\" if it doesn't work.");
        events = new Events(prop.getString());

        if (config.hasChanged()) {
            config.save();
        }
        MinecraftForge.EVENT_BUS.register(events);
        LogManager.getLogger().info("EnchantmentRevealer initialized.");
    }

    @EventHandler
    public void onServerStart(FMLServerStartingEvent event) {
        if (enableCommand) {
            event.registerServerCommand(new CommandXpSeed());
        }
    }
}

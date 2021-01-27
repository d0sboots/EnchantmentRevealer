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

import org.apache.logging.log4j.LogManager;

import io.github.d0sboots.enchantmentrevealer.CommandXpSeed.HexArgumentType;
import net.minecraft.command.arguments.ArgumentSerializer;
import net.minecraft.command.arguments.ArgumentTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EnchantmentRevealer.MODID)
public class EnchantmentRevealer {
    public static final String MODID = "enchantment_revealer";
    public static final String VERSION = "1.4";

    /**
     * We lump all our configuration into a single config file of type COMMON. This is because it's too
     * much of a pain to deal with multiple files, and the single server command we're defining is for
     * debugging, so it shouldn't be per-server anyway, but global.
     */
    public static class Config {
        public final BooleanValue verboseDebug;
        public final ConfigValue<String> useSeedHint;
        public final BooleanValue enableCommand;
        public final ConfigValue<Integer> syncTicksMax;

        Config(ForgeConfigSpec.Builder builder) {
            builder.comment("Client only settings").push("client");

            verboseDebug = builder
                    .comment("If true, enchanting information will be more verbose.")
                    .define("verboseDebug", false);
            useSeedHint = builder.comment("How to handle seed \"hint\" values from the server. On a vanilla server, ",
                    "these provide useful information that greatly speeds up the deduction process, but other ",
                    "servers may send garbage data instead. Setting this to \"always\" says to always trust ",
                    "the server provided value, while \"never\" means to ignore it. The default is \"sometimes\", ",
                    "which means try to use the seed, but recalculate as if \"never\" if it doesn't work.")
                    .define("useSeedHint", "sometimes");
            syncTicksMax = builder.comment(
                    "The maximum number of game ticks to wait for between an item being placed in the GUI ",
                    "and receiving the response from the server. You can increase this value if it seems like ",
                    "sometimes items are \"skipped\" without anything happenning, but going too large (multiple ",
                    "seconds, i.e. over 40) can introduce other issues in certain edge cases if you shuffle ",
                    "items fast enough.")
                    .define("syncTicksMax", 15);
            builder.pop();

            builder.comment("Server-side configuration settings").push("server");

            enableCommand = builder
                    .comment("If true, the /xpseed command will be enabled, allowing you to retrieve "
                            + "and set players' enchantment seeds directly")
                    .worldRestart()
                    .define("enableCommand", false);
            builder.pop();
        }
    }

    public static final Config CONFIG;
    static final ForgeConfigSpec configSpec;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CONFIG = new Config(builder);
        configSpec = builder.build();
    }

    public EnchantmentRevealer() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, configSpec);

        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(new Events()));
        // Subscribe to both event buses, because we get events from both and we're too lazy to create
        // separate classes.
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        LogManager.getLogger().info("EnchantmentRevealer initialized.");
    }

    @SubscribeEvent
    public void onSetup(FMLCommonSetupEvent unused) {
        if (EnchantmentRevealer.CONFIG.enableCommand.get()) {
            ArgumentTypes.register(new ResourceLocation("enchantmentrevealer:hex_argument_type"),
                    HexArgumentType.class, new ArgumentSerializer<>(HexArgumentType::integer));
        }
    }

    @SubscribeEvent
    public void onServerStart(FMLServerStartingEvent event) {
        if (EnchantmentRevealer.CONFIG.enableCommand.get()) {
            CommandXpSeed.register(event.getCommandDispatcher());
        }
    }
}

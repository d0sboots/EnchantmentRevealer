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

import java.util.Arrays;
import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Optional command that allows setting and getting any player's XP seed.
 *
 * Disabled by default, enable it in the config file.
 */
public class CommandXpSeed {
    // Notes: Useful seeds to test:
    // 0x6585bd63, power=15, item=Fishing Pole: No enchant for first slot
    // 0x249e08e5, power=15, item=Fishing Pole: 7230 possibilities, many different percentages
    // 0x249e08e4, power=0, item=Fishing Pole: No enchant for first two slots
    // 0x0249e08e, power=0, item=Golden Sword: Missing middle slot

    private static final FieldHelper<Integer, EntityPlayer> xpField =
            FieldHelper.offsetFrom(Integer.class, EntityPlayer.class, PlayerCapabilities.class, 4);

    private static final String PLAYER = "player";
    private static final String SEED = "seed";

    private static boolean isAllowedNumber(char c) {
        c = Character.toLowerCase(c);
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c == '.'
                || c == '-' || c == '+' || c == '#' || c == 'x';
    }

    /**
     * A custom ArgumentType for parsing integers, which works for integers in any format, not just
     * decimals.
     */
    static class HexArgumentType implements ArgumentType<Integer> {
        private static final HexArgumentType INSTANCE = new HexArgumentType();
        private static final Collection<String> EXAMPLES = Arrays.asList("0x5", "123", "-032");

        private HexArgumentType() {}

        public static HexArgumentType integer() { return INSTANCE; }

        public static int getInteger(final CommandContext<?> context, final String name) {
            return context.getArgument(name, int.class);
        }

        @Override
        public <S> Integer parse(final StringReader reader) throws CommandSyntaxException {
            final int start = reader.getCursor();
            while (reader.canRead() && isAllowedNumber(reader.peek())) {
                reader.skip();
            }
            final String number = reader.getString().substring(start, reader.getCursor());
            if (number.isEmpty()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt()
                        .createWithContext(reader);
            }
            try {
                // We parse as Long and then double-cast so that we can read unsigned
                // 32-bit numbers, but then interpret them as (signed) 32-bit ints.
                return (int) (long) Long.decode(number);
            } catch (final NumberFormatException ex) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt()
                        .createWithContext(reader, number);
            }
        }

        @Override
        public Collection<String> getExamples() { return EXAMPLES; }
    }


    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("xpseed")
                .requires(cs -> cs.hasPermissionLevel(2))
                .then(Commands.argument(SEED, HexArgumentType.integer())
                        .executes(cs -> setSeed(cs, true)))
                .then(Commands.literal("user")
                        .then(Commands.argument(PLAYER, EntityArgument.singlePlayer())
                                .then(Commands.argument(SEED, HexArgumentType.integer())
                                        .executes(cs -> setSeed(cs, false)))
                                .executes(cs -> getSeed(cs, false))))
                .executes(cs -> getSeed(cs, true)));
    }

    private static int sendFeedback(CommandContext<CommandSource> context, String translationKey,
            EntityPlayer player, int seed) {
        context.getSource().sendFeedback(new TextComponentTranslation(
                translationKey, player.getName(), String.format("0x%08X", seed)), true);
        return seed;
    }

    private static int setSeed(CommandContext<CommandSource> context, boolean localPlayer)
            throws CommandSyntaxException {
        EntityPlayer player = localPlayer ? context.getSource().asPlayer()
                : EntityArgument.getOnePlayer(context, PLAYER);
        int seed = HexArgumentType.getInteger(context, SEED);
        xpField.set(player, seed);
        return sendFeedback(context, "commands.xpseed.set", player, seed);
    }

    private static int getSeed(CommandContext<CommandSource> context, boolean localPlayer)
            throws CommandSyntaxException {
        EntityPlayer player = localPlayer ? context.getSource().asPlayer()
                : EntityArgument.getOnePlayer(context, PLAYER);
        int seed = player.getXPSeed();
        return sendFeedback(context, "commands.xpseed.query", player, seed);
    }
}

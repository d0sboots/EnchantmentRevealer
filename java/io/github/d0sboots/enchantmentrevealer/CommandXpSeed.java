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

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.server.MinecraftServer;

/**
 * Optional command that allows setting and getting any player's XP seed.
 *
 * Disabled by default, enable it in the config file.
 */
public class CommandXpSeed extends CommandBase
{
    // Notes: Useful seeds to test:
    // 0x6585bd63, power=15, item=Fishing Pole: No enchant for first slot
    // 0x249e08e5, power=15, item=Fishing Pole: 7230 possibilities, many different percentages
    // 0x249e08e4, power=0, item=Fishing Pole: No enchant for first two slots
    // 0x0249e08e, power=0, item=Golden Sword: Missing middle slot

    private static final FieldHelper<Integer, EntityPlayer> xpField =
            FieldHelper.offsetFrom(Integer.class, EntityPlayer.class, PlayerCapabilities.class, 4);
    /**
     * Gets the name of the command
     */
    @Override
    public String getCommandName()
    {
        return "xpseed";
    }

    /**
     * Return the required permission level for this command.
     */
    @Override
    public int getRequiredPermissionLevel()
    {
        return 2;
    }

    /**
     * Gets the usage string for the command.
     *
     * @param sender The command sender that executed the command
     */
    @Override
    public String getCommandUsage(ICommandSender sender)
    {
        return "commands.xpseed.usage";
    }

    /**
     * Callback when the command is invoked
     *
     * @param sender The command sender that executed the command
     * @param args The arguments that were passed
     */
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        final EntityPlayer player;
        final boolean isQuery;
        if (args.length <= 0)
        {
            player = getCommandSenderAsPlayer(sender);
            isQuery = true;
        } else if (args.length > 2) {
            throw new WrongUsageException("commands.xpseed.usage");
        } else if (args.length == 2) {
            player = getPlayer(server, sender, args[0]);
            int value;
            try {
                value = (int) (long) Long.decode(args[1]);
            } catch (NumberFormatException unused) {
                throw new NumberInvalidException("commands.generic.num.invalid", args[1]);
            }
            isQuery = false;
            xpField.set(player, value);
        } else {
            EntityPlayer localPlayer = null;
            boolean localQuery;
            try {
                localPlayer = getPlayer(server, sender, args[0]);
                localQuery = true;
            } catch (PlayerNotFoundException e) {
                int value;
                try {
                    value = (int) (long) Long.decode(args[0]);
                } catch (NumberFormatException unused) {
                    throw new CommandException("commands.xpseed.failure", args[0]);
                }
                localPlayer = getCommandSenderAsPlayer(sender);
                localQuery = false;
                xpField.set(localPlayer, value);
            }
            player = localPlayer;
            isQuery = localQuery;
        }
        notifyOperators(sender, this, isQuery ? "commands.xpseed.query" : "commands.xpseed.set",
                player.getName(), String.format("0x%08X", player.getXPSeed()));
    }
}

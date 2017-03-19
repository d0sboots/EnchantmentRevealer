# EnchantmentRevealer
A minecraft mod that reveals all enchantments in the enchantment GUI. It works even with vanilla Survival Multiplayer servers!

## How to get it
Grab the latest JAR from https://github.com/d0sboots/EnchantmentRevealer/releases/latest and install it as per
the instructions at http://www.minecraftmods.com/how-to-install-mods-for-minecraft-forge/. Note that this mod requires Minecraft Forge,
it cannot be installed stand-alone.

## How to use it
When using an enchanting table, the default GUI will be tweaked a little:
![Initial Screenshot](https://raw.githubusercontent.com/d0sboots/EnchantmentRevealer/master/images/screenshot-initial.png)

The most obvious changes happen when you drop an item in the table, however:
![Screenshot with a bow on the table](https://raw.githubusercontent.com/d0sboots/EnchantmentRevealer/master/images/screenshot-possibilities.png)

There are several things to notice here:
- The text in the upper-left corner has changed to show the number of *possibilities*. In general, the mod uses this area to display
a general status message.
- The alien gibberish text is gone. In its place is a short summary of the enchantment for each slot. The first line shows the
guaranteed effect (the same one that vanilla Minecraft shows), while the second line shows the average number of additional effects
that you receive, based on the mod's best current information.
- The tooltip shows the complete breakdown of each possible effect, and their probabilities. The first effect will always be 100%,
since that is the guaranteed effect.

Of course, out of all the 1646 possibilities that could happen (in this screenshot) when you enchant the item, only one of them will
*actually* happen, and which one is already pre-determined. In order to find out what the true possibility is, drag different items
onto the table. Each one reveals additional information to the mod, and the number of possibilities will go down. **Books are a
particularly good item to use**, often uniquely identifying the seed all by themselves. This is because books can be enchanted with
any effect, and thus have many more unique combinations.

Once you have tried enough items, the display will look like this:
![Screenshot with seed revealed](https://raw.githubusercontent.com/d0sboots/EnchantmentRevealer/master/images/screenshot-seed.png)

- The number of possibilities have been replaced with the exact *seed value*, in [hexadecimal](https://en.wikipedia.org/wiki/Hexadecimal).
The exact value here isn't generally important, what's important is that this means the mod knows **exactly** what enchantment(s) you
will receive. This number (and thus the enchantments) won't change until you enchant something - using a different table, dying, or
re-logging won't change it.
- The "+X more" values become exact.
- The tooltip changes to show the enchantment effects in white, without percentages (since they're all guaranteed now.)

## How it works
Internally, Minecraft uses a [pseudorandom number generator](https://en.wikipedia.org/wiki/Pseudorandom_number_generator) (RNG for short)
to determine which enchantments will be applied to an item. The state of this RNG is completely determined by a single 32-bit number,
which means that there are over 4 billion possible sets of enchantments that can be applied. However, the Minecraft server has to tell
the client several things in order for the enchantment GUI to work:
- The enchantment levels for each slot (the small green number)
- The guaranteed effect for each slot, which the vanilla GUI shows
- 12 bits of the seed are also sent, which the vanilla GUI uses to generate the alien gibberish text.

Each of these pieces of information narrows down the number of possible seeds that fit. By testing out several items, the possible values
can be quickly narrowed down to exactly one.

## Other features
The mod also can add a /xpseed command that can view or set the seed value for any player. This is disabled by default - to enable it,
edit the .cfg file (which will only be created once the mod has been used at least once). The command only works for server admins,
so it's mainly useful for single-player or LAN games.

## Disclaimer
Whether using this constitutes cheating or not is in the eye of the beholder (and possibly also the eye of your server admin).
I'm not responsible for anything that happens to you or your game while using this. However, unlike flying mods or whatnot,
this mod doesn't do anything "unnatural." You could accomplish the same thing with a website that asks for the results of
test-enchanting various items as well as the words of alien gibberish text, and it would get the same results, just with more
manual effort involved.

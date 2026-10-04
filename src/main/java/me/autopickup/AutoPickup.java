package me.autopickup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class AutoPickup extends JavaPlugin implements Listener, TabCompleter {

    private NamespacedKey enabledKey;

    @Override
    public void onEnable() {
        enabledKey = new NamespacedKey(this, "enabled");
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("autopickup");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("AutoPickup enabled!");
    }

    // ---------- Saving the on/off setting (stored on the player, survives restarts) ----------

    private boolean isEnabled(Player player) {
        Byte value = player.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private void setEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    // ---------- /autopickup [on|off] ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        boolean newState;
        if (args.length == 0) {
            newState = !isEnabled(player); // no argument = flip it
        } else if (args[0].equalsIgnoreCase("on")) {
            newState = true;
        } else if (args[0].equalsIgnoreCase("off")) {
            newState = false;
        } else {
            player.sendMessage(Component.text("Usage: /" + label + " [on|off]", NamedTextColor.RED));
            return true;
        }

        setEnabled(player, newState);
        if (newState) {
            player.sendMessage(Component.text("Auto-pickup is now ON", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Auto-pickup is now OFF", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    // ---------- Putting broken-block drops into the inventory ----------

    // This event fires with the exact items the block is about to drop,
    // so Fortune, Silk Touch and tool rules all still work normally.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        Location dropSpot = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        boolean gotSomething = false;

        Iterator<Item> it = event.getItems().iterator();
        while (it.hasNext()) {
            Item item = it.next();
            ItemStack stack = item.getItemStack();

            // addItem returns whatever didn't fit
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack extra : leftover.values()) {
                // Inventory full: drop the rest on the ground like normal
                player.getWorld().dropItemNaturally(dropSpot, extra);
            }

            it.remove(); // stop the original item from spawning
            gotSomething = true;
        }

        if (gotSomething) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.5f);
        }
    }

    // Give XP from ores etc. straight to the player too (still repairs Mending gear)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        int xp = event.getExpToDrop();
        if (xp > 0) {
            event.setExpToDrop(0);
            player.giveExp(xp, true);
        }
    }
}

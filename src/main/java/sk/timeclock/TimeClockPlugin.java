package sk.timeclock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.ServerTickManager;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TimeClockPlugin extends JavaPlugin implements Listener {

    private static final long CLICK_COOLDOWN_MS = 600;

    private NamespacedKey clockKey;
    private NamespacedKey recipeKey;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        clockKey = new NamespacedKey(this, "time_clock");
        recipeKey = new NamespacedKey(this, "time_clock");

        registerRecipe();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.removeRecipe(recipeKey);
    }

    // ---------- item + recipe ----------

    private ItemStack createClock() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Clock of Time", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(clockKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isTimeClock(ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(clockKey, PersistentDataType.BYTE);
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createClock());
        recipe.shape("QQQ", "QCQ", "QQQ");
        recipe.setIngredient('Q', Material.QUARTZ);
        recipe.setIngredient('C', Material.CLOCK);
        Bukkit.addRecipe(recipe);
    }

    // ---------- right click ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isTimeClock(event.getItem())) {
            return;
        }

        Player player = event.getPlayer();

        // do not toggle when the player is just opening a chest, door, etc.
        if (event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable()
                && !player.isSneaking()) {
            return;
        }
        if (!player.hasPermission("timeclock.use")) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);

        toggle(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    // ---------- tick freeze ----------

    private void toggle(Player player) {
        ServerTickManager tickManager = Bukkit.getServerTickManager();
        boolean freeze = !tickManager.isFrozen();
        tickManager.setFrozen(freeze);

        if (getConfig().getBoolean("sounds", true)) {
            Sound sound = freeze ? Sound.BLOCK_BEACON_DEACTIVATE : Sound.BLOCK_BEACON_ACTIVATE;
            player.getWorld().playSound(player.getLocation(), sound, 1f, 1f);
        }
    }

    // ---------- /timeclock ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("TimeClock config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            Player target = null;
            if (args.length > 1) {
                target = Bukkit.getPlayerExact(args[1]);
            } else if (sender instanceof Player self) {
                target = self;
            }
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }
            final Player receiver = target;
            receiver.getInventory().addItem(createClock()).values()
                    .forEach(left -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), left));
            return true;
        }

        return false;
    }
}

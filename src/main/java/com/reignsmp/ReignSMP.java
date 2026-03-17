package com.reignsmp;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ReignSMP extends JavaPlugin implements Listener {

    private static final long UNCLAIMED_TIMEOUT = 5L * 60 * 20;

    private boolean crownCrafted = false;
    private UUID crownEntityUUID = null;
    private long unclaimedTicks = 0;
    private NamespacedKey crownKey;
    private NamespacedKey recipeKey;

    @Override
    public void onEnable() {
        crownKey = new NamespacedKey("reignsmp", "is_crown");
        recipeKey = new NamespacedKey("reignsmp", "crown_recipe");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        java.io.File cfg = new java.io.File(getDataFolder(), "config.yml");
        if (!cfg.exists()) {
            try {
                cfg.createNewFile();
                java.nio.file.Files.write(cfg.toPath(),
                    "crown-crafted: false\ncrown-entity-uuid: ''\n".getBytes());
            } catch (Exception e) { getLogger().warning("Could not create config: " + e.getMessage()); }
        }
        reloadConfig();
        loadData();
        registerCrownRecipe();
        getServer().getPluginManager().registerEvents(this, this);
        startTicker();
        getLogger().info("[ReignSMP] Enabled! The Crown awaits...");
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("[ReignSMP] Disabled.");
    }

    private void loadData() {
        FileConfiguration cfg = getConfig();
        crownCrafted = cfg.getBoolean("crown-crafted", false);
        String u = cfg.getString("crown-entity-uuid", "");
        if (u != null && !u.isEmpty()) {
            try { crownEntityUUID = UUID.fromString(u); } catch (Exception ignored) {}
        }
    }

    private void saveData() {
        FileConfiguration cfg = getConfig();
        cfg.set("crown-crafted", crownCrafted);
        cfg.set("crown-entity-uuid", crownEntityUUID != null ? crownEntityUUID.toString() : "");
        saveConfig();
    }

    private ItemStack createCrown() {
        ItemStack crown = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta meta = crown.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2746 The Crown \u2746");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "The one who holds the crown rules all.");
            lore.add(ChatColor.RED + "Lose it, and lose everything.");
            meta.setLore(lore);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(crownKey, PersistentDataType.BYTE, (byte) 1);
            crown.setItemMeta(meta);
        }
        return crown;
    }

    private boolean isCrown(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(crownKey, PersistentDataType.BYTE);
    }

    private void registerCrownRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createCrown());
        recipe.shape("GSG", "DED", "BBB");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('S', Material.NETHER_STAR);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('E', Material.DRAGON_EGG);
        recipe.setIngredient('B', Material.GOLD_BLOCK);
        getServer().addRecipe(recipe);
    }

    private boolean hasCrown(Player p) {
        for (ItemStack i : p.getInventory().getContents()) {
            if (isCrown(i)) return true;
        }
        return false;
    }

    private void applyCrownEffects(Player p) {
        AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null && hp.getBaseValue() < 40.0) hp.setBaseValue(40.0);
        int dur = 40;
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, dur, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, dur, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, dur, 0, false, false, true));
    }

    private void removeCrownEffects(Player p) {
        AttributeInstance hp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null && hp.getBaseValue() > 20.0) {
            hp.setBaseValue(20.0);
            if (p.getHealth() > 20.0) p.setHealth(20.0);
        }
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.REGENERATION);
        p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        p.removePotionEffect(PotionEffectType.GLOWING);
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (hasCrown(p)) applyCrownEffects(p);
                    else removeCrownEffects(p);
                }
                if (crownEntityUUID != null) {
                    Item item = findCrownEntity();
                    if (item != null) {
                        unclaimedTicks += 20;
                        if (unclaimedTicks >= UNCLAIMED_TIMEOUT) {
                            teleportCrownRandom(item);
                            unclaimedTicks = 0;
                        }
                    } else {
                        unclaimedTicks = 0;
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void teleportCrownRandom(Item existingItem) {
        World world = Bukkit.getWorlds().get(0);
        Random rand = new Random();
        int x = rand.nextInt(10000) - 5000;
        int z = rand.nextInt(10000) - 5000;
        int y = world.getHighestBlockYAt(x, z) + 1;
        Location loc = new Location(world, x + 0.5, y, z + 0.5);
        if (existingItem != null) {
            existingItem.teleport(loc);
            crownEntityUUID = existingItem.getUniqueId();
        } else {
            Item dropped = world.dropItem(loc, createCrown());
            dropped.setPickupDelay(20);
            crownEntityUUID = dropped.getUniqueId();
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "  \u2746 THE CROWN IS UNCLAIMED \u2746");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "  Location: X: " + x + ", Z: " + z);
        Bukkit.broadcastMessage(ChatColor.GRAY + "  Will you claim it?");
        Bukkit.broadcastMessage("");
        saveData();
    }

    private Item findCrownEntity() {
        if (crownEntityUUID == null) return null;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Item && e.getUniqueId().equals(crownEntityUUID)) {
                    return (Item) e;
                }
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (hasCrown(p)) {
            removeCrownEffects(p);
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2746 THE CROWN HAS DROPPED! \u2746");
            Bukkit.broadcastMessage(ChatColor.YELLOW + p.getName() + " has lost The Crown!");
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        if (isCrown(e.getEntity().getItemStack())) {
            crownEntityUUID = e.getEntity().getUniqueId();
            unclaimedTicks = 0;
            e.getEntity().setPickupDelay(40);
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent e) {
        if (isCrown(e.getItemDrop().getItemStack())) {
            crownEntityUUID = e.getItemDrop().getUniqueId();
            unclaimedTicks = 0;
        }
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent e) {
        if (!isCrown(e.getItem().getItemStack())) return;
        if (e.getEntity() instanceof Player) {
            crownEntityUUID = null;
            unclaimedTicks = 0;
        } else {
            e.setCancelled(true);
            ((LivingEntity) e.getEntity()).setHealth(0);
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent e) {
        if (isCrown(e.getEntity().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Item)) return;
        if (!isCrown(((Item) e.getEntity()).getItemStack())) return;
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE
            || cause == EntityDamageEvent.DamageCause.FIRE_TICK
            || cause == EntityDamageEvent.DamageCause.LAVA
            || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            e.setCancelled(true);
        } else if (cause == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            teleportCrownRandom((Item) e.getEntity());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        InventoryType type = e.getInventory().getType();
        if (type == InventoryType.ENCHANTING
            || type == InventoryType.ANVIL
            || type == InventoryType.GRINDSTONE) {
            if (isCrown(e.getCurrentItem()) || isCrown(e.getCursor())) {
                e.setCancelled(true);
                e.getWhoClicked().sendMessage(ChatColor.RED + "The Crown cannot be modified!");
            }
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent e) {
        if (!isCrown(e.getRecipe().getResult())) return;
        if (crownCrafted) {
            e.setCancelled(true);
            e.getWhoClicked().sendMessage(ChatColor.RED + "The Crown already exists in this world!");
        } else {
            crownCrafted = true;
            saveData();
            String name = e.getWhoClicked().getName();
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "  \u2746 THE CROWN HAS BEEN FORGED \u2746");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "  " + name + " has crafted The Crown!");
            Bukkit.broadcastMessage(ChatColor.GRAY + "  The reign begins...");
            Bukkit.broadcastMessage("");
        }
    }
}

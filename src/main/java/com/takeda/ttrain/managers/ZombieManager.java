package com.takeda.ttrain.managers;

import com.takeda.ttrain.TTrainPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.event.entity.EntityResurrectEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZombieManager {
    private final Logger logger;
    private final TTrainPlugin plugin;
    private final Map<UUID, Zombie> activeZombies;
    private final Map<UUID, BukkitTask> activeTimers;
    private final MiniMessage miniMessage;

    public ZombieManager(TTrainPlugin plugin) {
        this.plugin = plugin;
        this.logger = TTrainPlugin.getPluginLogger();
        this.activeZombies = new ConcurrentHashMap<>();
        this.activeTimers = new ConcurrentHashMap<>();
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void spawnTrainingZombie(Player player, int totems, int duration) {
        Location spawnLoc = player.getLocation().add(0, plugin.getConfig().getDouble("zombie.spawn-height", 2.0), 0);
        
        // Check if world is enabled
        if (!plugin.getWorldManager().isWorldEnabled(spawnLoc.getWorld())) {
            logger.warn("Player {} attempted to spawn zombie in disabled world: {}", 
                player.getName(), spawnLoc.getWorld().getName());
            player.sendMessage(plugin.getConfigManager().getMessage("errors.world-disabled"));
            return;
        }

        try {
            Zombie zombie = player.getWorld().spawn(spawnLoc, Zombie.class);
            
            // Set zombie properties
            updateZombieNameTag(zombie, totems, duration);
            zombie.setCustomNameVisible(true);
            zombie.setPersistent(true);
            zombie.setRemoveWhenFarAway(false);
            zombie.setCanPickupItems(false);
            zombie.setShouldBurnInDay(false);
            zombie.setBaby(false);
            
            // Set health attributes
            double zombieHealth = plugin.getConfig().getDouble("zombie.health", 40.0);
            zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(zombieHealth);
            zombie.setHealth(zombieHealth);
            
            // Set metadata for tracking
            zombie.setMetadata("training_zombie", new FixedMetadataValue(plugin, true));
            zombie.setMetadata("owner", new FixedMetadataValue(plugin, player.getUniqueId()));
            zombie.setMetadata("totem_count", new FixedMetadataValue(plugin, totems));
            zombie.setMetadata("spawn_time", new FixedMetadataValue(plugin, System.currentTimeMillis()));
            zombie.setMetadata("duration", new FixedMetadataValue(plugin, duration));
            zombie.setMetadata("remaining_totems", new FixedMetadataValue(plugin, totems));
            
            // Equip the zombie
            equipZombie(zombie, totems);
            
            // Store the zombie
            activeZombies.put(player.getUniqueId(), zombie);
            
            // Schedule removal
            scheduleZombieRemoval(zombie, duration);
            
            // Start name tag update timer
            startNameTagTimer(zombie, totems, duration);
            
            // Play effects
            player.playSound(spawnLoc, Sound.valueOf(plugin.getConfigManager().getSoundEffect("zombie-spawn", "ENTITY_ZOMBIE_AMBIENT")), 1.0f, 1.0f);
            
            // Send success message using ConfigManager
            Component messageComponent = plugin.getConfigManager().getMessage("messages.action-bar.zombie-spawned"); // Action Bar
            if (messageComponent != null) {
                messageComponent = messageComponent.replaceText(builder -> builder
                    .matchLiteral("{totems}")
                    .replacement(String.valueOf(totems)))
                .replaceText(builder -> builder
                    .matchLiteral("{duration}")
                    .replacement(String.valueOf(duration)));
                player.sendActionBar(messageComponent);
            } else {
                // Fallback message if key is somehow missing despite checks
                player.sendActionBar(Component.text("✔ Zombie spawned: " + totems + " totems, " + duration + "s duration!"));
                logger.warn("Missing message key: messages.action-bar.zombie-spawned - using fallback message");
            }
            
            logger.info("Player {} spawned a training zombie with {} totems for {} seconds", 
                player.getName(), totems, duration);
        } catch (Exception e) {
            logger.error("Failed to spawn training zombie for player {}: {}", player.getName(), e.getMessage());
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.spawn-failed")); // Action Bar
        }
    }

    private void updateZombieNameTag(Zombie zombie, int totems, int timeRemaining) {
        // Create name tag with colored time and totem count using StringBuilder
        StringBuilder nameTagBuilder = new StringBuilder();
        nameTagBuilder.append("<gradient:#FF6B6B:#4ECDC4>Training Zombie</gradient> ");
        nameTagBuilder.append("<#FF5555>⏱ ").append(timeRemaining).append("s");
        nameTagBuilder.append(" <#55FF55>⚡ ").append(totems).append(" totems");
        
        zombie.customName(miniMessage.deserialize(nameTagBuilder.toString()));
    }

    private void equipZombie(Zombie zombie, int totems) {
        try {
            // Set netherite armor
            ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
            ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
            ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
            ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
            
            // Make armor unbreakable
            ItemMeta helmetMeta = helmet.getItemMeta();
            helmetMeta.setUnbreakable(true);
            helmet.setItemMeta(helmetMeta);
            
            ItemMeta chestplateMeta = chestplate.getItemMeta();
            chestplateMeta.setUnbreakable(true);
            chestplate.setItemMeta(chestplateMeta);
            
            ItemMeta leggingsMeta = leggings.getItemMeta();
            leggingsMeta.setUnbreakable(true);
            leggings.setItemMeta(leggingsMeta);
            
            ItemMeta bootsMeta = boots.getItemMeta();
            bootsMeta.setUnbreakable(true);
            boots.setItemMeta(bootsMeta);
            
            // Apply armor to zombie
            zombie.getEquipment().setHelmet(helmet);
            zombie.getEquipment().setChestplate(chestplate);
            zombie.getEquipment().setLeggings(leggings);
            zombie.getEquipment().setBoots(boots);
            
            // Set ALL totems in the offhand only
            ItemStack offHandTotem = new ItemStack(Material.TOTEM_OF_UNDYING, totems);
            zombie.getEquipment().setItemInOffHand(offHandTotem);
            zombie.getEquipment().setItemInMainHand(null); // Ensure main hand is empty
            
            // Make equipment stay
            zombie.getEquipment().setHelmetDropChance(0.0f);
            zombie.getEquipment().setChestplateDropChance(0.0f);
            zombie.getEquipment().setLeggingsDropChance(0.0f);
            zombie.getEquipment().setBootsDropChance(0.0f);
            zombie.getEquipment().setItemInMainHandDropChance(0.0f);
            zombie.getEquipment().setItemInOffHandDropChance(0.0f);
        } catch (Exception e) {
            logger.error("Failed to equip zombie: {}", e.getMessage());
        }
    }

    private void scheduleZombieRemoval(Zombie zombie, int duration) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (zombie.isValid() && zombie.hasMetadata("owner")) {
                     try {
                         UUID ownerUuid = (UUID) zombie.getMetadata("owner").get(0).value();
                         // Use the helper, don't force remove here (let it despawn or die naturally if time runs out)
                         removeZombie(zombie, ownerUuid, false); 
                         logger.debug("Scheduled removal task finished for zombie owned by {}", ownerUuid);
                     } catch (IndexOutOfBoundsException | ClassCastException e) {
                          logger.warn("Error retrieving owner UUID from zombie metadata during scheduled removal.");
                          // Attempt cleanup without owner UUID if possible
                          activeZombies.entrySet().removeIf(entry -> entry.getValue().equals(zombie));
                          removeZombieMetadata(zombie); // Remove metadata anyway
                     }
                } else {
                    // Zombie might have already been removed (e.g., last totem pop)
                    logger.debug("Scheduled removal task found invalid or ownerless zombie.");
                }
            }
        }.runTaskLater(plugin, duration * 20L);
    }
    
    private void startNameTagTimer(Zombie zombie, int totems, int duration) {
        if (!zombie.isValid()) return;
        
        UUID ownerUuid = (UUID) zombie.getMetadata("owner").get(0).value();
        
        // Cancel any existing timer
        BukkitTask existingTask = activeTimers.remove(ownerUuid);
        if (existingTask != null) {
            existingTask.cancel();
        }
        
        // Start a new timer that updates the name tag
        BukkitTask task = new BukkitRunnable() {
            private int timeRemaining = duration;
            
            @Override
            public void run() {
                if (timeRemaining <= 0 || !zombie.isValid() || !activeZombies.containsKey(ownerUuid)) {
                    cancel();
                    activeTimers.remove(ownerUuid);
                    return;
                }
                
                // Get current totem count from metadata
                int currentTotems = 0;
                if (zombie.hasMetadata("remaining_totems")) {
                    currentTotems = (int) zombie.getMetadata("remaining_totems").get(0).value();
                }
                
                // Update name tag with remaining time and current totem count
                updateZombieNameTag(zombie, currentTotems, timeRemaining);
                
                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Update every second
        
        activeTimers.put(ownerUuid, task);
    }

    public void cleanupAllZombies() {
        activeZombies.values().forEach(Zombie::remove);
        activeZombies.clear();
        
        // Cancel all timers
        activeTimers.values().forEach(BukkitTask::cancel);
        activeTimers.clear();
        
        logger.info("Cleaned up all active training zombies");
    }

    public boolean hasActiveZombie(UUID playerUuid) {
        return activeZombies.containsKey(playerUuid);
    }

    public Zombie getActiveZombie(UUID playerUuid) {
        return activeZombies.get(playerUuid);
    }
    
    public int getActiveZombieTotemCount(UUID playerUuid) {
        Zombie zombie = activeZombies.get(playerUuid);
        if (zombie != null && zombie.hasMetadata("totem_count")) {
            return (int) zombie.getMetadata("totem_count").get(0).value();
        }
        return 0;
    }

    /**
     * Register handlers to process totem usage for the zombie
     */
    public void registerTotemHandlers() {
        // Register an event listener for entity damage
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
            public void onEntityDamage(EntityDamageEvent event) {
                if (!(event.getEntity() instanceof Zombie zombie)) return;
                if (!zombie.hasMetadata("training_zombie")) return;
                
                // Ignore zero or negative damage events
                if (event.getFinalDamage() <= 0) return; 
                
                // If the zombie *would* die but has no totems, remove metadata 
                // before the EntityDeathEvent fires, preventing drops.
                if (zombie.getHealth() - event.getFinalDamage() <= 0) {
                    List<MetadataValue> meta = zombie.getMetadata("remaining_totems");
                    if (meta.isEmpty() || meta.get(0).asInt() <= 0) {
                        double currentHealth = zombie.getHealth();
                        double finalDamage = event.getFinalDamage();
                        logger.info("Training zombie taking lethal damage (Health: {}, Damage: {}) with 0 totems. Removing metadata before death.", 
                            currentHealth, finalDamage);
                        // Remove metadata *before* death to prevent potential drops/weirdness
                        removeZombieMetadata(zombie);
                        // Let the event proceed (death happens)
                    }
                    // If totems > 0, the EntityResurrectEvent will handle it.
                }
            }
            
            @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
            public void onEntityResurrect(EntityResurrectEvent event) {
                if (!(event.getEntity() instanceof Zombie zombie)) return;
                if (!zombie.hasMetadata("training_zombie")) return;
                
                // Check if the resurrection is happening via Totem (should always be the case here)
                if (event.isCancelled()) return; // Should not happen if totem used
                
                // Get remaining totems metadata
                List<MetadataValue> meta = zombie.getMetadata("remaining_totems");
                if (!meta.isEmpty()) {
                    int remainingTotems = meta.get(0).asInt();
                    
                    // Ensure we actually had totems (this check might be redundant but safe)
                    if (remainingTotems > 0) {
                        // Decrement totem count in metadata
                        remainingTotems--;
                        zombie.setMetadata("remaining_totems", new FixedMetadataValue(plugin, remainingTotems));
                        
                        // Bukkit handles setting health and the particle/sound effect automatically.
                        // We just need to update the count and notify the owner.
                        
                        // Get owner for notification
                        final UUID ownerUuid; // Make final
                        List<MetadataValue> ownerMeta = zombie.getMetadata("owner"); // Get metadata first
                        if (!ownerMeta.isEmpty()) {
                            ownerUuid = (UUID) ownerMeta.get(0).value();
                        } else {
                            ownerUuid = null; // Explicitly null if no owner meta
                            logger.warn("Could not find owner metadata on resurrected zombie {}", zombie.getUniqueId());
                        }
                        
                        if (ownerUuid != null) { // Check if ownerUuid is not null before using it
                            Player owner = plugin.getServer().getPlayer(ownerUuid);
                            if (owner != null && owner.isOnline()) {
                                String rawMessage = plugin.getConfigManager().getRawMessage("messages.action-bar.totem-used", 
                                    "<#f5365c>⚠ Zombie used totem! <white>{count}</white> left.</#f5365c>");
                                rawMessage = rawMessage.replace("{count}", String.valueOf(remainingTotems));
                                owner.sendActionBar(miniMessage.deserialize(rawMessage)); // Action Bar
                            }
                        }
                        
                        logger.debug("Training zombie resurrected using a totem. {} totems remaining.", remainingTotems);
                        
                        // Check if session should end now
                        if (ownerUuid != null && remainingTotems <= 0 && plugin.getConfigManager().shouldEndSessionOnLastTotem()) {
                            logger.info("Ending training session for {} as last totem popped.", ownerUuid);
                            // Manually trigger removal and cleanup (use a slight delay to ensure event processing completes)
                            plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeZombie(zombie, ownerUuid, true), 1L); 
                        } else if (remainingTotems <= 0) {
                             // If session doesn't end, ensure the zombie's offhand is now empty
                             // Bukkit *should* handle removing 1 totem, but let's ensure it's 0
                             plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                 if (zombie.isValid()) {
                                      zombie.getEquipment().setItemInOffHand(null);
                                 }
                             }, 1L);
                        }
                        
                    } else {
                        // This case should ideally not be reached if EntityResurrectEvent fired,
                        // but log it just in case.
                        logger.warn("EntityResurrectEvent fired for zombie with 0 'remaining_totems' metadata. This might indicate an issue.");
                        event.setCancelled(true); // Prevent resurrection if metadata says 0
                        removeZombieMetadata(zombie); // Clean up just in case
                    }
                } else {
                     // Metadata missing, cancel resurrection
                     logger.warn("EntityResurrectEvent fired for training zombie but missing 'remaining_totems' metadata. Cancelling resurrection.");
                     event.setCancelled(true); // Prevent resurrection if metadata is missing
                     removeZombieMetadata(zombie); // Clean up just in case
                }
            }
        }, plugin);
    }
    
    /**
     * Helper method to remove metadata from a zombie.
     */
    private void removeZombieMetadata(Zombie zombie) {
        if (zombie.hasMetadata("training_zombie")) {
            zombie.removeMetadata("training_zombie", plugin);
        }
        if (zombie.hasMetadata("remaining_totems")) {
             zombie.removeMetadata("remaining_totems", plugin);
        }
         if (zombie.hasMetadata("owner")) {
             zombie.removeMetadata("owner", plugin);
         }
         // Remove other metadata if needed...
    }
    
    /**
     * Helper method to cleanly remove a zombie and cancel its timers.
     * @param zombie The zombie entity.
     * @param ownerUuid The UUID of the owner.
     * @param forceRemove Should the entity be removed immediately?
     */
    private void removeZombie(Zombie zombie, UUID ownerUuid, boolean forceRemove) {
        activeZombies.remove(ownerUuid);
        
        // Cancel the name tag timer
        BukkitTask timerTask = activeTimers.remove(ownerUuid);
        if (timerTask != null) {
            timerTask.cancel();
        }
        
        if (zombie != null && zombie.isValid()) {
            removeZombieMetadata(zombie); // Ensure metadata is gone
            if (forceRemove) {
                zombie.remove(); // Remove immediately
                logger.debug("Force removed training zombie for {}", ownerUuid);
            }
        }
        
        // Send finished message to player if online
        Player owner = plugin.getServer().getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            owner.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.training-complete")); // Action Bar
            owner.playSound(owner.getLocation(), 
                Sound.valueOf(plugin.getConfigManager().getSoundEffect("zombie-death", "ENTITY_ZOMBIE_DEATH")), 1.0f, 1.0f);
        }
    }
} 
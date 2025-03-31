package com.takeda.ttrain.listeners;

import com.takeda.ttrain.TTrainPlugin;
import com.takeda.ttrain.config.ConfigManager.PlayerPreferences;
import com.takeda.ttrain.managers.GUIManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIListener implements Listener {
    private final Logger logger;
    private final TTrainPlugin plugin;
    private final Map<UUID, InputType> awaitingInput;
    private final MiniMessage miniMessage;

    public GUIListener(TTrainPlugin plugin) {
        this.plugin = plugin;
        this.logger = TTrainPlugin.getPluginLogger();
        this.awaitingInput = new ConcurrentHashMap<>();
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(plugin.getConfigManager().getGuiTitle())) return;
        
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        int slot = event.getRawSlot();
        Material clickedMaterial = clickedItem.getType();
        
        // Handle clicks based on configured slots and materials
        if (slot == GUIManager.getTotemSlot() && clickedMaterial == plugin.getConfigManager().getGUIMaterial("totem", Material.TOTEM_OF_UNDYING)) {
            handleTotemClick(player);
        } else if (slot == GUIManager.getDurationSlot() && clickedMaterial == plugin.getConfigManager().getGUIMaterial("duration", Material.CLOCK)) {
            handleDurationClick(player);
        } else if (slot == GUIManager.getSpawnSlot() && clickedMaterial == plugin.getConfigManager().getGUIMaterial("spawn", Material.ZOMBIE_HEAD)) {
            handleSpawnClick(player);
        } else if (slot == GUIManager.getSaveSlot() && clickedMaterial == plugin.getConfigManager().getGUIMaterial("save", Material.LIME_CONCRETE)) {
            handleSaveClick(player);
        } else if (slot == GUIManager.getResetSlot() && clickedMaterial == plugin.getConfigManager().getGUIMaterial("reset", Material.RED_CONCRETE)) {
            handleResetClick(player);
        } else if (slot == GUIManager.getExitSlot() && clickedMaterial == plugin.getConfigManager().getGUIMaterial("exit", Material.BARRIER)) {
            handleExitClick(player);
        }
    }

    private void handleTotemClick(Player player) {
        player.closeInventory();
        String message = plugin.getConfigManager().getRawMessage("messages.input.totem-count", "Enter totem count (1-{max}):");
        message = message.replace("{min}", String.valueOf(plugin.getConfigManager().getMinTotems()))
                .replace("{max}", String.valueOf(plugin.getConfigManager().getMaxTotems()));
        player.sendMessage(miniMessage.deserialize(message));
        awaitingInput.put(player.getUniqueId(), InputType.TOTEMS);
        player.playSound(player.getLocation(), 
            Sound.valueOf(plugin.getConfigManager().getSoundEffect("settings-change", "ENTITY_EXPERIENCE_ORB_PICKUP")), 1.0f, 1.0f);
    }

    private void handleDurationClick(Player player) {
        player.closeInventory();
        String message = plugin.getConfigManager().getRawMessage("messages.input.duration", "Enter duration in seconds (1-{max}):");
        message = message.replace("{min}", String.valueOf(plugin.getConfigManager().getMinTrainingDuration()))
                .replace("{max}", String.valueOf(plugin.getConfigManager().getMaxTrainingDuration()));
        player.sendMessage(miniMessage.deserialize(message));
        awaitingInput.put(player.getUniqueId(), InputType.DURATION);
        player.playSound(player.getLocation(), 
            Sound.valueOf(plugin.getConfigManager().getSoundEffect("settings-change", "ENTITY_EXPERIENCE_ORB_PICKUP")), 1.0f, 1.0f);
    }

    private void handleSpawnClick(Player player) {
        // Get current values from GUI manager first, fallback to preferences
        UUID playerId = player.getUniqueId();
        PlayerPreferences prefs = plugin.getConfigManager().getPlayerPreferences(playerId);
        
        Integer totems = plugin.getGuiManager().getTotemInput(playerId);
        if (totems == null) totems = prefs.getTotems();
        
        Integer duration = plugin.getGuiManager().getDurationInput(playerId);
        if (duration == null) duration = prefs.getDuration();
        
        if (plugin.getZombieManager().hasActiveZombie(playerId)) {
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.zombie-already-active"));
            player.playSound(player.getLocation(), 
                Sound.valueOf(plugin.getConfigManager().getSoundEffect("error", "ENTITY_VILLAGER_NO")), 1.0f, 1.0f);
            return;
        }
        
        // Check player permission
        if (!player.hasPermission("ttrain.spawn.gui")) {
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.no-permission"));
            player.playSound(player.getLocation(), 
                Sound.valueOf(plugin.getConfigManager().getSoundEffect("error", "ENTITY_VILLAGER_NO")), 1.0f, 1.0f);
            return;
        }
        
        player.closeInventory();
        plugin.getZombieManager().spawnTrainingZombie(player, totems, duration);
    }

    private void handleSaveClick(Player player) {
        PlayerPreferences prefs = plugin.getConfigManager().getPlayerPreferences(player.getUniqueId());
        
        // Update with any new inputs
        Integer newTotems = plugin.getGuiManager().getTotemInput(player.getUniqueId());
        Integer newDuration = plugin.getGuiManager().getDurationInput(player.getUniqueId());
        
        int totems = newTotems != null ? newTotems : prefs.getTotems();
        int duration = newDuration != null ? newDuration : prefs.getDuration();
        
        PlayerPreferences updatedPrefs = new PlayerPreferences(totems, duration);
        plugin.getConfigManager().savePlayerPreferences(player.getUniqueId(), updatedPrefs);
        
        player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.preferences-saved"));
        player.playSound(player.getLocation(), 
            Sound.valueOf(plugin.getConfigManager().getSoundEffect("success", "ENTITY_PLAYER_LEVELUP")), 1.0f, 1.0f);
        plugin.getGuiManager().clearInputs(player.getUniqueId());
        
        // Refresh the GUI to show updated settings
        plugin.getGuiManager().openTrainingGUI(player);
    }

    private void handleResetClick(Player player) {
        plugin.getConfigManager().resetPlayerPreferences(player.getUniqueId());
        player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.preferences-reset"));
        player.playSound(player.getLocation(), 
            Sound.valueOf(plugin.getConfigManager().getSoundEffect("success", "ENTITY_PLAYER_LEVELUP")), 1.0f, 1.0f);
        plugin.getGuiManager().clearInputs(player.getUniqueId());
        
        // Refresh the GUI with default values
        plugin.getGuiManager().openTrainingGUI(player);
    }

    private void handleExitClick(Player player) {
        player.closeInventory();
        player.playSound(player.getLocation(), 
            Sound.valueOf(plugin.getConfigManager().getSoundEffect("gui-close", "BLOCK_CHEST_CLOSE")), 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        InputType inputType = awaitingInput.remove(playerId);
        
        if (inputType == null) return;
        
        event.setCancelled(true);
        String input = event.getMessage();
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean success = false;
            try {
                logger.debug("Processing input '{}' from player {} for {}", input, player.getName(), inputType);
                switch (inputType) {
                    case TOTEMS -> success = plugin.getGuiManager().handleTotemInput(player, input);
                    case DURATION -> success = plugin.getGuiManager().handleDurationInput(player, input);
                }
            } catch (Exception e) {
                logger.error("Error handling player input: {}", e.getMessage(), e);
                player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.invalid-number"));
            }
            
            if (success) {
                reopenGUISafely(player);
            }
        });
    }
    
    /**
     * Safely reopens the GUI with multiple attempts if needed
     */
    private void reopenGUISafely(Player player) {
        if (!player.isOnline()) return;
        
        // First attempt after 5 ticks (250ms)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                logger.debug("First attempt to reopen GUI for {}", player.getName());
                plugin.getGuiManager().openTrainingGUI(player);
                
                // If first attempt fails, try again after 10 more ticks (500ms)
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && player.getOpenInventory() == null) {
                        logger.debug("Second attempt to reopen GUI for {}", player.getName());
                        plugin.getGuiManager().openTrainingGUI(player);
                    }
                }, 10L);
            }
        }, 5L);
    }

    private enum InputType {
        TOTEMS,
        DURATION
    }
} 
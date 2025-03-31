package com.takeda.ttrain.config;

import com.takeda.ttrain.TTrainPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private final Logger logger;
    private final TTrainPlugin plugin;
    private FileConfiguration config;
    private final Map<UUID, PlayerPreferences> playerPreferences;
    private final MiniMessage miniMessage;
    private final Map<String, String> messageCache;
    private boolean configNeedsSaving = false;

    public ConfigManager(TTrainPlugin plugin) {
        this.plugin = plugin;
        this.logger = TTrainPlugin.getPluginLogger();
        this.playerPreferences = new ConcurrentHashMap<>();
        this.miniMessage = MiniMessage.miniMessage();
        this.messageCache = new ConcurrentHashMap<>();
        loadConfig();
    }

    public void loadConfig() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                plugin.saveDefaultConfig();
                logger.info("Created default config.yml");
            }
            config = plugin.getConfig();
            configNeedsSaving = false; // Reset flag
            
            // Pre-cache messages for better performance
            cacheMessages();
            
            // Save config if any defaults were added during caching
            if (configNeedsSaving) {
                saveConfig();
            }
            
            // Load player preferences from config
            loadPlayerPreferences();
            
        } catch (Exception e) {
            logger.error("Error loading configuration", e);
        }
    }
    
    private void cacheMessages() {
        messageCache.clear();
        // Cache all message sections
        cacheMessagesFromSection("messages.input");
        cacheMessagesFromSection("messages.action-bar");
        cacheMessagesFromSection("messages.chat-errors");
        cacheMessagesFromSection("items"); // Includes name and lore
        cacheMessagesFromSection("gui"); // Include title
        
        // Add critical default values manually if they were missed
        ensureMessageExists("messages.chat-errors.player-only", "<#fb6340>✖ This command can only be used by players!</#fb6340>");
        ensureMessageExists("messages.chat-errors.invalid-usage", "<#fb6340>✖ Invalid usage! Use: <#adb5bd>/train [totems] [duration]</#adb5bd></#fb6340>");
        ensureMessageExists("messages.action-bar.no-permission", "<#fb6340>✖ You lack permission!</#fb6340>");
        ensureMessageExists("messages.action-bar.invalid-number", "<#fb6340>✖ Invalid number entered!</#fb6340>");
        ensureMessageExists("messages.action-bar.gui-error", "<#fb6340>✖ GUI Error! (Check console)</#fb6340>");
        ensureMessageExists("messages.action-bar.preferences-saved", "<#2dce89>✔ Preferences saved!</#2dce89>");
        ensureMessageExists("messages.action-bar.preferences-reset", "<#2dce89>✔ Settings reset to defaults!</#2dce89>");
        ensureMessageExists("messages.action-bar.totem-used", "<#f5365c>⚠ Zombie used totem! <white>{count}</white> left.</#f5365c>");
        ensureMessageExists("messages.action-bar.training-complete", "<#2dce89>✔ Training session ended!</#2dce89>");
        ensureMessageExists("messages.action-bar.zombie-spawned", "<#2dce89>✔ Zombie spawned: <white>{totems}</white> totems, <white>{duration}s</white> duration!</#2dce89>");
        ensureMessageExists("messages.action-bar.totem-count-set", "<#2dce89>✔ Totem count set to <white>{count}</white>!</#2dce89>");
        ensureMessageExists("messages.action-bar.duration-set", "<#2dce89>✔ Duration set to <white>{duration}s</white>!</#2dce89>");

        logger.info("Cached {} message entries from config.yml", messageCache.size());
    }
    
    /** 
     * Recursively caches messages from a config section.
     */
    private void cacheMessagesFromSection(String sectionPath) {
        ConfigurationSection section = config.getConfigurationSection(sectionPath);
        if (section == null) {
            logger.debug("Config section not found for caching: {}", sectionPath);
            return;
        }
        
        Set<String> keys = section.getKeys(true); // Get all keys recursively
        for (String key : keys) {
            String fullPath = sectionPath + "." + key;
            if (config.isString(fullPath)) {
                messageCache.put(fullPath, config.getString(fullPath));
            } else if (config.isList(fullPath)) {
                // Handle lore lists by joining with newline
                List<String> list = config.getStringList(fullPath);
                if (!list.isEmpty()) {
                     messageCache.put(fullPath, String.join("\n", list));
                }
            } // Ignore non-string/list values within the section
        }
    }

    /**
     * Ensures a message exists in the cache and config, adding a default if not.
     */
    private void ensureMessageExists(String key, String defaultValue) {
        if (!messageCache.containsKey(key)) {
            messageCache.put(key, defaultValue);
            config.set(key, defaultValue); // Set in config as well
            configNeedsSaving = true; // Mark config for saving
            logger.warn("Added missing default message key to config: {} (Will be saved)", key);
        }
    }

    public void saveConfig() {
        try {
            plugin.saveConfig();
            configNeedsSaving = false; // Reset flag after saving
            logger.debug("Saved config.yml");
        } catch (Exception e) {
            logger.error("Could not save config.yml", e);
        }
    }

    // --- Getters for Config Values --- 

    public int getMaxTotems() {
        return config.getInt("zombie.max-totems", 5);
    }
    
    public int getMinTotems() {
        return config.getInt("training.min-totems", 1);
    }

    public int getMaxTrainingDuration() {
        return config.getInt("training.max-duration", 300);
    }
    
    public int getMinTrainingDuration() {
        return config.getInt("training.min-duration", 10);
    }
    
    public int getDefaultTotems() {
        return config.getInt("training.default-totems", 1);
    }
    
    public int getDefaultDuration() {
        return config.getInt("training.default-duration", 60);
    }
    
    public boolean shouldEndSessionOnLastTotem() {
        return config.getBoolean("zombie.end-session-on-last-totem", true);
    }

    public Component getGuiTitle() {
        return getMessage("gui.title", "<gradient:#5e72e4:#825ee4><b>Crystal PvP Training</b></gradient>");
    }
    
    public int getGUISize() {
        int size = config.getInt("gui.size", 45);
        if (size <= 0 || size % 9 != 0) {
            logger.warn("Invalid GUI size ({}) in config.yml. Must be a multiple of 9. Using default 45.", size);
            return 45;
        }
        return size;
    }
    
    public int getGUISlot(String buttonName) {
        return config.getInt("gui.slots." + buttonName, -1);
    }
    
    public Material getGUIMaterial(String buttonName, Material defaultMaterial) {
        String materialName = config.getString("gui.button-materials." + buttonName);
        if (materialName == null) {
            logger.warn("Missing GUI button material for '{}' in config.yml. Using default: {}", buttonName, defaultMaterial.name());
            return defaultMaterial;
        }
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid GUI button material '{}' for '{}' in config.yml. Using default: {}", materialName, buttonName, defaultMaterial.name());
            return defaultMaterial;
        }
    }

    public String getSoundEffect(String key, String defaultValue) {
        return config.getString("sounds." + key, defaultValue);
    }

    /**
     * Gets the raw message string from the cache, or a default value if not found.
     * Does NOT trigger saving defaults.
     */
    public String getRawMessage(String key, String defaultValue) {
        String message = messageCache.get(key);
        if (message == null) {
            logger.warn("Missing message key '{}', using provided default.", key);
            return defaultValue;
        }
        return message;
    }
    
    /**
     * Gets a parsed Component message using MiniMessage.
     * Uses a default value if the key is missing.
     */
    public Component getMessage(String key, String defaultRawValue) {
        String rawMessage = getRawMessage(key, defaultRawValue);
        try {
            return miniMessage.deserialize(rawMessage);
        } catch (Exception e) {
            logger.error("Failed to parse MiniMessage for key '{}': {}. Falling back to plain text.", key, e.getMessage());
            // Fallback to plain text to avoid errors
            return Component.text(rawMessage); 
        }
    }
    
     /**
     * Gets a parsed Component message using MiniMessage.
     * Logs a warning and returns a plain text error message if the key is missing.
     */
    public Component getMessage(String key) {
        String rawMessage = messageCache.get(key);
        if (rawMessage == null) {
             logger.error("Missing required message key '{}'. Please check config.yml!", key);
             return Component.text("[Error: Missing message key: " + key + "]");
        }
        try {
            return miniMessage.deserialize(rawMessage);
        } catch (Exception e) {
            logger.error("Failed to parse MiniMessage for key '{}': {}. Falling back to plain text.", key, e.getMessage());
            // Fallback to plain text to avoid errors
            return Component.text(rawMessage); 
        }
    }
    
    /**
     * Gets a list of raw String lines for lore.
     * Returns an empty list if the key is missing or not a list/string.
     */
    public List<String> getRawLore(String key) {
        String rawLore = messageCache.get(key);
        if (rawLore == null || rawLore.isEmpty()) {
            if (!config.isList(key) && !config.isString(key)){
                 logger.warn("Missing or invalid lore key '{}'. Expected a list or string.", key);
            }
            return List.of(); // Return empty list
        }
        // Split the cached string (which might have come from a list joined by \n)
        return List.of(rawLore.split("\\n")); 
    }

    // --- Player Preferences Management --- 
    
    private void loadPlayerPreferences() {
        playerPreferences.clear();
        ConfigurationSection prefsSection = config.getConfigurationSection("player-preferences");
        if (prefsSection != null) {
            prefsSection.getKeys(false).forEach(key -> {
                try {
                    UUID uuid = UUID.fromString(key);
                    String path = "player-preferences." + key;
                    
                    int totems = config.getInt(path + ".totems", getDefaultTotems());
                    int duration = config.getInt(path + ".duration", getDefaultDuration());
                    
                    playerPreferences.put(uuid, new PlayerPreferences(totems, duration));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid UUID in player preferences: {}", key);
                } catch (Exception e) {
                    logger.warn("Error loading preferences for UUID: {}", key, e);
                }
            });
            logger.info("Loaded {} player preferences", playerPreferences.size());
        }
    }

    public synchronized void savePlayerPreferences(UUID uuid, PlayerPreferences preferences) {
        playerPreferences.put(uuid, preferences);
        // Save to config
        String path = "player-preferences." + uuid.toString();
        config.set(path + ".totems", preferences.getTotems());
        config.set(path + ".duration", preferences.getDuration());
        saveConfig(); // Save immediately after changing preferences
        logger.debug("Saved preferences for player {}", uuid);
    }

    public PlayerPreferences getPlayerPreferences(UUID uuid) {
        // Return existing prefs or create new default ones (doesn't load from config here)
        return playerPreferences.computeIfAbsent(uuid, k -> {
             logger.debug("No preferences found for {}, using defaults.", uuid);
             return new PlayerPreferences(getDefaultTotems(), getDefaultDuration());
        });
    }

    public void resetPlayerPreferences(UUID uuid) {
        playerPreferences.remove(uuid);
        config.set("player-preferences." + uuid.toString(), null); // Remove from config
        saveConfig(); // Save immediately
        logger.debug("Reset preferences for player {}", uuid);
    }

    // Static class to hold player preferences
    public static class PlayerPreferences {
        private final int totems;
        private final int duration;

        public PlayerPreferences(int totems, int duration) {
            this.totems = totems;
            this.duration = duration;
        }

        public int getTotems() {
            return totems;
        }

        public int getDuration() {
            return duration;
        }
    }
} 
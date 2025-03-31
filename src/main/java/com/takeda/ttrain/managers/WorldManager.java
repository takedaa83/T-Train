package com.takeda.ttrain.managers;

import com.takeda.ttrain.TTrainPlugin;
import org.bukkit.World;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {
    private final Logger logger;
    private final TTrainPlugin plugin;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;
    private final Map<String, String> worldAliases;

    public WorldManager(TTrainPlugin plugin) {
        this.plugin = plugin;
        this.logger = TTrainPlugin.getPluginLogger();
        this.enabledWorlds = new HashSet<>();
        this.disabledWorlds = new HashSet<>();
        this.worldAliases = new ConcurrentHashMap<>();
        loadWorldConfig();
    }

    public void loadWorldConfig() {
        try {
            // Clear existing configurations
            enabledWorlds.clear();
            disabledWorlds.clear();
            worldAliases.clear();

            // Load enabled worlds
            enabledWorlds.addAll(plugin.getConfig().getStringList("worlds.enabled"));
            logger.info("Loaded {} enabled worlds", enabledWorlds.size());

            // Load disabled worlds
            disabledWorlds.addAll(plugin.getConfig().getStringList("worlds.disabled"));
            logger.info("Loaded {} disabled worlds", disabledWorlds.size());

            // Load world aliases
            if (plugin.getConfig().contains("worlds.aliases")) {
                plugin.getConfig().getConfigurationSection("worlds.aliases").getKeys(false)
                    .forEach(alias -> worldAliases.put(alias, 
                        plugin.getConfig().getString("worlds.aliases." + alias)));
                logger.info("Loaded {} world aliases", worldAliases.size());
            }

            // Validate worlds
            validateWorlds();
        } catch (Exception e) {
            logger.error("Error loading world configuration", e);
        }
    }

    private void validateWorlds() {
        Set<String> invalidWorlds = new HashSet<>();

        // Check enabled worlds
        for (String worldName : enabledWorlds) {
            if (!isValidWorld(worldName)) {
                invalidWorlds.add(worldName);
                logger.warn("Invalid enabled world: {}", worldName);
            }
        }

        // Check disabled worlds
        for (String worldName : disabledWorlds) {
            if (!isValidWorld(worldName)) {
                invalidWorlds.add(worldName);
                logger.warn("Invalid disabled world: {}", worldName);
            }
        }

        // Check aliases
        for (Map.Entry<String, String> entry : worldAliases.entrySet()) {
            if (!isValidWorld(entry.getValue())) {
                invalidWorlds.add(entry.getValue());
                logger.warn("Invalid world alias target: {} -> {}", entry.getKey(), entry.getValue());
            }
        }

        // Remove invalid worlds
        invalidWorlds.forEach(worldName -> {
            enabledWorlds.remove(worldName);
            disabledWorlds.remove(worldName);
            worldAliases.values().remove(worldName);
        });

        if (!invalidWorlds.isEmpty()) {
            logger.warn("Removed {} invalid worlds from configuration", invalidWorlds.size());
        }
    }

    private boolean isValidWorld(String worldName) {
        return plugin.getServer().getWorld(worldName) != null;
    }

    public boolean isWorldEnabled(World world) {
        if (world == null) {
            logger.warn("Attempted to check if null world is enabled");
            return false;
        }

        String worldName = world.getName();
        String resolvedName = worldAliases.getOrDefault(worldName, worldName);

        if (disabledWorlds.contains(resolvedName)) {
            logger.debug("World {} is disabled", worldName);
            return false;
        }

        boolean enabled = enabledWorlds.contains(resolvedName);
        logger.debug("World {} is {}", worldName, enabled ? "enabled" : "disabled");
        return enabled;
    }

    public String resolveWorldName(String worldName) {
        return worldAliases.getOrDefault(worldName, worldName);
    }

    public Set<String> getEnabledWorlds() {
        return new HashSet<>(enabledWorlds);
    }

    public Set<String> getDisabledWorlds() {
        return new HashSet<>(disabledWorlds);
    }

    public Map<String, String> getWorldAliases() {
        return new ConcurrentHashMap<>(worldAliases);
    }
} 
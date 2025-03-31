package com.takeda.ttrain;

import com.takeda.ttrain.commands.TrainCommand;
import com.takeda.ttrain.config.ConfigManager;
import com.takeda.ttrain.managers.ZombieManager;
import com.takeda.ttrain.managers.GUIManager;
import com.takeda.ttrain.managers.WorldManager;
import com.takeda.ttrain.listeners.GUIListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TTrainPlugin extends JavaPlugin {
    private static TTrainPlugin instance;
    private static Logger pluginLogger;
    private ConfigManager configManager;
    private ZombieManager zombieManager;
    private GUIManager guiManager;
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        instance = this;
        pluginLogger = LoggerFactory.getLogger("T-Train");
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.zombieManager = new ZombieManager(this);
        this.guiManager = new GUIManager(this);
        this.worldManager = new WorldManager(this);
        
        // Register totem handlers for zombies
        this.zombieManager.registerTotemHandlers();
        
        // Register commands
        getCommand("train").setExecutor(new TrainCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        
        // Save default config
        saveDefaultConfig();
        
        pluginLogger.info("T-Train has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Clean up any active training sessions
        if (zombieManager != null) {
            zombieManager.cleanupAllZombies();
        }
        
        pluginLogger.info("T-Train has been disabled!");
    }

    public static TTrainPlugin getInstance() {
        return instance;
    }

    public static Logger getPluginLogger() {
        return pluginLogger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ZombieManager getZombieManager() {
        return zombieManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }
} 
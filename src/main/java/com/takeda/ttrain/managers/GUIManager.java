package com.takeda.ttrain.managers;

import com.takeda.ttrain.TTrainPlugin;
import com.takeda.ttrain.config.ConfigManager.PlayerPreferences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIManager {
    private final Logger logger;
    private final TTrainPlugin plugin;
    private final Map<UUID, Integer> totemInputs;
    private final Map<UUID, Integer> durationInputs;
    private final MiniMessage miniMessage;

    // Slot storage (loaded from config)
    private final Map<String, Integer> buttonSlots = new ConcurrentHashMap<>();

    public GUIManager(TTrainPlugin plugin) {
        this.plugin = plugin;
        this.logger = TTrainPlugin.getPluginLogger();
        this.totemInputs = new ConcurrentHashMap<>();
        this.durationInputs = new ConcurrentHashMap<>();
        this.miniMessage = MiniMessage.miniMessage();
        loadButtonSlots(); // Load slots on init
    }

    // Load and validate slots from config
    private void loadButtonSlots() {
        buttonSlots.clear();
        String[] buttonKeys = {"totem", "duration", "spawn", "save", "reset", "exit"};
        for (String key : buttonKeys) {
            int slot = plugin.getConfigManager().getGUISlot(key);
            if (slot >= 0) { // Basic validation (non-negative)
                buttonSlots.put(key, slot);
            } else {
                logger.error("Invalid or missing GUI slot for '{}' in config.yml. Button will not be added.", key);
            }
        }
    }

    public void openTrainingGUI(Player player) {
        try {
            if (!Bukkit.isPrimaryThread()) {
                Bukkit.getScheduler().runTask(plugin, () -> openTrainingGUI(player));
                return;
            }
            
            int size = plugin.getConfigManager().getGUISize();
            Component title = plugin.getConfigManager().getGuiTitle();
            Inventory gui = Bukkit.createInventory(null, size, title);
            
            createBorder(gui);
            
            UUID playerId = player.getUniqueId();
            PlayerPreferences prefs = plugin.getConfigManager().getPlayerPreferences(playerId);
            int currentTotems = totemInputs.getOrDefault(playerId, prefs.getTotems());
            int currentDuration = durationInputs.getOrDefault(playerId, prefs.getDuration());
            
            // Add buttons based on configured slots
            buttonSlots.forEach((key, slot) -> {
                ItemStack item = switch (key) {
                    case "totem" -> createTotemItem(currentTotems);
                    case "duration" -> createDurationItem(currentDuration);
                    case "spawn" -> createSpawnItem(currentTotems, currentDuration);
                    case "save" -> createSaveItem();
                    case "reset" -> createResetItem();
                    case "exit" -> createExitItem();
                    default -> null;
                };
                if (item != null) {
                    if (slot < size) { // Ensure slot is within bounds
                         gui.setItem(slot, item);
                    } else {
                        logger.warn("Configured slot {} for button '{}' is outside the GUI size ({}). Skipping.", slot, key, size);
                    }
                }
            });
            
            player.openInventory(gui);
            player.playSound(player.getLocation(), 
                Sound.valueOf(plugin.getConfigManager().getSoundEffect("gui-open", "BLOCK_CHEST_OPEN")), 1.0f, 1.0f);
            
            logger.debug("Opened training GUI for player {}", player.getName());
        } catch (Exception e) {
            logger.error("Error opening training GUI for player {}: {}", player.getName(), e.getMessage(), e);
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.gui-error"));
        }
    }

    private ItemStack createItem(String buttonKey, Material defaultMaterial, String nameKey, String loreKey, Map<String, String> placeholders) {
        Material material = plugin.getConfigManager().getGUIMaterial(buttonKey, defaultMaterial);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Set Name
        String name = plugin.getConfigManager().getRawMessage(nameKey, "Error: Missing Name");
        meta.displayName(miniMessage.deserialize(name));

        // Set Lore with Placeholders
        List<String> rawLore = plugin.getConfigManager().getRawLore(loreKey);
        List<Component> processedLore = new ArrayList<>();
        for (String rawLine : rawLore) {
            String lineWithPlaceholders = rawLine;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    lineWithPlaceholders = lineWithPlaceholders.replace(entry.getKey(), entry.getValue());
                }
            }
            try {
                processedLore.add(miniMessage.deserialize(lineWithPlaceholders));
            } catch (Exception e) {
                logger.error("Failed to parse MiniMessage lore line for key '{}': {}. Raw Line: '{}'. Skipping line.", loreKey, e.getMessage(), rawLine);
                processedLore.add(Component.text(lineWithPlaceholders));
            }
        }
        meta.lore(processedLore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTotemItem(int totems) {
        int min = plugin.getConfigManager().getMinTotems();
        int max = plugin.getConfigManager().getMaxTotems();
        return createItem("totem", Material.TOTEM_OF_UNDYING, "items.totem.name", "items.totem.lore",
                Map.of("{count}", String.valueOf(totems), "{min}", String.valueOf(min), "{max}", String.valueOf(max)));
    }
    
    private ItemStack createDurationItem(int duration) {
        int min = plugin.getConfigManager().getMinTrainingDuration();
        int max = plugin.getConfigManager().getMaxTrainingDuration();
        return createItem("duration", Material.CLOCK, "items.duration.name", "items.duration.lore",
                Map.of("{duration}", String.valueOf(duration), "{min}", String.valueOf(min), "{max}", String.valueOf(max)));
    }
    
    private ItemStack createSpawnItem(int totems, int duration) {
        return createItem("spawn", Material.ZOMBIE_HEAD, "items.spawn.name", "items.spawn.lore",
                Map.of("{totems}", String.valueOf(totems), "{duration}", String.valueOf(duration)));
    }
    
    private ItemStack createSaveItem() {
        return createItem("save", Material.LIME_CONCRETE, "items.save.name", "items.save.lore", null);
    }
    
    private ItemStack createResetItem() {
        int defaultTotems = plugin.getConfigManager().getDefaultTotems();
        int defaultDuration = plugin.getConfigManager().getDefaultDuration();
        return createItem("reset", Material.RED_CONCRETE, "items.reset.name", "items.reset.lore",
                Map.of("{default-totems}", String.valueOf(defaultTotems), "{default-duration}", String.valueOf(defaultDuration)));
    }
    
    private ItemStack createExitItem() {
        return createItem("exit", Material.BARRIER, "items.exit.name", "items.exit.lore", null);
    }

    private void createBorder(Inventory inventory) {
        Material borderMaterial = plugin.getConfigManager().getGUIMaterial("border", Material.GRAY_STAINED_GLASS_PANE);
        Material specialMaterial = plugin.getConfigManager().getGUIMaterial("special", Material.PURPLE_STAINED_GLASS_PANE);
        
        ItemStack borderItem = new ItemStack(borderMaterial);
        ItemMeta borderMeta = borderItem.getItemMeta();
        if (borderMeta != null) {
             borderMeta.displayName(Component.text(" "));
             borderItem.setItemMeta(borderMeta);
        }
        
        ItemStack specialItem = new ItemStack(specialMaterial);
        ItemMeta specialMeta = specialItem.getItemMeta();
         if (specialMeta != null) {
             specialMeta.displayName(Component.text(" "));
             specialItem.setItemMeta(specialMeta);
         }
        
        int size = inventory.getSize();
        int rows = size / 9;
        
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                 if (inventory.getItem(i) == null) { 
                     // Check if this slot is used by a configured button
                     boolean isButtonSlot = buttonSlots.containsValue(i);
                     if (!isButtonSlot) {
                         inventory.setItem(i, borderItem.clone());
                     }
                 }
            }
        }
        
        // Example: corners with special item, only if not used by buttons
        int[] cornerSlots = {0, 8, size - 9, size - 1};
        for (int cornerSlot : cornerSlots) {
            if (!buttonSlots.containsValue(cornerSlot) && inventory.getItem(cornerSlot) == null) {
                 inventory.setItem(cornerSlot, specialItem.clone());
            }
        }
    }
    
    // --- Slot Getters (using loaded map) --- 
    public static int getTotemSlot() { return getInstance().getSlotOrDefault("totem", 20); }
    public static int getDurationSlot() { return getInstance().getSlotOrDefault("duration", 24); }
    public static int getSpawnSlot() { return getInstance().getSlotOrDefault("spawn", 22); }
    public static int getSaveSlot() { return getInstance().getSlotOrDefault("save", 38); }
    public static int getResetSlot() { return getInstance().getSlotOrDefault("reset", 40); }
    public static int getExitSlot() { return getInstance().getSlotOrDefault("exit", 42); }

    // Helper to get slot from map or return default
    private int getSlotOrDefault(String key, int defaultSlot) {
        return buttonSlots.getOrDefault(key, defaultSlot);
    }
    
    // Need instance for static getters
    private static GUIManager getInstance() {
        return TTrainPlugin.getInstance().getGuiManager();
    }
    
    // --- Input Handling & Validation --- 

    public Integer getTotemInput(UUID playerId) {
        return totemInputs.get(playerId);
    }
    
    public Integer getDurationInput(UUID playerId) {
        return durationInputs.get(playerId);
    }
    
    public void clearInputs(UUID playerId) {
        totemInputs.remove(playerId);
        durationInputs.remove(playerId);
    }
    
    public boolean handleTotemInput(Player player, String input) {
        try {
            int value = Integer.parseInt(input);
            int minTotems = plugin.getConfigManager().getMinTotems();
            int maxTotems = plugin.getConfigManager().getMaxTotems();
            
            if (value < minTotems || value > maxTotems) {
                player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.invalid-totem-count")
                    .replaceText(builder -> builder.matchLiteral("{min}").replacement(String.valueOf(minTotems)))
                    .replaceText(builder -> builder.matchLiteral("{max}").replacement(String.valueOf(maxTotems))));
                return true; 
            }
            
            totemInputs.put(player.getUniqueId(), value);
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.totem-count-set")
                .replaceText(builder -> builder.matchLiteral("{count}").replacement(String.valueOf(value))));
            player.playSound(player.getLocation(), 
                Sound.valueOf(plugin.getConfigManager().getSoundEffect("settings-change", "ENTITY_EXPERIENCE_ORB_PICKUP")), 1.0f, 1.0f);
            return true; 
        } catch (NumberFormatException e) {
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.invalid-number"));
            return false; 
        }
    }
    
    public boolean handleDurationInput(Player player, String input) {
        try {
            int value = Integer.parseInt(input);
            int minDuration = plugin.getConfigManager().getMinTrainingDuration();
            int maxDuration = plugin.getConfigManager().getMaxTrainingDuration();
            
            if (value < minDuration || value > maxDuration) {
                player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.invalid-duration")
                     .replaceText(builder -> builder.matchLiteral("{min}").replacement(String.valueOf(minDuration)))
                     .replaceText(builder -> builder.matchLiteral("{max}").replacement(String.valueOf(maxDuration))));
                return true; 
            }
            
            durationInputs.put(player.getUniqueId(), value);
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.duration-set")
                .replaceText(builder -> builder.matchLiteral("{duration}").replacement(String.valueOf(value))));
            player.playSound(player.getLocation(), 
                Sound.valueOf(plugin.getConfigManager().getSoundEffect("settings-change", "ENTITY_EXPERIENCE_ORB_PICKUP")), 1.0f, 1.0f);
            return true;
        } catch (NumberFormatException e) {
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.invalid-number"));
            return false; 
        }
    }
} 
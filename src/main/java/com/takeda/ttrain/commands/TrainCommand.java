package com.takeda.ttrain.commands;

import com.takeda.ttrain.TTrainPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrainCommand implements CommandExecutor, TabCompleter {
    private static final Logger logger = LoggerFactory.getLogger(TrainCommand.class);
    private final TTrainPlugin plugin;

    public TrainCommand(TTrainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("messages.chat-errors.player-only"));
            return true;
        }
        
        // Check permission
        if (!player.hasPermission("ttrain.use")) {
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.no-permission"));
            return true;
        }
        
        // Handle different command formats
        if (args.length == 0) {
            // Open GUI with a slight delay for safety
            logger.info("Player {} is opening the training GUI", player.getName());
            
            // Wait 2 ticks to ensure previous inventories are closed properly
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getGuiManager().openTrainingGUI(player);
                    
                    // Add a backup open attempt if needed
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory() == null) {
                            logger.debug("Backup attempt to open GUI for {}", player.getName());
                            plugin.getGuiManager().openTrainingGUI(player);
                        }
                    }, 5L);
                }
            }, 2L);
            
            return true;
        }
        
        // Direct spawning with parameters
        if (!player.hasPermission("ttrain.spawn.command")) {
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.no-permission"));
            return true;
        }
        
        if (plugin.getZombieManager().hasActiveZombie(player.getUniqueId())) {
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.zombie-already-active"));
            return true;
        }
        
        try {
            int totems = plugin.getConfigManager().getDefaultTotems();
            int duration = plugin.getConfigManager().getDefaultDuration();
            
            if (args.length >= 1) {
                totems = Integer.parseInt(args[0]);
                if (totems < 1 || totems > plugin.getConfigManager().getMaxTotems()) {
                    player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.invalid-totem-count")
                        .replaceText(builder -> builder
                            .matchLiteral("{max}")
                            .replacement(String.valueOf(plugin.getConfigManager().getMaxTotems()))));
                    return true;
                }
            }
            
            if (args.length >= 2) {
                duration = Integer.parseInt(args[1]);
                int minDuration = plugin.getConfigManager().getMinTrainingDuration();
                int maxDuration = plugin.getConfigManager().getMaxTrainingDuration();
                if (duration < minDuration || duration > maxDuration) {
                    player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.invalid-duration")
                         .replaceText(builder -> builder.matchLiteral("{min}").replacement(String.valueOf(minDuration)))
                         .replaceText(builder -> builder.matchLiteral("{max}").replacement(String.valueOf(maxDuration))));
                    return true;
                }
            }
            
            logger.info("Player {} spawned a training zombie with command: {} totems, {} seconds", 
                player.getName(), totems, duration);
                
            plugin.getZombieManager().spawnTrainingZombie(player, totems, duration);
            return true;
        } catch (NumberFormatException e) {
            player.sendActionBar(plugin.getConfigManager().getMessage("messages.action-bar.invalid-number"));
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        
        if (!player.hasPermission("ttrain.spawn.command")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (int i = 1; i <= Math.min(plugin.getConfigManager().getMaxTotems(), 10); i++) {
                completions.add(String.valueOf(i));
            }
            return completions;
        }

        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            for (int i = 15; i <= Math.min(plugin.getConfigManager().getMaxTrainingDuration(), 300); i += 15) {
                completions.add(String.valueOf(i));
            }
            return completions;
        }

        return Collections.emptyList();
    }
} 
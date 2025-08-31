package com.unlockthecode.invitecode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class InviteCode extends JavaPlugin implements Listener, TabExecutor {

    private Set<UUID> verifiedPlayers = new HashSet<>();
    private File verifiedFile;
    private FileConfiguration verifiedConfig;

    private List<String> inviteCodes;
    private int timeLimit;
    private String kickMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        inviteCodes = getConfig().getStringList("invite-codes");
        timeLimit = getConfig().getInt("time-limit", 60);
        kickMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("kick-message", "&cYou must use /join <code> to play!"));

        loadVerified();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("join").setExecutor(this);
    }

    @Override
    public void onDisable() {
        saveVerified();
    }

    private void loadVerified() {
        verifiedFile = new File(getDataFolder(), "verified.yml");
        if (!verifiedFile.exists()) {
            try {
                verifiedFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        verifiedConfig = YamlConfiguration.loadConfiguration(verifiedFile);
        if (verifiedConfig.contains("verified")) {
            for (String uuid : verifiedConfig.getStringList("verified")) {
                verifiedPlayers.add(UUID.fromString(uuid));
            }
        }
    }

    private void saveVerified() {
        verifiedConfig.set("verified", verifiedPlayers.stream().map(UUID::toString).toList());
        try {
            verifiedConfig.save(verifiedFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!verifiedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must verify using /join <code> within " + timeLimit + " seconds.");

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!verifiedPlayers.contains(player.getUniqueId()) && player.isOnline()) {
                        player.kickPlayer(kickMessage);
                    }
                }
            }.runTaskLater(this, timeLimit * 20L);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /join <code>");
            return true;
        }

        String code = args[0];
        if (inviteCodes.contains(code)) {
            verifiedPlayers.add(player.getUniqueId());
            saveVerified();
            player.sendMessage(ChatColor.GREEN + "You have been verified! Welcome.");
        } else {
            player.sendMessage(ChatColor.RED + "Invalid invite code!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return inviteCodes;
        }
        return Collections.emptyList();
    }
}

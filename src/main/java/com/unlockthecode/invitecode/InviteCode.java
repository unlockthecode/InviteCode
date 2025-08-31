package com.unlockthecode.invitecode;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

public class InviteCode extends JavaPlugin implements Listener, TabExecutor {

    private Set<UUID> verifiedPlayers = new HashSet<>();
    private File verifiedFile;
    private FileConfiguration verifiedConfig;

    private List<String> inviteCodes;
    private int timeLimit;
    private String kickMessage;

    @Override
    public void onEnable() {
        // Make sure plugin folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Load verified players
        loadVerified();

        // Load config
        saveDefaultConfig();
        inviteCodes = getConfig().getStringList("invite-codes");
        timeLimit = getConfig().getInt("time-limit", 60);
        kickMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("kick-message", "&cYou must use /join <code> to play!"));

        // Register events and commands
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("join").setExecutor(this);
    }

    @Override
    public void onDisable() {
        saveVerified();
    }

    private void loadVerified() {
        verifiedFile = new File(getDataFolder(), "verified.yml");

        // Create file if it doesn’t exist
        if (!verifiedFile.exists()) {
            try {
                verifiedFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Load configuration
        verifiedConfig = YamlConfiguration.loadConfiguration(verifiedFile);

        // Load verified UUIDs
        if (verifiedConfig.contains("verified")) {
            for (String uuid : verifiedConfig.getStringList("verified")) {
                try {
                    verifiedPlayers.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveVerified() {
        if (verifiedConfig == null) {
            return; // prevent NullPointerException
        }
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

            String punishment = getConfig().getString("punishment", "kick").toLowerCase();

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!verifiedPlayers.contains(player.getUniqueId()) && player.isOnline()) {
                        switch (punishment) {
                            case "kick" ->
                                player.kickPlayer(kickMessage);
                            case "ban" -> {
                                Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                                        .addBan(player.getName(), getConfig().getString("ban-message"), null, null);
                                player.kickPlayer(getConfig().getString("ban-message"));
                            }
                            default ->
                                player.kickPlayer(kickMessage);
                        }
                    }
                }
            }.runTaskLater(this, timeLimit * 20L);

        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

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
}

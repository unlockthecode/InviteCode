package com.unlockthecode.invitecode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class InviteCode extends JavaPlugin implements Listener, TabExecutor {

    private Set<UUID> verifiedPlayers = new HashSet<>();
    private Map<UUID, Integer> failedAttempts = new HashMap<>();

    private File verifiedFile;
    private FileConfiguration verifiedConfig;

    private List<String> inviteCodes;
    private int timeLimit;
    private String kickMessage;
    private String banMessage;
    private int maxAttempts;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        loadVerified();

        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("join").setExecutor(this);
    }

    @Override
    public void onDisable() {
        saveVerified();
    }

    private void loadConfigValues() {
        inviteCodes = getConfig().getStringList("invite-codes");
        timeLimit = getConfig().getInt("time-limit", 60);
        maxAttempts = getConfig().getInt("max-attempts", 3);
        kickMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("kick-message", "&cYou must use /join <code> to play!"));
        banMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("ban-message", "&cYou are banned for not verifying with /join <code>."));
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
                try {
                    verifiedPlayers.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (verifiedConfig.contains("failed-attempts")) {
            for (String key : verifiedConfig.getConfigurationSection("failed-attempts").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int attempts = verifiedConfig.getInt("failed-attempts." + key, 0);
                    failedAttempts.put(uuid, attempts);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveVerified() {
        if (verifiedConfig == null) {
            return;
        }

        verifiedConfig.set("verified", verifiedPlayers.stream().map(UUID::toString).toList());

        for (UUID uuid : failedAttempts.keySet()) {
            verifiedConfig.set("failed-attempts." + uuid.toString(), failedAttempts.get(uuid));
        }

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
                                        .addBan(player.getName(), banMessage, null, null);
                                player.kickPlayer(banMessage);
                            }
                            case "ban-ip" -> {
                                String ip = player.getAddress().getAddress().getHostAddress();
                                Bukkit.getBanList(org.bukkit.BanList.Type.IP)
                                        .addBan(ip, banMessage, null, null);
                                player.kickPlayer(banMessage);
                            }
                            default ->
                                player.kickPlayer(kickMessage);
                        }
                    }
                }
            }.runTaskLater(this, timeLimit * 20L);
        }
    }

    // Prevent chat for unverified players
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!verifiedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot chat until you verify with /join <code>.");
        }
    }

    // Prevent movement for unverified players
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!verifiedPlayers.contains(player.getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle /join reload
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.isOp() && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            reloadConfig();
            loadConfigValues();
            sender.sendMessage(ChatColor.GREEN + "[InviteCode] Config reloaded successfully!");
            return true;
        }

        // Must be player for /join <code>
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
            failedAttempts.remove(player.getUniqueId());
            saveVerified();
            player.sendMessage(ChatColor.GREEN + "You have been verified! Welcome.");
        } else {
            int attempts = failedAttempts.getOrDefault(player.getUniqueId(), 0) + 1;
            failedAttempts.put(player.getUniqueId(), attempts);
            saveVerified();

            if (attempts >= maxAttempts) {
                String punishment = getConfig().getString("punishment", "kick").toLowerCase();

                switch (punishment) {
                    case "kick" ->
                        player.kickPlayer(kickMessage);
                    case "ban" -> {
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                                .addBan(player.getName(), banMessage, null, null);
                        player.kickPlayer(banMessage);
                    }
                    case "ban-ip" -> {
                        String ip = player.getAddress().getAddress().getHostAddress();
                        Bukkit.getBanList(org.bukkit.BanList.Type.IP)
                                .addBan(ip, banMessage, null, null);
                        player.kickPlayer(banMessage);
                    }
                    default ->
                        player.kickPlayer(kickMessage);
                }
            } else {
                player.sendMessage(ChatColor.RED + "Invalid invite code! Attempts: "
                        + attempts + "/" + maxAttempts);
            }
        }
        return true;
    }
}

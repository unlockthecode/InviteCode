package com.unlockthecode.invitecode;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

        saveDefaultConfig();
        loadConfigValues();

        loadVerified();

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("join"), "Command 'join' not found in plugin.yml")
                .setExecutor(this);
        getCommand("join").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveVerified();
    }

    private void loadConfigValues() {
        FileConfiguration cfg = getConfig();
        inviteCodes = cfg.getStringList("invite-codes");
        timeLimit = cfg.getInt("time-limit", 60);

        maxAttempts = cfg.getInt("max-attempts", 3);
        if (maxAttempts <= 0) {
            maxAttempts = 3;
            getLogger().warning("max-attempts was set <= 0, defaulting to 3.");
        }

        kickMessage = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("kick-message", "&cYou must use /join <code> to play!"));
        banMessage = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("ban-message", "&cYou are banned for too many failed /join attempts."));
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

        verifiedPlayers.clear();
        if (verifiedConfig.contains("verified")) {
            for (String uuid : verifiedConfig.getStringList("verified")) {
                try {
                    verifiedPlayers.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        failedAttempts.clear();
        if (verifiedConfig.contains("failedAttempts")) {
            for (String key : verifiedConfig.getConfigurationSection("failedAttempts").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int attempts = verifiedConfig.getInt("failedAttempts." + key, 0);
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

        verifiedConfig.set("verified", verifiedPlayers.stream()
                .map(UUID::toString)
                .collect(Collectors.toList()));

        Map<String, Integer> attemptMap = failedAttempts.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        Map.Entry::getValue
                ));
        verifiedConfig.createSection("failedAttempts", attemptMap);

        try {
            verifiedConfig.save(verifiedFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 🟢 Player Join
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // ✅ Reset attempts if they were previously banned but are now allowed back
        if (failedAttempts.containsKey(uuid) && !player.isBanned()) {
            failedAttempts.remove(uuid);
            saveVerified();
            getLogger().info("Reset failed attempts for " + player.getName() + " (unbanned join).");
        }

        if (!verifiedPlayers.contains(uuid)) {
            player.sendMessage(ChatColor.RED + "You must verify using /join <code> within " + timeLimit + " seconds.");

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!verifiedPlayers.contains(uuid) && player.isOnline()) {
                        player.kickPlayer(kickMessage); // timeout → just kick
                    }
                }
            }.runTaskLater(this, timeLimit * 20L);
        }
    }

    // 🟢 Block movement
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!verifiedPlayers.contains(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    // 🟢 Block chat
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!verifiedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(ChatColor.RED + "You must verify first with /join <code>.");
            event.setCancelled(true);
        }
    }

    // 🟢 Command logic
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Console commands
        if (!(sender instanceof Player player)) {
            if (args.length == 1) {
                switch (args[0].toLowerCase()) {
                    case "reload" -> {
                        reloadConfig();
                        loadConfigValues();
                        sender.sendMessage(ChatColor.GREEN + "Config reloaded.");
                    }
                    case "reloadverified" -> {
                        loadVerified();
                        sender.sendMessage(ChatColor.GREEN + "Verified list reloaded.");
                    }
                    case "resetattempts" -> {
                        failedAttempts.clear();
                        saveVerified();
                        sender.sendMessage(ChatColor.GREEN + "Failed attempts cleared.");
                    }
                    default ->
                        sender.sendMessage(ChatColor.RED + "Console: /join <reload|reloadverified|resetattempts>");
                }
            }
            return true;
        }

        // Player path
        UUID uuid = player.getUniqueId();

        if (verifiedPlayers.contains(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "You are already verified!");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /join <code>");
            return true;
        }

        String code = args[0];
        if (inviteCodes.contains(code)) {
            verifiedPlayers.add(uuid);
            failedAttempts.remove(uuid);
            saveVerified();
            player.sendMessage(ChatColor.GREEN + "You have been verified! Welcome.");
        } else {
            int attempts = failedAttempts.getOrDefault(uuid, 0) + 1;
            failedAttempts.put(uuid, attempts);
            saveVerified();

            if (attempts >= maxAttempts) {
                String punishment = getConfig().getString("punishment", "kick").toLowerCase();
                punish(player, punishment, kickMessage, banMessage);
            } else {
                player.sendMessage(ChatColor.RED + "Invalid code! Attempts: " + attempts + "/" + maxAttempts);
            }
        }

        return true;
    }

    private void punish(Player player, String punishment, String kickMsg, String banMsg) {
        switch (punishment) {
            case "kick" ->
                player.kickPlayer(kickMsg);
            case "ban" -> {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                        .addBan(player.getName(), banMsg, null, null);
                player.kickPlayer(banMsg);
            }
            case "ban-ip" -> {
                String ip = player.getAddress().getAddress().getHostAddress();
                Bukkit.getBanList(org.bukkit.BanList.Type.IP)
                        .addBan(ip, banMsg, null, null);
                player.kickPlayer(banMsg);
            }
            default ->
                player.kickPlayer(kickMsg);
        }
        saveVerified(); // persist after punishment
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            if (args.length == 1) {
                return Arrays.asList("reload", "reloadverified", "resetattempts");
            }
        }
        return Collections.emptyList();
    }
}

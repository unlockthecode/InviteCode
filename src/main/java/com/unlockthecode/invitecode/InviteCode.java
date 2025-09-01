package com.unlockthecode.invitecode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private final Set<UUID> verifiedPlayers = new HashSet<>();
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();

    private File verifiedFile;
    private FileConfiguration verifiedConfig;

    private List<String> inviteCodes = Collections.emptyList();
    private int timeLimit;
    private String kickMessage;
    private String banMessage;
    private int maxAttempts;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Load persistent data first
        loadVerified();

        // Load config values
        saveDefaultConfig();
        loadConfigValues(); // <-- ensures maxAttempts is set

        // Register
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("join"), "Command 'join' not found in plugin.yml")
                .setExecutor(this);
        getCommand("join").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveVerified();
    }

    /* -------------------- Config Helpers -------------------- */
    private void loadConfigValues() {
        FileConfiguration cfg = getConfig();
        inviteCodes = cfg.getStringList("invite-codes");
        timeLimit = cfg.getInt("time-limit", 60);
        maxAttempts = cfg.getInt("max-attempts", 3); // IMPORTANT: prevents insta-ban
        kickMessage = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("kick-message", "&cYou must use /join <code> to play!"));
        banMessage = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("ban-message", "&cYou are banned for not verifying with /join <code>."));
    }

    /* -------------------- Persistence -------------------- */
    private void loadVerified() {
        verifiedFile = new File(getDataFolder(), "verified.yml");
        if (!verifiedFile.exists()) {
            try {
                verifiedFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create verified.yml");
                e.printStackTrace();
            }
        }

        verifiedConfig = YamlConfiguration.loadConfiguration(verifiedFile);

        // Clear current in-memory state, then repopulate from file
        verifiedPlayers.clear();
        failedAttempts.clear();

        // Load verified list
        List<String> list = verifiedConfig.getStringList("verified");
        if (list != null) {
            for (String uuid : list) {
                try {
                    verifiedPlayers.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // Load failed-attempts map
        if (verifiedConfig.isConfigurationSection("failed-attempts")) {
            for (String key : Objects.requireNonNull(
                    verifiedConfig.getConfigurationSection("failed-attempts")).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int attempts = verifiedConfig.getInt("failed-attempts." + key, 0);
                    if (attempts > 0) {
                        failedAttempts.put(uuid, attempts);
                    }
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

        // rewrite attempts section fresh
        verifiedConfig.set("failed-attempts", null);
        if (!failedAttempts.isEmpty()) {
            for (Map.Entry<UUID, Integer> e : failedAttempts.entrySet()) {
                verifiedConfig.set("failed-attempts." + e.getKey(), e.getValue());
            }
        }

        try {
            verifiedConfig.save(verifiedFile);
        } catch (IOException e) {
            getLogger().severe("Could not save verified.yml");
            e.printStackTrace();
        }
    }

    /* -------------------- Events -------------------- */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!verifiedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must verify using /join <code> within " + timeLimit + " seconds.");

            final String punishment = getConfig().getString("punishment", "kick").toLowerCase();

            new BukkitRunnable() {
                @Override
                public void run() {
                    // Only punish if still unverified and online after timeLimit
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
                                // WARNING with tunnels/proxies (Playit/Velocity/etc.)
                                String ip = player.getAddress() != null
                                        ? player.getAddress().getAddress().getHostAddress()
                                        : null;
                                if (ip != null) {
                                    Bukkit.getBanList(org.bukkit.BanList.Type.IP)
                                            .addBan(ip, banMessage, null, null);
                                }
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

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!verifiedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot chat until you verify with /join <code>.");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // getTo() can be null on some edge teleports/world changes
        if (event.getTo() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!verifiedPlayers.contains(player.getUniqueId())) {
            // Only block position changes; allow yaw/pitch rotation
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    /* -------------------- Commands -------------------- */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No args: show usage
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /join <code> | reload | resetattempts | reloadverified");
            return true;
        }

        // Admin subcommands (ops or console)
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload") || sub.equals("resetattempts") || sub.equals("reloadverified")) {
            if (!sender.isOp() && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }

            switch (sub) {
                case "reload" -> {
                    reloadConfig();
                    loadConfigValues();
                    sender.sendMessage(ChatColor.GREEN + "[InviteCode] Config reloaded successfully!");
                }
                case "resetattempts" -> {
                    failedAttempts.clear();
                    saveVerified();
                    sender.sendMessage(ChatColor.GREEN + "[InviteCode] All failed attempts have been reset.");
                }
                case "reloadverified" -> {
                    loadVerified();
                    sender.sendMessage(ChatColor.GREEN + "[InviteCode] Verified players and attempts reloaded from file.");
                }
            }
            return true;
        }

        // Player verification path: /join <code>
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Console can use: /join reload | resetattempts | reloadverified");
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

            // Safety: if config was mis-set to <=0, treat as "no punishment"
            if (maxAttempts > 0 && attempts >= maxAttempts) {
                final String punishment = getConfig().getString("punishment", "kick").toLowerCase(Locale.ROOT);
                switch (punishment) {
                    case "kick" ->
                        player.kickPlayer(kickMessage);
                    case "ban" -> {
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                                .addBan(player.getName(), banMessage, null, null);
                        player.kickPlayer(banMessage);
                    }
                    case "ban-ip" -> {
                        String ip = player.getAddress() != null
                                ? player.getAddress().getAddress().getHostAddress()
                                : null;
                        if (ip != null) {
                            Bukkit.getBanList(org.bukkit.BanList.Type.IP)
                                    .addBan(ip, banMessage, null, null);
                        }
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

    /* -------------------- Tab Completion -------------------- */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("join")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            // Only show admin subs to ops/console; otherwise, suggest nothing (keep codes secret)
            if (sender.isOp() || sender instanceof org.bukkit.command.ConsoleCommandSender) {
                options.add("reload");
                options.add("reloadverified");
                options.add("resetattempts");
            }
            // basic filtering
            String cur = args[0].toLowerCase(Locale.ROOT);
            options.removeIf(s -> !s.startsWith(cur));
            return options;
        }
        return Collections.emptyList();
    }
}

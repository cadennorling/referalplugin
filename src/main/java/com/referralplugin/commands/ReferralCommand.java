package com.referralplugin.commands;

import com.referralplugin.ReferralPlugin;
import com.referralplugin.models.ReferralData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReferralCommand implements CommandExecutor, TabCompleter {

    private final ReferralPlugin plugin;

    public ReferralCommand(ReferralPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "code" -> handleCode(sender);
            case "use" -> handleUse(sender, args);
            case "info" -> handleInfo(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> sender.sendMessage(plugin.getMessage("unknown-command"));
        }
        return true;
    }

    private void handleCode(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getMessage("player-only")); return; }
        if (!player.hasPermission("referral.use")) { player.sendMessage(plugin.getMessage("no-permission")); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data = plugin.getDatabaseManager().getPlayer(player.getUniqueId());
            if (data == null) {
                String code = plugin.getReferralManager().generateCode(player);
                plugin.getDatabaseManager().savePlayer(player.getUniqueId(), player.getName(), code);
                player.sendMessage(plugin.getMessage("code-generated", "%code%", code));
            } else {
                player.sendMessage(plugin.getMessage("code-already-exists", "%code%", data.getReferralCode()));
            }
        });
    }

    private void handleUse(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getMessage("player-only")); return; }
        if (!player.hasPermission("referral.use")) { player.sendMessage(plugin.getMessage("no-permission")); return; }
        if (args.length < 2) { player.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&cUsage: /referral use <code>")); return; }
        String code = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = plugin.getReferralManager().applyReferralCode(player, code);
            if (error != null) {
                player.sendMessage(plugin.getMessage(error));
            } else {
                ReferralData referrerData = plugin.getDatabaseManager().getPlayerByCode(code);
                String referrerName = referrerData != null ? referrerData.getUsername() : "unknown";
                player.sendMessage(plugin.getMessage("code-success", "%code%", code, "%referrer%", referrerName));
            }
        });
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("referral.use")) { sender.sendMessage(plugin.getMessage("no-permission")); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data;
            if (args.length >= 2 && sender.hasPermission("referral.admin")) {
                data = plugin.getDatabaseManager().getPlayerByName(args[1]);
                if (data == null) { sender.sendMessage(plugin.getMessage("admin-player-not-found")); return; }
            } else {
                if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getMessage("player-only")); return; }
                data = plugin.getDatabaseManager().getPlayer(player.getUniqueId());
                if (data == null) return;
            }
            int pending = plugin.getDatabaseManager().getPendingReferralCount(data.getUuid());
            sender.sendMessage(plugin.getMessage("info-header"));
            sender.sendMessage(plugin.getMessage("info-code", "%code%", data.getReferralCode()));
            sender.sendMessage(plugin.getMessage("info-referrals", "%count%", String.valueOf(data.getTotalReferrals())));
            sender.sendMessage(plugin.getMessage("info-pending", "%pending%", String.valueOf(pending)));
            if (data.getReferredByUUID() != null) {
                ReferralData referrerData = plugin.getDatabaseManager().getPlayer(data.getReferredByUUID());
                sender.sendMessage(plugin.getMessage("info-referred-by", "%referrer%",
                        referrerData != null ? referrerData.getUsername() : "Unknown"));
            } else {
                sender.sendMessage(plugin.getMessage("info-not-referred"));
            }
        });
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("referral.admin")) { sender.sendMessage(plugin.getMessage("no-permission")); return; }
        if (args.length < 2) { sendAdminHelp(sender); return; }
        switch (args[1].toLowerCase()) {
            case "reload" -> { plugin.reload(); sender.sendMessage(plugin.getMessage("admin-reload")); }
            case "resetcode" -> handleAdminResetCode(sender, args);
            case "lookup" -> handleAdminLookup(sender, args);
            case "complete" -> handleAdminComplete(sender, args);
            default -> sendAdminHelp(sender);
        }
    }

    private void handleAdminResetCode(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&cUsage: /referral admin resetcode <player>")); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data = plugin.getDatabaseManager().getPlayerByName(args[2]);
            if (data == null) { sender.sendMessage(plugin.getMessage("admin-player-not-found")); return; }
            String newCode = plugin.getReferralManager().generateRandomCode();
            plugin.getDatabaseManager().resetReferralCode(data.getUuid(), newCode);
            sender.sendMessage(plugin.getMessage("admin-reset-code", "%player%", args[2]) + ReferralPlugin.colorize(" &7New code: &f" + newCode));
        });
    }

    private void handleAdminLookup(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&cUsage: /referral admin lookup <player>")); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data = plugin.getDatabaseManager().getPlayerByName(args[2]);
            if (data == null) { sender.sendMessage(plugin.getMessage("admin-player-not-found")); return; }
            sender.sendMessage(plugin.getMessage("admin-lookup-header", "%player%", args[2]));
            sender.sendMessage(plugin.getMessage("admin-lookup-code", "%code%", data.getReferralCode()));
            sender.sendMessage(plugin.getMessage("admin-lookup-referrals", "%count%", String.valueOf(data.getTotalReferrals())));
            if (data.getReferredByUUID() != null) {
                ReferralData referrer = plugin.getDatabaseManager().getPlayer(data.getReferredByUUID());
                sender.sendMessage(plugin.getMessage("admin-lookup-referred-by", "%referrer%", referrer != null ? referrer.getUsername() : "Unknown"));
            } else {
                sender.sendMessage(plugin.getMessage("admin-lookup-referred-by", "%referrer%", "None"));
            }
            long accumulated = plugin.getReferralManager().getTotalAccumulated(data.getUuid());
            if (accumulated > 0) {
                long required = plugin.getConfig().getLong("settings.required-play-time", 3600);
                sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&7Playtime Progress: &f" + formatTime(accumulated) + " &7/ &f" + formatTime(required)));
            }
        });
    }

    private void handleAdminComplete(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&cUsage: /referral admin complete <player>")); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data = plugin.getDatabaseManager().getPlayerByName(args[2]);
            if (data == null) { sender.sendMessage(plugin.getMessage("admin-player-not-found")); return; }
            if (!plugin.getDatabaseManager().hasPendingSession(data.getUuid())) {
                sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&cThis player has no pending referral session.")); return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getReferralManager().completeReferral(data.getUuid());
                sender.sendMessage(plugin.getMessage("admin-grant-referral", "%player%", args[2]));
            });
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessage("help-header"));
        sender.sendMessage(plugin.getMessage("help-code"));
        sender.sendMessage(plugin.getMessage("help-use"));
        sender.sendMessage(plugin.getMessage("help-info"));
        if (sender.hasPermission("referral.admin")) sender.sendMessage(plugin.getMessage("help-admin"));
        sender.sendMessage(plugin.getMessage("help-footer"));
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&b--- Admin Commands ---"));
        sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&f/referral admin reload &7- Reload config"));
        sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&f/referral admin resetcode <player> &7- Reset a player's code"));
        sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&f/referral admin lookup <player> &7- Look up referral data"));
        sender.sendMessage(plugin.getPrefix() + ReferralPlugin.colorize("&f/referral admin complete <player> &7- Manually complete a referral"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("help", "code", "use", "info"));
            if (sender.hasPermission("referral.admin")) subs.add("admin");
            subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).forEach(completions::add);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("referral.admin")) {
            Arrays.asList("reload", "resetcode", "lookup", "complete", "help").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).forEach(completions::add);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("referral.admin")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("resetcode") || sub.equals("lookup") || sub.equals("complete")) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getName().toLowerCase().startsWith(args[2].toLowerCase()))
                        .map(p -> p.getName()).forEach(completions::add);
            }
        }
        return completions;
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}

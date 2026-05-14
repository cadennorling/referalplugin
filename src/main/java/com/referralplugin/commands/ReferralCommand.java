package com.referralplugin.commands;

import com.referralplugin.ReferralPlugin;
import com.referralplugin.models.ReferralData;
import com.referralplugin.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
            case "help"  -> sendHelp(sender);
            case "code"  -> handleCode(sender);
            case "use"   -> handleUse(sender, args);
            case "info"  -> handleInfo(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default      -> plugin.sendMessage(sender, "unknown-command");
        }
        return true;
    }

    // ===================== /referral code =====================

    private void handleCode(CommandSender sender) {
        if (!(sender instanceof Player player)) { plugin.sendMessage(sender, "player-only"); return; }
        if (!player.hasPermission("referral.use")) { plugin.sendMessage(sender, "no-permission"); return; }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data = plugin.getDatabaseManager().getPlayer(player.getUniqueId());
            String code;
            boolean existing;
            if (data == null) {
                code = plugin.getReferralManager().generateCode(player);
                plugin.getDatabaseManager().savePlayer(player.getUniqueId(), player.getName(), code);
                existing = false;
            } else {
                code = data.getReferralCode();
                existing = true;
            }

            String msgKey = existing ? "code-already-exists" : "code-generated";
            Component message = buildClickableCodeMessage(plugin.getRawMessage(msgKey, "%code%", code), code);
            plugin.sendComponent(player, message);
        });
    }

    /**
     * Builds a message where the code portion is clickable (copy to clipboard)
     * and shows a hover tooltip.
     */
    private Component buildClickableCodeMessage(String rawMessage, String code) {
        // Split the message around the code so we can make just the code clickable
        String[] parts = rawMessage.split(code, 2);

        Component before = parts.length > 0 ? Text.translate(parts[0]) : Component.empty();
        Component after  = parts.length > 1 ? Text.translate(parts[1]) : Component.empty();

        Component codeComponent = Text.translate("&f&l" + code)
                .clickEvent(ClickEvent.copyToClipboard(code))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Click to copy your referral code!", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));

        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(before)
                .append(codeComponent)
                .append(after);
    }

    // ===================== /referral use <code> =====================

    private void handleUse(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { plugin.sendMessage(sender, "player-only"); return; }
        if (!player.hasPermission("referral.use")) { plugin.sendMessage(sender, "no-permission"); return; }
        if (args.length < 2) {
            plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&cUsage: /referral use <code>"));
            return;
        }
        String code = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = plugin.getReferralManager().applyReferralCode(player, code);
            if (error != null) {
                plugin.sendMessage(sender, error);
            } else {
                ReferralData referrerData = plugin.getDatabaseManager().getPlayerByCode(code);
                String referrerName = referrerData != null ? referrerData.getUsername() : "unknown";
                plugin.sendMessage(sender, "code-success", "%code%", code, "%referrer%", referrerName);
            }
        });
    }

    // ===================== /referral info =====================

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("referral.use")) { plugin.sendMessage(sender, "no-permission"); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data;
            if (args.length >= 2 && sender.hasPermission("referral.admin")) {
                data = plugin.getDatabaseManager().getPlayerByName(args[1]);
                if (data == null) { plugin.sendMessage(sender, "admin-player-not-found"); return; }
            } else {
                if (!(sender instanceof Player player)) { plugin.sendMessage(sender, "player-only"); return; }
                data = plugin.getDatabaseManager().getPlayer(player.getUniqueId());
                if (data == null) return;
            }

            int pending = plugin.getDatabaseManager().getPendingReferralCount(data.getUuid());
            plugin.sendMessage(sender, "info-header");
            // Make the code in /info also clickable
            plugin.sendComponent(sender, buildClickableCodeMessage(
                    plugin.getRawMessage("info-code", "%code%", data.getReferralCode()), data.getReferralCode()));
            plugin.sendMessage(sender, "info-referrals", "%count%", String.valueOf(data.getTotalReferrals()));
            plugin.sendMessage(sender, "info-pending", "%pending%", String.valueOf(pending));
            if (data.getReferredByUUID() != null) {
                ReferralData referrerData = plugin.getDatabaseManager().getPlayer(data.getReferredByUUID());
                plugin.sendMessage(sender, "info-referred-by", "%referrer%",
                        referrerData != null ? referrerData.getUsername() : "Unknown");
            } else {
                plugin.sendMessage(sender, "info-not-referred");
            }
        });
    }

    // ===================== /referral admin =====================

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("referral.admin")) { plugin.sendMessage(sender, "no-permission"); return; }
        if (args.length < 2) { sendAdminHelp(sender); return; }
        switch (args[1].toLowerCase()) {
            case "reload"    -> { plugin.reload(); plugin.sendMessage(sender, "admin-reload"); }
            case "resetcode" -> handleAdminResetCode(sender, args);
            case "lookup"    -> handleAdminLookup(sender, args);
            case "complete"  -> handleAdminComplete(sender, args);
            default          -> sendAdminHelp(sender);
        }
    }

    private void handleAdminResetCode(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&cUsage: /referral admin resetcode <player>"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data = plugin.getDatabaseManager().getPlayerByName(args[2]);
            if (data == null) { plugin.sendMessage(sender, "admin-player-not-found"); return; }
            String newCode = plugin.getReferralManager().generateRandomCode();
            plugin.getDatabaseManager().resetReferralCode(data.getUuid(), newCode);
            Component msg = Text.translate(plugin.getRawMessage("admin-reset-code", "%player%", args[2]))
                    .append(buildClickableCodeMessage(" &7New code: &f" + newCode, newCode));
            plugin.sendComponent(sender, msg);
        });
    }

    private void handleAdminLookup(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&cUsage: /referral admin lookup <player>"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data = plugin.getDatabaseManager().getPlayerByName(args[2]);
            if (data == null) { plugin.sendMessage(sender, "admin-player-not-found"); return; }
            plugin.sendMessage(sender, "admin-lookup-header", "%player%", args[2]);
            plugin.sendComponent(sender, buildClickableCodeMessage(
                    plugin.getRawMessage("admin-lookup-code", "%code%", data.getReferralCode()), data.getReferralCode()));
            plugin.sendMessage(sender, "admin-lookup-referrals", "%count%", String.valueOf(data.getTotalReferrals()));
            if (data.getReferredByUUID() != null) {
                ReferralData referrer = plugin.getDatabaseManager().getPlayer(data.getReferredByUUID());
                plugin.sendMessage(sender, "admin-lookup-referred-by", "%referrer%",
                        referrer != null ? referrer.getUsername() : "Unknown");
            } else {
                plugin.sendMessage(sender, "admin-lookup-referred-by", "%referrer%", "None");
            }
            long accumulated = plugin.getReferralManager().getTotalAccumulated(data.getUuid());
            if (accumulated > 0) {
                long required = plugin.getConfig().getLong("settings.required-play-time", 3600);
                plugin.sendComponent(sender, Text.translate(plugin.getPrefix() +
                        "&7Playtime Progress: &f" + formatTime(accumulated) + " &7/ &f" + formatTime(required)));
            }
        });
    }

    private void handleAdminComplete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&cUsage: /referral admin complete <player>"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReferralData data = plugin.getDatabaseManager().getPlayerByName(args[2]);
            if (data == null) { plugin.sendMessage(sender, "admin-player-not-found"); return; }
            if (!plugin.getDatabaseManager().hasPendingSession(data.getUuid())) {
                plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&cThis player has no pending referral session."));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getReferralManager().completeReferral(data.getUuid());
                plugin.sendMessage(sender, "admin-grant-referral", "%player%", args[2]);
            });
        });
    }

    // ===================== Help =====================

    private void sendHelp(CommandSender sender) {
        plugin.sendMessage(sender, "help-header");
        plugin.sendMessage(sender, "help-code");
        plugin.sendMessage(sender, "help-use");
        plugin.sendMessage(sender, "help-info");
        if (sender.hasPermission("referral.admin")) plugin.sendMessage(sender, "help-admin");
        plugin.sendMessage(sender, "help-footer");
    }

    private void sendAdminHelp(CommandSender sender) {
        plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&b--- Admin Commands ---"));
        plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&f/referral admin reload &7- Reload config"));
        plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&f/referral admin resetcode <player> &7- Reset a player's code"));
        plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&f/referral admin lookup <player> &7- Look up referral data"));
        plugin.sendComponent(sender, Text.translate(plugin.getPrefix() + "&f/referral admin complete <player> &7- Manually complete a referral"));
    }

    // ===================== Tab Complete =====================

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
                        .map(Player::getName).forEach(completions::add);
            }
        }
        return completions;
    }

    // ===================== Utility =====================

    private String formatTime(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}

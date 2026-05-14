package com.referralplugin.managers;

import com.referralplugin.ReferralPlugin;
import com.referralplugin.models.ReferralData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReferralManager {

    private final ReferralPlugin plugin;
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> accumulatedCache = new ConcurrentHashMap<>();
    private BukkitTask trackingTask;

    public ReferralManager(ReferralPlugin plugin) {
        this.plugin = plugin;
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        ReferralData data = plugin.getDatabaseManager().getPlayer(uuid);
        if (data == null) {
            String code = generateCode(player);
            plugin.getDatabaseManager().savePlayer(uuid, player.getName(), code);
        } else {
            plugin.getDatabaseManager().savePlayer(uuid, player.getName(), data.getReferralCode());
        }

        if (plugin.getDatabaseManager().hasPendingSession(uuid)) {
            long accumulated = plugin.getDatabaseManager().getAccumulatedSeconds(uuid);
            accumulatedCache.put(uuid, accumulated);
            sessionStartTimes.put(uuid, System.currentTimeMillis());

            if (plugin.getConfig().getBoolean("settings.notify-referrer-on-join", true)) {
                notifyReferrer(uuid, player.getName());
            }
        }
    }

    public void onPlayerQuit(Player player) {
        saveSessionTime(player.getUniqueId());
    }

    private void saveSessionTime(UUID uuid) {
        Long startTime = sessionStartTimes.remove(uuid);
        if (startTime == null) return;
        long secondsOnline = (System.currentTimeMillis() - startTime) / 1000;
        long newTotal = accumulatedCache.getOrDefault(uuid, 0L) + secondsOnline;
        accumulatedCache.put(uuid, newTotal);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().addAccumulatedSeconds(uuid, secondsOnline));
    }

    public void startTrackingTask() {
        trackingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkCompletions, 1200L, 1200L);
    }

    public void stopTrackingTask() {
        if (trackingTask != null) trackingTask.cancel();
    }

    private void checkCompletions() {
        long requiredSeconds = plugin.getConfig().getLong("settings.required-play-time", 3600);
        for (Map.Entry<UUID, Long> entry : sessionStartTimes.entrySet()) {
            UUID referredUUID = entry.getKey();
            long secondsThisSession = (System.currentTimeMillis() - entry.getValue()) / 1000;
            long total = accumulatedCache.getOrDefault(referredUUID, 0L) + secondsThisSession;
            if (total >= requiredSeconds) {
                Bukkit.getScheduler().runTask(plugin, () -> completeReferral(referredUUID));
            }
        }
    }

    public void completeReferral(UUID referredUUID) {
        sessionStartTimes.remove(referredUUID);
        accumulatedCache.remove(referredUUID);
        plugin.getDatabaseManager().completeSession(referredUUID);

        ReferralData referredData = plugin.getDatabaseManager().getPlayer(referredUUID);
        if (referredData == null || referredData.getReferredByUUID() == null) return;

        UUID referrerUUID = referredData.getReferredByUUID();
        ReferralData referrerData = plugin.getDatabaseManager().getPlayer(referrerUUID);
        if (referrerData == null) return;

        String referredName = referredData.getUsername();
        String referrerName = referrerData.getUsername();

        plugin.getDatabaseManager().incrementReferralCount(referrerUUID);
        plugin.getDatabaseManager().markRewardIssued(referredUUID);

        int newCount = referrerData.getTotalReferrals() + 1;
        plugin.getRewardManager().issueReferrerReward(referrerUUID, referrerName, referredName, newCount);

        if (plugin.getConfig().getBoolean("settings.reward-referred-player", true)) {
            plugin.getRewardManager().issueReferredReward(referredUUID, referredName, referrerName);
        }

        Player referrer = Bukkit.getPlayer(referrerUUID);
        if (referrer != null && referrer.isOnline()) {
            referrer.sendMessage(plugin.getMessage("referral-complete-referrer", "%referred%", referredName));
        }

        Player referred = Bukkit.getPlayer(referredUUID);
        if (referred != null && referred.isOnline()) {
            referred.sendMessage(plugin.getMessage("referral-complete-referred"));
        }

        if (plugin.getConfig().getBoolean("settings.broadcast-on-complete", true)) {
            Bukkit.broadcastMessage(plugin.getMessage("broadcast-complete",
                    "%referrer%", referrerName, "%referred%", referredName));
        }
    }

    public String generateCode(Player player) {
        if (plugin.getConfig().getBoolean("settings.use-username-as-code", false)) {
            return player.getName();
        }
        return generateRandomCode();
    }

    public String generateRandomCode() {
        int length = plugin.getConfig().getInt("settings.code-length", 8);
        String chars = plugin.getConfig().getString("settings.code-characters", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    public String applyReferralCode(Player player, String code) {
        UUID uuid = player.getUniqueId();
        ReferralData myData = plugin.getDatabaseManager().getPlayer(uuid);

        if (myData != null && myData.hasUsedCode()) return "code-already-used";

        long useCodeWindow = plugin.getConfig().getLong("settings.use-code-window", 86400);
        if (useCodeWindow > 0 && myData != null) {
            long secondsSinceJoin = (System.currentTimeMillis() - myData.getJoinTimestamp()) / 1000;
            if (secondsSinceJoin > useCodeWindow) return "code-window-expired";
        }

        ReferralData referrerData = plugin.getDatabaseManager().getPlayerByCode(code);
        if (referrerData == null) return "code-not-found";

        if (plugin.getConfig().getBoolean("settings.prevent-self-referral", true)
                && referrerData.getUuid().equals(uuid)) return "code-used-self";

        int maxReferrals = plugin.getConfig().getInt("settings.max-referrals", 0);
        if (maxReferrals > 0 && referrerData.getTotalReferrals() >= maxReferrals) return "max-referrals-reached";

        plugin.getDatabaseManager().setReferredBy(uuid, referrerData.getUuid());

        long requiredSeconds = plugin.getConfig().getLong("settings.required-play-time", 3600);
        plugin.getDatabaseManager().createSession(uuid, referrerData.getUuid(), requiredSeconds);

        long accumulated = plugin.getDatabaseManager().getAccumulatedSeconds(uuid);
        accumulatedCache.put(uuid, Math.max(0, accumulated));
        sessionStartTimes.put(uuid, System.currentTimeMillis());

        return null;
    }

    public void saveAllSessions() {
        for (UUID uuid : new HashSet<>(sessionStartTimes.keySet())) saveSessionTime(uuid);
    }

    public void reload() {}

    private void notifyReferrer(UUID referredUUID, String referredName) {
        ReferralData referredData = plugin.getDatabaseManager().getPlayer(referredUUID);
        if (referredData == null || referredData.getReferredByUUID() == null) return;
        Player referrer = Bukkit.getPlayer(referredData.getReferredByUUID());
        if (referrer != null && referrer.isOnline()) {
            referrer.sendMessage(plugin.getMessage("notify-join", "%referred%", referredName));
        }
    }

    public long getTotalAccumulated(UUID uuid) {
        long cached = accumulatedCache.getOrDefault(uuid, 0L);
        Long startTime = sessionStartTimes.get(uuid);
        if (startTime != null) cached += (System.currentTimeMillis() - startTime) / 1000;
        return cached;
    }

    public Map<UUID, Long> getSessionStartTimes() { return sessionStartTimes; }
    public Map<UUID, Long> getAccumulatedCache() { return accumulatedCache; }
}

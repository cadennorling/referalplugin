package com.referralplugin.managers;

import com.referralplugin.ReferralPlugin;
import com.referralplugin.models.ReferralData;
import com.referralplugin.utils.Text;
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
            plugin.sendMessage(referrer, "referral-complete-referrer", "%referred%", referredName);
        }

        Player referred = Bukkit.getPlayer(referredUUID);
        if (referred != null && referred.isOnline()) {
            plugin.sendMessage(referred, "referral-complete-referred");
        }

        if (plugin.getConfig().getBoolean("settings.broadcast-on-complete", true)) {
            Bukkit.broadcastMessage(Text.translateToPrimitive(Text.translate(plugin.getRawMessage(
                    "broadcast-complete", "%referrer%", referrerName, "%referred%", referredName))));
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
        String chars = plugin.getConfig().getStrin

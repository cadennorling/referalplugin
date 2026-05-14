package com.referralplugin.managers;

import com.referralplugin.ReferralPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.UUID;

public class RewardManager {

    private final ReferralPlugin plugin;

    public RewardManager(ReferralPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {}

    public void issueReferrerReward(UUID referrerUUID, String referrerName, String referredName, int newReferralCount) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("referrer-rewards");
        if (section != null) runCommands(section.getStringList("commands"), referrerName, referredName);
        checkMilestone(referrerUUID, referrerName, referredName, newReferralCount);
    }

    public void issueReferredReward(UUID referredUUID, String referredName, String referrerName) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("referred-rewards");
        if (section != null) runCommands(section.getStringList("commands"), referrerName, referredName);
    }

    private void checkMilestone(UUID referrerUUID, String referrerName, String referredName, int count) {
        if (!plugin.getConfig().getBoolean("milestones.enabled", true)) return;
        ConfigurationSection milestones = plugin.getConfig().getConfigurationSection("milestones.rewards");
        if (milestones == null) return;
        if (milestones.contains(String.valueOf(count))) {
            ConfigurationSection milestone = milestones.getConfigurationSection(String.valueOf(count));
            if (milestone == null) return;
            runCommands(milestone.getStringList("commands"), referrerName, referredName);
        }
    }

    private void runCommands(List<String> commands, String referrerName, String referredName) {
        for (String cmd : commands) {
            String processed = cmd.replace("%referrer%", referrerName).replace("%referred%", referredName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
        }
    }
}

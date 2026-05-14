package com.referralplugin;

import com.referralplugin.commands.ReferralCommand;
import com.referralplugin.database.DatabaseManager;
import com.referralplugin.listeners.PlayerJoinListener;
import com.referralplugin.listeners.PlayerQuitListener;
import com.referralplugin.managers.ReferralManager;
import com.referralplugin.managers.RewardManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ReferralPlugin extends JavaPlugin {

    private static ReferralPlugin instance;
    private DatabaseManager databaseManager;
    private ReferralManager referralManager;
    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Failed to connect to MySQL database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        databaseManager.createTables();

        rewardManager = new RewardManager(this);
        referralManager = new ReferralManager(this);

        ReferralCommand referralCommand = new ReferralCommand(this);
        getCommand("referral").setExecutor(referralCommand);
        getCommand("referral").setTabCompleter(referralCommand);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);

        referralManager.startTrackingTask();

        getLogger().info("ReferralPlugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (referralManager != null) {
            referralManager.stopTrackingTask();
            referralManager.saveAllSessions();
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        getLogger().info("ReferralPlugin disabled.");
    }

    public void reload() {
        reloadConfig();
        referralManager.reload();
        rewardManager.reload();
    }

    public static ReferralPlugin getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ReferralManager getReferralManager() { return referralManager; }
    public RewardManager getRewardManager() { return rewardManager; }

    public String getPrefix() {
        return colorize(getConfig().getString("settings.prefix", "&8[&bReferral&8] &r"));
    }

    public String getMessage(String key) {
        String msg = getConfig().getString("messages." + key, "&cMessage not found: " + key);
        return colorize(getPrefix() + msg);
    }

    public String getMessage(String key, String... replacements) {
        String msg = getMessage(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public boolean isDebug() { return getConfig().getBoolean("settings.debug", false); }

    public void debug(String message) {
        if (isDebug()) getLogger().info("[DEBUG] " + message);
    }

    public static String colorize(String text) {
        return text == null ? "" : text.replace("&", "\u00A7");
    }
}

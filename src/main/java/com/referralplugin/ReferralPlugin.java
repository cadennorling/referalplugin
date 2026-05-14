package com.referralplugin;

import com.referralplugin.commands.ReferralCommand;
import com.referralplugin.database.DatabaseManager;
import com.referralplugin.listeners.PlayerJoinListener;
import com.referralplugin.listeners.PlayerQuitListener;
import com.referralplugin.managers.ReferralManager;
import com.referralplugin.managers.RewardManager;
import com.referralplugin.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
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

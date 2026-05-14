package com.referralplugin.listeners;

import com.referralplugin.ReferralPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final ReferralPlugin plugin;

    public PlayerJoinListener(ReferralPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getReferralManager().onPlayerJoin(event.getPlayer())
        );
    }
}

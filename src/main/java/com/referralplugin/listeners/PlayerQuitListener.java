package com.referralplugin.listeners;

import com.referralplugin.ReferralPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final ReferralPlugin plugin;

    public PlayerQuitListener(ReferralPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getReferralManager().onPlayerQuit(event.getPlayer());
    }
}

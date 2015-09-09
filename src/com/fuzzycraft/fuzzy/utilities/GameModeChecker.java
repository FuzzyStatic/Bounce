package com.fuzzycraft.fuzzy.utilities;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import com.fuzzycraft.fuzzy.Bounce;

public class GameModeChecker implements Listener {

    private World world;
    
    /**
     * Constructor.
     * @param plugin
     */
    public GameModeChecker() {
        this.world = Bounce.spawn.getWorld();
    }
    
    /**
     * Set gamemode for player at spawn.
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();        
        
        if (player.getWorld() == this.world) {
            GameModeChecker.setSurvival(player);
        }
    }
    
    public static void setSurvival(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gm 0 " + player.getName());
    }
    
    public static void setSpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gm 3 " + player.getName());
    }
    
    public static void setSurvivalAll(List<Player> players) {
        for (Player player : players) {
            player.setGameMode(GameMode.SURVIVAL);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gm 0 " + player.getName());
        }
    }
}

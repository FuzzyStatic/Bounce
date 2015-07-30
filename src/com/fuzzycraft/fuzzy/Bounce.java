package com.fuzzycraft.fuzzy;

import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.fuzzycraft.fuzzy.constants.Defaults;

/**
 * 
 * @author FuzzyStatic (fuzzy@fuzzycraft.com)
 *
 */

public class Bounce extends JavaPlugin {
	
	public static World spawnWorld;
	
	private Bounce plugin = this;
	
	public void onEnable() {				
		new BukkitRunnable() {
        	
			public void run() {
				spawnWorld = getServer().getWorld(Defaults.SPAWN_WORLD);
				
				for (String world : Defaults.GAME_WORLDS) {
					World gameWorld = getServer().getWorld(world);
					BounceManagement pm = new BounceManagement(plugin, gameWorld);
					PluginManager manager = getServer().getPluginManager();
					manager.registerEvents(pm, plugin);
				}
			}
			
		}.runTaskLater(this, 1);
	}
}

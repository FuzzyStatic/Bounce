package com.fuzzycraft.fuzzy;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.fuzzycraft.fuzzy.commands.Quit;
import com.fuzzycraft.fuzzy.constants.Defaults;

/**
 * 
 * @author FuzzyStatic (fuzzy@fuzzycraft.com)
 *
 */

public class Bounce extends JavaPlugin {
	
    public static Location spawn;
	
	private Bounce plugin = this;
	
	public void onEnable() {				
		new BukkitRunnable() {
        	
			public void run() {
			    spawn = new Location(getServer().getWorld(Defaults.SPAWN_WORLD), 
                        Defaults.SPAWN_X, 
                        Defaults.SPAWN_Y, 
                        Defaults.SPAWN_Z,
                        Defaults.SPAWN_YAW,
                        Defaults.SPAWN_PITCH);
				
				for (String world : Defaults.GAME_WORLDS) {
					World gameWorld = getServer().getWorld(world);
					BounceManagement pm = new BounceManagement(plugin, gameWorld);
					PluginManager manager = getServer().getPluginManager();
					manager.registerEvents(pm, plugin);
				}
			}
			
		}.runTaskLater(this, 1);
		
		getCommand(Quit.CMD).setExecutor(new Quit());
	}
}

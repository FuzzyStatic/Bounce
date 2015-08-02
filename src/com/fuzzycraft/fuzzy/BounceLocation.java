package com.fuzzycraft.fuzzy;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.fuzzycraft.fuzzy.constants.Defaults;

/**
 * 
 * @author FuzzyStatic (fuzzy@fuzzycraft.com)
 *
 */

public class BounceLocation {
	
	public Bounce plugin;
	public World world;
	private int minX, maxX, minY, maxY, minZ, maxZ;

	/**
	 * Constructor.
	 * @param plugin
	 */
	public BounceLocation(Bounce plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		this.minX = Defaults.MIN_X;
		this.maxX = Defaults.MAX_X;
		this.minY = Defaults.MIN_Y;
		this.maxY = Defaults.MAX_Y;
		this.minZ = Defaults.MIN_Z;
		this.maxZ = Defaults.MAX_Z;
	}

	/**
	 * Create random location based on specified defaults.
	 * @return 
	 */
	public Location getRandomLocation() {
		Random rand = new Random();
		double x = rand.nextInt((this.maxX - this.minX) + 1) + this.minX; 
		double y = rand.nextInt((this.maxY - this.minY) + 1) + this.minY; 
		double z = rand.nextInt((this.maxZ - this.minZ) + 1) + this.minZ;
		return new Location(this.world, x, y, z);
	}
	
	/**
	 * Spawn specified player at location.
	 */
	public void spawnPlayer(Player player) {
		boolean teleport = true;
		
		do {
			Location location = this.getRandomLocation();
			Location firstLocation = new Location(location.getWorld(), location.getX(), location.getY() + 1, location.getZ());
			Location secondLocation = new Location(location.getWorld(), location.getX(), location.getY() + 2, location.getZ());
				
			if (location.getBlock().getType() != Material.AIR
					&& location.getBlock().getType() != Material.LAVA 
					&& location.getBlock().getType() != Material.STATIONARY_LAVA
					&& location.getBlock().getType() != Material.LADDER
					&& firstLocation.getBlock().getType() == Material.AIR
					&& secondLocation.getBlock().getType() == Material.AIR) {
				player.teleport(firstLocation);
				teleport = false;
			}
        } while (teleport);
	}
}

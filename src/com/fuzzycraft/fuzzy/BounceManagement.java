package com.fuzzycraft.fuzzy;

import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.fuzzycraft.fuzzy.constants.Defaults;
import com.fuzzycraft.fuzzy.utilities.TeleportPlayers;

/**
 * 
 * @author FuzzyStatic (fuzzy@fuzzycraft.com)
 *
 */

public class BounceManagement implements Listener {
	
	public enum Status {
		STARTING, RUNNING, CLEANING;
	}
	
	public Bounce plugin;
	private World world;
	private BounceLocation pl;
	private TeleportPlayers tp;
	private Material material;
	private int runningTime, cleaningTime, startingTime, minPlayers, materialAmount, materialRemaining, pointsMaterial, pointsKill;
	private Status status;
	private boolean active = false;
	private List<Player> scoreboardPlayers;
	private HashMap<Player, Integer> playerMaterial = new HashMap<Player, Integer>();
	private HashMap<Player, Integer> playerKills = new HashMap<Player, Integer>();
		
	/**
	 * Constructor.
	 * @param plugin
	 */
	public BounceManagement(final Bounce plugin, World world) {
		this.plugin = plugin;
		this.world = world;
		this.pl = new BounceLocation(this.plugin, this.world);
		this.tp = new TeleportPlayers(Bounce.spawnWorld, this.world);
		this.material = Defaults.MATERIAL;
		this.runningTime = Defaults.RUNNING_TIME;
		this.cleaningTime = Defaults.CLEANING_TIME;
		this.startingTime = Defaults.STARTING_TIME;
		this.minPlayers = Defaults.MIN_PLAYERS;
		this.materialAmount = Defaults.MATERIAL_AMOUNT;
		this.pointsMaterial = Defaults.POINTS_EGG;
		this.pointsKill = Defaults.POINTS_KILL;
	}
	
	/**
	 * Create board for joining player.
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
				
		if (this.active || player.getWorld() != this.world) {
			return;
		}
				
		if (this.status != Status.STARTING) {
			this.tp.teleportPlayerToStart(player);
		} else {
			this.latePlayer(player);
		}
				
		int playersInWorld = this.world.getPlayers().size();
				
		if (playersInWorld >= this.minPlayers) {
			this.start();
		}
	}
	
	/**
	 * Give catcher random item when he catches catchee.
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getClickedBlock() == null) {
			return;
		}
		
		Block block = event.getClickedBlock();
		Player player = event.getPlayer();
		
		if (this.status != Status.RUNNING || block.getWorld() != this.world) {
			return;
		}

		// Give block breaker a block point.
		if (block.getType() == this.material) {
			block.setType(Material.AIR);
			this.playerMaterial.put(player, this.playerMaterial.get(player) + 1);
			this.materialRemaining--;
		}
		
		// Update scoreboard.
        for (Player participants : this.world.getPlayers()) {
        	this.setPlayerBoard(participants);
		}
	}
	
	/**
	 * Check for player death.
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityDeath(EntityDeathEvent event) {
        if (this.status != Status.RUNNING || !(event.getEntity() instanceof Player) || event.getEntity().getWorld() != this.world) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Player killer = null;
        
        if (player.getKiller() instanceof Player) {
            killer = (Player) player.getKiller();
        } else if (player.getKiller() instanceof Projectile) {
            Projectile projectile = (Projectile) player.getKiller();
            
            if (projectile.getShooter() instanceof Player) {
            	killer = (Player) projectile.getShooter();
            }
        } else {
        	return;
        }
        
        // Eliminate drops
        event.getDrops().clear();
        
        // Give killer a kill.
        if (killer != null && killer != event.getEntity() && killer.getWorld() == this.world) {
        	this.playerKills.put(killer, this.playerKills.get(killer) + 1);
        }
                
        // Update scoreboard.
        for (Player participants : this.world.getPlayers()) {
        	this.setPlayerBoard(participants);
		}
    }
	
	/**
	 * Check for player respawn.
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerRespawn(final PlayerRespawnEvent event) {
		final Player player = event.getPlayer();
		
		if (this.status != Status.RUNNING) {		
			if (player.getWorld() != this.world) {
				return;
			}
			
			new BukkitRunnable() {
            	
    			public void run() {
    				tp.teleportPlayerToStart(player);
    			}
    			
    		}.runTaskLater(this.plugin, 1);
    		
        	return;
        }
		
		if (player.getWorld() != this.world) {
			return;
		}
        
        // Respawn in game after death.
		new BukkitRunnable() {
        	
			public void run() {
		        setupPlayer(player);
			}
			
		}.runTaskLater(this.plugin, 1);
    }
	
	/**
	 * Prevent damage when event is not running.
	 * @param event
	 */
	@EventHandler (priority = EventPriority.HIGHEST)
    public void onEntityDamagedByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && this.status != Status.RUNNING && event.getDamager().getWorld() == this.world) {
            event.setCancelled(true);
        }
    }
	
	/**
	 * Display start timer.
	 * @param player
	 * @param cooldownTime
	 */
	public void startTimer(int timer) {
		if (timer <= 0) {
			cleanHashMap(playerMaterial);
			cleanHashMap(playerKills);
			pl.spawnMaterial(material, materialAmount);
			this.materialRemaining = this.materialAmount;
			sendMassMessage(world.getPlayers(), Defaults.GAME_TAG + ChatColor.GREEN + " Game on!");
				
			for (Player player : this.world.getPlayers()) {
				this.scoreboardPlayers = this.world.getPlayers();
				this.setupPlayer(player);
			}
			
			this.run();
			return;
		}
		
		if (timer <= 5) {
			sendMassMessage(world.getPlayers(), Defaults.GAME_TAG + " " + ChatColor.GREEN + timer);
		}
				
		// Decrement timer.
		final int newTimer = --timer;
		
		// Create the task anonymously with decremented timer.
		new BukkitRunnable() {
		      
			public void run() {
				int playersInWorld = world.getPlayers().size();
				
				if (playersInWorld >= minPlayers) {
					startTimer(newTimer);
				} else {
					active = false;
					sendMassMessage(world.getPlayers(), Defaults.GAME_TAG + ChatColor.DARK_RED + " " + ChatColor.GREEN + "Not enough players.");
				}
			}
				
		}.runTaskLater(this.plugin, 20);
	}
	
	/**
	 * Start the event.
	 */
	public void start() {
		this.active = true;
		this.pl.removeAll(material);
		this.sendMassMessage(this.world.getPlayers(), Defaults.GAME_TAG + ChatColor.DARK_RED + " Game will start in " + ChatColor.GREEN + this.startingTime + " seconds!");		
		this.status = Status.STARTING;
		this.startTimer(this.startingTime);
	}
	
	/**
	 * Display start timer.
	 * @param player
	 * @param cooldownTime
	 */
	public void runTimer(int timer) {
		if (timer <= 0) {
			sendMassMessage(world.getPlayers(), Defaults.GAME_TAG + ChatColor.DARK_RED + " Game is over! Thanks for playing!");
			
			// Show everyone their score
			for (Player player : world.getPlayers()) {
				player.sendMessage(Defaults.GAME_TAG + ChatColor.DARK_RED + " Your score is " + ChatColor.GREEN + this.getPlayerScore(player) + "!");
				player.sendMessage(Defaults.GAME_TAG + ChatColor.DARK_RED + " Winner is " + ChatColor.GREEN + this.getWinner() + "!");
			}
			
			this.clean();
			return;
		}
		
		if (timer == 15 || timer == 30 || timer == 60 || timer == 90) {
			sendMassMessage(this.world.getPlayers(), Defaults.GAME_TAG + ChatColor.DARK_RED + " Game ends in " + ChatColor.GREEN + timer + " seconds!");
		}
		
		if (timer <= 5) {
			sendMassMessage(this.world.getPlayers(), Defaults.GAME_TAG + ChatColor.DARK_RED + " " + ChatColor.GREEN + timer);
		}
				
		// Decrement timer.
		final int newTimer = --timer;
		
		// Create the task anonymously to decrement timer.
		new BukkitRunnable() {
		      
			public void run() {
				runTimer(newTimer);
			}
				
		}.runTaskLater(this.plugin, 20);
	}
	
	/**
	 * Finish event after evenTime and name winners.
	 */
	public void run() {
		this.status = Status.RUNNING;
		this.runTimer(this.runningTime);
	}
	
	/**
	 * Clean up for next event.
	 */
	public void clean() {
		this.status = Status.CLEANING;
		
		new BukkitRunnable() {
        	
			public void run() {
				sendMassMessage(world.getPlayers(), Defaults.GAME_TAG + ChatColor.DARK_RED + " You are being teleported back to the hub...");
				tp.teleportPlayersToSpawn(world.getPlayers());
				pl.removeAll(material);
				active = false;
			}
			
		}.runTaskLater(this.plugin, this.cleaningTime * 20);
	}
	
	/**
	 * Set all keys of player to values of 0.
	 * @param map
	 */
	public void cleanHashMap(HashMap<Player, Integer> map) {
		map.clear();
		
		for (Player player : this.world.getPlayers()) {
			map.put(player, 0);
		}
	}
	
	/**
	 * Setup the player when he joins the game. Give full health, give items, set scoreboard.
	 */
	public void setupPlayer(Player player) {
		player.setFireTicks(0);
		player.setHealth((double) player.getMaxHealth());
		player.getInventory().clear();
		this.pl.spawnPlayer(player);
		this.setPlayerBoard(player);
	}
	
	/**
	 * Initialize maps of player.
	 * @param map
	 */
	public void latePlayer(Player player) {
		if (this.playerMaterial.get(player) == null) {
			this.playerMaterial.put(player, 0);
		}
		
		if (this.playerKills.get(player) == null) {
			this.playerKills.put(player, 0);
		}
		
		this.scoreboardPlayers.add(player);
		this.setupPlayer(player);
		
		for (Player participant : this.world.getPlayers()) {
			this.setPlayerBoard(participant);
		}
	}
	
	/**
	 * Send message to list of players
	 */
	public void sendMassMessage(List<Player> players, String msg) {
		for (Player player : players) {
			player.sendMessage(msg);
		}
	}
	
	/**
	 * Create Scoreboard for player.
	 */
	public void setPlayerBoard(Player player) {
		Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
		Objective objective = board.registerNewObjective("timers", "dummy");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		objective.setDisplayName(ChatColor.DARK_PURPLE + "Eggs Left: " + ChatColor.GREEN + this.materialRemaining + ChatColor.WHITE + "/" + ChatColor.DARK_GREEN + this.materialAmount);
		
		for (Player participant : this.scoreboardPlayers) {
			objective.getScore(participant.getName().toString()).setScore(getPlayerScore(participant));
		}
		
		player.setScoreboard(board);
	}
	
	/**
	 * Return winner of the game.
	 * @return
	 */
	public String getWinner() {
		Player winner = null;
		int winnerScore = 0;
		
		for (Player player : this.world.getPlayers()) {
			if ((int) getPlayerScore(player) > winnerScore 
					|| (
							(int) getPlayerScore(player) == winnerScore 
							&& this.playerMaterial.get(player) > this.playerMaterial.get(winner)
						)
				) {
				winnerScore = getPlayerScore(player);
				winner = player;
			}
		}
		
		return winner.getDisplayName();
	}
	
	/**
	 * Return current player score. If no score exists return 0.
	 * @param player
	 * @return
	 */
	public int getPlayerScore(Player player) {
		if (this.playerMaterial.get(player) != null && this.playerKills.get(player) != null) {
			return (this.playerMaterial.get(player) * this.pointsMaterial) + (this.playerKills.get(player) * this.pointsKill);
		} else {
			return 0;
		}
	}
}
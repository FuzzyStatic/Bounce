package com.fuzzycraft.fuzzy;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

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
	private int runningTime, cleaningTime, startingTime, 
				minPlayers, pointsKill, winGold;
	private Status status;
	private boolean active = false;
	private List<Player> scoreboardPlayers, tiedPlayers;
	private List<ItemStack> eventItems;
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
		this.runningTime = Defaults.RUNNING_TIME;
		this.cleaningTime = Defaults.CLEANING_TIME;
		this.startingTime = Defaults.STARTING_TIME;
		this.minPlayers = Defaults.MIN_PLAYERS;
		this.pointsKill = Defaults.POINTS_KILL;
		this.winGold = Defaults.WIN_GOLD;	
		this.eventItems.add(new ItemStack(Material.IRON_HELMET, 1));
		this.eventItems.add(new ItemStack(Material.IRON_LEGGINGS, 1));
		this.eventItems.add(new ItemStack(Material.IRON_BOOTS, 1));
		this.eventItems.add(new ItemStack(Material.DIAMOND_SWORD, 1));
		this.eventItems.add(new ItemStack(Material.BOW, 1));
		this.eventItems.add(new ItemStack(Material.ARROW, 32));
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
	 * Maintain velocity after stepping on slime block.
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerMovement(PlayerMoveEvent event) {	
		if (this.status != Status.RUNNING || event.getPlayer().getWorld() != this.world) {
			return;
		}
				
		Player player = event.getPlayer();
		Location from = event.getFrom();
		Location fromBelow = new Location(this.world, from.getX(), from.getY()-1, from.getZ());
						
		if (fromBelow.getBlock().getType() == Material.SLIME_BLOCK) {
			Vector initialVelocity = player.getVelocity();
			
			if (initialVelocity != new Vector(0 ,0 ,0)) {
				player.setVelocity(new Vector(initialVelocity.getX() + 0.05, initialVelocity.getY() + 0.75, initialVelocity.getZ() + 0.05));
			}
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
			cleanHashMap(playerKills);
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
			
			Player winner = this.getWinner();
			
			// Show everyone their score
			for (Player player : this.world.getPlayers()) {
				player.sendMessage(Defaults.GAME_TAG + ChatColor.DARK_RED + " Your score is " + ChatColor.GREEN + this.getPlayerScore(player) + "!");
				player.sendMessage(Defaults.GAME_TAG + ChatColor.DARK_RED + " Winner is " + ChatColor.GREEN + winner.getDisplayName() + "!");
				player.getInventory().clear();
			}
			
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fe grant " + winner.getName() + " " + this.winGold);
			
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
		
		for (ItemStack item : this.eventItems) {
			player.getInventory().addItem(item);
		}
		
		this.pl.spawnPlayer(player);
		this.setPlayerBoard(player);
	}
	
	/**
	 * Initialize maps of player.
	 * @param map
	 */
	public void latePlayer(Player player) {
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
		
		for (Player participant : this.scoreboardPlayers) {
			objective.getScore(participant.getName().toString()).setScore(getPlayerScore(participant));
		}
		
		player.setScoreboard(board);
	}
	
	/**
	 * Return winner of the game.
	 * @return
	 */
	public Player getWinner() {
		this.tiedPlayers.add(this.world.getPlayers().get(new Random().nextInt(this.world.getPlayers().size())));
		int winnerScore = 0;
		
		for (Player player : this.world.getPlayers()) {
			if ((int) getPlayerScore(player) > winnerScore) {
				winnerScore = getPlayerScore(player);
				this.tiedPlayers.clear();
				tiedPlayers.add(player);
			}
			
			if ((int) getPlayerScore(player) == winnerScore) {
				winnerScore = getPlayerScore(player);
				this.tiedPlayers.add(player);
			}
		}
		
		return this.tiedPlayers.get(new Random().nextInt(this.tiedPlayers.size()));
	}
	
	/**
	 * Return current player score. If no score exists return 0.
	 * @param player
	 * @return
	 */
	public int getPlayerScore(Player player) {
		if (this.playerKills.get(player) != null) {
			return (this.playerKills.get(player) * this.pointsKill);
		} else {
			return 0;
		}
	}
}
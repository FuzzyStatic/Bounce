package com.fuzzycraft.fuzzy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
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
import com.fuzzycraft.fuzzy.utilities.GameModeChecker;
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
                maxPlayers, minPlayers, 
                pointsKill;
    private Status status;
    private boolean active = false;
    private List<Player> scoreboardPlayers, spectators;
    private HashMap<Player, Integer> playerKills = new HashMap<Player, Integer>();
        
    /**
     * Constructor.
     * @param plugin
     */
    public BounceManagement(final Bounce plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.pl = new BounceLocation(this.plugin, this.world);
        this.tp = new TeleportPlayers(this.world);
        this.runningTime = Defaults.RUNNING_TIME;
        this.cleaningTime = Defaults.CLEANING_TIME;
        this.startingTime = Defaults.STARTING_TIME;
        this.maxPlayers = Defaults.MAX_PLAYERS;
        this.minPlayers = Defaults.MIN_PLAYERS;
        this.pointsKill = Defaults.POINTS_KILL;
        this.scoreboardPlayers = new ArrayList<Player>();
        this.spectators = new ArrayList<Player>();
        this.status = Status.STARTING;
    }
    
    /**
     * Setup player when joining game world.
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        int playersInWorld = this.world.getPlayers().size();
        
        this.clearItemsAll(player);
        this.clearPlayerBoard(player);
        
        if (player.getWorld() != this.world) {
            return;
        }
        
        GameModeChecker.setSurvival(player);
        
        if (this.scoreboardPlayers.contains(player)) {
            this.updateScoreboards();
            return;
        }
        
        // Set to spectator if too many players or game has started.
        if (playersInWorld > this.maxPlayers || this.status != Status.STARTING) {
            if (!this.spectators.contains(player)) {
                this.spectators.add(player);
            }
            
            this.tp.teleportPlayerToStart(player);
            
            // Spectators require delay for board.
            new BukkitRunnable() {
                
                public void run() {
                    updateScoreboards();
                }
                
            }.runTaskLater(this.plugin, 1);
            
            GameModeChecker.setSpectator(player);  
            return;
        }
                
        if (this.active) {
            return;
        }
                
        // Start if minimum player requirement is met.
        if (playersInWorld >= this.minPlayers && this.status == Status.STARTING) {
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
                player.setVelocity(new Vector(initialVelocity.getX() * 1.5, initialVelocity.getY() + 0.35, initialVelocity.getZ() * 1.5));
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
        
        if (!this.scoreboardPlayers.contains(player)) {
            return;
        }
        
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
                
        this.updateScoreboards();
    }
    
    /**
     * Check for player respawn.
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        
        if (player.getWorld() != this.world) {
            return;
        }
        
        if (this.status != Status.RUNNING) {        
            
            new BukkitRunnable() {
                
                public void run() {
                    tp.teleportPlayerToStart(player);
                }
                
            }.runTaskLater(this.plugin, 1);
            
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
            this.scoreboardPlayers.clear();
            this.spectators.clear();
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
            sendMassMessage(this.scoreboardPlayers, Defaults.GAME_TAG + ChatColor.DARK_RED + " Game is over! Thanks for playing!");
            sendMassMessage(this.spectators, Defaults.GAME_TAG + ChatColor.DARK_RED + " Game is over!");
            
            Player winner = this.getWinner();
            
            if (winner != null) {
                String msg = Defaults.GAME_TAG + ChatColor.DARK_RED + " Winner is " + ChatColor.GREEN + winner.getDisplayName() + "!";
                sendMassMessage(this.scoreboardPlayers, msg);
                sendMassMessage(this.spectators, msg);
            }
            
            // Show everyone their score
            for (Player player : this.scoreboardPlayers) {
                player.sendMessage(Defaults.GAME_TAG + ChatColor.DARK_RED + " Your score is " + ChatColor.GREEN + this.getPlayerScore(player) + "!");               
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fe grant " + player.getName() + " " + this.getPlayerScore(player));
                this.clearItemsAll(player);
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
        this.scoreboardPlayers.clear();
        this.spectators.clear();
        this.world.getEntities().clear();
        
        new BukkitRunnable() {
            
            public void run() {
                sendMassMessage(world.getPlayers(), Defaults.GAME_TAG + ChatColor.DARK_RED + " You are being teleported back to the hub...");
                GameModeChecker.setSurvivalAll(world.getPlayers());
                tp.teleportPlayersToSpawn(world.getPlayers());
                active = false;
                status = Status.STARTING;
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
        
        // Event items
        player.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET, 1));
        player.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE, 1));
        player.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS, 1));
        player.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS, 1));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD, 1));
        ItemStack powerBow = new ItemStack(Material.BOW, 1);
        powerBow.addEnchantment(Enchantment.ARROW_DAMAGE, 1);
        player.getInventory().addItem(powerBow);
        player.getInventory().addItem(new ItemStack(Material.ARROW, 32));
        
        this.pl.spawnPlayer(player);
        this.updateScoreboards();
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
        objective.setDisplayName("" + ChatColor.GREEN + ChatColor.BOLD + "BOUNCE");
        
        for (Player participant : this.scoreboardPlayers) {
            objective.getScore(participant.getName().toString()).setScore(getPlayerScore(participant));
        }
        
        player.setScoreboard(board);
    }
    
    /**
     * Update all player scoreboards.
     */
    public void updateScoreboards() {
        for (Player participants : this.scoreboardPlayers) {
            this.setPlayerBoard(participants);
        }
        
        for (Player spectator : this.spectators) {
            this.setPlayerBoard(spectator);
        }
    }
    
    /**
     * Clear all items for player.
     */
    public void clearItemsAll(Player player) {
        player.getEquipment().setHelmet(new ItemStack(Material.AIR));
        player.getEquipment().setChestplate(new ItemStack(Material.AIR));
        player.getEquipment().setLeggings(new ItemStack(Material.AIR));
        player.getEquipment().setBoots(new ItemStack(Material.AIR));
        player.getInventory().clear();
    }
    
    /**
     * Clear Scoreboard for player.
     */
    public void clearPlayerBoard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(board);
    }
    
    /**
     * Return winner of the game.
     * @return
     */
    public Player getWinner() {
        if (this.scoreboardPlayers.isEmpty()) {
            return null;
        }
        
        List<Player> tiedPlayers = new ArrayList<Player>();
        tiedPlayers.add(this.scoreboardPlayers.get(new Random().nextInt(this.scoreboardPlayers.size())));
        int winnerScore = 0;
        
        for (Player player : this.scoreboardPlayers) {
            if ((int) getPlayerScore(player) > winnerScore) {
                winnerScore = getPlayerScore(player);
                tiedPlayers.clear();
                tiedPlayers.add(player);
            }
            
            if ((int) getPlayerScore(player) == winnerScore) {
                winnerScore = getPlayerScore(player);
                tiedPlayers.add(player);
            }
        }
        
        return tiedPlayers.get(new Random().nextInt(tiedPlayers.size()));
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
	

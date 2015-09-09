package com.fuzzycraft.fuzzy.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.fuzzycraft.fuzzy.Bounce;

public class Quit implements CommandExecutor {

    public static final String CMD = "QUIT";
    
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if(commandLabel.equalsIgnoreCase(CMD)) {
            Player player = (Player) sender;
            player.teleport(Bounce.spawn);
        }
        
        return false;
    }
}

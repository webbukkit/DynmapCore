package org.dynmap;

import java.util.HashSet;
import java.util.Set;

import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;

/**
 * Handler for world and map edit commands (via /dmap)
 */
public class DynmapMapCommands {

    public boolean processCommand(DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapCore core) {
        /* Re-parse args - handle doublequotes */
        args = DynmapCore.parseArgs(args, sender);
        if(args.length < 1)
            return false;
        cmd = args[0];
        if(cmd.equalsIgnoreCase("worldlist")) {
            return handleWorldList(sender, args, core);
        }
        else if(cmd.equalsIgnoreCase("worldset")) {
            return handleWorldSet(sender, args, core);
        }
        return false;
    }
    
    private boolean handleWorldList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldlist"))
            return true;
        Set<String> wnames = null;
        if(args.length > 1) {
            wnames = new HashSet<String>();
            for(int i = 1; i < args.length; i++)
                wnames.add(args[i]);
        }
        /* Get active worlds */
        for(DynmapWorld w : core.getMapManager().getWorlds()) {
            if((wnames != null) && (wnames.contains(w.getName()) == false)) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("world ").append(w.getName()).append(": loaded=").append(w.isLoaded()).append(", enabled=").append(w.isEnabled());
            DynmapLocation loc = w.getCenterLocation();
            if(loc != null) {
                sb.append(", center=").append(loc.x).append("/").append(loc.y).append("/").append(loc.z);
            }
            sb.append(", extrazoomout=").append(w.getExtraZoomOutLevels());
            sender.sendMessage(sb.toString());
        }
        /* Get disabled worlds */
        for(String wn : core.getMapManager().getDisabledWorlds()) {
            if((wnames != null) && (wnames.contains(wn) == false)) {
                continue;
            }
            sender.sendMessage("world " + wn + ": isenabled=false");
        }
        
        return true;
    }
    private boolean handleWorldSet(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldset"))
            return true;
        if(args.length < 3) {
            sender.sendMessage("World name and setting:newvalue required");
            return false;
        }
        String wname = args[1]; /* Get world name */
        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        
        boolean did_update = false;
        for(int i = 2; i < args.length; i++) {
            String[] tok = args[i].split(":");  /* Split at colon */
            if(tok.length != 2) {
                sender.sendMessage("Syntax error: " + args[i]);
                return false;
            }
            if(tok[0].equalsIgnoreCase("enabled")) {
                did_update |= core.setWorldEnable(wname, !tok[1].equalsIgnoreCase("false"));
            }
            else if(tok[0].equalsIgnoreCase("extrazoomout")) {  /* Extrazoomout setting */
                if(w == null) {
                    sender.sendMessage("Cannot set extrazoomout on disabled or undefined world");
                    return true;
                }
                int exo = -1;
                try {
                    exo = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {}
                if((exo < 0) || (exo > 32)) {
                    sender.sendMessage("Invalid value for extrazoomout: " + tok[1]);
                    return true;
                }
                did_update |= core.setWorldZoomOut(wname, exo);
            }
            else if(tok[0].equalsIgnoreCase("center")) {    /* Center */
                if(w == null) {
                    sender.sendMessage("Cannot set center on disabled or undefined world");
                    return true;
                }
                boolean good = false;
                DynmapLocation loc = null;
                try {
                    String[] toks = tok[1].split("/");
                    if(toks.length == 3) {
                        double x = 0, y = 0, z = 0;
                        x = Double.valueOf(toks[0]);
                        y = Double.valueOf(toks[1]);
                        z = Double.valueOf(toks[2]);
                        loc = new DynmapLocation(wname, x, y, z);
                       good = true;
                    }
                    else if(tok[1].equalsIgnoreCase("default")) {
                        good = true;
                    }
                    else if(tok[1].equalsIgnoreCase("here")) {
                        if(sender instanceof DynmapPlayer) {
                            loc = ((DynmapPlayer)sender).getLocation();
                        }
                        else {
                            sender.sendMessage("Setting center to 'here' requires player");
                            return true;
                        }
                    }
                } catch (NumberFormatException nfx) {}
                if(!good) {
                    sender.sendMessage("Center value must be formatted x/y/z or be set to 'default' or 'here'");
                    return true;
                }
                did_update |= core.setWorldCenter(wname, loc);
            }
        }
        /* If world updatd, refresh it */
        if(did_update) {
            sender.sendMessage("Refreshing configuration for world " + wname);
            core.refreshWorld(wname);
        }
        
        return true;
    }
}

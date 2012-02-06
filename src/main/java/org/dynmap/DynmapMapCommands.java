package org.dynmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.kzedmap.MapTileRenderer;

/**
 * Handler for world and map edit commands (via /dmap)
 */
public class DynmapMapCommands {

    private boolean checkIfActive(DynmapCore core, DynmapCommandSender sender, String wname) {
        MapManager mm = core.getMapManager();
        if((mm != null) && mm.isRenderJobActive(wname)) {
            sender.sendMessage("Cannot edit map data while render job active.  Run /dynmap cancelrender " + wname);
            return true;
        }
        return false;
    }
    
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
        else if(cmd.equalsIgnoreCase("maplist")) {
            return handleMapList(sender, args, core);
        }
        else if(cmd.equalsIgnoreCase("mapdelete")) {
            return handleMapDelete(sender, args, core);
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
            return true;
        }
        String wname = args[1]; /* Get world name */
        /* Test if render active - quit if so */
        if(checkIfActive(core, sender, wname)) {
            return true;
        }
        
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
            else if(tok[0].equalsIgnoreCase("order")) {
                if(w == null) {
                    sender.sendMessage("Cannot set center on disabled or undefined world");
                    return true;
                }
                int order = -1;
                try {
                    order = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {}
                if(order < 1) {
                    sender.sendMessage("Order value must be number from 1 to number of worlds");
                    return true;
                }
                did_update |= core.setWorldOrder(wname, order-1);
            }
        }
        /* If world updatd, refresh it */
        if(did_update) {
            sender.sendMessage("Refreshing configuration for world " + wname);
            core.refreshWorld(wname);
        }
        
        return true;
    }
    
    private boolean handleMapList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.maplist"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World name is required");
            return true;
        }
        String wname = args[1]; /* Get world name */
        
        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        if(w == null) { 
            sender.sendMessage("Only loaded world can be listed");
            return true;
        }
        List<MapType> maps = w.maps;
        for(MapType mt : maps) {
            if(mt instanceof KzedMap) {
                KzedMap km = (KzedMap)mt;
                for(MapTileRenderer r : km.renderers) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("map ").append(r.getName()).append(": class=").append(r.getClass().getName());
                    sender.sendMessage(sb.toString());
                }
            }
            else {
                StringBuilder sb = new StringBuilder();
                sb.append("map ").append(mt.getName()).append(": class=").append(mt.getClass().getName());
                sender.sendMessage(sb.toString());
            }
        }
        
        return true;
    }
    private boolean handleMapDelete(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.mapdelete"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World:map name required");
            return true;
        }
        for(int i = 1; i < args.length; i++) {
            String world_map_name = args[i];
            String[] tok = world_map_name.split(":");
            if(tok.length != 2) {
                sender.sendMessage("Invalid world:map name: " + world_map_name);
                return true;
            }
            String wname = tok[0];
            String mname = tok[1];
            /* Test if render active - quit if so */
            if(checkIfActive(core, sender, wname)) {
                return true;
            }
            DynmapWorld w = core.getWorld(wname);   /* Try to get world */
            if(w == null) {
                sender.sendMessage("Cannot delete maps from disabled or unloaded world: " + wname);
                return true;
            }
            List<MapType> maps = new ArrayList<MapType>(w.maps);
            boolean done = false;
            for(int idx = 0; (!done) && (idx < maps.size()); idx++) {
                MapType mt = maps.get(idx);
                if(mt instanceof KzedMap) {
                    KzedMap km = (KzedMap)mt;
                    MapTileRenderer[] rnd = km.renderers;
                    for(int ridx = 0; (!done) && (ridx < rnd.length); ridx++) {
                        if(rnd[ridx].getName().equals(mname)) {
                            /* If last one, delete whole map */
                            if(rnd.length == 1) {
                                w.maps.remove(mt);
                            }
                            else {  /* Remove from list */
                                MapTileRenderer[] newrnd = new MapTileRenderer[rnd.length-1];
                                for(int k = 0; k < ridx; k++) {
                                    newrnd[k] = rnd[k];
                                }
                                for(int k = ridx; k < newrnd.length - 1; k++) {
                                    newrnd[k] = rnd[k+1];
                                }
                                km.renderers = newrnd;
                            }
                            done = true;
                        }
                    }
                }
                else if(mt.getName().equals(mname)) {
                    w.maps.remove(mt);
                    done = true;
                }
            }
            /* If done, save updated config for world */
            if(done) {
                if(core.updateWorldConfig(w)) {
                    sender.sendMessage("Refreshing configuration for world " + wname);
                    core.refreshWorld(wname);
                }
            }
        }
        
        return true;
    }
}

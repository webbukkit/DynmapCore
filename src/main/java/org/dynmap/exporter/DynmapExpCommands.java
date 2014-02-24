package org.dynmap.exporter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.hdmap.HDLighting;
import org.dynmap.hdmap.HDMap;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.hdmap.HDShader;
import org.dynmap.kzedmap.KzedMap;
import org.dynmap.kzedmap.MapTileRenderer;

/**
 * Handler for export commands (/dynmapexp)
 */
public class DynmapExpCommands {

    public boolean processCommand(DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapCore core) {
        /* Re-parse args - handle doublequotes */
        args = DynmapCore.parseArgs(args, sender);
        if(args.length < 1)
            return false;
        cmd = args[0];
        boolean rslt = false;
        
        if(cmd.equalsIgnoreCase("shader")) {
            rslt = handleShaderExport(sender, args, core);
        }
        else if (cmd.equalsIgnoreCase("radius")) {
            rslt = handleRadiusExport(sender, args, core);
        }
        return rslt;
    }

    private boolean handleShaderExport(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dynmapexp.shader")) {
            return true;
        }
        if (args.length > 1) {
            HDShader s = MapManager.mapman.hdmapman.shaders.get(args[1]);
            if (s == null) {
                sender.sendMessage("Unknown shader '" + args[1] + "'");
                return true;
            }
            File f;
            if (args.length > 2) {
                f = new File(core.getExportFolder(), args[2]);
                if (f.getParentFile().equals(core.getExportFolder()) == false) {
                    sender.sendMessage("Invalid file name '" + args[2] + "'");
                    return true;
                }
            }
            else {
                f = new File(core.getExportFolder(), args[1] + ".zip");
            }
            sender.sendMessage("Exporting shader '" + args[1] + "' to " + f.getPath());
            OBJExport exp = new OBJExport(f, s, null, core);
            MapManager.mapman.startOBJExport(exp, sender);
        }
        else {
            sender.sendMessage("Shader name required");
        }
        return true;
    }
    private boolean handleRadiusExport(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if ((sender instanceof DynmapPlayer) == false) {    // Not a player
            sender.sendMessage("Only usable by player");
            return true;
        }
        DynmapPlayer plyr = (DynmapPlayer) sender;
        DynmapLocation loc = plyr.getLocation();
        DynmapWorld world = null;
        if (loc != null) {
            world = core.getWorld(loc.world);
        }
        if (world == null) {
            sender.sendMessage("Location not found for player");
            return true;
        }
        if(!core.checkPlayerPermission(sender, "dynmapexp.radius")) {
            return true;
        }
        int rad = 16;
        String shadername = "stdtexture";
        if (args.length > 1) {
            try {
                rad = Integer.parseInt(args[1]);
            } catch (NumberFormatException x) {
                sender.sendMessage("Invalid radius: " + args[1]);
                return true;
            }
        }
        if (args.length > 2) {
            shadername = args[2];
        }
        HDShader s = MapManager.mapman.hdmapman.shaders.get(shadername);
        if (s == null) {
            sender.sendMessage("Unknown shader '" + shadername + "'");
            return true;
        }
        String fname = shadername + ".zip";
        File f;
        if (args.length > 3) {
            fname = args[3];
        }
        f = new File(core.getExportFolder(), fname);
        if (f.getParentFile().equals(core.getExportFolder()) == false) {
            sender.sendMessage("Invalid file name '" + fname + "'");
            return true;
        }
        sender.sendMessage("Exporting radius of " + rad + "blocks using shader '" + shadername + "' to " + f.getPath());
        OBJExport exp = new OBJExport(f, s, world, core);
        exp.setRenderBounds((int)loc.x - rad, 0, (int)loc.z - rad, (int)loc.x + rad, world.worldheight, (int)loc.z + rad);
        MapManager.mapman.startOBJExport(exp, sender);
        
        return true;
    }
}

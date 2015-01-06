package org.dynmap.common;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.utils.MapChunkCache;

/**
 * This interface defines a server-neutral interface for the DynmapCore and other neutral components to use to access server provided
 * services.  Platform-specific plugin must supply DynmapCore with an instance of an object implementing this interface.
 */
public abstract class DynmapServerInterface {
    /**
     * Schedule task to run on server-safe thread (one suitable for other server API calls)
     * @param run - runnable method
     * @param delay - delay in server ticks (50msec)
     */
    public abstract void scheduleServerTask(Runnable run, long delay);
    /**
     * Call method on server-safe thread
     * @param task - Callable method
     * @return future for completion of call
     */
    public abstract <T> Future<T> callSyncMethod(Callable<T> task);
    /**
     * Get list of online players
     * @return list of online players
     */
    public abstract DynmapPlayer[] getOnlinePlayers();
    /**
     * Request reload of plugin
     */
    public abstract void reload();
    /**
     * Get active player
     * @param name - player name
     * @return player
     */
    public abstract DynmapPlayer getPlayer(String name);
    /**
     * Get offline player
     * @param name - player name
     * @return player (offline or not)
     */
    public abstract DynmapPlayer getOfflinePlayer(String name);
    
    /**
     * Get banned IPs
     */
    public abstract Set<String> getIPBans();
    /**
     * Get server name
     */
    public abstract String getServerName();
    /**
     * Test if player ID is banned
     */
    public abstract boolean isPlayerBanned(String pid);    
    /**
     * Strip out chat color
     */
    public abstract String stripChatColor(String s);
    /**
     * Request notificiation for given events (used by DynmapListenerManager)
     */
    public abstract boolean requestEventNotification(EventType type);
    /**
     * Send notification of web chat message
     * @param source - source
     * @param name - name
     * @param msg - message text
     * @return true if not cancelled
     */
    public abstract boolean sendWebChatEvent(String source, String name, String msg);
    /**
     * Broadcast message to players
     * @param msg
     */
    public abstract void broadcastMessage(String msg);
    /**
     * Get Biome ID list
     */
    public abstract String[] getBiomeIDs();
    /**
     * Get snapshot cache hit rate
     */
    public abstract double getCacheHitRate();
    /**
     * Reset cache stats
     */
    public abstract void resetCacheStats();
    /**
     * Get world by name
     */
    public abstract DynmapWorld getWorldByName(String wname);
    /**
     * Test which of given set of permisssions a possibly offline user has
     */
    public abstract Set<String> checkPlayerPermissions(String player, Set<String> perms);
    /**
     * Test single permission attribute
     */
    public abstract boolean checkPlayerPermission(String player, String perm);
    /**
     * Render processor helper - used by code running on render threads to request chunk snapshot cache
     */
    public abstract MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks, 
        boolean blockdata, boolean highesty, boolean biome, boolean rawbiome);
    /**
     * Get maximum player count
     */
    public abstract int getMaxPlayers();
    /**
     * Get current player count
     */
    public abstract int getCurrentPlayers();
    /**
     * Test if given mod is loaded (Forge)
     * @param name - mod name
     */
    public boolean isModLoaded(String name) {
        return false;
    }
    /**
     * Get version of mod with given name
     * 
     * @param name - name of mod
     * @return version, or null of not found
     */
    public String getModVersion(String name) {
        return null;
    }

    /**
     * Get block ID at given coordinate in given world (if chunk is loaded)
     * @param wname - world name
     * @param x - X coordinate
     * @param y - Y coordinate
     * @param z - Z coordinate
     * @return block ID, or -1 if chunk at given coordinate isn't loaded
     */
    public abstract int getBlockIDAt(String wname, int x, int y, int z);
    /**
     * Get current TPS for server (20.0 is nominal)
     * @return ticks per second
     */
    public abstract double getServerTPS();
    /**
     * Get address configured for server
     * 
     * @return "" or null if none configured
     */
    public abstract String getServerIP();
    /**
     * Get file/directory for given mod (for loading mod resources)
     * @param mod - mod name
     * @return file or directory, or null if not loaded
     */
    public File getModContainerFile(String mod) {
        return null;
    }
    /**
     * Get mod list
     */
    public List<String> getModList() {
        return Collections.emptyList();
    }
    /**
     * Get block ID map (modID:blockname, keyed by block ID)
     */
    public Map<Integer, String> getBlockIDMap() {
        return Collections.emptyMap();
    }
    /**
     * Open resource (check all mods)
     * @param modid - mod id
     * @param rname - resource namep
     * @return stream, or null
     */
    public InputStream openResource(String modid, String rname) {
        return null;
    }
    /**
     * Get block unique ID map (module:blockid)
     */
    public Map<String, Integer> getBlockUniqueIDMap() {
        return Collections.emptyMap();
    }
    /**
     * Get item unique ID map (module:itemid)
     */
    public Map<String, Integer> getItemUniqueIDMap() {
        return Collections.emptyMap();
    }
    /**
     * Test if current thread is server thread
     */
    public boolean isServerThread() {
        return false;
    }
}

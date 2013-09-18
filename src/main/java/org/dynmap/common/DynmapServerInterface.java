package org.dynmap.common;

import java.io.File;
import java.util.List;
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
public interface DynmapServerInterface {
    /**
     * Schedule task to run on server-safe thread (one suitable for other server API calls)
     * @param run - runnable method
     * @param delay - delay in server ticks (50msec)
     */
    public void scheduleServerTask(Runnable run, long delay);
    /**
     * Call method on server-safe thread
     * @param call - Callable method
     * @return future for completion of call
     */
    public <T> Future<T> callSyncMethod(Callable<T> task);
    /**
     * Get list of online players
     * @return list of online players
     */
    public DynmapPlayer[] getOnlinePlayers();
    /**
     * Request reload of plugin
     */
    public void reload();
    /**
     * Get active player
     * @param name - player name
     * @return player
     */
    public DynmapPlayer getPlayer(String name);
    /**
     * Get offline player
     * @param name - player name
     * @reurn player (offline or not)
     */
    public DynmapPlayer getOfflinePlayer(String name);
    
    /**
     * Get banned IPs
     */
    public Set<String> getIPBans();
    /**
     * Get server name
     */
    public String getServerName();
    /**
     * Test if player ID is banned
     */
    public boolean isPlayerBanned(String pid);    
    /**
     * Strip out chat color
     */
    public String stripChatColor(String s);
    /**
     * Request notificiation for given events (used by DynmapListenerManager)
     */
    public boolean requestEventNotification(EventType type);
    /**
     * Send notification of web chat message
     * @param source - source
     * @param name - name
     * @param msg - message text
     * @return true if not cancelled
     */
    public boolean sendWebChatEvent(String source, String name, String msg);
    /**
     * Broadcast message to players
     * @param msg
     */
    public void broadcastMessage(String msg);
    /**
     * Get Biome ID list
     */
    public String[] getBiomeIDs();
    /**
     * Get snapshot cache hit rate
     */
    public double getCacheHitRate();
    /**
     * Reset cache stats
     */
    public void resetCacheStats();
    /**
     * Get world by name
     */
    public DynmapWorld getWorldByName(String wname);
    /**
     * Test which of given set of permisssions a possibly offline user has
     */
    public Set<String> checkPlayerPermissions(String player, Set<String> perms);
    /**
     * Test single permission attribute
     */
    public boolean checkPlayerPermission(String player, String perm);
    /**
     * Render processor helper - used by code running on render threads to request chunk snapshot cache
     */
    public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks, 
        boolean blockdata, boolean highesty, boolean biome, boolean rawbiome);
    /**
     * Get maximum player count
     */
    public int getMaxPlayers();
    /**
     * Get current player count
     */
    public int getCurrentPlayers();
    /**
     * Test if given mod is loaded (Forge)
     * @param name - mod name
     */
    public boolean isModLoaded(String name);
    /**
     * Get version of mod with given name
     * 
     * @param name - name of mod
     * @return version, or null of not found
     */
    public String getModVersion(String name);

    /**
     * Get block ID at given coordinate in given world (if chunk is loaded)
     * @param wname - world name
     * @param x - X coordinate
     * @param y - Y coordinate
     * @param z - Z coordinate
     * @return block ID, or -1 if chunk at given coordainte isnt loaded
     */
    public int getBlockIDAt(String wname, int x, int y, int z);
    /**
     * Get current TPS for server (20.0 is nominal)
     * @returns ticks per second
     */
    public double getServerTPS();
    /**
     * Get address configured for server
     * 
     * @return "" or null if none configured
     */
    public String getServerIP();
    /**
     * Get file/directory for given mod (for loading mod resources)
     * @param mod - mod name
     * @return file or directory, or null if not loaded
     */
    public File getModContainerFile(String mod);
}

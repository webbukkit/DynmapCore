package org.dynmap.storage;

import java.util.zip.CRC32;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.MapType;

/**
 * Generic interface for map data storage (image tiles, and associated hash codes)
 */
public abstract class MapStorage {
    protected long serverID;
    
    protected MapStorage() {
        this.serverID = 0;
    }
    
    /**
     * Initialize with core
     * @param core - core instance
     */
    public boolean init(DynmapCore core) {
        return true;
    }
    
    /**
     * Set server ID for map storage instance
     * @param serverID - server ID (default is zero)
     */
    public void setServerID(long serverID) {
        this.serverID = serverID;
    }
    
    /**
     * Get tile reference for given tile
     *
     * @param world - world
     * @param map - map
     * @param x - tile X coordinate
     * @param y - tile Y coordinate
     * @param zoom - tile zoom level (0=base rendered tiles)
     * @param var - tile variant (standard, day, etc)
     * @returns MapStorageTile for given coordinate (whether or not tile exists)
     */
    public abstract MapStorageTile getTile(DynmapWorld world, MapType map, int x, int y, int zoom, MapType.ImageVariant var);

    /**
     * Get tile reference for given tile, by world and URI
     *
     * @param world - world
     * @param uri - tile URI
     * @returns MapStorageTile for given coordinate (whether or not tile exists)
     */
    public abstract MapStorageTile getTile(DynmapWorld world, String uri);

    /**
     * Enumerate existing map tiles, matching given constraints
     * @param world - specific world
     * @param map - specific map (if non-null)
     * @param cb - callback to receive matching tiles
     */
    public abstract void enumMapTiles(DynmapWorld world, MapType map, MapStorageTileEnumCB cb);

    /**
     * Purge existing map tiles, matching given constraints
     * @param world - specific world
     * @param map - specific map (if non-null)
     */
    public abstract void purgeMapTiles(DynmapWorld world, MapType map);

    /**
     * Calculate hashcode for raw image buffer
     * @param buf - ARGB array
     * @param off - offset of start in array
     * @param len - length of image data
     * @return hashcode (>= 0)
     */
    public static long calculateImageHashCode(int[] buf, int off, int len) {
        CRC32 crc32 = new CRC32();
        final int perCall = 256;
        int accum = 0;
        byte[] crcworkbuf = new byte[4 * perCall];
        for (int i = 0; i < len; i++) {
            int v = buf[i + off];
            crcworkbuf[accum++] = (byte)v;
            crcworkbuf[accum++] = (byte)(v>>8);
            crcworkbuf[accum++] = (byte)(v>>16);
            crcworkbuf[accum++] = (byte)(v>>24);
            if (accum == crcworkbuf.length) {
                crc32.update(crcworkbuf, 0, accum);
                accum = 0;
            }
        }
        if (accum > 0) {    // Remainder?
            crc32.update(crcworkbuf, 0, accum);
            accum = 0;
        }
        return crc32.getValue();
    }
}

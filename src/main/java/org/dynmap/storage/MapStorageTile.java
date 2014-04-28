package org.dynmap.storage;

import org.dynmap.DynmapWorld;
import org.dynmap.MapType;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;

/**
 * Abstract class for instance of a stored map tile
 */
public abstract class MapStorageTile {
    public final DynmapWorld world;
    public final MapType map;
    public final int x, y;
    public final int zoom;
    public final MapType.ImageVariant var;
    
    public static class TileRead {
        public BufferInputStream image;    // Image bytes
        public MapType.ImageFormat format; // Image format
        public long hashCode;              // Image hashcode (-1 = unknown)
    }

    protected MapStorageTile(DynmapWorld world, MapType map, int x, int y, int zoom, MapType.ImageVariant var) {
        this.world = world;
        this.map = map;
        this.x = x;
        this.y = y;
        this.zoom = zoom;
        this.var = var;
    }
    /**
     * Test if given tile exists in the tile storage
     * @param fmt - tile format
     * @returns true if tile exists, false if not
     */
    public abstract boolean exists(MapType.ImageFormat fmt);
    /**
     * Test if tile exists and matches given hash code
     * @param fmt - tile format
     * @param hash - hash code to test against tile's content
     * @returns true if tile exists and matches given hash code, false if not
     */
    public abstract boolean matchesHashCode(MapType.ImageFormat fmt, long hash);
    /**
     * Read tile
     *
     * @return loaded Tile, or null if not read
     */
    public abstract TileRead read();
    /**
     * Write tile
     *
     * @param fmt - tile format
     * @param hash - hash code of uncompressed image
     * @param encImage - output stream for encoded image
     * @return true if write succeeded
     */
    public abstract boolean write(MapType.ImageFormat fmt, long hash, BufferOutputStream encImage);
    /**
     * Delete tile
     *
     * @param fmt - tile format
     * @param hash - hash code of uncompressed image
     * @param encImage - output stream for encoded image
     * @return true if write succeeded
     */
    public boolean delete(MapType.ImageFormat fmt) {
        return write(fmt, -1, null);
    }
    /**
     * Get write lock on tile
     */
    public abstract boolean getWriteLock();
    /**
     * Release write lock on tile
     */
    public abstract void releaseWriteLock();
    /**
     * Get read lock on tile
     * @param timeout - timeout, in msec (-1 = never)
     * @return true if lock acquired, false if not (timeout)
     */
    public abstract boolean getReadLock(long timeout);
    /**
     * Get read lock on tile (indefinite timeout)
     * @return true if lock acquired, false if not (timeout)
     */
    public boolean getReadLock() {
        return getReadLock(-1L);
    }
    /**
     * Release read lock on tile
     */
    public abstract void releaseReadLock();
    /**
     * Cleanup
     */
    public abstract void cleanup();
    /**
     * Get URI for tile (for web interface)
     */
    public abstract String getURI(MapType.ImageFormat fmt);
    /**
     * Enqueue zoom out update for tile
     * @param fmt - image format
     */
    public abstract void enqueueZoomOutUpdate(MapType.ImageFormat fmt);
}

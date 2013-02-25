package org.dynmap.utils;

import org.dynmap.DynmapWorld;
import org.dynmap.common.BiomeMap;

public interface MapChunkCache {
    public enum HiddenChunkStyle {
        FILL_AIR,
        FILL_STONE_PLAIN,
        FILL_OCEAN
    };
    /**
     * Set chunk data type needed
     * @param blockdata - need block type and data for chunk
     * @param biome - need biome data
     * @param highestblocky - need highest-block-y data
     * @param rawbiome - need raw biome temp/rain data
     * @return true if all data types can be retrieved, false if not
     */
    boolean setChunkDataTypes(boolean blockdata, boolean biome, boolean highestblocky, boolean rawbiome);
    /**
     * Load chunks into cache
     * @param maxToLoad - maximum number to load at once
     * @return number loaded
     */
    int loadChunks(int maxToLoad);
    /**
     * Test if done loading
     */
    boolean isDoneLoading();
    /**
     * Test if all empty blocks
     */
    boolean isEmpty();
    /**
     * Unload chunks
     */
    void unloadChunks();
    /**
     * Test if section (16 x 16 x 16) at given coord is empty (all air)
     */
    boolean isEmptySection(int sx, int sy, int sz);
    /**
     * Get cache iterator
     */
    public MapIterator getIterator(int x, int y, int z);
    /**
     * Set hidden chunk style (default is FILL_AIR)
     */
    public void setHiddenFillStyle(HiddenChunkStyle style);
    /**
     * Add visible area limit - can be called more than once 
     * Needs to be set before chunks are loaded
     * Coordinates are block coordinates
     */
    public void setVisibleRange(VisibilityLimit limit);
    /**
     * Add hidden area limit - can be called more than once 
     * Needs to be set before chunks are loaded
     * Coordinates are block coordinates
     */
    public void setHiddenRange(VisibilityLimit limit);
    /**
     * Get world
     */
    public DynmapWorld getWorld();
    /**
     * Get total chunks loaded
     * @return
     */
    public int getChunksLoaded();
    /**
     * Get total chunk loads attempted
     * @return
     */
    public int getChunkLoadsAttempted();
    /**
     * Get total run time processing chunks
     * @return
     */
    public long getTotalRuntimeNanos();
    
    public long getExceptionCount();
}

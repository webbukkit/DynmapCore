package org.dynmap.utils;

import org.dynmap.DynmapWorld;

public abstract class MapChunkCache {
    public enum HiddenChunkStyle {
        FILL_AIR,
        FILL_STONE_PLAIN,
        FILL_OCEAN
    };
    public enum ChunkStats {
        CACHED_SNAPSHOT_HIT("Cached"),          // Stats for cached snapshot hits
        LOADED_CHUNKS("Already Loaded"),        // Stats for snapshotting already loaded chunks
        UNLOADED_CHUNKS("Load Required"),     // Stats for chunks requiring load/unload
        UNGENERATED_CHUNKS("Not Generated");    // Stats for chunks requested that did not exist
        private final String label;
        ChunkStats(String lbl) {
            label = lbl;
        }
        public String getLabel() { return label; }
    };
    
    private long timeTotal[] = new long[ChunkStats.values().length];
    private int cntTotal[] = new int[ChunkStats.values().length];

    /**
     * Set chunk data type needed
     * @param blockdata - need block type and data for chunk
     * @param biome - need biome data
     * @param highestblocky - need highest-block-y data
     * @param rawbiome - need raw biome temp/rain data
     * @return true if all data types can be retrieved, false if not
     */
    public abstract boolean setChunkDataTypes(boolean blockdata, boolean biome, boolean highestblocky, boolean rawbiome);
    /**
     * Load chunks into cache
     * @param maxToLoad - maximum number to load at once
     * @return number loaded
     */
    public abstract int loadChunks(int maxToLoad);
    /**
     * Test if done loading
     */
    public abstract boolean isDoneLoading();
    /**
     * Test if all empty blocks
     */
    public abstract boolean isEmpty();
    /**
     * Unload chunks
     */
    public abstract void unloadChunks();
    /**
     * Test if section (16 x 16 x 16) at given coord is empty (all air)
     */
    public abstract boolean isEmptySection(int sx, int sy, int sz);
    /**
     * Get cache iterator
     */
    public abstract MapIterator getIterator(int x, int y, int z);
    /**
     * Set hidden chunk style (default is FILL_AIR)
     */
    public abstract void setHiddenFillStyle(HiddenChunkStyle style);
    /**
     * Add visible area limit - can be called more than once 
     * Needs to be set before chunks are loaded
     * Coordinates are block coordinates
     */
    public abstract void setVisibleRange(VisibilityLimit limit);
    /**
     * Add hidden area limit - can be called more than once 
     * Needs to be set before chunks are loaded
     * Coordinates are block coordinates
     */
    public abstract void setHiddenRange(VisibilityLimit limit);
    /**
     * Get world
     */
    public abstract DynmapWorld getWorld();
    /**
     * Get number of chunks with given disposition
     * @param type - chunk load type
     * @return
     */
    public int getChunksLoaded(ChunkStats type) {
        return cntTotal[type.ordinal()];
    }
    /**
     * Get total run time processing chunks with given disposition
     * @param type - chunk load type
     * @return
     */
    public long getTotalRuntimeNanos(ChunkStats type) {
        return timeTotal[type.ordinal()];
    }
    
    protected void endChunkLoad(long startTime, ChunkStats type) {
        int ord = type.ordinal();
        timeTotal[ord] += System.nanoTime() - startTime;
        cntTotal[ord]++;
    }
}

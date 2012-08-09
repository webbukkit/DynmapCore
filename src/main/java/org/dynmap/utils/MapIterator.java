package org.dynmap.utils;

import org.dynmap.common.BiomeMap;

/**
 * Iterator for traversing map chunk cache (base is for non-snapshot)
 */
public interface MapIterator {
    /**
     * Initialize iterator at given coordinates
     * 
     * @param x0
     * @param y0
     * @param z0
     */
    void initialize(int x0, int y0, int z0);
    /**
     * Get block ID at current coordinates
     * 
     * @return block id
     */
    int getBlockTypeID();
    /**
     * Get block data at current coordinates
     * @return block data
     */
    int getBlockData();
    /**
     * Get block sky light level at current coordinate
     * @return sky light level
     */
    int getBlockSkyLight();
    /**
     * Get emitted light level at current coordinate
     * @return emitted light level
     */
    int getBlockEmittedLight();
    /**
     * Get biome at coordinates
     */
    public BiomeMap getBiome();
    /**
     * Get smoothed grass color multiplier
     */
    public int getSmoothGrassColorMultiplier(int[] colormap, int width);
    /**
     * Get smoothed foliage color multiplier
     */
    public int getSmoothFoliageColorMultiplier(int[] colormap, int width);
    /**
     * get smoothed water color multiplier
     */
    public int getSmoothWaterColorMultiplier();
    /**
     * get smoothed water color multiplier
     */
    public int getSmoothWaterColorMultiplier(int[] colormap, int width);
    /**
     * Get smoothed color multiplier, given normal and swamp color map
     */
    public int getSmoothColorMultiplier(int[] colormap, int width, int[] swampcolormap, int swampwidth);
    /**
     * Get raw temperature data (0.0-1.0)
     */
    public double getRawBiomeTemperature();
    /**
     * Get raw rainfall data (0.0-1.0)
     */
    public double getRawBiomeRainfall();
    /**
     * Step current position in given direction
     */
    void stepPosition(BlockStep step);
    /**
     * Step current position in opposite of given direction
     */
    void unstepPosition(BlockStep step);
    /**
     * Unstep current position to previous position : return step to take to return
     */
    BlockStep unstepPosition();
    /**
     * Set Y coordinate of current position
     * @param y
     */
    void setY(int y);
    /**
     * Get X coordinate
     */
    int getX();
    /**
     * Get Y coordinate
     */
    int getY();
    /**
     * Get Z coordinate
     */
    int getZ();
    /**
     * Get block ID at 1 step in given direction
     * 
     * @return block id
     */
    int getBlockTypeIDAt(BlockStep s);
    /**
     * Get last step taken
     */
    BlockStep getLastStep();
    /**
     * Get world height
     */
    int getWorldHeight();
    /**
     * Get block key for current position (unique ID for block within cache being iterated)
     */
    long getBlockKey();
    /**
     * Test if current section (16 x 16 x 16) is empty (all air)
     */
    boolean isEmptySection();
}

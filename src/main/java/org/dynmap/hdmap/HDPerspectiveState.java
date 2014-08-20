package org.dynmap.hdmap;

import org.dynmap.utils.MapIterator;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.Vector3D;
import org.dynmap.utils.LightLevels;

public interface HDPerspectiveState {
    /**
     * Get light levels - only available if shader requested it
     */
    void getLightLevels(LightLevels ll);
    /**
     * Get sky light level - only available if shader requested it
     */
    void getLightLevelsAtStep(BlockStep step, LightLevels ll);
    /**
     * Get current block type ID
     */
    int getBlockTypeID();
    /**
     * Get current block data
     */
    int getBlockData();
    /**
     * Get current block render data
     */
    int getBlockRenderData();
    /**
     * Get direction of last block step
     */
    BlockStep getLastBlockStep();
    /**
     * Get perspective scale
     */
    double getScale();
    /**
     * Get start of current ray, in world coordinates
     */
    Vector3D getRayStart();
    /**
     * Get end of current ray, in world coordinates
     */
    Vector3D getRayEnd();
    /**
     * Get pixel X coordinate
     */
    int getPixelX();
    /**
     * Get pixel Y coordinate
     */
    int getPixelY();
    /**
     * Return submodel alpha value (-1 if no submodel rendered)
     */
    int getSubmodelAlpha();
    /**
     * Return subblock coordinates of current ray position
     */
    int[] getSubblockCoord();
    /**
     * Get map iterator
     */
    MapIterator getMapIterator();
    /**
     * Get current texture index
     */
    int getTextureIndex();
    /**
     * Get current U of patch intercept
     */
    double getPatchU();
    /**
     * Get current V of patch intercept
     */
    double getPatchV();
    /**
     * Light level cache
     * @param idx - index of light level (0-3)
     */
    LightLevels getCachedLightLevels(int idx);
}

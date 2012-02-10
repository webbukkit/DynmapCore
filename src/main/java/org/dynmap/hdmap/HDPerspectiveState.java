package org.dynmap.hdmap;

import org.dynmap.utils.MapIterator;
import org.dynmap.utils.MapIterator.BlockStep;
import org.dynmap.utils.Vector3D;

public interface HDPerspectiveState {
    public static class LightLevels {
        public final int sky;
        public final int emitted;
        public LightLevels(int sky, int emitted) {
            this.sky = sky;
            this.emitted = emitted;
        }
    }
    /**
     * Get light levels - only available if shader requested it
     */
    LightLevels getLightLevels();
    /**
     * Get sky light level - only available if shader requested it
     */
    LightLevels getLightLevelsAtStep(BlockStep step);
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
}

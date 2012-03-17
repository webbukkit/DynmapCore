package org.dynmap.common;

/* Generic biome mapping */
public enum BiomeMap {
    NULL,
    RAINFOREST,
    SWAMPLAND(0.8, 0.9, 0xE0FFAE, 0x4E0E4E, 0x4E0E4E),
    SEASONAL_FOREST,
    FOREST(0.7, 0.8),
    SAVANNA,
    SHRUBLAND,
    TAIGA(0.05, 0.8),
    DESERT(2.0, 0.0),
    PLAINS(0.8, 0.4),
    ICE_DESERT,
    TUNDRA,
    HELL(2.0, 0.0),
    SKY,
    OCEAN,
    RIVER,
    EXTREME_HILLS(0.2, 0.3),
    FROZEN_OCEAN(0.0, 0.5),
    FROZEN_RIVER(0.0, 0.5),
    ICE_PLAINS(0.0, 0.5),
    ICE_MOUNTAINS(0.0, 0.5),
    MUSHROOM_ISLAND(0.9, 1.0),
    MUSHROOM_SHORE(0.9, 1.0),
    BEACH(0.8, 0.4),
    DESERT_HILLS(2.0, 0.0),
    FOREST_HILLS(0.7, 0.8),
    TAIGA_HILLS(0.05, 0.8),
    SMALL_MOUNTAINS(0.2, 0.8),
    JUNGLE(1.2, 0.9),
    JUNGLE_HILLS(1.2, 0.9);
    
    private final double tmp;
    private final double rain;
    private final int watercolormult;
    private final int grassmult;
    private final int foliagemult;
    
    private BiomeMap(double tmp, double rain, int waterColorMultiplier, int grassmult, int foliagemult) {
        /* Clamp values : we use raw values from MC code, which are clamped during color mapping only */
        if(tmp > 1.0) tmp = 1.0;
        this.tmp = tmp;
        this.rain = rain;
        this.watercolormult = waterColorMultiplier;
        this.grassmult = grassmult;
        this.foliagemult = foliagemult;
    }
    private BiomeMap() {
        this(0.5, 0.5, 0xFFFFFF, 0, 0);
    }
    private BiomeMap(double tmp, double rain) {
        this(tmp, rain, 0xFFFFFF, 0, 0);
    }
    
    public final int biomeLookup(int width) {
        int w = width-1;
        int t = (int)((1.0-tmp)*w);
        int h = (int)((1.0 - (tmp*rain))*w);
        return width*h + t;
    }
    
    public final int getModifiedGrassMultiplier(int rawgrassmult) {
        if(grassmult == 0)
            return rawgrassmult;
        return ((rawgrassmult & 0xfefefe) + grassmult) / 2;
    }
    
    public final int getModifiedFoliageMultiplier(int rawfoliagemult) {
        if(foliagemult == 0)
            return rawfoliagemult;
        return ((rawfoliagemult & 0xfefefe) + foliagemult) / 2;
    }

    public final int getWaterColorMult() {
        return watercolormult;
    }
}

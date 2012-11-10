package org.dynmap.common;

/* Generic biome mapping */
public class BiomeMap {
    private static BiomeMap[] biome_by_index = new BiomeMap[0];
    public static final BiomeMap NULL = new BiomeMap(-1, "NULL", 0.5, 0.5, 0xFFFFFF, 0, 0);

    public static final BiomeMap OCEAN = new BiomeMap(0, "OCEAN");
    public static final BiomeMap PLAINS = new BiomeMap(1, "PLAINS", 0.8, 0.4);
    public static final BiomeMap DESERT = new BiomeMap(2, "DESERT", 2.0, 0.0);
    public static final BiomeMap EXTREME_HILLS = new BiomeMap(3, "EXTREME_HILLS", 0.2, 0.3);
    public static final BiomeMap FOREST = new BiomeMap(4, "FOREST", 0.7, 0.8);
    public static final BiomeMap TAIGA = new BiomeMap(5, "TAIGA", 0.05, 0.8);
    public static final BiomeMap SWAMPLAND = new BiomeMap(6, "SWAMPLAND", 0.8, 0.9, 0xE0FFAE, 0x4E0E4E, 0x4E0E4E);
    public static final BiomeMap RIVER = new BiomeMap(7, "RIVER");
    public static final BiomeMap HELL = new BiomeMap(8, "HELL", 2.0, 0.0);
    public static final BiomeMap SKY = new BiomeMap(9, "SKY");
    public static final BiomeMap FROZEN_OCEAN = new BiomeMap(10, "FROZEN_OCEAN", 0.0, 0.5);
    public static final BiomeMap FROZEN_RIVER = new BiomeMap(11, "FROZEN_RIVER", 0.0, 0.5);
    public static final BiomeMap ICE_PLAINS = new BiomeMap(12, "ICE_PLAINS", 0.0, 0.5);
    public static final BiomeMap ICE_MOUNTAINS = new BiomeMap(13, "ICE_MOUNTAINS", 0.0, 0.5);
    public static final BiomeMap MUSHROOM_ISLAND = new BiomeMap(14, "MUSHROOM_ISLAND", 0.9, 1.0);
    public static final BiomeMap MUSHROOM_SHORE = new BiomeMap(15, "MUSHROOM_SHORE", 0.9, 1.0);
    public static final BiomeMap BEACH = new BiomeMap(16, "BEACH", 0.8, 0.4);
    public static final BiomeMap DESERT_HILLS = new BiomeMap(17, "DESERT_HILLS", 2.0, 0.0);
    public static final BiomeMap FOREST_HILLS = new BiomeMap(18, "FOREST_HILLS", 0.7, 0.8);
    public static final BiomeMap TAIGA_HILLS = new BiomeMap(19, "TAIGA_HILLS", 0.05, 0.8);
    public static final BiomeMap SMALL_MOUNTAINS = new BiomeMap(20, "SMALL_MOUNTAINS", 0.2, 0.8);
    public static final BiomeMap JUNGLE = new BiomeMap(21, "JUNGLE", 1.2, 0.9);
    public static final BiomeMap JUNGLE_HILLS = new BiomeMap(22, "JUNGLE_HILLS", 1.2, 0.9);
    
    public static final int LAST_WELL_KNOWN = 22;
    
    private final double tmp;
    private final double rain;
    private final int watercolormult;
    private final int grassmult;
    private final int foliagemult;
    private final String id;
    private final int index;
    
    private static boolean isUniqueID(String id) {
        for(int i = 0; i < biome_by_index.length; i++) {
            if(biome_by_index[i] == null) continue;
            if(biome_by_index[i].id.equals(id))
                return false;
        }
        return true;
    }
    private BiomeMap(int idx, String id, double tmp, double rain, int waterColorMultiplier, int grassmult, int foliagemult) {
        /* Clamp values : we use raw values from MC code, which are clamped during color mapping only */
        if(tmp > 1.0) tmp = 1.0;
        this.tmp = tmp;
        this.rain = rain;
        this.watercolormult = waterColorMultiplier;
        this.grassmult = grassmult;
        this.foliagemult = foliagemult;
        id = id.toUpperCase().replace(' ', '_');
        if(isUniqueID(id) == false) {
            id = id + "_" + idx;
        }
        this.id = id;
        idx++;  /* Insert one after ID value - null is zero index */
        this.index = idx;
        if(idx >= 0) {
            if(idx >= biome_by_index.length) {
                BiomeMap newmap[] = new BiomeMap[idx+1];
                int oldlen = biome_by_index.length;
                System.arraycopy(biome_by_index, 0, newmap, 0, oldlen);
                biome_by_index = newmap;

                for(int i = oldlen; i < idx; i++) {
                    new BiomeMap(i-1, "BIOME_" + (i-1));
                }
            }
            biome_by_index[idx] = this;
        }
    }
    public BiomeMap(int idx, String id) {
        this(idx, id, 0.5, 0.5, 0xFFFFFF, 0, 0);
    }
    
    public BiomeMap(int idx, String id, double tmp, double rain) {
        this(idx, id, tmp, rain, 0xFFFFFF, 0, 0);
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
    public final int ordinal() {
        return index;
    }
    public static final BiomeMap byBiomeID(int idx) {
        idx++;
        if((idx >= 0) && (idx < biome_by_index.length))
            return biome_by_index[idx];
        else
            return NULL;
    }
    public final String toString() {
        return id;
    }
    public static final BiomeMap[] values() {
        return biome_by_index;
    }
}

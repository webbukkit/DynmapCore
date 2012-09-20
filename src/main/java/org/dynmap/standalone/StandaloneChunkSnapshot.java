package org.dynmap.standalone;

import java.util.List;

import org.spout.nbt.CompoundMap;
import org.spout.nbt.CompoundTag;
import org.spout.nbt.IntTag;
import org.spout.nbt.LongTag;
import org.spout.nbt.ByteTag;
import org.spout.nbt.ByteArrayTag;
import org.spout.nbt.ShortArrayTag;
import org.spout.nbt.IntArrayTag;
import org.spout.nbt.Tag;
import org.spout.nbt.ListTag;

public class StandaloneChunkSnapshot {
    private static final int NUM_SECTIONS = 16;
    private static final int BLKS_PER_SECTION = 16 * 16 * 16;
    private int x, z;
    private byte[] biomes;
    private long lastUpdate;
    private boolean populated;
    private int[] heightMap;
    private short[][] blockid = new short[NUM_SECTIONS][];
    private byte[][] blockdata = new byte[NUM_SECTIONS][];
    private byte[][] skylight = new byte[NUM_SECTIONS][];
    private byte[][] emitlight = new byte[NUM_SECTIONS][];
    private boolean[] notempty = new boolean[NUM_SECTIONS];
    
    private static final short[] EMPTY_SECTION = new short[BLKS_PER_SECTION];
    private static final byte[] EMPTY_DATA = new byte[BLKS_PER_SECTION/2];
    private static final byte[] EMPTY_SKY = new byte[BLKS_PER_SECTION/2];
    
    static {
        for (int i = 0; i < EMPTY_SKY.length; i++) {
            EMPTY_SKY[i] = (byte)0xFF;
        }
    }
    
    public StandaloneChunkSnapshot() {
        for (int i = 0; i < NUM_SECTIONS; i++) {
            blockid[i] = EMPTY_SECTION;
            blockdata[i] = EMPTY_DATA;
            skylight[i] = EMPTY_SKY;
            emitlight[i] = EMPTY_DATA;
        }
    }
    
    public StandaloneChunkSnapshot(Tag tag) {
        this();
        
        CompoundMap map = ((CompoundTag)tag).getValue();
        map = ((CompoundTag)map.get("Level")).getValue(); // Get Level section
        /* Get coordinates */
        x = ((IntTag)map.get("xPos")).getValue();
        z = ((IntTag)map.get("zPos")).getValue();
        /* Get biomes */
        biomes = ((ByteArrayTag)map.get("Biomes")).getValue();
        /* Get last update timestamp */
        lastUpdate = ((LongTag)map.get("LastUpdate")).getValue();
        /* Get terrain populated */
        populated = ((ByteTag)map.get("TerrainPopulated")).getValue() != 0;
        /* Get height map */
        heightMap = ((IntArrayTag)map.get("HeightMap")).getValue();
        /* Handle sections */
        @SuppressWarnings("unchecked")
        List<CompoundTag> sections = ((ListTag<CompoundTag>)map.get("Sections")).getValue();
        for(CompoundTag stag : sections) {
            CompoundMap smap = stag.getValue();
            byte y = ((ByteTag)smap.get("Y")).getValue();
            if((y < 0) || (y >= NUM_SECTIONS)) continue;
            byte[] blks = ((ByteArrayTag)smap.get("Blocks")).getValue();
            byte[] data = ((ByteArrayTag)smap.get("Data")).getValue();
            byte[] sky = ((ByteArrayTag)smap.get("SkyLight")).getValue();
            byte[] emit = ((ByteArrayTag)smap.get("BlockLight")).getValue();

            blockid[y] = new short[BLKS_PER_SECTION];
            short[] blkids = blockid[y];
            for (int i = 0; i < BLKS_PER_SECTION; i++) {
                blkids[i] = (short)(0xFF & blks[i]);
            }
            ByteArrayTag extblkid = (ByteArrayTag)smap.get("Add");
            if (extblkid != null) {
                byte[] v = extblkid.getValue();
                for (int j = 0; j < (BLKS_PER_SECTION / 2); j++) {
                    short b = (short) (v[j] & 0xFF);
                    if (b == 0) {
                        continue;
                    }
                    blkids[j<<1] |= (b & 0x0F) << 8;
                    blkids[(j<<1)+1] |= (b & 0xF0) << 4;
                }
            }
            blockdata[y] = data;
            skylight[y] = sky;
            emitlight[y] = emit;
            notempty[y] = true;
        }
    }
    
    public int getBlockTypeId(int x, int y, int z) {
        return blockid[y >> 4][((y & 0xF) << 8) | (z << 4) | x];
    }

    public int getBlockData(int x, int y, int z) {
        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        return (blockdata[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    }

    public int getBlockSkyLight(int x, int y, int z) {
        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        return (skylight[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    }

    public int getBlockEmittedLight(int x, int y, int z) {
        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        return (emitlight[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    }

    public int getBiomeID(int x, int z) {
        return biomes[z << 4 | x];
    }

    public final boolean isSectionEmpty(int sy) {
        return !notempty[sy];
    }
}

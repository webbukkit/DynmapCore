package org.dynmap.standalone;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.utils.MapChunkCache;
import org.spout.nbt.Tag;
import org.spout.nbt.CompoundTag;
import org.spout.nbt.CompoundMap;
import org.spout.nbt.IntTag;
import org.spout.nbt.ByteTag;
import org.spout.nbt.LongTag;
import org.spout.nbt.TagType;
import org.spout.nbt.stream.NBTInputStream;

/**
 * Handler class for standalone worlds (based on Anvil format world data directory)
 */
public class StandaloneWorld extends DynmapWorld {
    private CompoundMap level_dat;
    private File wdir;
    private File regiondir;
    private DynmapLocation spawn;
    private String env;
    private boolean is_nether;
    private boolean is_raining;
    private boolean is_thunder;
    private long world_time;
    private boolean loaded;
    private RegionMap regions;
    private Object regionsem = new Object();
    
    private static final int MAX_FILES_ACTIVE = 10;
    private static final int SECTOR_SIZE = 4096;
    private static final int VERSION_GZIP = 1;
    private static final int VERSION_DEFLATE = 2;
    
    private static class CoordPair {
        int x, z;
        public int hashCode() {
            return x ^ (z * 71);
        }
        public boolean equals(Object o) {
            if (o instanceof CoordPair) {
                CoordPair c = (CoordPair) o;
                return ((c.x == x) && (c.z == z));
            }
            else {
                return false;
            }
        }
    }
    private static class RegionFileHandler {
        private File f;
        private RandomAccessFile raf;
        private int[] offsets;
        private int[] timestamps;
        
        public RegionFileHandler(File dir, int x, int z) throws IOException {
            f = new File(dir, "r." + x + "." + z + ".mca");
            if (!f.canRead()) {
                throw new IOException();
            }
            raf = new RandomAccessFile(f, "rw");
            offsets = new int[32 * 32];
            timestamps = new int[32 * 32];
            /* Load offsets */
            raf.seek(0);
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = raf.readInt();
            }
            for (int i = 0; i < timestamps.length; i++) {
                timestamps[i] = raf.readInt();
            }
        }
        public Tag readChunk(int x, int z) {
            byte[] rec = null;
            int ver = 0;
            synchronized(this) {
                int off = offsets[x + (32 * z)];
                if((off == 0) || (raf == null)) {
                    return null;
                }
                try {
                    raf.seek((off >> 8) * SECTOR_SIZE);
                    int len = raf.readInt(); // Get length
                    if (len > ((off & 0xFF) * SECTOR_SIZE)) {
                        return null;
                    }
                    ver = raf.readByte(); // Get version
                    rec = new byte[len - 1];
                    raf.read(rec);
                } catch (IOException iox) {
                    return null;
                }
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(rec);
            InputStream in = null;
            if (ver == VERSION_GZIP) {
                try {
                    in = new GZIPInputStream(bais);
                } catch (IOException iox) {
                    return null;
                }
            }
            else if (ver == VERSION_DEFLATE) {
                in = new InflaterInputStream(bais);
            }
            else {
                return null;
            }
            Tag t = null;
            try {
                NBTInputStream nis = new NBTInputStream(new BufferedInputStream(in), false);
                t = nis.readTag();
                nis.close();
            } catch (IOException iox) {
                return null;
            }
            return t;
        }
        
        public synchronized void cleanup() {
            if(raf != null) {
                try {
                    raf.close();
                } catch (IOException iox) {
                }
                raf = null;
            }
        }
    }
    private static class RegionMap extends LinkedHashMap<CoordPair, RegionFileHandler>  {
        public RegionMap() {
            super(MAX_FILES_ACTIVE, 0.7F, true); // Make access-order based linking (for LRU)
        }
        protected boolean removeEldestEntry(Map.Entry<CoordPair, RegionFileHandler> eldest) {
            if (this.size() >= MAX_FILES_ACTIVE) {
                eldest.getValue().cleanup(); // Clean up oldest region handler
                return true;
            }
            return false;
         }
    }
    
    public static void main(String[] v) {
        StandaloneWorld w;
        
        try {
            w = new StandaloneWorld("world", new File("/Users/mike/mcpc/world_nether"), "nether");
            Tag t = w.getChunk(0, 0);
            StandaloneChunkSnapshot ss = new StandaloneChunkSnapshot(t);
            
        } catch (IOException x) {
            Log.severe("Error: " + x.getMessage());
        }
    }
    
    public void updateLevelDat() throws IOException {
        /* Read level.dat - required */
        File lvl = new File(wdir, "level.dat");
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(lvl);
            NBTInputStream nis = new NBTInputStream(fis);
            Tag t = nis.readTag();
            if (t.getType() == TagType.TAG_COMPOUND) {
                CompoundTag ct = (CompoundTag)(((CompoundTag)t).getValue().get("Data"));
                level_dat = ct.getValue();
                /* Initialize spawn location */
                spawn.x = ((IntTag)level_dat.get("SpawnX")).getValue();
                spawn.y = ((IntTag)level_dat.get("SpawnY")).getValue();
                spawn.z = ((IntTag)level_dat.get("SpawnZ")).getValue();
                /* Test if raining */
                this.is_raining = (((ByteTag)level_dat.get("raining")).getValue() != 0);
                this.is_thunder = (((ByteTag)level_dat.get("thundering")).getValue() != 0);
                /* Get world time */
                this.world_time = ((LongTag)level_dat.get("Time")).getValue();
                /* Got valid data */
                loaded = true;
            }
            nis.close();
        } catch (IOException iox) {
            Log.info("Error opening level.dat for world " + getName() + " in " + wdir.getPath());
            throw iox;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException iox) {}
            }
        }
    }
    public StandaloneWorld(String wname, File wdir, String env) throws IOException {
        super(wname, 256, 64);
        this.wdir = wdir;
        this.env = env;
        this.is_nether = env.equals("nether");
        
        if(env.equals("nether"))
            this.regiondir = new File(wdir, "DIM-1/region");
        else if(env.equals("the_end")) 
            this.regiondir = new File(wdir, "DIM1/region");
        else
            this.regiondir = new File(wdir, "region");
        spawn = new DynmapLocation();
        spawn.world = this.getName();
        regions = new RegionMap();
        /* Load level.dat */
        updateLevelDat();
        
    }

    @Override
    public boolean isNether() {
        return is_nether;
    }

    @Override
    public DynmapLocation getSpawnLocation() {
        return spawn;
    }

    @Override
    public long getTime() {
        return this.world_time;
    }

    @Override
    public boolean hasStorm() {
        return this.is_raining;
    }

    @Override
    public boolean isThundering() {
        return this.is_thunder;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public int getLightLevel(int x, int y, int z) {
        return 0;
    }

    @Override
    public int getHighestBlockYAt(int x, int z) {
        return 0;
    }

    @Override
    public boolean canGetSkyLightLevel() {
        return true;
    }

    @Override
    public int getSkyLightLevel(int x, int y, int z) {
        return 0;
    }

    @Override
    public String getEnvironment() {
        return env;
    }

    @Override
    public MapChunkCache getChunkCache(List<DynmapChunk> chunks) {
        // TODO Auto-generated method stub
        return null;
    }

    public Tag getChunk(int x, int z) {
        RegionFileHandler rf = null;
        CoordPair cp = new CoordPair();
        cp.x = (x >> 5);
        cp.z = (z >> 5);
        synchronized(regionsem) {
            rf = regions.get(cp);
            if (rf == null) {
                try {
                    rf = new RegionFileHandler(regiondir, cp.x, cp.z);
                } catch (IOException iox) {
                    return null;
                }
                regions.put(cp,  rf);
            }
        }
        return rf.readChunk(x & 0x1F, z & 0x1F);
    }
}

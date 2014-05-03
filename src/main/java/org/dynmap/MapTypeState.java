package org.dynmap;

import java.util.ArrayList;
import java.util.List;

import org.dynmap.utils.TileFlags;

public class MapTypeState {
    public static final long DEF_INV_PERIOD = 30;
    public static final long NANOS_PER_SECOND = 1000000000L;
    public MapType type;
    private Object invTileLock = new Object();
    private TileFlags pendingInvTiles = new TileFlags();
    private TileFlags pendingInvTilesAlt = new TileFlags();
    private TileFlags invTiles = new TileFlags();
    private TileFlags.Iterator invTilesIter = invTiles.getIterator();
    private long nextInvTS;
    private long invTSPeriod;
    private ArrayList<TileFlags> zoomOutInv = new ArrayList<TileFlags>();
    private TileFlags.Iterator zoomOutInvIter = null;
    private int zoomOutInvIterLevel = -1;
    private final int zoomOutLevels;
    
    public MapTypeState(DynmapWorld world, MapType mt) {
        type = mt;
        invTSPeriod = DEF_INV_PERIOD * NANOS_PER_SECOND;
        nextInvTS = System.nanoTime() + invTSPeriod;
        zoomOutLevels = world.getExtraZoomOutLevels() + mt.getMapZoomOutLevels();
        for (int i = 0; i < zoomOutLevels; i++) {
            zoomOutInv.add(null);
        }
    }
    public void setInvalidatePeriod(long inv_per_in_secs) {
        invTSPeriod = inv_per_in_secs * NANOS_PER_SECOND;
    }

    public boolean invalidateTile(int tx, int ty) {
        boolean done;
        synchronized(invTileLock) {
            done = !pendingInvTiles.setFlag(tx, ty, true);
        }
        return done;
    }

    public int invalidateTiles(List<TileFlags.TileCoord> coords) {
        int cnt = 0;
        synchronized(invTileLock) {
            for(TileFlags.TileCoord c : coords) {
                if(!pendingInvTiles.setFlag(c.x, c.y, true)) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    public void tickMapTypeState(long now_nano) {
        if(nextInvTS < now_nano) {
            synchronized(invTileLock) {
                TileFlags tmp = pendingInvTilesAlt;
                pendingInvTilesAlt = pendingInvTiles;
                pendingInvTiles = tmp;
                invTiles.union(tmp);
                tmp.clear();
                nextInvTS = now_nano + invTSPeriod;
            }
        }
    }
    
    public boolean getNextInvalidTileCoord(TileFlags.TileCoord coord) {
        boolean match;
        synchronized(invTileLock) {
            match = invTilesIter.next(coord);
        }
        return match;
    }
    
    public void validateTile(int tx, int ty) {
        synchronized(invTileLock) {
            invTiles.setFlag(tx, ty,  false);
            pendingInvTiles.setFlag(tx, ty, false);
            pendingInvTilesAlt.setFlag(tx, ty, false);
        }
    }
    
    public boolean isInvalidTile(int tx, int ty) {
        synchronized(invTileLock) {
            return invTiles.getFlag(tx, ty);
        }
    }
    
    public List<String> save() {
        synchronized(invTileLock) {
            invTiles.union(pendingInvTiles);
            invTiles.union(pendingInvTilesAlt);
            pendingInvTiles.clear();
            pendingInvTilesAlt.clear();
            return invTiles.save();
        }
    }
    public void restore(List<String> saved) {
        synchronized(invTileLock) {
            TileFlags tf = new TileFlags();
            tf.load(saved);
            invTiles.union(tf);
        }
    }
    public int getInvCount() {
        synchronized(invTileLock) {
            return invTiles.countFlags();
        }
    }
    public void clear() {
        synchronized(invTileLock) {
            invTiles.clear();
        }
    }
    public void setZoomOutInv(int x, int y, int zoomlevel) {
        if (zoomlevel >= zoomOutLevels) {
            return;
        }
        synchronized(invTileLock) {
            TileFlags tf = zoomOutInv.get(zoomlevel);
            if (tf == null) {
                tf = new TileFlags();
                zoomOutInv.set(zoomlevel, tf);
            }
            tf.setFlag(x >> zoomlevel, y >> zoomlevel, true);
        }
    }
    public boolean getZoomOutInv(int x, int y, int zoomlevel) {
        if (zoomlevel >= zoomOutLevels) {
            return false;
        }
        synchronized(invTileLock) {
            zoomOutInv.ensureCapacity(zoomlevel+1);
            TileFlags tf = zoomOutInv.get(zoomlevel);
            if (tf == null) {
                return false;
            }
            return tf.getFlag(x >> zoomlevel, y >> zoomlevel);
        }
    }
    public boolean clearZoomOutInv(int x, int y, int zoomlevel) {
        if (zoomlevel >= zoomOutLevels) {
            return false;
        }
        synchronized(invTileLock) {
            TileFlags tf = zoomOutInv.get(zoomlevel);
            if (tf == null) {
                return false;
            }
            boolean prev = tf.setFlag(x >> zoomlevel, y >> zoomlevel, false);
            if (tf.countFlags() == 0) { // Empty?
                zoomOutInv.set(zoomlevel, null);
            }
            return prev;
        }
    }
    public static class ZoomOutCoord extends TileFlags.TileCoord {
        public int zoomlevel;
    }
    public boolean nextZoomOutInv(ZoomOutCoord coord) {
        synchronized(invTileLock) {
            // Try existing iterator
            if (zoomOutInvIter != null) {
                if (zoomOutInvIter.hasNext()) {
                    zoomOutInvIter.next(coord);
                    coord.zoomlevel = zoomOutInvIterLevel;
                    coord.x = coord.x << zoomOutInvIterLevel;
                    coord.y = coord.y << zoomOutInvIterLevel;
                    return true;
                }
                zoomOutInvIter = null;
            }
            for (int i = 0; i < zoomOutLevels; i++) {
                // Advance to next
                zoomOutInvIterLevel = (zoomOutInvIterLevel + 1) % zoomOutLevels;
                TileFlags tf = zoomOutInv.get(zoomOutInvIterLevel);
                if (tf != null) {
                    zoomOutInvIter = tf.getIterator();
                    if (zoomOutInvIter.hasNext()) {
                        zoomOutInvIter.next(coord);
                        coord.zoomlevel = zoomOutInvIterLevel;
                        coord.x = coord.x << zoomOutInvIterLevel;
                        coord.y = coord.y << zoomOutInvIterLevel;
                        return true;
                    }
                    else {
                        zoomOutInvIter = null;
                    }
                }
            }
        }
        return false;
    }
}

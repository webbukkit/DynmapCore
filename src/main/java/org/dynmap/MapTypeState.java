package org.dynmap;

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
    
    public MapTypeState(MapType mt) {
        type = mt;
        invTSPeriod = DEF_INV_PERIOD * NANOS_PER_SECOND;
        nextInvTS = System.nanoTime() + invTSPeriod;
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
}

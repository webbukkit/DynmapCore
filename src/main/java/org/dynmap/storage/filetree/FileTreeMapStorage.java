package org.dynmap.storage.filetree;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType;
import org.dynmap.MapType.ImageFormat;
import org.dynmap.MapType.ImageVariant;
import org.dynmap.debug.Debug;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;

public class FileTreeMapStorage extends MapStorage {
    private static Object lock = new Object();
    private static HashMap<String, Integer> filelocks = new HashMap<String, Integer>();
    private static final Integer WRITELOCK = new Integer(-1);
    
    private File baseTileDir;
    private TileHashManager hashmap;
    
    public class StorageTile extends MapStorageTile {
        private final String baseFilename;
        private File f; // cached file
        private ImageFormat f_fmt;
        
        StorageTile(DynmapWorld world, MapType map, int x, int y,
                int zoom, ImageVariant var) {
            super(world, map, x, y, zoom, var);
            baseFilename = world.getName() + "/" + map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + x + "_" + y;
        }
        private File getTileFile(ImageFormat fmt) {
            if ((f == null) || (fmt != f_fmt)) {
                f = new File(baseTileDir, baseFilename + "." + fmt.getFileExt());
                f_fmt = fmt;
            }
            return f;
        }
        @Override
        public boolean exists(ImageFormat fmt) {
            File ff = getTileFile(fmt);
            return ff.isFile() && ff.canRead();
        }

        @Override
        public boolean matchesHashCode(ImageFormat fmt, long hash) {
            return exists(fmt) && (hash == hashmap.getImageHashCode(world.getName() + "." + map.getPrefix(), null, x, y));
        }

        @Override
        public TileRead read() {
            ImageFormat fmt = map.getImageFormat();
            File ff = getTileFile(fmt);
            if (ff.exists() == false) {
                if (fmt == ImageFormat.FORMAT_PNG) {
                    fmt = ImageFormat.FORMAT_JPG;
                }
                else {
                    fmt = ImageFormat.FORMAT_PNG;
                }
                ff = getTileFile(fmt);
            }
            if (ff.isFile()) {
                TileRead tr = new TileRead();
                byte[] buf = new byte[(int) ff.length()];
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(ff);
                    fis.read(buf, 0, buf.length);   // Read whole thing
                } catch (IOException iox) {
                    Log.info("read (" + ff.getPath() + ") failed = " + iox.getMessage());
                    return null;
                } finally {
                    if (fis != null) {
                        try { fis.close(); } catch (IOException iox) {}
                        fis = null;
                    }
                }
                tr.image = new BufferInputStream(buf);
                tr.format = fmt;
                tr.hashCode = hashmap.getImageHashCode(world.getName() + "." + map.getPrefix(), null, x, y);
                return tr;
            }
            return null;
        }

        private static final int MAX_WRITE_RETRIES = 6;

        @Override
        public boolean write(ImageFormat fmt, long hash, BufferOutputStream encImage) {
            File ff = getTileFile(fmt);
            File ffpar = ff.getParentFile();
            if (encImage == null) { // Delete?
                ffpar.delete();
                hashmap.updateHashCode(world.getName() + "." + map.getPrefix(), null, x, y, -1);
                // Signal update for zoom out
                if (zoom == 0) {
                    world.enqueueZoomOutUpdate(ff);
                }
                return true;
            }
            if (ffpar.exists() == false) {
                ffpar.mkdirs();
            }
            File fnew = new File(ff.getPath() + ".new");
            File fold = new File(ff.getPath() + ".old");
            boolean done = false;
            int retrycnt = 0;
            while(!done) {
                RandomAccessFile f = null;
                try {
                    f = new RandomAccessFile(fnew, "rw");
                    f.write(encImage.buf, 0, encImage.len);
                    done = true;
                } catch (IOException fnfx) {
                    if(retrycnt < MAX_WRITE_RETRIES) {
                        Debug.debug("Image file " + ff.getPath() + " - unable to write - retry #" + retrycnt);
                        try { Thread.sleep(50 << retrycnt); } catch (InterruptedException ix) { return false; }
                        retrycnt++;
                    }
                    else {
                        Log.info("Image file " + ff.getPath() + " - unable to write - failed");
                        return false;
                    }
                } finally {
                    if(f != null) {
                        try { f.close(); } catch (IOException iox) { done = false; }
                    }
                    if(done) {
/*TODO:                        if (preUpdateCommand != null && !preUpdateCommand.isEmpty()) {
                            try {
                                new ProcessBuilder(preUpdateCommand, fnew.getAbsolutePath()).start().waitFor();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        */
                        ff.renameTo(fold);
                        fnew.renameTo(ff);
                        fold.delete();
/*TODO                        if (postUpdateCommand != null && !postUpdateCommand.isEmpty()) {
                            try {
                                new ProcessBuilder(postUpdateCommand, fname.getAbsolutePath()).start().waitFor();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
*/                            
                    }
                }            
            }
            hashmap.updateHashCode(world.getName() + "." + map.getPrefix(), null, x, y, hash);
            // Signal update for zoom out
            if (zoom == 0) {
                world.enqueueZoomOutUpdate(ff);
            }
            return true;
        }

        @Override
        public boolean getWriteLock() {
            synchronized(lock) {
                boolean got_lock = false;
                while(!got_lock) {
                    Integer lockcnt = filelocks.get(baseFilename);    /* Get lock count */
                    if(lockcnt != null) {   /* If any locks, can't get write lock */
                        try {
                            lock.wait(); 
                        } catch (InterruptedException ix) {
                            Log.severe("getWriteLock(" + baseFilename + ") interrupted");
                            return false;
                        }
                    }
                    else {
                        filelocks.put(baseFilename, WRITELOCK);
                        got_lock = true;
                    }
                }
            }
            return true;
        }

        @Override
        public void releaseWriteLock() {
            synchronized(lock) {
                Integer lockcnt = filelocks.get(baseFilename);    /* Get lock count */
                if(lockcnt == null)
                    Log.severe("releaseWriteLock(" + baseFilename + ") on unlocked file");
                else if(lockcnt.equals(WRITELOCK)) {
                    filelocks.remove(baseFilename);   /* Remove lock */
                    lock.notifyAll();   /* Wake up folks waiting for locks */
                }
                else
                    Log.severe("releaseWriteLock(" + baseFilename + ") on read-locked file");
            }
        }

        @Override
        public boolean getReadLock(long timeout) {
            synchronized(lock) {
                boolean got_lock = false;
                long starttime = 0;
                if(timeout > 0)
                    starttime = System.currentTimeMillis();
                while(!got_lock) {
                    Integer lockcnt = filelocks.get(baseFilename);    /* Get lock count */
                    if(lockcnt == null) {
                        filelocks.put(baseFilename, Integer.valueOf(1));  /* First lock */
                        got_lock = true;
                    }
                    else if(!lockcnt.equals(WRITELOCK)) {   /* Other read locks */
                        filelocks.put(baseFilename, Integer.valueOf(lockcnt+1));
                        got_lock = true;
                    }
                    else {  /* Write lock in place */
                        try {
                            if(timeout < 0) {
                                lock.wait();
                            }
                            else {
                                long now = System.currentTimeMillis();
                                long elapsed = now-starttime; 
                                if(elapsed > timeout)   /* Give up on timeout */
                                    return false;
                                lock.wait(timeout-elapsed);
                            }
                        } catch (InterruptedException ix) {
                            Log.severe("getReadLock(" + baseFilename + ") interrupted");
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        @Override
        public void releaseReadLock() {
            synchronized(lock) {
                Integer lockcnt = filelocks.get(baseFilename);    /* Get lock count */
                if(lockcnt == null)
                    Log.severe("releaseReadLock(" + baseFilename + ") on unlocked file");
                else if(lockcnt.equals(WRITELOCK))
                    Log.severe("releaseReadLock(" + baseFilename + ") on write-locked file");
                else if(lockcnt > 1) {
                    filelocks.put(baseFilename, Integer.valueOf(lockcnt-1));
                }
                else {
                    filelocks.remove(baseFilename);   /* Remove lock */
                    lock.notifyAll();   /* Wake up folks waiting for locks */
                }
            }
        }

        @Override
        public void cleanup() {
        }
        
        @Override
        public String getURI(MapType.ImageFormat fmt) {
            return baseFilename + "." + fmt.getFileExt();
        }
    }
    
    public FileTreeMapStorage() {
    }

    @Override
    public boolean init(DynmapCore core) {
        if (!super.init(core)) {
            return false;
        }
        baseTileDir = core.getTilesFolder();
        hashmap = new TileHashManager(baseTileDir, true);

        return true;
    }
    
    @Override
    public MapStorageTile getTile(DynmapWorld world, MapType map, int x, int y,
            int zoom, ImageVariant var) {
        return new StorageTile(world, map, x, y, zoom, var);
    }
}

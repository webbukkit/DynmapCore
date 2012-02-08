package org.dynmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dynmap.debug.Debug;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.FileLockManager;
import org.dynmap.utils.MapChunkCache;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import javax.imageio.ImageIO;

public abstract class DynmapWorld {

    public enum AutoGenerateOption {
        NONE,
        FORMAPONLY,
        PERMANENT
    }
    public List<MapType> maps = new ArrayList<MapType>();
    public UpdateQueue updates = new UpdateQueue();
    public DynmapLocation center;
    public List<DynmapLocation> seedloc;    /* All seed location - both direct and based on visibility limits */
    private List<DynmapLocation> seedloccfg;    /* Configured full render seeds only */
    
    public List<MapChunkCache.VisibilityLimit> visibility_limits;
    public List<MapChunkCache.VisibilityLimit> hidden_limits;
    public AutoGenerateOption do_autogenerate;
    public MapChunkCache.HiddenChunkStyle hiddenchunkstyle;
    public int servertime;
    public boolean sendposition;
    public boolean sendhealth;
    public boolean bigworld;    /* If true, deeper directory hierarchy */
    private int extrazoomoutlevels;  /* Number of additional zoom out levels to generate */
    public File worldtilepath;
    private Object lock = new Object();
    @SuppressWarnings("unchecked")
    private HashSet<String> zoomoutupdates[] = new HashSet[0];
    private boolean checkts = true;	/* Check timestamps on first run with new configuration */
    private boolean cancelled;
    private String wname;
    private String title;
    private boolean is_enabled;
    
    /* World height data */
    public final int worldheight;
    public final int heightshift;
    public final int heightmask;
    
    protected DynmapWorld(String wname, int worldheight) {
        this.wname = wname;
        this.worldheight = worldheight;
        int shift;
        for(shift = 0; ((1 << shift) < worldheight); shift++) {}
        heightshift = shift;
        heightmask = (1 << shift) - 1;
    }
    @SuppressWarnings("unchecked")
    public void setExtraZoomOutLevels(int lvl) {
        extrazoomoutlevels = lvl;
        zoomoutupdates = new HashSet[lvl];
        for(int i = 0; i < lvl; i++)
            zoomoutupdates[i] = new HashSet<String>();
    	checkts = true;
    }
    public int getExtraZoomOutLevels() { return extrazoomoutlevels; }
    
    public void enqueueZoomOutUpdate(File f) {
        enqueueZoomOutUpdate(f, 0);
    }
    
    private void enqueueZoomOutUpdate(File f, int level) {
        synchronized(lock) {
            if(level >= zoomoutupdates.length) {
               @SuppressWarnings("unchecked")
               HashSet<String> new_zoomout[] = new HashSet[level+1];
               System.arraycopy(zoomoutupdates, 0, new_zoomout, 0, zoomoutupdates.length);
               for(int i = 0; i < new_zoomout.length; i++) {
                   if(i < zoomoutupdates.length)
                       new_zoomout[i] = zoomoutupdates[i];
                   else
                       new_zoomout[i] = new HashSet<String>();
               }
               zoomoutupdates = new_zoomout;
            }
            zoomoutupdates[level].add(f.getPath());
        }
    }
    
    private boolean popQueuedUpdate(File f, int level) {
        if(level >= zoomoutupdates.length)
            return false;
        synchronized(lock) {
            return zoomoutupdates[level].remove(f.getPath());
        }
    }
    
    private String[]	peekQueuedUpdates(int level) {
        if(level >= zoomoutupdates.length)
            return new String[0];
        synchronized(lock) {
            return zoomoutupdates[level].toArray(new String[zoomoutupdates[level].size()]);
        }
    }
    
    private static class DirFilter implements FilenameFilter {
        public boolean accept(File f, String n) {
            if(!n.equals("..") && !n.equals(".")) {
                File fn = new File(f, n);
                return fn.isDirectory();
            }
            return false;
        }
    }

    private static final String COORDSTART = "-0123456789";
    private static class PNGFileFilter implements FilenameFilter {
        String prefix;
        String suffix;
        public PNGFileFilter(String pre, MapType.ImageFormat fmt) {
            if((pre != null) && (pre.length() > 0))
                prefix = pre; 
            suffix = "." + fmt.getFileExt();
        }
        public boolean accept(File f, String n) {
            if(n.endsWith(suffix)) {
                if((prefix != null) && (!n.startsWith(prefix)))
                    return false;
                if((prefix == null) && (COORDSTART.indexOf(n.charAt(0)) < 0))
                    return false;
                File fn = new File(f, n);
                return fn.isFile();
            }
            return false;
        }
    }

    public void freshenZoomOutFiles() {
        boolean done = false;
        int last_done = 0;
        for(int i = 0; (!cancelled) && (!done); i++) {
            done = freshenZoomOutFilesByLevel(i);
            last_done = i;
        }
        /* Purge updates for levels above what any map needs */
        for(int i = last_done; i < zoomoutupdates.length; i++) {
            zoomoutupdates[i].clear();
        }
        checkts = false;	/* Just handle queued updates after first scan */
    }
    
    public void cancelZoomOutFreshen() {
        cancelled = true;
    }
    
    private static class PrefixData {
    	int stepsize;
    	int[] stepseq;
    	boolean neg_step_x;
        boolean neg_step_y;
    	String baseprefix;
    	int zoomlevel;
    	int background;
    	String zoomprefix;
    	String fnprefix;
        String zfnprefix;
        int bigworldshift;
        boolean isbigmap;
        MapType.ImageFormat fmt;
    }
    
    public boolean freshenZoomOutFilesByLevel(int zoomlevel) {
        int cnt = 0;
        Debug.debug("freshenZoomOutFiles(" + wname + "," + zoomlevel + ")");
        if(worldtilepath.exists() == false) /* Quit if not found */
            return true;
        HashMap<String, PrefixData> maptab = buildPrefixData(zoomlevel);

        if(checkts) {	/* If doing timestamp based scan (initial) */
        	DirFilter df = new DirFilter();
        	for(String pfx : maptab.keySet()) { /* Walk through prefixes */
                if(cancelled) return true;
        		PrefixData pd = maptab.get(pfx);
        		if(pd.isbigmap) { /* If big world, next directories are map name specific */
        			File dname = new File(worldtilepath, pfx);
        			/* Now, go through subdirectories under this one, and process them */
        			String[] subdir = dname.list(df);
        			if(subdir == null) continue;
        			for(String s : subdir) {
        			    if(cancelled) return true;
        				File sdname = new File(dname, s);
        				cnt += processZoomDirectory(sdname, pd);
        			}
        		}
        		else {  /* Else, classic file layout */
        			cnt += processZoomDirectory(worldtilepath, maptab.get(pfx));
        		}
        	}
        	Debug.debug("freshenZoomOutFiles(" + wname + "," + zoomlevel + ") - done (" + cnt + " updated files)");
        }
        else {	/* Else, only process updates */
            String[] paths = peekQueuedUpdates(zoomlevel);	/* Get pending updates */
            HashMap<String, ProcessTileRec> toprocess = new HashMap<String, ProcessTileRec>();
            /* Accumulate zoomed tiles to be processed (combine triggering subtiles) */
            for(String p : paths) {
                if(cancelled) return true;
            	File f = new File(p);	/* Make file */
            	/* Find matching prefix */
            	for(PrefixData pd : maptab.values()) { /* Walk through prefixes */
                    if(cancelled) return true;
            		ProcessTileRec tr = null;
            		/* If big map and matches name pattern */
            		if(pd.isbigmap && f.getName().startsWith(pd.fnprefix) && 
            				f.getParentFile().getParentFile().getName().equals(pd.baseprefix)) {
                        tr = processZoomFile(f, pd);
            		}
                    /* If not big map and matches name pattern */
                    else if((!pd.isbigmap) && f.getName().startsWith(pd.fnprefix)) {
                        tr = processZoomFile(f, pd);
                    }
                    if(tr != null) {
                        String zfpath = tr.zf.getPath();
                        if(!toprocess.containsKey(zfpath))  {
                            toprocess.put(zfpath, tr);
                        }
                    }
                }
            }
            /* Do processing */
            for(ProcessTileRec s : toprocess.values()) {
                if(cancelled) return true;
                processZoomTile(s.pd, s.zf, s.zfname, s.x, s.y);
            }
        }
        /* Return true when we have none left at the level */
        return (maptab.size() == 0);
    }
    
    private HashMap<String, PrefixData> buildPrefixData(int zoomlevel) {
        HashMap<String, PrefixData> maptab = new HashMap<String, PrefixData>();
        /* Build table of file prefixes and step sizes */
        for(MapType mt : maps) {
            /* If level is above top needed for this map, skip */
            if(zoomlevel > (this.extrazoomoutlevels + mt.getMapZoomOutLevels()))
                continue;
            List<MapType.ZoomInfo> pfx = mt.baseZoomFileInfo();
            int stepsize = mt.baseZoomFileStepSize();
            int bigworldshift = mt.getBigWorldShift();
            boolean neg_step_x = false;
            boolean neg_step_y = false;
            switch(mt.zoomFileMapStep()) {
                case X_PLUS_Y_PLUS:
                    break;
                case X_MINUS_Y_PLUS:
                    neg_step_x = true;
                    break;
                case X_PLUS_Y_MINUS:
                    neg_step_y = true;
                    break;
                case X_MINUS_Y_MINUS:
                    neg_step_x = neg_step_y = true;
                    break;
            }
            int[] stepseq = mt.zoomFileStepSequence();
            for(MapType.ZoomInfo p : pfx) {
                PrefixData pd = new PrefixData();
                pd.stepsize = stepsize;
                pd.neg_step_x = neg_step_x;
                pd.neg_step_y = neg_step_y;
                pd.stepseq = stepseq;
                pd.baseprefix = p.prefix;
                pd.background = p.background_argb;
                pd.zoomlevel = zoomlevel;
                pd.zoomprefix = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".substring(0, zoomlevel);
                pd.bigworldshift = bigworldshift;
                pd.isbigmap = mt.isBigWorldMap(this);
                pd.fmt = mt.getImageFormat();
                if(pd.isbigmap) {
                    if(zoomlevel > 0) {
                        pd.zoomprefix += "_";
                        pd.zfnprefix = "z" + pd.zoomprefix;
                    }
                    else {
                        pd.zfnprefix = "z_";
                    }
                    pd.fnprefix = pd.zoomprefix;
                }
                else {
                    pd.fnprefix = pd.zoomprefix + pd.baseprefix;
                    pd.zfnprefix = "z" + pd.fnprefix;
                }
                
                maptab.put(p.prefix, pd);
            }
        }
        return maptab;
    }
    
    private static class ProcessTileRec {
        File zf;
        String zfname;
        int x, y;
        PrefixData pd;
    }

    private String makeFilePath(PrefixData pd, int x, int y, boolean zoomed) {
        if(pd.isbigmap)
            return pd.baseprefix + "/" + (x >> pd.bigworldshift) + "_" + (y >> pd.bigworldshift) + "/" + (zoomed?pd.zfnprefix:pd.fnprefix) + x + "_" + y + "." + pd.fmt.getFileExt();
        else
            return (zoomed?pd.zfnprefix:pd.fnprefix) + "_" + x + "_" + y + "." + pd.fmt.getFileExt();            
    }
    
    private int processZoomDirectory(File dir, PrefixData pd) {
        Debug.debug("processZoomDirectory(" + dir.getPath() + "," + pd.baseprefix + ")");
        HashMap<String, ProcessTileRec> toprocess = new HashMap<String, ProcessTileRec>();
        String[] files = dir.list(new PNGFileFilter(pd.fnprefix, pd.fmt));
        if(files == null)
            return 0;
        for(String fn : files) {
            ProcessTileRec tr = processZoomFile(new File(dir, fn), pd);
            if(tr != null) {
                String zfpath = tr.zf.getPath();
                if(!toprocess.containsKey(zfpath))  {
                    toprocess.put(zfpath, tr);
                }
            }
        }
        int cnt = 0;
        /* Do processing */
        for(ProcessTileRec s : toprocess.values()) {
            processZoomTile(s.pd, s.zf, s.zfname, s.x, s.y);
            cnt++;
        }
        Debug.debug("processZoomDirectory(" + dir.getPath() + "," + pd.baseprefix + ") - done (" + cnt + " files)");
        return cnt;
    }
    
    private ProcessTileRec processZoomFile(File f, PrefixData pd) {
    	/* If not checking timstamp, we're out if nothing queued for this file */
    	if(!checkts) {
    		if(!popQueuedUpdate(f, pd.zoomlevel))
    			return null;
    	}
        int step = pd.stepsize << pd.zoomlevel;
        String fn = f.getName();
        /* Parse filename to predict zoomed out file */
        fn = fn.substring(0, fn.lastIndexOf('.'));  /* Strip off extension */
        String[] tok = fn.split("_");   /* Split by underscores */
        int x = 0;
        int y = 0;
        boolean parsed = false;
        if(tok.length >= 2) {
            try {
                x = Integer.parseInt(tok[tok.length-2]);
                y = Integer.parseInt(tok[tok.length-1]);
                parsed = true;
            } catch (NumberFormatException nfx) {
            }
        }
        if(!parsed)
            return null;
        if(pd.neg_step_x) x = -x;
        if(x >= 0)
            x = x - (x % (2*step));
        else
            x = x + (x % (2*step));
        if(pd.neg_step_x) x = -x;
        if(pd.neg_step_y) y = -y;
        if(y >= 0)
            y = y - (y % (2*step));
        else
            y = y + (y % (2*step));
        if(pd.neg_step_y) y = -y;
        /* Make name of corresponding zoomed tile */
        String zfname = makeFilePath(pd, x, y, true);
        File zf = new File(worldtilepath, zfname);
        if(checkts) {	/* If checking timestamp, see if we need update based on enqueued update OR old file time */
        	/* If we're not updated, and zoom file exists and is older than our file, nothing to do */
        	if((!popQueuedUpdate(f, pd.zoomlevel)) && zf.exists() && (zf.lastModified() >= f.lastModified())) {
        		return null;
        	}
        }
        ProcessTileRec rec = new ProcessTileRec();
        rec.zf = zf;
        rec.x = x;
        rec.y = y;
        rec.zfname = zfname;
        rec.pd = pd;
        Debug.debug("Process " + zf.getPath() + " due to " + f.getPath());
        return rec;
    }
    
    private void processZoomTile(PrefixData pd, File zf, String zfname, int tx, int ty) {
        Debug.debug("processZoomFile(" + pd.baseprefix + "," + zf.getPath() + "," + tx + "," + ty + ")");
        int width = 128, height = 128;
        BufferedImage zIm = null;
        DynmapBufferedImage kzIm = null;
        boolean blank = true;
        int[] argb = new int[width*height];
        int step = pd.stepsize << pd.zoomlevel;
        int ztx = tx;
        int zty = ty;
        tx = tx - (pd.neg_step_x?step:0);	/* Adjust for negative step */ 
        ty = ty - (pd.neg_step_y?step:0);   /* Adjust for negative step */ 

        /* create image buffer */
        kzIm = DynmapBufferedImage.allocateBufferedImage(width, height);
        zIm = kzIm.buf_img;

        for(int i = 0; i < 4; i++) {
            File f = new File(worldtilepath, makeFilePath(pd, (tx + step*(1&pd.stepseq[i])), (ty + step*(pd.stepseq[i]>>1)), false));
            if(f.exists()) {
                BufferedImage im = null;
            	FileLockManager.getReadLock(f);
                popQueuedUpdate(f, pd.zoomlevel);
                try {
                    im = ImageIO.read(f);
                } catch (IOException e) {
                } catch (IndexOutOfBoundsException e) {
                } finally {
                    FileLockManager.releaseReadLock(f);
                }
                if(im != null) {
                    im.getRGB(0, 0, width, height, argb, 0, width);    /* Read data */
                    im.flush();
                    blank = false;
                    /* Do binlinear scale to 64x64 */
                    int off = 0;
                    for(int y = 0; y < height; y += 2) {
                        off = y*width;
                        for(int x = 0; x < width; x += 2, off += 2) {
                            int p0 = argb[off];
                            int p1 = argb[off+1];
                            int p2 = argb[off+width];
                            int p3 = argb[off+width+1];
                            int alpha = ((p0 >> 24) & 0xFF) + ((p1 >> 24) & 0xFF) + ((p2 >> 24) & 0xFF) + ((p3 >> 24) & 0xFF);
                            int red = ((p0 >> 16) & 0xFF) + ((p1 >> 16) & 0xFF) + ((p2 >> 16) & 0xFF) + ((p3 >> 16) & 0xFF);
                            int green = ((p0 >> 8) & 0xFF) + ((p1 >> 8) & 0xFF) + ((p2 >> 8) & 0xFF) + ((p3 >> 8) & 0xFF);
                            int blue = (p0 & 0xFF) + (p1 & 0xFF) + (p2 & 0xFF) + (p3 & 0xFF);
                            argb[off>>1] = (((alpha>>2)&0xFF)<<24) | (((red>>2)&0xFF)<<16) | (((green>>2)&0xFF)<<8) | ((blue>>2)&0xFF);
                        }
                    }
                    /* blit scaled rendered tile onto zoom-out tile */
                    zIm.setRGB(((i>>1) != 0)?0:width/2, (i & 1) * height/2, width/2, height/2, argb, 0, width);
                }
                else {
                    Arrays.fill(argb, pd.background);
                }
            }
            else {
                Arrays.fill(argb, pd.background);
            }
            /* blit scaled rendered tile onto zoom-out tile */
            zIm.setRGB(((i>>1) != 0)?0:width/2, (i & 1) * height/2, width/2, height/2, argb, 0, width);
        }
        FileLockManager.getWriteLock(zf);
        try {
            MapManager mm = MapManager.mapman;
            if(mm == null)
                return;
            TileHashManager hashman = mm.hashman;
            long crc = hashman.calculateTileHash(kzIm.argb_buf); /* Get hash of tile */
            int tilex = ztx/step/2;
            int tiley = zty/step/2;
            String key = wname+".z"+pd.zoomprefix+pd.baseprefix;
            if(blank) {
                if(zf.exists()) {
                    zf.delete();
                    hashman.updateHashCode(key, null, tilex, tiley, -1);
                    MapManager.mapman.pushUpdate(this, new Client.Tile(zfname));
                    enqueueZoomOutUpdate(zf, pd.zoomlevel+1);
                }
            }
            else if((!zf.exists()) || (crc != mm.hashman.getImageHashCode(key, null, tilex, tiley))) {
                try {
                    if(!zf.getParentFile().exists())
                        zf.getParentFile().mkdirs();
                    FileLockManager.imageIOWrite(zIm, pd.fmt, zf);
                    Debug.debug("Saved zoom-out tile at " + zf.getPath());
                } catch (IOException e) {
                    Debug.error("Failed to save zoom-out tile: " + zf.getName(), e);
                } catch (java.lang.NullPointerException e) {
                    Debug.error("Failed to save zoom-out tile (NullPointerException): " + zf.getName(), e);
                }
                hashman.updateHashCode(key, null, tilex, tiley, crc);
                MapManager.mapman.pushUpdate(this, new Client.Tile(zfname));
                enqueueZoomOutUpdate(zf, pd.zoomlevel+1);
            }
        } finally {
            FileLockManager.releaseWriteLock(zf);
            DynmapBufferedImage.freeBufferedImage(kzIm);
        }
    }
    /* Get world name */
    public String getName() {
        return wname;
    }
    /* Test if world is nether */
    public abstract boolean isNether();
    /* Get world spawn location */
    public abstract DynmapLocation getSpawnLocation();
    
    public int hashCode() {
        return wname.hashCode();
    }
    /* Get world time */
    public abstract long getTime();
    /* World is storming */
    public abstract boolean hasStorm();
    /* World is thundering */
    public abstract boolean isThundering();
    /* World is loaded */
    public abstract boolean isLoaded();
    /* Get light level of block */
    public abstract int getLightLevel(int x, int y, int z);
    /* Get highest Y coord of given location */
    public abstract int getHighestBlockYAt(int x, int z);
    /* Test if sky light level is requestable */
    public abstract boolean canGetSkyLightLevel();
    /* Return sky light level */
    public abstract int getSkyLightLevel(int x, int y, int z);
    /**
     * Get world environment ID (lower case - normal, the_end, nether)
     */
    public abstract String getEnvironment();
    /**
     * Get map chunk cache for world
     */
    public abstract MapChunkCache getChunkCache(List<DynmapChunk> chunks);

    /**
     * Get title for world
     */
    public String getTitle() {
        return title;
    }
    /**
     * Get center location
     */
    public DynmapLocation getCenterLocation() {
        if(center != null)
            return center;
        else
            return getSpawnLocation();
    }
    
    /* Load world configuration from configuration node */
    public boolean loadConfiguration(DynmapCore core, ConfigurationNode worldconfig) {
        is_enabled = worldconfig.getBoolean("enabled", false); 
        if (!is_enabled) {
            return false;
        }
        title = worldconfig.getString("title", wname);
        ConfigurationNode ctr = worldconfig.getNode("center");
        int mid_y = worldheight/2;
        if(ctr != null)
            center = new DynmapLocation(wname, ctr.getDouble("x", 0.0), ctr.getDouble("y", mid_y), ctr.getDouble("z", 0));
        else
            center = null;
        Log.verboseinfo("Loading maps of world '" + wname + "'...");
        for(MapType map : worldconfig.<MapType>createInstances("maps", new Class<?>[] { DynmapCore.class }, new Object[] { core })) {
            if(map.getName() != null)
                maps.add(map);
        }
        Log.info("Loaded " + maps.size() + " maps of world '" + wname + "'.");
        
        List<ConfigurationNode> loclist = worldconfig.getNodes("fullrenderlocations");
        seedloc = new ArrayList<DynmapLocation>();
        seedloccfg = new ArrayList<DynmapLocation>();
        servertime = (int)(getTime() % 24000);
        sendposition = worldconfig.getBoolean("sendposition", true);
        sendhealth = worldconfig.getBoolean("sendhealth", true);
        bigworld = worldconfig.getBoolean("bigworld", false);
        setExtraZoomOutLevels(worldconfig.getInteger("extrazoomout", 0));
        worldtilepath = new File(core.getTilesFolder(), wname);
        if(loclist != null) {
            for(ConfigurationNode loc : loclist) {
                DynmapLocation lx = new DynmapLocation(wname, loc.getInteger("x", 0), loc.getInteger("y", mid_y), loc.getInteger("z", 0));
                seedloc.add(lx); /* Add to both combined and configured seed list */
                seedloccfg.add(lx);
            }
        }
        /* Load visibility limits, if any are defined */
        List<ConfigurationNode> vislimits = worldconfig.getNodes("visibilitylimits");
        if(vislimits != null) {
            visibility_limits = new ArrayList<MapChunkCache.VisibilityLimit>();
            for(ConfigurationNode vis : vislimits) {
                MapChunkCache.VisibilityLimit lim = new MapChunkCache.VisibilityLimit();
                lim.x0 = vis.getInteger("x0", 0);
                lim.x1 = vis.getInteger("x1", 0);
                lim.z0 = vis.getInteger("z0", 0);
                lim.z1 = vis.getInteger("z1", 0);
                visibility_limits.add(lim);
                /* Also, add a seed location for the middle of each visible area */
                seedloc.add(new DynmapLocation(wname, (lim.x0+lim.x1)/2, 64, (lim.z0+lim.z1)/2));
            }            
        }
        /* Load hidden limits, if any are defined */
        List<ConfigurationNode> hidelimits = worldconfig.getNodes("hiddenlimits");
        if(hidelimits != null) {
            hidden_limits = new ArrayList<MapChunkCache.VisibilityLimit>();
            for(ConfigurationNode vis : hidelimits) {
                MapChunkCache.VisibilityLimit lim = new MapChunkCache.VisibilityLimit();
                lim.x0 = vis.getInteger("x0", 0);
                lim.x1 = vis.getInteger("x1", 0);
                lim.z0 = vis.getInteger("z0", 0);
                lim.z1 = vis.getInteger("z1", 0);
                hidden_limits.add(lim);
            }            
        }
        String autogen = worldconfig.getString("autogenerate-to-visibilitylimits", "none");
        if(autogen.equals("permanent")) {
            do_autogenerate = AutoGenerateOption.PERMANENT;
        }
        else if(autogen.equals("map-only")) {
            do_autogenerate = AutoGenerateOption.FORMAPONLY;
        }
        else {
            do_autogenerate = AutoGenerateOption.NONE;
        }
        if((do_autogenerate != AutoGenerateOption.NONE) && (visibility_limits == null)) {
            Log.info("Warning: Automatic world generation to visible limits option requires that visibitylimits be set - option disabled");
            do_autogenerate = AutoGenerateOption.NONE;
        }
        String hiddenchunkstyle = worldconfig.getString("hidestyle", "stone");
        if(hiddenchunkstyle.equals("air"))
            this.hiddenchunkstyle = MapChunkCache.HiddenChunkStyle.FILL_AIR;
        else if(hiddenchunkstyle.equals("ocean"))
            this.hiddenchunkstyle = MapChunkCache.HiddenChunkStyle.FILL_OCEAN;
        else
            this.hiddenchunkstyle = MapChunkCache.HiddenChunkStyle.FILL_STONE_PLAIN;
        
        return true;
    }
    /*
     * Make configuration node for saving world
     */
    public ConfigurationNode saveConfiguration() {
        ConfigurationNode node = new ConfigurationNode();
        /* Add name and title */
        node.put("name", wname);
        node.put("title", getTitle());
        node.put("enabled", is_enabled);
        /* Add center */
        if(center != null) {
            ConfigurationNode c = new ConfigurationNode();
            c.put("x", center.x);
            c.put("y", center.y);
            c.put("z", center.z);
            node.put("center", c.entries);
        }
        /* Add seed locations, if any */
        if(seedloccfg.size() > 0) {
            ArrayList<Map<String,Object>> locs = new ArrayList<Map<String,Object>>();
            for(int i = 0; i < seedloccfg.size(); i++) {
                DynmapLocation dl = seedloccfg.get(i);
                ConfigurationNode ll = new ConfigurationNode();
                ll.put("x", dl.x);
                ll.put("y", dl.y);
                ll.put("z", dl.z);
                locs.add(ll.entries);
            }
            node.put("fullrenderlocations", locs);
        }
        /* Add flags */
        node.put("sendposition", sendposition);
        node.put("sendhealth", sendhealth);
        node.put("bigworld", bigworld);
        node.put("extrazoomout", extrazoomoutlevels);
        /* Save visibility limits, if defined */
        if(visibility_limits != null) {
            ArrayList<Map<String,Object>> lims = new ArrayList<Map<String,Object>>();
            for(int i = 0; i < visibility_limits.size(); i++) {
                MapChunkCache.VisibilityLimit lim = visibility_limits.get(i);
                LinkedHashMap<String, Object> lv = new LinkedHashMap<String,Object>();
                lv.put("x0", lim.x0);
                lv.put("z0", lim.z0);
                lv.put("x1", lim.x1);
                lv.put("z1", lim.z1);
                lims.add(lv);
            }
            node.put("visibilitylimits", lims);
        }
        /* Save hidden limits, if defined */
        if(hidden_limits != null) {
            ArrayList<Map<String,Object>> lims = new ArrayList<Map<String,Object>>();
            for(int i = 0; i < hidden_limits.size(); i++) {
                MapChunkCache.VisibilityLimit lim = visibility_limits.get(i);
                LinkedHashMap<String, Object> lv = new LinkedHashMap<String,Object>();
                lv.put("x0", lim.x0);
                lv.put("z0", lim.z0);
                lv.put("x1", lim.x1);
                lv.put("z1", lim.z1);
                lims.add(lv);
            }
            node.put("hiddenlimits", lims);
        }
        /* Save auto-generate settings */
        String autogen = "none";
        switch(do_autogenerate) {
        case PERMANENT:
            autogen = "permanent";
            break;
        case FORMAPONLY:
            autogen = "map-only";
            break;
        }
        node.put("autogenerate-to-visibilitylimits", autogen);
        /* Handle hide style */
        String hide = "stone";
        switch(hiddenchunkstyle) {
        case FILL_AIR:
            hide = "air";
            break;
        case FILL_OCEAN:
            hide = "ocean";
            break;
        }
        node.put("hidestyle", hide);
        /* Handle map settings */
        ArrayList<Map<String,Object>> mapinfo = new ArrayList<Map<String,Object>>();
        for(MapType mt : maps) {
            ConfigurationNode mnode = mt.saveConfiguration();
            mapinfo.add(mnode);
        }
        node.put("maps", mapinfo);

        return node;
    }
    public boolean isEnabled() {
        return is_enabled;
    }
    public void setTitle(String title) {
        this.title = title;
    }
}

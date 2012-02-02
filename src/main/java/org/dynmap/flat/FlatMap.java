package org.dynmap.flat;

import org.dynmap.DynmapWorld;
import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dynmap.Client;
import org.dynmap.Color;
import org.dynmap.ColorScheme;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapCore.CompassMode;
import org.dynmap.MapManager;
import org.dynmap.TileHashManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.debug.Debug;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.FileLockManager;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.MapIterator.BlockStep;
import org.json.simple.JSONObject;

public class FlatMap extends MapType {
    private String prefix;
    private String name;
    private ColorScheme colorScheme;
    private int maximumHeight = -1;
    private int ambientlight = 15;;
    private int shadowscale[] = null;
    private boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */
    protected boolean transparency;
    private enum Texture { NONE, SMOOTH, DITHER };
    private Texture textured = Texture.NONE;
    private boolean isbigmap;
    private String title;
    private String icon;
    private String bg_cfg;
    private String bg_day_cfg;
    private String bg_night_cfg;
    private int mapzoomin;
    private double shadowstrength;
    
    public FlatMap(DynmapCore core, ConfigurationNode configuration) {
        name = configuration.getString("name", null);
        prefix = configuration.getString("prefix", name);
        colorScheme = ColorScheme.getScheme(core, (String) configuration.get("colorscheme"));
        maximumHeight = configuration.getInteger("maximumheight", 127);

        shadowstrength = configuration.getDouble("shadowstrength", 0.0);
        if(shadowstrength > 0.0) {
            shadowscale = new int[16];
            shadowscale[15] = 256;
            /* Normal brightness weight in MC is a 20% relative dropoff per step */
            for(int i = 14; i >= 0; i--) {
                double v = shadowscale[i+1] * (1.0 - (0.2 * shadowstrength));
                shadowscale[i] = (int)v;
                if(shadowscale[i] > 256) shadowscale[i] = 256;
                if(shadowscale[i] < 0) shadowscale[i] = 0;
            }
        }
        ambientlight = configuration.getInteger("ambientlight", 15);
        
        night_and_day = configuration.getBoolean("night-and-day", false);
        transparency = configuration.getBoolean("transparency", false);  /* Default off */
        String tex = configuration.getString("textured", "none");
        if(tex.equals("none"))
            textured = Texture.NONE;
        else if(tex.equals("dither"))
            textured = Texture.DITHER;
        else
            textured = Texture.SMOOTH;
        isbigmap = configuration.getBoolean("isbigmap", false);

        title = configuration.getString("title");
        icon = configuration.getString("icon");
        bg_cfg = configuration.getString("background");
        bg_day_cfg = configuration.getString("backgroundday");
        bg_night_cfg = configuration.getString("backgroundnight");
        mapzoomin = configuration.getInteger("mapzoomin", 3);
    }
    
    @Override
    public ConfigurationNode saveConfiguration() {
        ConfigurationNode cn = super.saveConfiguration();
        if(title != null)
            cn.put("title", title);
        cn.put("prefix", prefix);
        if(icon != null)
            cn.put("icon", icon);
        if(colorScheme != null)
            cn.put("colorscheme", colorScheme.name);
        cn.put("maximumheight", maximumHeight);
        cn.put("shadowstrength", shadowstrength);
        cn.put("ambientlgith", ambientlight);
        cn.put("night-and-day", night_and_day);
        cn.put("transparency", transparency);
        String txt = "none";
        if(textured == Texture.DITHER)
            txt = "dither";
        else if(textured == Texture.SMOOTH)
            txt = "smooth";
        cn.put("textured", txt);
        cn.put("isbigmap", isbigmap);
        if(bg_cfg != null)
            cn.put("background", bg_cfg);
        if(bg_day_cfg != null)
            cn.put("backgroundday", bg_day_cfg);
        if(bg_night_cfg != null)
            cn.put("backgroundnight", bg_night_cfg);
        cn.put("mapzoomin", mapzoomin);

        return cn;
    }

    @Override
    public MapTile[] getTiles(DynmapWorld w, int x, int y, int z) {
        return new MapTile[] { new FlatMapTile(w, this, x>>7, z>>7, 128) };
    }

    @Override
    public MapTile[] getTiles(DynmapWorld w, int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {
        ArrayList<MapTile> rslt = new ArrayList<MapTile>();
        for(int i = xmin; i <= xmax; i++) {
            for(int j = zmin; j < zmax; j++) {
                rslt.add(new FlatMapTile(w, this, i, j, 128));
            }
        }
        return rslt.toArray(new MapTile[rslt.size()]);
    }
    
    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        FlatMapTile t = (FlatMapTile) tile;
        DynmapWorld w = t.getDynmapWorld();
        int x = t.x;
        int y = t.y;
        int s = t.size;
        return new MapTile[] {
            new FlatMapTile(w, this, x, y - 1, s),
            new FlatMapTile(w, this, x + 1, y, s),
            new FlatMapTile(w, this, x, y + 1, s),
            new FlatMapTile(w, this, x - 1, y, s) };
    }

    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile tile) {
        FlatMapTile t = (FlatMapTile) tile;
        int chunksPerTile = t.size / 16;
        int sx = t.x * chunksPerTile;
        int sz = t.y * chunksPerTile;

        ArrayList<DynmapChunk> result = new ArrayList<DynmapChunk>(chunksPerTile * chunksPerTile);
        for (int x = 0; x < chunksPerTile; x++)
            for (int z = 0; z < chunksPerTile; z++) {
                result.add(new DynmapChunk(sx + x, sz + z));
            }
        return result;
    }

    public boolean render(MapChunkCache cache, MapTile tile, File outputFile) {
        FlatMapTile t = (FlatMapTile) tile;
        int maxheight = maximumHeight;
        int worldheight = tile.getDynmapWorld().worldheight - 1;
        int hshift = tile.getDynmapWorld().heightshift - 7;
        
        if(maxheight < 0)
            maxheight = worldheight;
        boolean isnether = t.getDynmapWorld().isNether() && (maxheight == worldheight);

        boolean didwrite = false;
        Color rslt = new Color();
        int[] pixel = new int[4];
        int[] pixel_day = null;
        DynmapBufferedImage im = DynmapBufferedImage.allocateBufferedImage(t.size, t.size);
        int[] argb_buf = im.argb_buf;
        DynmapBufferedImage im_day = null;
        int[] argb_buf_day = null;
        if(night_and_day) {
            im_day = DynmapBufferedImage.allocateBufferedImage(t.size, t.size);
            argb_buf_day = im_day.argb_buf;
            pixel_day = new int[4];
        }
        MapIterator mapiter = cache.getIterator(t.x * t.size, worldheight, t.y * t.size);
        for (int x = 0; x < t.size; x++) {
            mapiter.initialize(t.x * t.size + x, worldheight, t.y * t.size);
            for (int y = 0; y < t.size; y++, mapiter.stepPosition(BlockStep.Z_PLUS)) {
                int blockType;
                mapiter.setY(worldheight);
                if(isnether) {
                    while((blockType = mapiter.getBlockTypeID()) != 0) {
                        mapiter.stepPosition(BlockStep.Y_MINUS);
                        if(mapiter.getY() < 0) {    /* Solid - use top */
                            mapiter.setY(worldheight);
                            blockType = mapiter.getBlockTypeID();
                            break;
                        }
                    }
                    if(blockType == 0) {    /* Hit air - now find non-air */
                        while((blockType = mapiter.getBlockTypeID()) == 0) {
                            mapiter.stepPosition(BlockStep.Y_MINUS);
                            if(mapiter.getY() < 0) {
                                mapiter.setY(0);
                                break;
                            }
                        }
                    }
                }
                else {
                    int my = maxheight;
                    mapiter.setY(my);
                    while((blockType = mapiter.getBlockTypeID()) == 0) {
                        my--;
                        if(my < 0) break;
                        mapiter.stepPosition(BlockStep.Y_MINUS);
                    }
                }
                int data = 0;
                Color[] colors;
                try {
                    if(colorScheme.datacolors[blockType] != null) {
                        data = mapiter.getBlockData();
                        colors = colorScheme.datacolors[blockType][data];
                    }
                    else {
                        colors = colorScheme.colors[blockType];
                    }
                } catch (ArrayIndexOutOfBoundsException aioobx) {
                    colorScheme.resizeColorArray(blockType);
                    colors = null;
                }
                if (colors == null)
                    continue;
                Color c;
                if(textured == Texture.SMOOTH)
                    c = colors[4];
                else if((textured == Texture.DITHER) && (((x+y) & 0x01) == 1)) {
                    c = colors[2];                    
                }
                else {
                    c = colors[0];
                }
                if (c == null)
                    continue;

                pixel[0] = c.getRed();
                pixel[1] = c.getGreen();
                pixel[2] = c.getBlue();
                pixel[3] = c.getAlpha();
                
                /* If transparency needed, process it */
                if(transparency && (pixel[3] < 255)) {
                    process_transparent(pixel, pixel_day, mapiter);
                }
                /* If ambient light less than 15, do scaling */
                else if((shadowscale != null) && (ambientlight < 15)) {
                    if(mapiter.getY() < worldheight) 
                        mapiter.stepPosition(BlockStep.Y_PLUS);
                    if(night_and_day) { /* Use unscaled color for day (no shadows from above) */
                        pixel_day[0] = pixel[0];    
                        pixel_day[1] = pixel[1];
                        pixel_day[2] = pixel[2];
                        pixel_day[3] = 255;
                    }
                    int light = Math.max(ambientlight, mapiter.getBlockEmittedLight());
                    pixel[0] = (pixel[0] * shadowscale[light]) >> 8;
                    pixel[1] = (pixel[1] * shadowscale[light]) >> 8;
                    pixel[2] = (pixel[2] * shadowscale[light]) >> 8;
                    pixel[3] = 255;
                }
                else {  /* Only do height keying if we're not messing with ambient light */
                    int ys = mapiter.getY() >> hshift;  /* Normalize to 0-127 */
                    boolean below = ys < 64;

                    // Make height range from 0 - 1 (1 - 0 for below and 0 - 1 above)
                    float height = (below ? 64 - ys : ys - 64) / 64.0f;

                    // Defines the 'step' in coloring.
                    float step = 10 / 128.0f;

                    // The step applied to height.
                    float scale = ((int)(height/step))*step;

                    // Make the smaller values change the color (slightly) more than the higher values.
                    scale = (float)Math.pow(scale, 1.1f);

                    // Don't let the color go fully white or fully black.
                    scale *= 0.8f;

                    if (below) {
                        pixel[0] -= pixel[0] * scale;
                        pixel[1] -= pixel[1] * scale;
                        pixel[2] -= pixel[2] * scale;
                        pixel[3] = 255;
                    } else {
                        pixel[0] += (255-pixel[0]) * scale;
                        pixel[1] += (255-pixel[1]) * scale;
                        pixel[2] += (255-pixel[2]) * scale;
                        pixel[3] = 255;
                    }
                    if(night_and_day) {
                        pixel_day[0] = pixel[0];
                        pixel_day[1] = pixel[1];
                        pixel_day[2] = pixel[2];
                        pixel_day[3] = 255;
                    }
                        
                }
                rslt.setRGBA(pixel[0], pixel[1], pixel[2], pixel[3]);
                argb_buf[(t.size-y-1) + (x*t.size)] = rslt.getARGB();
                if(night_and_day) {
                    rslt.setRGBA(pixel_day[0], pixel_day[1], pixel_day[2], pixel[3]);
                    argb_buf_day[(t.size-y-1) + (x*t.size)] = rslt.getARGB();
                }
            }
        }
        /* Test to see if we're unchanged from older tile */
        TileHashManager hashman = MapManager.mapman.hashman;
        long crc = hashman.calculateTileHash(argb_buf);
        boolean tile_update = false;
        FileLockManager.getWriteLock(outputFile);
        try {
            if((!outputFile.exists()) || (crc != hashman.getImageHashCode(tile.getKey(prefix), null, t.x, t.y))) {
                /* Wrap buffer as buffered image */
                Debug.debug("saving image " + outputFile.getPath());
                if(!outputFile.getParentFile().exists())
                    outputFile.getParentFile().mkdirs();
                try {
                    FileLockManager.imageIOWrite(im.buf_img, ImageFormat.FORMAT_PNG, outputFile);
                } catch (IOException e) {
                    Debug.error("Failed to save image: " + outputFile.getPath(), e);
                } catch (java.lang.NullPointerException e) {
                    Debug.error("Failed to save image (NullPointerException): " + outputFile.getPath(), e);
                }
                MapManager.mapman.pushUpdate(tile.getDynmapWorld(), new Client.Tile(tile.getFilename()));
                hashman.updateHashCode(tile.getKey(prefix), null, t.x, t.y, crc);
                tile.getDynmapWorld().enqueueZoomOutUpdate(outputFile);
                tile_update = true;
                didwrite = true;
            }
            else {
                Debug.debug("skipping image " + outputFile.getPath() + " - hash match");
            }
        } finally {
            FileLockManager.releaseWriteLock(outputFile);
            DynmapBufferedImage.freeBufferedImage(im);
        }
        MapManager.mapman.updateStatistics(tile, prefix, true, tile_update, true);

        /* If day too, handle it */
        if(night_and_day) {
            File dayfile = new File(tile.getDynmapWorld().worldtilepath, tile.getDayFilename());
            crc = hashman.calculateTileHash(argb_buf_day);
            FileLockManager.getWriteLock(dayfile);
            try {
                if((!dayfile.exists()) || (crc != hashman.getImageHashCode(tile.getKey(prefix), "day", t.x, t.y))) {
                    Debug.debug("saving image " + dayfile.getPath());
                    if(!dayfile.getParentFile().exists())
                        dayfile.getParentFile().mkdirs();
                    try {
                        FileLockManager.imageIOWrite(im_day.buf_img, ImageFormat.FORMAT_PNG, dayfile);
                    } catch (IOException e) {
                        Debug.error("Failed to save image: " + dayfile.getPath(), e);
                    } catch (java.lang.NullPointerException e) {
                        Debug.error("Failed to save image (NullPointerException): " + dayfile.getPath(), e);
                    }
                    MapManager.mapman.pushUpdate(tile.getDynmapWorld(), new Client.Tile(tile.getDayFilename()));   
                    hashman.updateHashCode(tile.getKey(prefix), "day", t.x, t.y, crc);
                    tile.getDynmapWorld().enqueueZoomOutUpdate(dayfile);
                    tile_update = true;
                    didwrite = true;
                }
                else {
                    Debug.debug("skipping image " + dayfile.getPath() + " - hash match");
                    tile_update = false;
                }
            } finally {
                FileLockManager.releaseWriteLock(dayfile);
                DynmapBufferedImage.freeBufferedImage(im_day);
            }
            MapManager.mapman.updateStatistics(tile, prefix+"_day", true, tile_update, true);
        }
        
        return didwrite;
    }
    private void process_transparent(int[] pixel, int[] pixel_day, MapIterator mapiter) {
        int r = pixel[0], g = pixel[1], b = pixel[2], a = pixel[3];
        int r_day = 0, g_day = 0, b_day = 0, a_day = 0;
        if(pixel_day != null) {
            r_day = pixel[0]; g_day = pixel[1]; b_day = pixel[2]; a_day = pixel[3];
        }
        /* Scale alpha to be proportional to iso view (where we go through 4 blocks to go sqrt(6) or 2.45 units of distance */
        if(a < 255)
            a = a_day = 255 - ((255-a)*(255-a) >> 8);
        /* Handle lighting on cube */
        if((shadowscale != null) && (ambientlight < 15)) {
            boolean did_inc = false;
            if(mapiter.getY() < 127) {
                mapiter.stepPosition(BlockStep.Y_PLUS);
                did_inc = true;
            }
            if(night_and_day) { /* Use unscaled color for day (no shadows from above) */
                r_day = r; g_day = g; b_day = b; a_day = a;
            }
            int light = Math.max(ambientlight, mapiter.getBlockEmittedLight());
            r = (r * shadowscale[light]) >> 8;
            g = (g * shadowscale[light]) >> 8;
            b = (b * shadowscale[light]) >> 8;
            if(did_inc)
                mapiter.stepPosition(BlockStep.Y_MINUS);
        }
        if(a < 255) {   /* If not opaque */
            pixel[0] = pixel[1] = pixel[2] = pixel[3] = 0;
            if(pixel_day != null) 
                pixel_day[0] = pixel_day[1] = pixel_day[2] = pixel_day[3] = 0;
            mapiter.stepPosition(BlockStep.Y_MINUS);
            if(mapiter.getY() >= 0) {
                int blockType = mapiter.getBlockTypeID();
                int data = 0;
                Color[] colors = colorScheme.colors[blockType];
                if(colorScheme.datacolors[blockType] != null) {
                    data = mapiter.getBlockData();
                    colors = colorScheme.datacolors[blockType][data];
                }
                if (colors != null) {
                    Color c = colors[0];
                    if (c != null) {
                        pixel[0] = c.getRed();
                        pixel[1] = c.getGreen();
                        pixel[2] = c.getBlue();
                        pixel[3] = c.getAlpha();
                    }
                }
                /* Recurse to resolve color here */
                process_transparent(pixel, pixel_day, mapiter);
            }
        }
        /* Blend colors from behind block and block, based on alpha */
        r *= a;
        g *= a;
        b *= a;
        int na = 255 - a;
        pixel[0] = (pixel[0] * na + r) >> 8;
        pixel[1] = (pixel[1] * na + g) >> 8;
        pixel[2] = (pixel[2] * na + b) >> 8;
        pixel[3] = 255;
        if(pixel_day != null) {
            r_day *= a_day;
            g_day *= a_day;
            b_day *= a_day;
            na = 255 - a_day;
            pixel_day[0] = (pixel_day[0] * na + r_day) >> 8;
            pixel_day[1] = (pixel_day[1] * na + g_day) >> 8;
            pixel_day[2] = (pixel_day[2] * na + b_day) >> 8;
            pixel_day[3] = 255;
        }
    }

    public String getName() {
        return name;
    }
    
    public String getPrefix() {
        return prefix;
    }

    /* Get maps rendered concurrently with this map in this world */
    public List<MapType> getMapsSharingRender(DynmapWorld w) {
        return Collections.singletonList((MapType)this);
    }

    /* Get names of maps rendered concurrently with this map type in this world */
    public List<String> getMapNamesSharingRender(DynmapWorld w) {
        return Collections.singletonList(name);
    }

    public List<ZoomInfo> baseZoomFileInfo() {
        ArrayList<ZoomInfo> s = new ArrayList<ZoomInfo>();
        s.add(new ZoomInfo(getPrefix() + "_128", 0));
        if(night_and_day)
            s.add(new ZoomInfo(getPrefix()+"_day_128", 0));
        return s;
    }
    
    public int baseZoomFileStepSize() { return 1; }

    private static final int[] stepseq = { 1, 3, 0, 2 };
    
    public MapStep zoomFileMapStep() { return MapStep.X_PLUS_Y_PLUS; }

    public int[] zoomFileStepSequence() { return stepseq; }

    /* How many bits of coordinate are shifted off to make big world directory name */
    public int getBigWorldShift() { return 5; }

    /* Returns true if big world file structure is in effect for this map */
    @Override
    public boolean isBigWorldMap(DynmapWorld w) {
        return w.bigworld || isbigmap;
    }

    public static class FlatMapTile extends MapTile {
        FlatMap map;
        public int x;
        public int y;
        public int size;
        private String fname;
        private String fname_day;

        public FlatMapTile(DynmapWorld world, FlatMap map, int x, int y, int size) {
            super(world);
            this.map = map;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        public FlatMapTile(DynmapWorld world, String parm) throws Exception {
            super(world);
            
            String[] parms = parm.split(",");
            if(parms.length < 4) throw new Exception("wrong parameter count");
            this.x = Integer.parseInt(parms[0]);
            this.y = Integer.parseInt(parms[1]);
            this.size = Integer.parseInt(parms[2]);
            for(MapType t : world.maps) {
                if(t.getName().equals(parms[3]) && (t instanceof FlatMap)) {
                    this.map = (FlatMap)t;
                    break;
                }
            }
            if(this.map == null) throw new Exception("invalid map");
        }
        
        @Override
        protected String saveTileData() {
            return String.format("%d,%d,%d,%s", x, y, size, map.getName());
        }
        
        @Override
        public String getFilename() {
            if(fname == null) {
                if(world.bigworld)
                    fname = map.prefix + "_" + size + "/" + ((-(y+1))>>5) + "_" + (x>>5) + "/" + -(y+1) + "_" + x + ".png";
                else
                    fname = map.prefix + "_" + size + "_" + -(y+1) + "_" + x + ".png";
            }
            return fname;
        }
        @Override
        public String getDayFilename() {
            if(fname_day == null) {
                if(world.bigworld)
                    fname_day = map.prefix + "_day_" + size + "/" + ((-(y+1))>>5) + "_" + (x>>5) + "/" + -(y+1) + "_" + x + ".png";
                else
                    fname_day = map.prefix + "_day_" + size + "_" + -(y+1) + "_" + x + ".png";
            }
            return fname_day;
        }
        public String toString() {
            return world.getName() + ":" + getFilename();
        }

        @Override
        public boolean render(MapChunkCache cache, String mapname) {
            return map.render(cache, this, MapManager.mapman.getTileFile(this));
        }

        @Override
        public List<DynmapChunk> getRequiredChunks() {
            return map.getRequiredChunks(this);
        }

        @Override
        public MapTile[] getAdjecentTiles() {
            return map.getAdjecentTiles(this);
        }

        @Override
        public int hashCode() {
            return x ^ y ^ size ^ map.getName().hashCode();
        }
        
        @Override
        public boolean equals(Object x) {
            if(x instanceof FlatMapTile) {
                return equals((FlatMapTile)x);
            }
            return false;
        }
        public boolean equals(FlatMapTile o) {
            return (o.x == x) && (o.y == y) && (o.map == map);
        }
        
        @Override
        public String getKey(String prefix) {
            return world.getName() + "." + map.getPrefix();
        }
        
        public boolean isHightestBlockYDataNeeded() { return true; }
        public boolean isBiomeDataNeeded() { return false; }
        public boolean isRawBiomeDataNeeded() { return false; }
        public boolean isBlockTypeDataNeeded() { return true; }
        public int tileOrdinalX() { return x; }
        public int tileOrdinalY() { return y; }

    }
    
    @Override
    public void buildClientConfiguration(JSONObject worldObject, DynmapWorld world) {
        JSONObject o = new JSONObject();
        s(o, "type", "FlatMapType");
        s(o, "name", name);
        s(o, "title", title);
        s(o, "icon", icon);
        s(o, "prefix", prefix);
        s(o, "background", bg_cfg);
        s(o, "nightandday", night_and_day);
        s(o, "backgroundday", bg_day_cfg);
        s(o, "backgroundnight", bg_night_cfg);
        s(o, "bigmap", this.isBigWorldMap(world));
        s(o, "mapzoomin", mapzoomin);
        s(o, "mapzoomout", world.getExtraZoomOutLevels());
        if(MapManager.mapman.getCompassMode() != CompassMode.PRE19)
            s(o, "compassview", "E");   /* Always from east */
        else
            s(o, "compassview", "S");   /* Always from south */
        s(o, "image-format", ImageFormat.FORMAT_PNG.getFileExt());
        a(worldObject, "maps", o);
    }
}

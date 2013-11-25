package org.dynmap.kzedmap;

import org.dynmap.DynmapWorld;
import java.io.File;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.TileFlags;
import org.json.simple.JSONObject;

public class KzedMap extends MapType {
    /* dimensions of a map tile */
    public static final int tileWidth = 128;
    public static final int tileHeight = 128;

    /*
     * (logical!) dimensions of a zoomed out map tile must be twice the size of
     * the normal tile
     */
    public static final int zTileWidth = 256;
    public static final int zTileHeight = 256;
    
    public MapTileRenderer[] renderers;
    private boolean isbigmap;

    public KzedMap(DynmapCore core, ConfigurationNode configuration) {
        Log.verboseinfo("Loading renderers for map '" + getClass().toString() + "'...");
        List<MapTileRenderer> renderers = configuration.<MapTileRenderer>createInstances("renderers", new Class<?>[] { DynmapCore.class }, new Object[] { core } );
        this.renderers = new MapTileRenderer[renderers.size()];
        renderers.toArray(this.renderers);
        Log.verboseinfo("Loaded " + renderers.size() + " renderers for map '" + getClass().toString() + "'.");
        isbigmap = configuration.getBoolean("isbigmap", false);
        setTileUpdateDelay(configuration.getInteger("tileupdatedelay", -1));
    }

    @Override
    public ConfigurationNode saveConfiguration() {
        ConfigurationNode cn = super.saveConfiguration();

        cn.put("isbigmap", isbigmap);
        if(this.tileupdatedelay > 0) {
            cn.put("tileupdatedelay", this.tileupdatedelay);
        }
        ArrayList<Map<String,Object>> rendinfo = new ArrayList<Map<String,Object>>();
        for(int i = 0; i < renderers.length; i++) {
            ConfigurationNode rcn = renderers[i].saveConfiguration();
            rendinfo.add(rcn.entries);
        }
        
        return cn;
    }
    @Override
    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int x, int y, int z) {
        int dx = x;
        int dy = y - 127;
        int dz = z;
        int px = dx + dz;
        int py = dx - dz - dy;

        int tx = tilex(px);
        int ty = tiley(py);

        ArrayList<TileFlags.TileCoord> tiles = new ArrayList<TileFlags.TileCoord>();

        boolean ledge = tilex(px - 4) != tx;
        boolean tedge = tiley(py - 4) != ty;
        boolean redge = tilex(px + 4) != tx;
        boolean bedge = tiley(py + 4) != ty;

        int tilex = tx >> 7;
        int tiley = ty >> 7; 

        tiles.add(new TileFlags.TileCoord(tilex, tiley));
        if (ledge)
            tiles.add(new TileFlags.TileCoord(tilex - 1, tiley));
        if (redge)
            tiles.add(new TileFlags.TileCoord(tilex + 1, tiley));
        if (tedge)
            tiles.add(new TileFlags.TileCoord(tilex, tiley - 1));
        if (bedge)
            tiles.add(new TileFlags.TileCoord(tilex, tiley + 1));

        if (ledge && tedge)
            tiles.add(new TileFlags.TileCoord(tilex - 1, tiley - 1));
        if (ledge && bedge)
            tiles.add(new TileFlags.TileCoord(tilex - 1, tiley + 1));
        if (redge && tedge)
            tiles.add(new TileFlags.TileCoord(tilex + 1, tiley - 1));
        if (redge && bedge)
            tiles.add(new TileFlags.TileCoord(tilex + 1, tiley + 1));

        return tiles;
    }

    @Override
    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld world, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        ArrayList<TileFlags.TileCoord> tiles = new ArrayList<TileFlags.TileCoord>();
        /* Transform both to tile coordinates */
        int dx = minx;
        int dy = miny - 127;
        int dz = minz;
        int px0 = dx + dz;
        int py0 = dx - dz - dy;
        dx = maxx;
        dy = maxy - 127;
        dz = maxz;
        int px1 = dx + dz;
        int py1 = dx - dz - dy;
        /* Compute ranges */
        int mintx = (px1<px0)?px0:px1;
        int maxtx = (px1<px0)?px1+1:px0+1;
        int minty = (py1<py0)?py0:py1;
        int maxty = (py1<py0)?py1+1:py0+1;
        
        /* Now, add the tiles for the ranges - not perfect, but it works (some extra tiles on corners possible) */
        for(int i = mintx >> 7; i <= maxtx >> 7; i++) {
            for(int j = minty >> 7; j < maxty >> 7; j++) {
                tiles.add(new TileFlags.TileCoord(i, j));
            }
        }
        return tiles;
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            KzedMapTile t = (KzedMapTile) tile;
            DynmapWorld world = tile.getDynmapWorld();
            MapTileRenderer renderer = t.renderer;
            return new MapTile[] {
                new KzedMapTile(world, this, renderer, t.px - tileWidth, t.py + tileHeight),
                new KzedMapTile(world, this, renderer, t.px + tileWidth, t.py - tileHeight),
                new KzedMapTile(world, this, renderer, t.px - tileWidth, t.py - tileHeight),
                new KzedMapTile(world, this, renderer, t.px + tileWidth, t.py + tileHeight),
                new KzedMapTile(world, this, renderer, t.px - tileWidth, t.py),
                new KzedMapTile(world, this, renderer, t.px + tileWidth, t.py),
                new KzedMapTile(world, this, renderer, t.px, t.py - tileHeight),
                new KzedMapTile(world, this, renderer, t.px, t.py + tileHeight) };
        }
        return new MapTile[0];
    }

    /**
     * Test if point x,z is inside rectangle with corner at r0x,r0z and with
     * size vectors s1x,s1z and s2x,s2z
     *
     */
    private boolean testPointInRectangle(int x, int z, int r0x, int r0z, int s1x, int s1z,
            int s2x, int s2z) {
        int xr = x - r0x;
        int zr = z - r0z;   /* Get position relative to rectangle corner */
        int dots1 = xr*s1x + zr*s1z;
        int dots2 = xr*s2x + zr*s2z;
        /* If dot product of relative point and each side is between zero and dot product
         * of each side and itself, we're inside
         */
        if((dots1 >= 0) && (dots1 <= (s1x*s1x+s1z*s1z)) &&
                (dots2 >= 0) && (dots2 <= (s2x*s2x+s2z*s2z))) {
            return true;
        }
        return false;
    }
    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile tile) {
        if (tile instanceof KzedMapTile) {
            KzedMapTile t = (KzedMapTile) tile;

            int vscale = tile.getDynmapWorld().worldheight / 128;
            
            int ix = t.px / 2 + t.py / 2;
            int iz = t.px / 2 - t.py / 2;

            int x1 = ix - (KzedMap.tileHeight / 2);
            int x2 = ix + (KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2) * vscale;

            int z1 = iz - (KzedMap.tileHeight / 2) * vscale;
            int z2 = iz + KzedMap.tileWidth / 2 + KzedMap.tileHeight / 2;

            int x, z;

            /* Actual pattern of chunks needed is create by the slanted
             * square prism corresponding to the render path of the tile.
             * Top of prism (corresponding to y=127) is diamond shape from
             * ix, iz to ix+64,iz+64 to ix+128,iz to ix+64,iz-64
             * Bottom is same shape, offset by -64 on x, +64 on z (net
             * render path to y=0), correspond to ix-64, iz+64 to
             * ix,iz+128 to ix+64,iz+64 to ix,iz.  Projection of
             * the prism on to the x,z plane (which is all that matters for
             * chunks) yields a diagonal rectangular area from ix-64(x1),iz+64
             * to ix,iz+128(z2) to ix+128(x2),iz to ix+64,iz-64(z1).
             * Chunks outside this are not needed - we scan a simple rectangle
             * (chunk grid aligned) and skip adding the ones that are outside.
             * This results in 42% less chunks being loaded.
             */
            ArrayList<DynmapChunk> chunks = new ArrayList<DynmapChunk>();

            int prismx = KzedMap.tileWidth*(vscale+1)/2;
            int prismz = -(KzedMap.tileHeight+(vscale+1)/2);
            for (x = x1; x < x2; x += 16) {
                for (z = z1; z < z2; z += 16) {
                    /* If any of the chunk corners are inside the rectangle, we need it */
                    if((!testPointInRectangle(x, z, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            prismx, prismz)) &&
                        (!testPointInRectangle(x+15, z, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            prismx, prismz)) &&
                        (!testPointInRectangle(x+15, z+15, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            prismx, prismz)) &&
                        (!testPointInRectangle(x, z+15, x1, iz + KzedMap.tileWidth/2,
                            KzedMap.tileWidth/2, KzedMap.tileHeight/2,
                            prismx, prismz)))
                        continue;
                    DynmapChunk chunk = new DynmapChunk(x / 16, z / 16);
                    chunks.add(chunk);
                }
            }
            return chunks;
        } else {
            return new ArrayList<DynmapChunk>();
        }
    }

    public boolean render(MapChunkCache cache, MapTile tile, File outputFile) {
        if (tile instanceof KzedMapTile) {
            return ((KzedMapTile) tile).renderer.render(cache, (KzedMapTile) tile, outputFile);
        }
        return false;
    }

    /* tile X for position x */
    static int tilex(int x) {
        if (x < 0)
            return x - (tileWidth + (x % tileWidth));
        else
            return x - (x % tileWidth);
    }

    /* tile Y for position y */
    static int tiley(int y) {
        if (y < 0)
            return y - (tileHeight + (y % tileHeight));
        else
            return y - (y % tileHeight);
    }

    /* zoomed-out tile X for tile position x */
    static int ztilex(int x) {
        if (x < 0)
            return x + x % zTileWidth;
        else
            return x - (x % zTileWidth);
    }

    /* zoomed-out tile Y for tile position y */
    static int ztiley(int y) {
        if (y < 0)
            return y + y % zTileHeight;
        else
            return y - (y % zTileHeight);
    }


    public boolean isBiomeDataNeeded() {
        for(MapTileRenderer r : renderers) {
           if(r.isBiomeDataNeeded())
               return true;
        }
        return false;
    }

    public boolean isRawBiomeDataNeeded() { 
        for(MapTileRenderer r : renderers) {
            if(r.isRawBiomeDataNeeded())
                return true;
         }
         return false;
     }

    public List<ZoomInfo> baseZoomFileInfo() {
        ArrayList<ZoomInfo> s = new ArrayList<ZoomInfo>();
        for(MapTileRenderer r : renderers) {
            s.add(new ZoomInfo("z" + r.getPrefix(), 0));
            if(r.isNightAndDayEnabled())
                s.add(new ZoomInfo("z" + r.getPrefix() + "_day", 0));
        }
        return s;
    }
    public int baseZoomFileStepSize() { return zTileWidth; }
    
    public MapStep zoomFileMapStep() { return MapStep.X_MINUS_Y_PLUS; }

    private static final int[] stepseq = { 0, 2, 1, 3 };
    
    public int[] zoomFileStepSequence() { return stepseq; }
    /* How many bits of coordinate are shifted off to make big world directory name */
    public int getBigWorldShift() { return 12; }

    /* Returns true if big world file structure is in effect for this map */
    @Override
    public boolean isBigWorldMap(DynmapWorld w) {
        return w.bigworld || isbigmap; 
    }
    
    public String getName() {
        return "KzedMap";
    }

    /* Get maps rendered concurrently with this map in this world */
    public List<MapType> getMapsSharingRender(DynmapWorld w) {
        return Collections.singletonList((MapType)this);
    }

    /* Get names of maps rendered concurrently with this map type in this world */
    public List<String> getMapNamesSharingRender(DynmapWorld w) {
        ArrayList<String> lst = new ArrayList<String>();
        for(MapTileRenderer rend : renderers) {
            if(rend.isNightAndDayEnabled())
                lst.add(rend.getName() + "(night/day)");
            else
                lst.add(rend.getName());
        }
        return lst;
    }

    @Override
    public void buildClientConfiguration(JSONObject worldObject, DynmapWorld world) {
        for(MapTileRenderer renderer : renderers) {
            renderer.buildClientConfiguration(worldObject, world, this);
        }
    }
    
    @Override
    public String getPrefix() {
        //throw new InvalidParameterException("getPrefix not valid on KzedMap");
        return "kzed";
    }

    @Override
    public void addMapTiles(List<MapTile> list, DynmapWorld w, int tx, int ty) {
        for(int i = 0; i < renderers.length; i++) {
            list.add(new KzedMapTile(w, this, renderers[i], tx << 7, ty << 7));
        }
    }
}

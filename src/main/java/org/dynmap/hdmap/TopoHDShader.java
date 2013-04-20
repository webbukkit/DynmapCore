package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import java.util.List;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.utils.DynLongHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.BlockStep;
import org.json.simple.JSONObject;

public class TopoHDShader implements HDShader {
    private String name;
    private Color linecolor;  /* Color for topo lines */
    private Color fillcolor[];  /* Color for nontopo surfaces */
    private Color watercolor;
    private int[] hiddenids;
    
    private Color readColor(String id, ConfigurationNode cfg) {
        String lclr = cfg.getString(id, null);
        if((lclr != null) && (lclr.startsWith("#"))) {
            try {
                int c = Integer.parseInt(lclr.substring(1), 16);
                return new Color((c>>16)&0xFF, (c>>8)&0xFF, c&0xFF);
            } catch (NumberFormatException nfx) {
                Log.severe("Invalid color value: " + lclr + " for '" + id + "'");
            }
        }
        return null;
    }
    public TopoHDShader(DynmapCore core, ConfigurationNode configuration) {
        name = (String) configuration.get("name");
        
        fillcolor = new Color[256];   /* Color by Y */
        /* Load defined colors from parameters */
        for(int i = 0; i < 256; i++) {
            fillcolor[i] = readColor("color" + i,  configuration);
        }
        linecolor = readColor("linecolor",  configuration);
        watercolor = readColor("watercolor",  configuration);
        /* Now, interpolate missing colors */
        if(fillcolor[0] == null) {
            fillcolor[0] = new Color(0, 0, 0);
        }
        if(fillcolor[255] == null) {
            fillcolor[255] = new Color(255, 255, 255);
        }
        int starty = 0;
        for(int i = 1; i < 256; i++) {
            if(fillcolor[i] != null) {  /* Found color? */
                int delta = i - starty;
                Color c0 = fillcolor[starty];
                Color c1 = fillcolor[i];
                /* Interpolate missing colors since last one */
                for(int j = 1; j < delta; j++) {
                    fillcolor[starty + j] = new Color((c0.getRed()*(delta-j) + c1.getRed()*j)/delta, (c0.getGreen()*(delta-j) + c1.getGreen()*j)/delta, (c0.getBlue()*(delta-j) + c1.getBlue()*j)/delta);
                }
                starty = i;  /* New start color */
            }
        }
        hiddenids = new int[2048];
        hiddenids[0] = 0x1; /* Air is hidden always */
        List<Object> hidden = configuration.getList("hiddenids");
        if(hidden != null) {
            for(Object o : hidden) {
                if(o instanceof Integer) {
                    int v = ((Integer)o);
                    if((v > 0) && (v < 65535)) {
                        hiddenids[v >> 5] |= (1 << (v & 0x1F));
                    }
                }
            }
        }
    }
    
    @Override
    public boolean isBiomeDataNeeded() { 
        return false; 
    }
    
    @Override
    public boolean isRawBiomeDataNeeded() { 
        return false; 
    }
    
    @Override
    public boolean isHightestBlockYDataNeeded() {
        return false;
    }

    @Override
    public boolean isBlockTypeDataNeeded() {
        return true;
    }

    @Override
    public boolean isSkyLightLevelNeeded() {
        return false;
    }

    @Override
    public boolean isEmittedLightLevelNeeded() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }
    
    private class OurShaderState implements HDShaderState {
        private Color color[];
        private Color tmpcolor[];
        private Color c;
        protected MapIterator mapiter;
        protected HDMap map;
        private HDLighting lighting;
        private int scale;
        private int heightshift;    /* Divide to keep in 0-127 range of colors */
        
        private OurShaderState(MapIterator mapiter, HDMap map, MapChunkCache cache) {
            this.mapiter = mapiter;
            this.map = map;
            this.lighting = map.getLighting();
            if(lighting.isNightAndDayEnabled()) {
                color = new Color[] { new Color(), new Color() };
                tmpcolor = new Color[] { new Color(), new Color() };
            }
            else {
                color = new Color[] { new Color() };
                tmpcolor = new Color[] { new Color() };
            }
            scale = (int)map.getPerspective().getScale();
            c = new Color();
            /* Compute divider for Y - to map to existing color range */
            int wh = mapiter.getWorldHeight();
            heightshift = 0;
            while(wh > 256) {
                heightshift++;
                wh >>= 1;
            }
        }
        /**
         * Get our shader
         */
        public HDShader getShader() {
            return TopoHDShader.this;
        }

        /**
         * Get our map
         */
        public HDMap getMap() {
            return map;
        }
        
        /**
         * Get our lighting
         */
        public HDLighting getLighting() {
            return lighting;
        }
        
        /**
         * Reset renderer state for new ray
         */
        public void reset(HDPerspectiveState ps) {
            for(int i = 0; i < color.length; i++)
                color[i].setTransparent();
        }
        
        private final boolean isHidden(int id) {
            if((id == 0) || ((hiddenids[id >> 5] & (1 << (id & 0x1F))) != 0)) {
                return true;
            }
            return false;
        }
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(HDPerspectiveState ps) {
            int blocktype = ps.getBlockTypeID();
            
            if(isHidden(blocktype)) {
                return false;
            }
            /* See if we're close to an edge */
            int[] xyz = ps.getSubblockCoord();
            /* See which face we're on (only do lines on top face) */
            switch(ps.getLastBlockStep()) {
            case Y_MINUS:
            case Y_PLUS:
                if((linecolor != null) &&
                        (((xyz[0] == 0) && (isHidden(mapiter.getBlockTypeIDAt(BlockStep.X_MINUS)))) ||
                        ((xyz[0] == (scale-1)) && (isHidden(mapiter.getBlockTypeIDAt(BlockStep.X_PLUS)))) ||
                        ((xyz[2] == 0) && (isHidden(mapiter.getBlockTypeIDAt(BlockStep.Z_MINUS)))) ||
                        ((xyz[2] == (scale-1)) && (isHidden(mapiter.getBlockTypeIDAt(BlockStep.Z_PLUS)))))) {
                    c.setColor(linecolor);
                }
                else if((watercolor != null) && ((blocktype == 8) || (blocktype == 9))) {
                    c.setColor(watercolor);
                }
                else {
                    c.setColor(fillcolor[mapiter.getY() >> heightshift]);
                }
                break;
            default:
                if((linecolor != null) && (xyz[1] == (scale-1)))
                    c.setColor(linecolor);
                else if((watercolor != null) && ((blocktype == 8) || (blocktype == 9))) {
                    c.setColor(watercolor);
                }
                else
                    c.setColor(fillcolor[mapiter.getY() >> heightshift]);
                break;
            }
            /* Handle light level, if needed */
            lighting.applyLighting(ps, this, c, tmpcolor);

            for(int i = 0; i < color.length; i++)
                color[i] = tmpcolor[i];

            return true;
        }        
        /**
         * Ray ended - used to report that ray has exited map (called if renderer has not reported complete)
         */
        public void rayFinished(HDPerspectiveState ps) {
        }
        /**
         * Get result color - get resulting color for ray
         * @param c - object to store color value in
         * @param index - index of color to request (renderer specific - 0=default, 1=day for night/day renderer
         */
        public void getRayColor(Color c, int index) {
            c.setColor(color[index]);
        }
        /**
         * Clean up state object - called after last ray completed
         */
        public void cleanup() {
        }
        @Override
        public DynLongHashMap getCTMTextureCache() {
            return null;
        }
    }

    /**
     *  Get renderer state object for use rendering a tile
     * @param map - map being rendered
     * @param cache - chunk cache containing data for tile to be rendered
     * @param mapiter - iterator used when traversing rays in tile
     * @return state object to use for all rays in tile
     */
    public HDShaderState getStateInstance(HDMap map, MapChunkCache cache, MapIterator mapiter) {
        return new OurShaderState(mapiter, map, cache);
    }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "shader", name);
    }
}

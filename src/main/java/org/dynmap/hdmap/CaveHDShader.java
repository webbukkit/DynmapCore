package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;

import java.util.List;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.utils.DynLongHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.json.simple.JSONObject;

public class CaveHDShader implements HDShader {
    private String name;
    private boolean iflit;
    private int[] hiddenids;

    private void setHidden(int id) {
        if((id >= 0) && (id < 65535)) {
            hiddenids[id >> 5] |= (1 << (id & 0x1F));
        }
    }
    private boolean isHidden(int id) {
        return (hiddenids[id >> 5] & (1 << (id & 0x1F))) != 0;
    }
    public CaveHDShader(DynmapCore core, ConfigurationNode configuration) {
        name = (String) configuration.get("name");
        iflit = configuration.getBoolean("onlyiflit", false);
        
        hiddenids = new int[2048];
        setHidden(0); /* Air is hidden always */
        List<Object> hidden = configuration.getList("hiddenids");
        if(hidden != null) {
            for(Object o : hidden) {
                if(o instanceof Integer) {
                    int v = ((Integer)o);
                    setHidden(v);
                }
            }
        }
        else {
            setHidden(17);
            setHidden(18);
            setHidden(20);
            setHidden(64);
            setHidden(71);
            setHidden(78);
            setHidden(79);
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
        return iflit;
    }

    @Override
    public String getName() {
        return name;
    }
    
    private class OurShaderState implements HDShaderState {
        private Color color;
        protected MapIterator mapiter;
        protected HDMap map;
        private boolean air;
        private int yshift;
        
        private OurShaderState(MapIterator mapiter, HDMap map) {
            this.mapiter = mapiter;
            this.map = map;
            this.color = new Color();
            int wheight = mapiter.getWorldHeight();
            yshift = 0;
            while(wheight > 128) {
                wheight >>= 1;
                yshift++;
            }
        }
        /**
         * Get our shader
         */
        public HDShader getShader() {
            return CaveHDShader.this;
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
            return map.getLighting();
        }
        
        /**
         * Reset renderer state for new ray
         */
        public void reset(HDPerspectiveState ps) {
            color.setTransparent();
            air = true;
        }
        
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(HDPerspectiveState ps) {
            int blocktype = ps.getBlockTypeID();
            if (isHidden(blocktype)) {
                blocktype = 0;
            }
            else {
                air = false;
                return false;
            }
            if ((blocktype == 0) && !air) {
            	if(iflit && (ps.getMapIterator().getBlockEmittedLight() == 0)) {
            		return false;
            	}
                int cr, cg, cb;
                int mult = 256;

                int ys = mapiter.getY() >> yshift;
                if (ys < 64) {
                    cr = 0;
                    cg = 64 + ys * 3;
                    cb = 255 - ys * 4;
                } else {
                    cr = (ys - 64) * 4;
                    cg = 255;
                    cb = 0;
                }
                /* Figure out which color to use */
                switch(ps.getLastBlockStep()) {
                    case X_PLUS:
                    case X_MINUS:
                        mult = 224;
                        break;
                    case Z_PLUS:
                    case Z_MINUS:
                        mult = 256;
                        break;
                    default:
                        mult = 160;
                        break;
                }
                cr = cr * mult / 256;
                cg = cg * mult / 256;
                cb = cb * mult / 256;

                color.setRGBA(cr, cg, cb, 255);
                return true;
            }
            return false;
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
            c.setColor(color);
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
        return new OurShaderState(mapiter, map);
    }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "shader", name);
    }
}

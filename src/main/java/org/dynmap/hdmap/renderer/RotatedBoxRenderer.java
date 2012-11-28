package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.MapIterator;

public class RotatedBoxRenderer extends CustomRenderer {
    private int blkid;
    // Models for rotation values
    private RenderPatch[][] models;
    private Integer[] rotValues;

    // Indexing attribute
    private String idx_attrib = null;
    
    private String[] tileEntityAttribs = null;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        this.blkid = blkid; /* Remember our block ID */
        /* See if index attribute defined */
        idx_attrib = custparm.get("textureIndex");
        ArrayList<Integer> map = new ArrayList<Integer>();
        for(int id = 0; ; id++) {
            String v = custparm.get("index" + id);
            if(v == null) break;
            map.add(Integer.valueOf(v));
        }
        rotValues = map.toArray(new Integer[map.size()]);
        models = new RenderPatch[rotValues.length][];
        /* Build unrotated base model */
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        addBox(rpf, list, 0, 1, 0, 1, 0, 1, null);
        
        for(int id = 0; id < rotValues.length; id++) {
            String v = custparm.get("map" + id);
            if(v == null) v = Integer.toString(90*id);
            String sv[] = v.split("/");
            int x = 0, y = 0, z = 0;
            if(sv.length == 1) {    /* Only 1 = Y axis */
                try {
                    y = Integer.parseInt(v);
                } catch (NumberFormatException nfx) {
                    Log.severe("Invalid map format:" + v);
                    return false;
                }
            }
            else if(sv.length == 3) { 
                try {
                    x = Integer.parseInt(sv[0]);
                    y = Integer.parseInt(sv[1]);
                    z = Integer.parseInt(sv[2]);
                } catch (NumberFormatException nfx) {
                    Log.severe("Invalid map format:" + v);
                    return false;
                }
            }
            else {
                Log.severe("Invalid map format:" + v);
                return false;
            }
            models[id] = new RenderPatch[6];
            for(int i = 0; i < 6; i++) {
                models[id][i] = rpf.getRotatedPatch(list.get(i), x, y, z, i);
            }
        }
        if(idx_attrib != null) {
            tileEntityAttribs = new String[1];
            tileEntityAttribs[0] = idx_attrib;
        }
        else {
            tileEntityAttribs = null;
        }
        
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 6;
    }
    
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return tileEntityAttribs;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int textureIdx = 0;
        
        /* See if we have texture index */
        if(idx_attrib != null) {
            Object idxv = ctx.getBlockTileEntityField(idx_attrib);
            if(idxv instanceof Number) {
                textureIdx = ((Number)idxv).intValue();
            }
        }
        else {  /* Else, use data if no index attribute */
            textureIdx = ctx.getBlockData();
        }
        //Log.info("index=" + textureIdx);
        for(int i = 0; i < rotValues.length; i++) {
            if(rotValues[i] == textureIdx) {
                //Log.info("match: " + i);
                return models[i];
            }
        }
        Log.info("Unmatched rotation index: " + textureIdx + " for " + ctx.getBlockTypeID() + ":" + ctx.getBlockData());
        return models[0];
    }
}

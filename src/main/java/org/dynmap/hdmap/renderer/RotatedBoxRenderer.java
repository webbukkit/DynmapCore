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

    private static final String defRotMap[] = { "012345", "014532", "013254", "015423", "102354", 
        "104523","103245", "105432", "450123", "320145","540132","230154","451032", "321054",
        "541023", "231045", "453201", "325401", "542301", "234501", "452310", "324510", "543210", "235410" };
 
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
        if(map.size() == 0) {   /* None? use default mapping */
            for(int i = 0; i < defRotMap.length; i++) {
                map.add(i);
            }
        }
        rotValues = map.toArray(new Integer[map.size()]);
        models = new RenderPatch[rotValues.length][];
        int[] sides = new int[6];
        for(int id = 0; id < rotValues.length; id++) {
            String v = custparm.get("map" + id);
            if(v == null) {
                if(id < defRotMap.length)
                    v = defRotMap[id];
                else
                    v = defRotMap[0];
            }
            int vmap = 0;
            try {
                vmap = Integer.parseInt(v);
            } catch (NumberFormatException nfx) {
                Log.severe("Invalid map" + id + " : " + v);
                return false;
            }
            for (int i = 5; i >= 0; i--) {
                sides[i] = vmap % 10;
                vmap = vmap / 10;
            }
            ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
            addBox(rpf, list, 0, 1, 0, 1, 0, 1, sides);
            models[id] = list.toArray(new RenderPatch[list.size()]);
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
        Log.info("index=" + textureIdx);
        for(int i = 0; i < rotValues.length; i++) {
            if(rotValues[i] == textureIdx) {
                Log.info("match: " + i + ":" + defRotMap[i]);
                return models[i];
            }
        }
        return models[0];
    }
}

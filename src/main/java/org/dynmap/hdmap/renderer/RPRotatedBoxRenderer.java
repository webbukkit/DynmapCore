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

public class RPRotatedBoxRenderer extends CustomRenderer {
    private static final int[][] rotTable = { { 0, 1, 2, 3, 4, 5, 0, 112347, 0 }, { 0, 1, 4, 5, 3, 2, 45, 112320, 27 }, { 0, 1, 3, 2, 5, 4, 27, 112347, 0 }, { 0, 1, 5, 4, 2, 3, 54, 112320, 27 }, { 1, 0, 2, 3, 5, 4, 112347, 112347, 0 }, { 1, 0, 4, 5, 2, 3, 112374, 112320, 27 }, { 1, 0, 3, 2, 4, 5, 112320, 112347, 0 }, { 1, 0, 5, 4, 3, 2, 112365, 112320, 27 }, { 4, 5, 0, 1, 2, 3, 217134, 1728, 110619 }, { 3, 2, 0, 1, 4, 5, 220014, 0, 112347 }, { 5, 4, 0, 1, 3, 2, 218862, 1728, 110619 }, { 2, 3, 0, 1, 5, 4, 220590, 0, 112347 }, { 4, 5, 1, 0, 3, 2, 188469, 1728, 110619 }, { 3, 2, 1, 0, 5, 4, 191349, 0, 112347 }, { 5, 4, 1, 0, 2, 3, 190197, 1728, 110619 }, { 2, 3, 1, 0, 4, 5, 191925, 0, 112347 }, { 4, 5, 3, 2, 0, 1, 2944, 110619, 1728 }, { 3, 2, 5, 4, 0, 1, 187264, 27, 112320 }, { 5, 4, 2, 3, 0, 1, 113536, 110619, 1728 }, { 2, 3, 4, 5, 0, 1, 224128, 27, 112320 }, { 4, 5, 2, 3, 1, 0, 3419, 110619, 1728 }, { 3, 2, 4, 5, 1, 0, 187739, 27, 112320 }, { 5, 4, 3, 2, 1, 0, 114011, 110619, 1728 }, { 2, 3, 5, 4, 1, 0, 224603, 27, 112320 } };

    private int blkid;
    // Models for rotation values
    private RenderPatch[][] models;

    // Indexing attribute
    private String idx_attrib = null;
    
    private String[] tileEntityAttribs = { "rot" };

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        this.blkid = blkid; /* Remember our block ID */

        models = new RenderPatch[rotTable.length][];
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
        Object rot = ctx.getBlockTileEntityField("rot");
        int idx = 0;
        if(rot instanceof Number) {
            idx = ((Number)rot).intValue();
        }
        if((idx < 0) || (idx >= models.length)) {
            idx = 0;
        }
        if(models[idx] == null) {
            ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
            int[] txt = new int[6];
            txt[0] = rotTable[idx][0];
            txt[1] = rotTable[idx][1];
            txt[2] = rotTable[idx][4];
            txt[3] = rotTable[idx][5];
            txt[4] = rotTable[idx][2];
            txt[5] = rotTable[idx][3];
            this.addBox(ctx.getPatchFactory(), list, 0, 1, 0, 1, 0, 1, txt);
            models[idx] = list.toArray(new RenderPatch[6]);
        }
        return models[idx];
    }
}

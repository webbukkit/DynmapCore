package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.MapIterator;

public class TFCLooseRockRenderer extends CustomRenderer {
    private int blkid;
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        this.blkid = blkid; /* Remember our block ID */

        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return 1;
    }
    
    private int[] patches = { 0, 0, 0, 0, 0, 0 };
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        /* Generate seed from coordinates */
        int seed = (ctx.getX() * ctx.getZ()) + ctx.getY();
        Random R = new Random(seed);
        /* Generate rock dimension */
        float xOffset = (R.nextInt(5) - 2) * 0.05F;
        float zOffset = (R.nextInt(5) - 2) * 0.05F;
        float xOffset2 = (R.nextInt(5) - 2) * 0.05F;
        float yOffset2 = (R.nextInt(5) - 2) * 0.05F;
        float zOffset2 = (R.nextInt(5) - 2) * 0.05F;
        
        this.addBox(ctx.getPatchFactory(), list, 0.35 + xOffset,  0.65 + xOffset2, 0.0, 0.15 + yOffset2, 0.35 + zOffset, 0.65 + zOffset2, patches);
        
        return list.toArray(new RenderPatch[6]);
    }
}

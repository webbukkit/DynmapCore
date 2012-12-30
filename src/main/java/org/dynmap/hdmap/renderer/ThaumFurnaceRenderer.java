package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.Map;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class ThaumFurnaceRenderer extends CustomRenderer {
    private int blkid;
    
    private static final int TXTIDX_FIRST_GRID = 0; /* First 9 - 3 x 3 arrangement */
    private static final int TXTIDX_SECOND_GRID = 9; /* Second 9 - 3 x 3 arrangement */
    private static final int TXTIDX_SPECIAL_23 = 18;    /* 23 */
    private static final int TXTIDX_SPECIAL_24 = 19;    /* 24 */
    private static final int TXTIDX_SPECIAL_39 = 20;    /* 39 */
    private static final int TXTIDX_SPECIAL_40 = 21;    /* 40 */
    private static final int TXTIDX_SPECIAL_6 = 22;     /* 6 */
    private static final int TXTIDX_SPECIAL_7 = 23;     /* 7 */
    private static final int TXTIDX_LAVA = 24;     /* Lava */
    
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        this.blkid = blkid; /* Remember our block ID */

        return true;
    }

    private boolean blockTouching(MapDataContext ctx, int id, int meta, int side) {
        if (    ((side > 3) && (ctx.getBlockTypeIDAt(0, 0, 1) == id) && (ctx.getBlockDataAt(0, 0, 1) == meta)) || 
                ((side > 3) && (ctx.getBlockTypeIDAt(0, 0, -1) == id) && (ctx.getBlockDataAt(0, 0, -1) == meta)) || 
                ((side > 1) && (side < 4) && (ctx.getBlockTypeIDAt(1, 0, 0) == id) && (ctx.getBlockDataAt(1, 0, 0) == meta)) || 
                ((side > 1) && (side < 4) && (ctx.getBlockTypeIDAt(-1, 0, 0) == id) && (ctx.getBlockDataAt(-1, 0, 0) == meta)) || 
                ((side > 1) && (ctx.getBlockTypeIDAt(0, 1, 0) == id) && (ctx.getBlockDataAt(0, 1, 0) == meta)) || 
                ((side > 1) && (ctx.getBlockTypeIDAt(0, -1, 0) == id) && (ctx.getBlockDataAt(0, -1, 0) == meta))) {
            return true;
        }
        if (    ((side > 3) && (ctx.getBlockTypeIDAt(0, 1, 1) == id) && (ctx.getBlockDataAt(0, 1, 1) == meta)) ||
                ((side > 3) && (ctx.getBlockTypeIDAt(0, 1, -1) == id) && (ctx.getBlockDataAt(0, 1, -1) == meta)) ||
                ((side > 1) && (side < 4) && (ctx.getBlockTypeIDAt(1, 1, 0) == id) && (ctx.getBlockDataAt(1, 1, 0) == meta)) ||
                ((side > 1) && (side < 4) && (ctx.getBlockTypeIDAt(-1, 1, 0) == id) && (ctx.getBlockDataAt(-1, 1, 0) == meta))) {
            return true;
        }
        if (    ((side > 3) && (ctx.getBlockTypeIDAt(0, -1, 1) == id) && (ctx.getBlockDataAt(0, -1, 1) == meta)) ||
                ((side > 3) && (ctx.getBlockTypeIDAt(0, -1, -1) == id) && (ctx.getBlockDataAt(0, -1, -1) == meta)) ||
                ((side > 1) && (side < 4) && (ctx.getBlockTypeIDAt(1, -1, 0) == id) && (ctx.getBlockDataAt(1, -1, 0) == meta)) ||
                ((side > 1) && (side < 4) && (ctx.getBlockTypeIDAt(-1, -1, 0) == id) && (ctx.getBlockDataAt(-1, -1, 0) == meta))) {
            return true;
        }
        switch (side) {
            case 0:
                if ((ctx.getBlockTypeIDAt(0, -1, 0) != id) || (ctx.getBlockDataAt(0, -1, 0) != meta))
                    break;
                return true;
            case 1:
                if ((ctx.getBlockTypeIDAt(0, 1, 0) != id) || (ctx.getBlockDataAt(0, 1, 0) != meta))
                    break;
                return true;
        }
        return false;
    }

    /* Calculate index of texture for given side */
    private int calcTexture(MapDataContext ctx, int side) {
        int meta = ctx.getBlockData();
        int lvl = calcLevel(ctx);
        int add = TXTIDX_FIRST_GRID;
        if(blockTouching(ctx, blkid, 10, side)) {
            add = TXTIDX_SECOND_GRID;
        }
        switch (side) {
            case 0:
            case 1:
                if ((side == 1) && (lvl == 2)) {
                    switch (meta) {
                        case 2:
                            return TXTIDX_SPECIAL_23;
                        case 4:
                            return TXTIDX_SPECIAL_24;
                        case 6:
                            return TXTIDX_SPECIAL_40;
                        case 8:
                            return TXTIDX_SPECIAL_39;
                    }
                }
                if (add == TXTIDX_SECOND_GRID)
                    break;
                if (meta == 5)
                    return TXTIDX_SPECIAL_7;
                return (meta - 1) % 3 + (meta - 1) / 3 * 3;
            case 2:
                switch (meta) {
                    default:
                        if (lvl != 1)
                            return TXTIDX_SPECIAL_7;
                        return TXTIDX_SPECIAL_6;
                    case 1:
                        return 2 + (lvl*3) + add;
                    case 2:
                        return 1 + (lvl*3) + add;
                    case 3:
                        return 0 + (lvl*3) + add;
                }
            case 3:
                switch (meta) {
                    default:
                        if (lvl != 1)
                            return TXTIDX_SPECIAL_7;
                        return TXTIDX_SPECIAL_6;
                    case 7:
                        return 0 + (lvl*3) + add;
                    case 8:
                        return 1 + (lvl*3) + add;
                    case 9:
                        return 2 + (lvl*3) + add;
                }
            case 4:
                switch (meta) {
                    default:
                        if (lvl != 1)
                            return TXTIDX_SPECIAL_7;
                        return TXTIDX_SPECIAL_6;
                    case 1:
                        return 0 + (lvl*3) + add;
                    case 4:
                        return 1 + (lvl*3) + add;
                    case 7:
                        return 2 + (lvl*3) + add;
                }
            case 5:
                switch (meta) {
                    default:
                        if (lvl != 1)
                            return TXTIDX_SPECIAL_7;
                        return TXTIDX_SPECIAL_6;
                    case 3:
                        return 2 + (lvl*3) + add;
                    case 6:
                        return 1 + (lvl*3) + add;
                    case 9:
                        return 0 + (lvl*3) + add;
                }
        }
        return add == 0 ? TXTIDX_SPECIAL_7 : TXTIDX_SPECIAL_6;
    }
    private int calcLevel(MapDataContext ctx) {
        int meta = ctx.getBlockData();
        int metaA = ctx.getBlockDataAt(0,  1,  0);
        if ((metaA == 10) || (metaA == 0))
            metaA = meta;
        int metaB = ctx.getBlockDataAt(0, -1, 0);
        if ((metaB == 10) || (metaB == 0))
            metaB = meta;
        int blockA = ctx.getBlockTypeIDAt(0,  1,  0);
        int blockB = ctx.getBlockTypeIDAt(0, -1, 0);
        if ((meta == metaA) && (meta == metaB) && (blkid == blockA) && (blkid == blockB))
            return 1;
        if ((meta == metaA) && (blkid == blockA) && ((meta != metaB) || (blkid != blockB)))
            return 2;
        return 0;
    }

    @Override
    public int getMaximumTextureCount() {
        return 24;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int txtids[] = new int[6];
        if(ctx.getBlockData() == 0) {
            for(int i = 0; i < 6; i++) {
                txtids[i] = TXTIDX_LAVA;
            }
        }
        else {
            for(int i = 0; i < 6; i++) {
                txtids[i] = calcTexture(ctx, i);
            }
        }
        this.addBox(ctx.getPatchFactory(), list, 0, 1, 0, 1, 0, 1, txtids);
        return list.toArray(new RenderPatch[6]);
    }
}

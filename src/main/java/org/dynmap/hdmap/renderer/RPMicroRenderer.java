package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class RPMicroRenderer extends CustomRenderer {
    private int blkid;
    private static final String[] tileFields = { "cvs", "cvm" };
    /* Defined texture indexes
    * 0 = Cobblestone (cobblestone:0)
    * 1 = Stone (stone:0)
    * 2 = Wood planks (Oak) (planks:0)
    * 3 = Sandstone (sandStone:0)
    * 4 = Mossy Stone (cobblestoneMossy:0)
    * 5 = Brick (brick:0)
    * 6 = Obsidian (obsidian:0)
    * 7 = Glass (glass:0)
    * 8 = Dirt (dirt:0)
    * 9 = Clay (blockClay:0)
    * 10 = Bookshelf (bookShelf:0)
    * 11 = Netherrack (87:0)
    * 12 = Wood (Oak) (wood:0)
    * 13 = Wood (Spruce) (wood:1)
    * 14 = Wood (Birch) (wood:2)
    * 15 = Soul Sand (slowSand:0)
    * 16 = Polished Stone (stairSingle:0)
    * 17 = Iron (blockSteel:0)
    * 18 = Gold (blockGold:0)
    * 19 = Diamond (blockDiamond:0)
    * 20 = Lapis Lazuli (blockLapis:0)
    * 21 = Snow (blockSnow:0)
    * 22 = Pumpkin (pumpkin:0)
    * 23 = Stone Brick (stoneBrick:0)
    * 24 = Stone Brick (stoneBrick:1)
    * 25 = Stone Brick (stoneBrick:2)
    * 26 = Nether Brick (netherBrick:0)
    * 27 = Stone Brick (stoneBrick:3)
    * 28 = Wooden Planks (planks:1)
    * 29 = Wooden Planks (planks:2)
    * 30 = Wooden Planks (planks:3)
    * 31 = Sandstone (sandStone:1)
    * 32 = Wool (cloth:0)
    * 33 = Wool (cloth:1)
    * 34 = Wool (cloth:2)
    * 35 = Wool (cloth:3)
    * 36 = Wool (cloth:4)
    * 37 = Wool (cloth:5)
    * 38 = Wool (cloth:6)
    * 39 = Wool (cloth:7)
    * 40 = Wool (cloth:8)
    * 41 = Wool (cloth:9)
    * 42 = Wool (cloth:10)
    * 43 = Wool (cloth:11)
    * 44 = Wool (cloth:12)
    * 45 = Wool (cloth:13)
    * 46 = Wool (cloth:14)
    * 47 = Wool (cloth:15)
    * 48 = Marble (blockStone:0)
    * 49 = Basalt (blockStone:1)
    * 50 = Marble Brick (blockStone:2)
    * 51 = Basalt Cobblestone (blockStone:3)
    * 52 = Basalt Brick (blockStone:4)
    * 53 = Rubberwood (blockLogs:0)
    * 54 = Ruby Block (blockStorage:0)
    * 55 = Emerald Block (blockStorage:1)
    * 56 = Sapphire Block (blockStorage:2)
    * 57 = Sandstone (sandStone:2)
    * 58 = Wood (wood:3)
    * 59 = Sandstone bottom
    * 60 = Sandstone top
    * 61 = Log top/bottom
    * 62 = Stone step top
    * 63 = Pumpkin top/bottom
    * 64 = Rubberwood log top/bottom
    */
    private static final int NUM_TEXTURES = 65;

    /* Texture index = material index in RP */
    private static final int materialTextureMap[][] = {
        { 0 }, // 0 = Cobblestone (cobblestone:0)
        { 1 }, // 1 = Stone (stone:0)
        { 2 }, // 2 = Wood planks (Oak) (planks:0)
        { 59, 60, 3, 3, 3, 3 }, // 3 = Sandstone (sandStone:0)
        { 4 }, // 4 = Mossy Stone (cobblestoneMossy:0)
        { 5 }, // 5 = Brick (brick:0)
        { 6 }, // 6 = Obsidian (obsidian:0)
        { 7 }, // 7 = Glass (glass:0)
        { 8 }, // 8 = Dirt (dirt:0)
        { 9 }, // 9 = Clay (blockClay:0)
        { 2, 2, 10, 10, 10, 10 }, // 10 = Bookshelf (bookShelf:0)        
        { 11 }, // 11 = Netherrack (87:0)
        { 61, 61, 12, 12, 12, 12 }, // 12 = Wood (Oak) (wood:0)
        { 61, 61, 13, 13, 13, 13 }, // 13 = Wood (Spruce) (wood:1)
        { 61, 61, 14, 14, 14, 14 }, // 14 = Wood (Birch) (wood:2)
        { 15 }, // 15 = Soul Sand (slowSand:0)
        { 62, 62, 16, 16, 16, 16 }, // 16 = Polished Stone (stairSingle:0)
        { 17 }, // 17 = Iron (blockSteel:0)
        { 18 }, // 18 = Gold (blockGold:0)
        { 19 }, // 19 = Diamond (blockDiamond:0)
        { 20 }, // 20 = Lapis Lazuli (blockLapis:0)
        { 21 }, // 21 = Snow (blockSnow:0)
        { 63, 63, 22, 22, 22, 22 }, // 22 = Pumpkin (pumpkin:0)       
        { 23 }, // 23 = Stone Brick (stoneBrick:0)
        { 24 }, // 24 = Stone Brick (stoneBrick:1)
        { 25 }, // 25 = Stone Brick (stoneBrick:2)
        { 26 }, // 26 = Nether Brick (netherBrick:0)
        { 27 }, // 27 = Stone Brick (stoneBrick:3)
        { 28 }, // 28 = Wooden Planks (planks:1)
        { 29 }, // 29 = Wooden Planks (planks:2)
        { 30 }, // 30 = Wooden Planks (planks:3)
        { 60, 60, 31, 31, 31, 31 }, // 31 = Sandstone (sandStone:1)
        { 32 }, // 32 = Wool (cloth:0)
        { 33 }, // 33 = Wool (cloth:1)
        { 34 }, // 34 = Wool (cloth:2)
        { 35 }, // 35 = Wool (cloth:3)
        { 36 }, // 36 = Wool (cloth:4)
        { 37 }, // 37 = Wool (cloth:5)
        { 38 }, // 38 = Wool (cloth:6)
        { 39 }, // 39 = Wool (cloth:7)
        { 40 }, // 40 = Wool (cloth:8)
        { 41 }, // 41 = Wool (cloth:9)
        { 42 }, // 42 = Wool (cloth:10)
        { 43 }, // 43 = Wool (cloth:11)
        { 44 }, // 44 = Wool (cloth:12)
        { 45 }, // 45 = Wool (cloth:13)
        { 46 }, // 46 = Wool (cloth:14)
        { 47 }, // 47 = Wool (cloth:15)
        { 48 }, // 48 = Marble (blockStone:0)
        { 49 }, // 49 = Basalt (blockStone:1)
        { 50 }, // 50 = Marble Brick (blockStone:2)
        { 51 }, // 51 = Basalt Cobblestone (blockStone:3)
        { 52 }, // 52 = Basalt Brick (blockStone:4)
        { 64, 64, 53, 53, 53, 53 }, // 53 = Rubberwood (blockLogs:0)
        { 54 }, // 54 = Ruby Block (blockStorage:0)
        { 55 }, // 55 = Emerald Block (blockStorage:1)
        { 56 }, // 56 = Sapphire Block (blockStorage:2)
        { 0 }, // 57
        { 0 }, // 58
        { 0 }, // 59
        { 0 }, // 60
        { 0 }, // 61
        { 0 }, // 62
        { 0 }, // 63
        { 60, 60, 57, 57, 57, 57 }, // 64 = Sandstone (sandStone:2)
        { 61, 61, 58, 58, 58, 58 } // 65 = Wood (wood:3)
    };
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        this.blkid = blkid; /* Remember our block ID */
        /* Flesh out sides map */
        for(int i = 0; i < materialTextureMap.length; i++) {
            if(materialTextureMap[i].length < 6) {
                int[] sides = new int[6];
                Arrays.fill(sides,  materialTextureMap[i][0]);
                materialTextureMap[i] = sides;
            }
        }
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return NUM_TEXTURES;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int covermask = 0;
        byte[] covervals = new byte[2*29];
        Object v = ctx.getBlockTileEntityField("cvm");
        if(v instanceof Integer) {
            covermask = ((Integer)v).intValue();
        }
        v = ctx.getBlockTileEntityField("cvs");
        if(v instanceof byte[]) {
            covervals = (byte[])v;
        }
//        String s = String.format("[%d,%d,%d]:cvm=%08x,cvs=", ctx.getX(), ctx.getY(), ctx.getZ(), covermask);
//        for(int i = 0; i < covervals.length; i+=2) {
//            s += String.format("%02x%02x:", covervals[i], covervals[i+1]);
//        }
//        Log.info(s);
        /* Build patch list */
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        for(int i = 0, off = 0; i < 29; i++) {
            if ((covermask & (1 << i)) != 0) {
                addPatchesFor(ctx.getPatchFactory(), list, i, covervals[off], covervals[off+1]);
                off += 2;
            }
        }
        return list.toArray(new RenderPatch[list.size()]);
    }
    
    private static final double[] thick_0_5 = { 0.125, 0.25, 0.5, 0.125, 0.25, 0.5, 0.375, 0.625, 0.75, 0.875, 0.375, 0.625, 0.75, 0.875 };
    private static final double[] thick_6_25 = { 0.125, 0.25, 0.5, 0.375, 0.625, 0.75, 0.875 };
    private static final double[] thick_26_28 = { 0.125, 0.25, 0.375 };

    private boolean isHollow(int shape, int thickness) {
        if(shape < 6) {
            switch(thickness) {
                case 3:
                case 4:
                case 5:
                case 10:
                case 11:
                case 12:
                case 13:
                    return true;
            }
        }
        return false;
    }
    private double getThickness(int shape, int thickness) {
        double[] v;
        if(shape < 6) {
            v = thick_0_5;
            if(thickness < v.length)
                return v[thickness];
            else
                return 1.0;
        }
        else if((shape >= 26) && (shape < 29)) {
            v = thick_26_28;
            if(thickness < v.length)
                return v[thickness];
            else
                return v[0];
        }
        else {
            v = thick_6_25;
            if(thickness < v.length)
                return v[thickness];
            else
                return 1.0;
        }
    }
    
    private void addPatchesFor(RenderPatchFactory rpf, ArrayList<RenderPatch> list, int shape, int material, int thickness) {
        int[] sides;
        if((material < 0) || (material >= materialTextureMap.length)) {
            material = 0;
        }
        sides = materialTextureMap[material];   /* Get sides map for texture */
        double thick = getThickness(shape, thickness);
        
        /* If a hollow block, handle specially */
        if(isHollow(shape, thickness)) {
            switch(shape) {
                case 0: /* Bottom cover */
                    this.addBox(rpf, list, 0, 0.75, 0, thick, 0, 0.25, sides);
                    this.addBox(rpf, list, 0.75, 1, 0, thick, 0, 0.75, sides);
                    this.addBox(rpf, list, 0.25, 1, 0, thick, 0.75, 1, sides);
                    this.addBox(rpf, list, 0, 0.25, 0, thick, 0.25, 1, sides);
                    break;
                case 1: /* Top cover */
                    this.addBox(rpf, list, 0, 0.75, 1-thick, 1, 0, 0.25, sides);
                    this.addBox(rpf, list, 0.75, 1, 1-thick, 1, 0, 0.75, sides);
                    this.addBox(rpf, list, 0.25, 1, 1-thick, 1, 0.75, 1, sides);
                    this.addBox(rpf, list, 0, 0.25, 1-thick, 1, 0.25, 1, sides);
                    break;
                case 2: /* Z min cover */
                    this.addBox(rpf, list, 0, 0.75, 0, 0.25, 0, thick, sides);
                    this.addBox(rpf, list, 0.75, 1, 0, 0.75, 0, thick, sides);
                    this.addBox(rpf, list, 0.25, 1, 0.75, 1, 0, thick, sides);
                    this.addBox(rpf, list, 0, 0.25, 0.25, 1, 0, thick, sides);
                    break;
                case 3: /* Z max cover */
                    this.addBox(rpf, list, 0, 0.75, 0, 0.25, 1-thick, 1, sides);
                    this.addBox(rpf, list, 0.75, 1, 0, 0.75, 1-thick, 1, sides);
                    this.addBox(rpf, list, 0.25, 1, 0.75, 1, 1-thick, 1, sides);
                    this.addBox(rpf, list, 0, 0.25, 0.25, 1, 1-thick, 1, sides);
                    break;
                case 4: /* X min cover */
                    this.addBox(rpf, list, 0, thick, 0, 0.75, 0, 0.25, sides);
                    this.addBox(rpf, list, 0, thick, 0.75, 1, 0, 0.75, sides);
                    this.addBox(rpf, list, 0, thick, 0.25, 1, 0.75, 1, sides);
                    this.addBox(rpf, list, 0, thick, 0, 0.25, 0.25, 1, sides);
                    break;
                case 5: /* X max cover */
                    this.addBox(rpf, list, 1-thick, 1, 0, 0.75, 0, 0.25, sides);
                    this.addBox(rpf, list, 1-thick, 1, 0.75, 1, 0, 0.75, sides);
                    this.addBox(rpf, list, 1-thick, 1, 0.25, 1, 0.75, 1, sides);
                    this.addBox(rpf, list, 1-thick, 1, 0, 0.25, 0.25, 1, sides);
                    break;
            }
        }
        else {
            switch(shape) {
                case 0: /* Bottom cover */
                    this.addBox(rpf, list, 0, 1, 0, thick, 0, 1, sides);
                    break;
                case 1: /* Top cover */
                    this.addBox(rpf, list, 0, 1, 1-thick, 1.0, 0, 1, sides);
                    break;
                case 2: /* Z min cover */
                    this.addBox(rpf, list, 0, 1, 0, 1, 0, thick, sides);
                    break;
                case 3: /* Z max cover */
                    this.addBox(rpf, list, 0, 1, 0, 1, 1.0-thick, 1, sides);
                    break;
                case 4: /* X min cover */
                    this.addBox(rpf, list, 0, thick, 0, 1, 0, 1, sides);
                    break;
                case 5: /* X max cover */
                    this.addBox(rpf, list, 1.0-thick, 1, 0, 1, 0, 1, sides);
                    break;
                case 6: /* Xmin, Ymin, Zmin corner */
                    this.addBox(rpf, list, 0, thick, 0, thick, 0, thick, sides);
                    break;
                case 7: /* Xmin, Ymin, Zmax corner */
                    this.addBox(rpf, list, 0, thick, 0, thick, 1-thick, 1, sides);
                    break;
                case 8: /* Xmax, Ymin, Zmin corner */
                    this.addBox(rpf, list, 1-thick, 1, 0, thick, 0, thick, sides);
                    break;
                case 9: /* Xmax, Ymin, Zmax corner */
                    this.addBox(rpf, list, 1-thick, 1, 0, thick, 1-thick, 1, sides);
                    break;
                case 10: /* Xmin, Ymax, Zmin corner */
                    this.addBox(rpf, list, 0, thick, 1-thick, 1, 0, thick, sides);
                    break;
                case 11: /* Xmin, Ymax, Zmax corner */
                    this.addBox(rpf, list, 0, thick, 1-thick, 1, 1-thick, 1, sides);
                    break;
                case 12: /* Xmax, Ymax, Zmin corner */
                    this.addBox(rpf, list, 1-thick, 1, 1-thick, 1, 0, thick, sides);
                    break;
                case 13: /* Xmax, Ymax, Zmax corner */
                    this.addBox(rpf, list, 1-thick, 1, 1-thick, 1, 1-thick, 1, sides);
                    break;
                case 14: /* Zmin, Ymin Strip */
                    this.addBox(rpf, list, 0, 1, 0, thick, 0, thick, sides);
                    break;
                case 15: /* Zmax, Ymin Strip */
                    this.addBox(rpf, list, 0, 1, 0, thick, 1-thick, 1, sides);
                    break;
                case 16: /* Xmin, Ymin Strip */
                    this.addBox(rpf, list, 0, thick, 0, thick, 0, 1, sides);
                    break;
                case 17: /* Xmax, Ymin Strip */
                    this.addBox(rpf, list, 1-thick, 1, 0, thick, 0, 1, sides);
                    break;
                case 18: /* Xmin, Zmin Strip */
                    this.addBox(rpf, list, 0, thick, 0, 1, 0, thick, sides);
                    break;
                case 19: /* Xmin, Zmax Strip */
                    this.addBox(rpf, list, 0, thick, 0, 1, 1-thick, 1, sides);
                    break;
                case 20: /* Xmax, Zmin Strip */
                    this.addBox(rpf, list, 1-thick, 1, 0, 1, 0, thick, sides);
                    break;
                case 21: /* Xmax, Zmax Strip */
                    this.addBox(rpf, list, 1-thick, 1, 0, 1, 1-thick, 1, sides);
                    break;
                case 22: /* Zmin, Ymax Strip */
                    this.addBox(rpf, list, 0, 1, 1-thick, 1, 0, thick, sides);
                    break;
                case 23: /* Zmax, Ymax Strip */
                    this.addBox(rpf, list, 0, 1, 1-thick, 1, 1-thick, 1, sides);
                    break;
                case 24: /* Xmin, Ymax Strip */
                    this.addBox(rpf, list, 0, thick, 1-thick, 1, 0, 1, sides);
                    break;
                case 25: /* Xmax, Ymax Strip */
                    this.addBox(rpf, list, 1-thick, 1, 1-thick, 1, 0, 1, sides);
                    break;
                case 26: /* Pillar Y */
                    this.addBox(rpf, list, 0.5-thick, 0.5+thick, 0, 1, 0.5-thick, 0.5+thick, sides);
                    break;
                case 27: /* Pillar Z */
                    this.addBox(rpf, list, 0.5-thick, 0.5+thick, 0.5-thick, 0.5+thick, 0, 1, sides);
                    break;
                case 28: /* Pillar X */
                    this.addBox(rpf, list, 0, 1, 0.5-thick, 0.5+thick, 0.5-thick, 0.5+thick, sides);
                    break;
                default:
                    this.addBox(rpf, list, 0, 1, 0, 1, 0, 1, sides);
                    break;
            }
        }
    }
    
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return tileFields;
    }
}

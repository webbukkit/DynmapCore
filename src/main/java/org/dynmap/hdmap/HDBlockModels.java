package org.dynmap.hdmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dynmap.ConfigurationNode;
import org.dynmap.Log;
import org.dynmap.utils.MapIterator.BlockStep;
import org.dynmap.utils.Vector3D;

/**
 * Custom block models - used for non-cube blocks to represent the physical volume associated with the block
 * Used by perspectives to determine if rays have intersected a block that doesn't occupy its whole block
 */
public class HDBlockModels {
    private static int linkalg[] = new int[256];
    private static int linkmap[][] = new int[256][];
    
    private static HashMap<Integer, HDBlockModel> models_by_id_data = new HashMap<Integer, HDBlockModel>();
    
    
    private static void resizeTable(int idx) {
        int cnt = idx+1;
        int[] newlinkalg = new int[cnt];
        System.arraycopy(linkalg, 0, newlinkalg, 0, linkalg.length);
        linkalg = newlinkalg;
        int[][] newlinkmap = new int[cnt][];
        System.arraycopy(linkmap, 0, newlinkmap, 0, linkmap.length);
        linkmap = newlinkmap;
    }
    
    public static class HDScaledBlockModels {
        private short[][][] modelvectors;
        private HDPatchDefinition[][][] patches;

        public final short[] getScaledModel(int blocktype, int blockdata, int blockrenderdata) {
            try {
                if(modelvectors[blocktype] == null) {
                    return null;
                }
                return modelvectors[blocktype][(blockrenderdata>=0)?blockrenderdata:blockdata];
            } catch (ArrayIndexOutOfBoundsException aioobx) {
                short[][][] newmodels = new short[blocktype+1][][];
                System.arraycopy(modelvectors, 0, newmodels, 0, modelvectors.length);
                modelvectors = newmodels;
                return null;
            }
        }
        public HDPatchDefinition[] getPatchModel(int blocktype, int blockdata, int blockrenderdata) {
            try {
                if(patches[blocktype] == null) {
                    return null;
                }
                return patches[blocktype][(blockrenderdata>=0)?blockrenderdata:blockdata];
            } catch (ArrayIndexOutOfBoundsException aioobx) {
                HDPatchDefinition[][][] newpatches = new HDPatchDefinition[blocktype+1][][];
                System.arraycopy(patches, 0, newpatches, 0, patches.length);
                patches = newpatches;
                return null;
            }
        }
    }
    
    public enum SideVisibility { TOP, BOTTOM, BOTH };
    
    /* Define patch in surface-based models - origin (xyz), u-vector (xyz) v-vector (xyz), u limits and v limits */
    public static class HDPatchDefinition {
        public double x0, y0, z0;   /* Origin of patch (lower left corner of texture) */
        public double xu, yu, zu;   /* Coordinates of end of U vector (relative to origin) - corresponds to u=1.0 (lower right corner) */
        public double xv, yv, zv;   /* Coordinates of end of V vector (relative to origin) - corresponds to v=1.0 (upper left corner) */
        public double umin, umax;   /* Limits of patch - minimum and maximum u value */
        public double vmin, vmax;   /* Limits of patch - minimum and maximum v value */
        public double uplusvmax;    /* Limits of patch - max of u+v (triangle) */
        public Vector3D u, v;       /* U and V vector, relative to origin */
        public static final int MAX_PATCHES = 32;   /* Max patches per model */
        public BlockStep step;      /* Best approximation of orientation of surface, from top (positive determinent) */
        public SideVisibility sidevis;  /* Which side is visible */
        /* Offset vector of middle of block */
        private static final Vector3D offsetCenter = new Vector3D(0.5,0.5,0.5);
        
        public HDPatchDefinition() {
            x0 = y0 = z0 = 0.0;
            xu = zu = 0.0; yu = 1.0;
            yv = zv = 0.0; xv = 1.0;
            umin = vmin = 0.0;
            umax = vmax = 1.0;
            uplusvmax = Double.MAX_VALUE;
            u = new Vector3D();
            v = new Vector3D();
            sidevis = SideVisibility.BOTH;
        }
        /**
         * Construct patch, based on rotation of existing patch clockwise by N
         * 90 degree steps
         * @param orig
         * @param rotate_cnt
         */
        public HDPatchDefinition(HDPatchDefinition orig, int rotatex, int rotatey, int rotatez) {
            Vector3D vec = new Vector3D(orig.x0, orig.y0, orig.z0);
            rotate(vec, rotatex, rotatey, rotatez); /* Rotate origin */
            x0 = vec.x; y0 = vec.y; z0 = vec.z;
            /* Rotate U */
            vec.x = orig.xu; vec.y = orig.yu; vec.z = orig.zu;
            rotate(vec, rotatex, rotatey, rotatez); /* Rotate origin */
            xu = vec.x; yu = vec.y; zu = vec.z;
            /* Rotate V */
            vec.x = orig.xv; vec.y = orig.yv; vec.z = orig.zv;
            rotate(vec, rotatex, rotatey, rotatez); /* Rotate origin */
            xv = vec.x; yv = vec.y; zv = vec.z;
            umin = orig.umin; vmin = orig.vmin;
            umax = orig.umax; vmax = orig.vmax;
            uplusvmax = orig.uplusvmax;
            sidevis = orig.sidevis;
            u = new Vector3D();
            v = new Vector3D();
            update();
        }
        
        private void rotate(Vector3D vec, int xcnt, int ycnt, int zcnt) {
            vec.subtract(offsetCenter); /* Shoft to center of block */
            /* Do X rotation */
            double rot = Math.toRadians(xcnt);
            double nval = vec.z * Math.sin(rot) + vec.y * Math.cos(rot);
            vec.z = vec.z * Math.cos(rot) - vec.y * Math.sin(rot);
            vec.y = nval;
            /* Do Y rotation */
            rot = Math.toRadians(ycnt);
            nval = vec.x * Math.cos(rot) - vec.z * Math.sin(rot);
            vec.z = vec.x * Math.sin(rot) + vec.z * Math.cos(rot);
            vec.x = nval;
            /* Do Z rotation */
            rot = Math.toRadians(zcnt);
            nval = vec.y * Math.sin(rot) + vec.x * Math.cos(rot);
            vec.y = vec.y * Math.cos(rot) - vec.x * Math.sin(rot);
            vec.x = nval;
            vec.add(offsetCenter); /* Shoft back to corner */
        }
        
        public void update() {
            u.x = xu - x0; u.y = yu - y0; u.z = zu - z0;
            v.x = xv - x0; v.y = yv - y0; v.z = zv - z0;
            /* Now compute normal of surface - U cross V */
            Vector3D d = new Vector3D(u);
            d.crossProduct(v);
            /* Now, find the largest component of the normal (dominant direction) */
            if(Math.abs(d.x) > (Math.abs(d.y)*0.9)) { /* If X > 0.9Y */
                if(Math.abs(d.x) > Math.abs(d.z)) { /* If X > Z */
                    if(d.x > 0) {
                        step = BlockStep.X_PLUS;
                    }
                    else {
                        step = BlockStep.X_MINUS;
                    }
                }
                else {  /* Else Z >= X */
                    if(d.z > 0) {
                        step = BlockStep.Z_PLUS;
                    }
                    else {
                        step = BlockStep.Z_MINUS;
                    }
                }
            }
            else {  /* Else Y >= X */
                if((Math.abs(d.y)*0.9) > Math.abs(d.z)) { /* If 0.9Y > Z */
                    if(d.y > 0) {
                        step = BlockStep.Y_PLUS;
                    }
                    else {
                        step = BlockStep.Y_MINUS;
                    }
                }
                else {  /* Else Z >= Y */
                    if(d.z > 0) {
                        step = BlockStep.Z_PLUS;
                    }
                    else {
                        step = BlockStep.Z_MINUS;
                    }
                }
            }
        }
        public boolean validate() {
            boolean good = true;
            if((x0 < -1.0) || (x0 > 2.0)) {
                Log.severe("Invalid x0=" + x0);
                good = false;
            }
            if((y0 < -1.0) || (y0 > 2.0)) {
                Log.severe("Invalid y0=" + y0);
                good = false;
            }
            if((z0 < -1.0) || (z0 > 2.0)) {
                Log.severe("Invalid z0=" + z0);
                good = false;
            }
            if((xu < -1.0) || (xu > 2.0)) {
                Log.severe("Invalid xu=" + xu);
                good = false;
            }
            if((yu < -1.0) || (yu > 2.0)) {
                Log.severe("Invalid yu=" + yu);
                good = false;
            }
            if((zu < -1.0) || (zu > 2.0)) {
                Log.severe("Invalid zu=" + zu);
                good = false;
            }
            if((xv < -1.0) || (xv > 2.0)) {
                Log.severe("Invalid xv=" + xv);
                good = false;
            }
            if((yv < -1.0) || (yv > 2.0)) {
                Log.severe("Invalid yv=" + yv);
                good = false;
            }
            if((zv < -1.0) || (zv > 2.0)) {
                Log.severe("Invalid zv=" + zv);
                good = false;
            }
            if((umin < 0.0) || (umin >= umax)) {
                Log.severe("Invalid umin=" + umin);
                good = false;
            }
            if((vmin < 0.0) || (vmin >= vmax)) {
                Log.severe("Invalid vmin=" + vmin);
                good = false;
            }
            if(umax > 1.0) {
                Log.severe("Invalid umax=" + umax);
                good = false;
            }
            if(vmax > 1.0) {
                Log.severe("Invalid vmax=" + vmax);
                good = false;
            }
            
            return good;
        }
    }
    
    private static HashMap<Integer, HDScaledBlockModels> scaled_models_by_scale = new HashMap<Integer, HDScaledBlockModels>();
    
    public static abstract class HDBlockModel {
        private int blockid;
        private int databits;
        /**
         * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
         * @param blockid - block ID
         * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
         */
        protected HDBlockModel(int blockid, int databits) {
            this.blockid = blockid;
            this.databits = databits;
            for(int i = 0; i < 16; i++) {
                if((databits & (1<<i)) != 0) {
                    models_by_id_data.put((blockid<<4)+i, this);
                }
            }
        }
    }
    
    public static class HDBlockVolumetricModel extends HDBlockModel {
        /* Volumetric model specific attributes */
        private long blockflags[];
        private int nativeres;
        private HashMap<Integer, short[]> scaledblocks;
        /**
         * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
         * (for volumetric models)
         * @param blockid - block ID
         * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
         * @param nativeres - native subblocks per edge of cube (up to 64)
         * @param blockflags - array of native^2 long integers representing volume of block (bit X of element (nativeres*Y+Z) is set if that subblock is filled)
         *    if array is short, other elements area are assumed to be zero (fills from bottom of block up)
         */
        public HDBlockVolumetricModel(int blockid, int databits, int nativeres, long[] blockflags) {
            super(blockid, databits);
            
            this.nativeres = nativeres;
            this.blockflags = new long[nativeres * nativeres];
            System.arraycopy(blockflags, 0, this.blockflags, 0, blockflags.length);
        }
        /**
         * Test if given native block is filled (for volumetric model)
         */
        public final boolean isSubblockSet(int x, int y, int z) {
            return ((blockflags[nativeres*y+z] & (1 << x)) != 0);
        }
        /**
         * Set subblock value (for volumetric model)
         */
        public final void setSubblock(int x, int y, int z, boolean isset) {
            if(isset)
                blockflags[nativeres*y+z] |= (1 << x);
            else
                blockflags[nativeres*y+z] &= ~(1 << x);            
        }
        /**
         * Get scaled map of block: will return array of alpha levels, corresponding to how much of the
         * scaled subblocks are occupied by the original blocks (indexed by Y*res*res + Z*res + X)
         * @param res - requested scale (res subblocks per edge of block)
         * @return array of alpha values (0-255), corresponding to resXresXres subcubes of block
         */
        public short[] getScaledMap(int res) {
            if(scaledblocks == null) { scaledblocks = new HashMap<Integer, short[]>(); }
            short[] map = scaledblocks.get(Integer.valueOf(res));
            if(map == null) {
                map = new short[res*res*res];
                if(res == nativeres) {
                    for(int i = 0; i < blockflags.length; i++) {
                        for(int j = 0; j < nativeres; j++) {
                            if((blockflags[i] & (1 << j)) != 0)
                                map[res*i+j] = 255;
                        }
                    }
                }
                /* If scaling from smaller sub-blocks to larger, each subblock contributes to 1-2 blocks
                 * on each axis:  need to calculate crossovers for each, and iterate through smaller
                 * blocks to accumulate contributions
                 */
                else if(res > nativeres) {
                    int weights[] = new int[res];
                    int offsets[] = new int[res];
                    /* LCM of resolutions is used as length of line (res * nativeres)
                     * Each native block is (res) long, each scaled block is (nativeres) long
                     * Each scaled block overlaps 1 or 2 native blocks: starting with native block 'offsets[]' with
                     * 'weights[]' of its (res) width in the first, and the rest in the second
                     */
                    for(int v = 0, idx = 0; v < res*nativeres; v += nativeres, idx++) {
                        offsets[idx] = (v/res); /* Get index of the first native block we draw from */
                        if((v+nativeres-1)/res == offsets[idx]) {   /* If scaled block ends in same native block */
                            weights[idx] = nativeres;
                        }
                        else {  /* Else, see how much is in first one */
                            weights[idx] = (offsets[idx] + res) - v;
                            weights[idx] = (offsets[idx]*res + res) - v;
                        }
                    }
                    /* Now, use weights and indices to fill in scaled map */
                    for(int y = 0, off = 0; y < res; y++) {
                        int ind_y = offsets[y];
                        int wgt_y = weights[y];
                        for(int z = 0; z < res; z++) {
                            int ind_z = offsets[z];
                            int wgt_z = weights[z];
                            for(int x = 0; x < res; x++, off++) {
                                int ind_x = offsets[x];
                                int wgt_x = weights[x];
                                int raw_w = 0;
                                for(int xx = 0; xx < 2; xx++) {
                                    int wx = (xx==0)?wgt_x:(nativeres-wgt_x);
                                    if(wx == 0) continue;
                                    for(int yy = 0; yy < 2; yy++) {
                                        int wy = (yy==0)?wgt_y:(nativeres-wgt_y);
                                        if(wy == 0) continue;
                                        for(int zz = 0; zz < 2; zz++) {
                                            int wz = (zz==0)?wgt_z:(nativeres-wgt_z);
                                            if(wz == 0) continue;
                                            if(isSubblockSet(ind_x+xx, ind_y+yy, ind_z+zz)) {
                                                raw_w += wx*wy*wz;
                                            }
                                        }
                                    }
                                }
                                map[off] = (short)((255*raw_w) / (nativeres*nativeres*nativeres));
                                if(map[off] > 255) map[off] = 255;
                                if(map[off] < 0) map[off] = 0;
                            }
                        }
                    }
                }
                else {  /* nativeres > res */
                    int weights[] = new int[nativeres];
                    int offsets[] = new int[nativeres];
                    /* LCM of resolutions is used as length of line (res * nativeres)
                     * Each native block is (res) long, each scaled block is (nativeres) long
                     * Each native block overlaps 1 or 2 scaled blocks: starting with scaled block 'offsets[]' with
                     * 'weights[]' of its (res) width in the first, and the rest in the second
                     */
                    for(int v = 0, idx = 0; v < res*nativeres; v += res, idx++) {
                        offsets[idx] = (v/nativeres); /* Get index of the first scaled block we draw to */
                        if((v+res-1)/nativeres == offsets[idx]) {   /* If native block ends in same scaled block */
                            weights[idx] = res;
                        }
                        else {  /* Else, see how much is in first one */
                            weights[idx] = (offsets[idx]*nativeres + nativeres) - v;
                        }
                    }
                    /* Now, use weights and indices to fill in scaled map */
                    long accum[] = new long[map.length];
                    for(int y = 0; y < nativeres; y++) {
                        int ind_y = offsets[y];
                        int wgt_y = weights[y];
                        for(int z = 0; z < nativeres; z++) {
                            int ind_z = offsets[z];
                            int wgt_z = weights[z];
                            for(int x = 0; x < nativeres; x++) {
                                if(isSubblockSet(x, y, z)) {
                                    int ind_x = offsets[x];
                                    int wgt_x = weights[x];
                                    for(int xx = 0; xx < 2; xx++) {
                                        int wx = (xx==0)?wgt_x:(res-wgt_x);
                                        if(wx == 0) continue;
                                        for(int yy = 0; yy < 2; yy++) {
                                            int wy = (yy==0)?wgt_y:(res-wgt_y);
                                            if(wy == 0) continue;
                                            for(int zz = 0; zz < 2; zz++) {
                                                int wz = (zz==0)?wgt_z:(res-wgt_z);
                                                if(wz == 0) continue;
                                                accum[(ind_y+yy)*res*res + (ind_z+zz)*res + (ind_x+xx)] +=
                                                    wx*wy*wz;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for(int i = 0; i < map.length; i++) {
                        map[i] = (short)(accum[i]*255/nativeres/nativeres/nativeres);
                        if(map[i] > 255) map[i] = 255;                            
                        if(map[i] < 0) map[i] = 0;
                    }
                }
                scaledblocks.put(Integer.valueOf(res), map);
            }
            return map;
        }
    }

    public static class HDBlockPatchModel extends HDBlockModel {
        /* Patch model specific attributes */
        private HDPatchDefinition[] patches;
        /**
         * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
         * (for patch models)
         * @param blockid - block ID
         * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
         * @param patches - list of patches (surfaces composing model)
         */
        public HDBlockPatchModel(int blockid, int databits, HDPatchDefinition[] patches) {
            super(blockid, databits);
            this.patches = patches;
        }
        /**
         * Get patches for block model (if patch model)
         */
        public final HDPatchDefinition[] getPatches() {
            return patches;
        }
    }
    
    /**
     * Get link algorithm
     * @param blkid - block ID
     * @return 0=no link alg
     */
    public static final int getLinkAlgID(int blkid) {
        try {
            return linkalg[blkid];
        } catch (ArrayIndexOutOfBoundsException aioobx) {
            resizeTable(blkid);
            return 0;
        }
    }
    /**
     * Get link block IDs
     * @param blkid - block ID
     * @return array of block IDs to link with
     */
    public static final int[] getLinkIDs(int blkid) {
        try {
            return linkmap[blkid];
        } catch (ArrayIndexOutOfBoundsException aioobx) {
            resizeTable(blkid);
            return null;
        }
    }
    
    /**
     * Get scaled set of models for all modelled blocks 
     * @param scale
     * @return
     */
    public static HDScaledBlockModels   getModelsForScale(int scale) {
        HDScaledBlockModels model = scaled_models_by_scale.get(Integer.valueOf(scale));
        if(model == null) {
            model = new HDScaledBlockModels();
            short[][][] blockmodels = new short[256][][];
            HDPatchDefinition[][][] patches = new HDPatchDefinition[256][][];
            
            for(HDBlockModel m : models_by_id_data.values()) {
                if(m.blockid >= blockmodels.length){
                    short[][][] newmodels = new short[m.blockid+1][][];
                    System.arraycopy(blockmodels,  0, newmodels, 0, blockmodels.length);
                    blockmodels = newmodels;
                    HDPatchDefinition[][][] newpatches = new HDPatchDefinition[m.blockid+1][][];
                    System.arraycopy(patches,  0, newpatches, 0, patches.length);
                    patches = newpatches;
                }
                if(m instanceof HDBlockVolumetricModel) {
                    short[][] row = blockmodels[m.blockid];
                    if(row == null) {
                        row = new short[16][];
                        blockmodels[m.blockid] = row; 
                    }
                    HDBlockVolumetricModel vm = (HDBlockVolumetricModel)m;
                    short[] smod = vm.getScaledMap(scale);
                    /* See if scaled model is full block : much faster to not use it if it is */
                    if(smod != null) {
                        boolean keep = false;
                        for(int i = 0; (!keep) && (i < smod.length); i++) {
                            if(smod[i] == 0) keep = true;
                        }
                        if(keep) {
                            for(int i = 0; i < 16; i++) {
                                if((m.databits & (1 << i)) != 0) {
                                    row[i] = smod;
                                }
                            }
                        }
                    }
                }
                else if(m instanceof HDBlockPatchModel) {
                    HDBlockPatchModel pm = (HDBlockPatchModel)m;
                    HDPatchDefinition[] patch = pm.getPatches();
                    HDPatchDefinition[][] row = patches[m.blockid];
                    if(row == null) {
                        row = new HDPatchDefinition[16][];
                        patches[m.blockid] = row; 
                    }
                    if(patch != null) {
                        for(int i = 0; i < 16; i++) {
                            if((m.databits & (1 << i)) != 0) {
                                row[i] = patch;
                            }
                        }
                    }
                }
            }
            model.modelvectors = blockmodels;
            model.patches = patches;
            scaled_models_by_scale.put(scale, model);
        }
        return model;
    }
    /**
     * Load models 
     */
    public static void loadModels(File datadir, ConfigurationNode config) {
        /* Reset models-by-ID-Data cache */
        models_by_id_data.clear();
        /* Reset scaled models by scale cache */
        scaled_models_by_scale.clear();
        
        /* Load block models */
        InputStream in = TexturePack.class.getResourceAsStream("/models.txt");
        if(in != null) {
            loadModelFile(in, "models.txt", config);
            try { in.close(); } catch (IOException iox) {} in = null;
        }
        File customdir = new File(datadir, "renderdata");
        String[] files = customdir.list();
        if(files != null) {
            for(String fn : files) {
                if(fn.endsWith("-models.txt") == false)
                    continue;
                File custom = new File(customdir, fn);
                if(custom.canRead()) {
                    try {
                        in = new FileInputStream(custom);
                        loadModelFile(in, custom.getPath(), config);
                    } catch (IOException iox) {
                        Log.severe("Error loading " + custom.getPath());
                    } finally {
                        if(in != null) { 
                            try { in.close(); } catch (IOException iox) {}
                            in = null;
                        }
                    }
                }
            }
        }
    }
    private static Integer getIntValue(Map<String,Integer> vars, String val) throws NumberFormatException {
        if(Character.isLetter(val.charAt(0))) {
            Integer v = vars.get(val);
            if(v == null)
                throw new NumberFormatException("invalid ID - " + val);
            return v;
        }
        else {
            return Integer.valueOf(val);
        }
    }
    /**
     * Load models from file
     */
    private static void loadModelFile(InputStream in, String fname, ConfigurationNode config) {
        LineNumberReader rdr = null;
        int cnt = 0;
        try {
            String line;
            ArrayList<HDBlockVolumetricModel> modlist = new ArrayList<HDBlockVolumetricModel>();
            ArrayList<HDBlockPatchModel> pmodlist = new ArrayList<HDBlockPatchModel>();
            HashMap<String,Integer> varvals = new HashMap<String,Integer>();
            HashMap<String, HDPatchDefinition> patchdefs = new HashMap<String, HDPatchDefinition>();
            int layerbits = 0;
            int rownum = 0;
            int scale = 0;
            rdr = new LineNumberReader(new InputStreamReader(in));
            while((line = rdr.readLine()) != null) {
                if(line.startsWith("block:")) {
                    ArrayList<Integer> blkids = new ArrayList<Integer>();
                    int databits = 0;
                    scale = 0;
                    line = line.substring(6);
                    String[] args = line.split(",");
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) {
                            blkids.add(getIntValue(varvals,av[1]));
                        }
                        else if(av[0].equals("data")) {
                            if(av[1].equals("*"))
                                databits = 0xFFFF;
                            else
                                databits |= (1 << getIntValue(varvals,av[1]));
                        }
                        else if(av[0].equals("scale")) {
                            scale = Integer.parseInt(av[1]);
                        }
                    }
                    /* If we have everything, build block */
                    if((blkids.size() > 0) && (databits != 0) && (scale > 0)) {
                        modlist.clear();
                        for(Integer id : blkids) {
                            if(id > 0) {
                                modlist.add(new HDBlockVolumetricModel(id.intValue(), databits, scale, new long[0]));
                                cnt++;
                            }
                        }
                    }
                    else {
                        Log.severe("Block model missing required parameters = line " + rdr.getLineNumber() + " of " + fname);
                    }
                    layerbits = 0;
                }
                else if(line.startsWith("layer:")) {
                    line = line.substring(6);
                    String args[] = line.split(",");
                    layerbits = 0;
                    rownum = 0;
                    for(String a: args) {
                        layerbits |= (1 << Integer.parseInt(a));
                    }
                }
                else if(line.startsWith("rotate:")) {
                    line = line.substring(7);
                    String args[] = line.split(",");
                    int id = -1;
                    int data = -1;
                    int rot = -1;
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) { id = getIntValue(varvals,av[1]); }
                        if(av[0].equals("data")) { data = getIntValue(varvals,av[1]); }
                        if(av[0].equals("rot")) { rot = Integer.parseInt(av[1]); }
                    }
                    /* get old model to be rotated */
                    HDBlockModel mod = models_by_id_data.get((id<<4)+data);
                    if((mod != null) && ((rot%90) == 0) && (mod instanceof HDBlockVolumetricModel)) {
                        HDBlockVolumetricModel vmod = (HDBlockVolumetricModel)mod;
                        for(int x = 0; x < scale; x++) {
                            for(int y = 0; y < scale; y++) {
                                for(int z = 0; z < scale; z++) {
                                    if(vmod.isSubblockSet(x, y, z) == false) continue;
                                    switch(rot) {
                                        case 0:
                                            for(HDBlockVolumetricModel bm : modlist) {
                                                bm.setSubblock(x, y, z, true);
                                            }
                                            break;
                                        case 90:
                                            for(HDBlockVolumetricModel bm : modlist) {
                                                bm.setSubblock(scale-z-1, y, x, true);
                                            }
                                            break;
                                        case 180:
                                            for(HDBlockVolumetricModel bm : modlist) {
                                                bm.setSubblock(scale-x-1, y, scale-z-1, true);
                                            }
                                            break;
                                        case 270:
                                            for(HDBlockVolumetricModel bm : modlist) {
                                                bm.setSubblock(z, y, scale-x-1, true);
                                            }
                                            break;
                                    }
                                }
                            }
                        }
                    }
                    else {
                        Log.severe("Invalid rotate error - line " + rdr.getLineNumber() + " of " + fname);
                        return;
                    }
                }
                else if(line.startsWith("patchrotate:")) {
                    line = line.substring(12);
                    String args[] = line.split(",");
                    int id = -1;
                    int data = -1;
                    int rotx = 0;
                    int roty = 0;
                    int rotz = 0;
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) { id = getIntValue(varvals,av[1]); }
                        if(av[0].equals("data")) { data = getIntValue(varvals,av[1]); }
                        if(av[0].equals("rot")) { roty = Integer.parseInt(av[1]); }
                        if(av[0].equals("roty")) { roty = Integer.parseInt(av[1]); }
                        if(av[0].equals("rotx")) { rotx = Integer.parseInt(av[1]); }
                        if(av[0].equals("rotz")) { rotz = Integer.parseInt(av[1]); }
                    }
                    /* get old model to be rotated */
                    HDBlockModel mod = models_by_id_data.get((id<<4)+data);
                    if((mod != null) && (mod instanceof HDBlockPatchModel)) {
                        HDBlockPatchModel pmod = (HDBlockPatchModel)mod;
                        HDPatchDefinition patches[] = pmod.getPatches();
                        HDPatchDefinition newpatches[] = new HDPatchDefinition[patches.length];
                        for(int i = 0; i < patches.length; i++) {
                            newpatches[i] = new HDPatchDefinition(patches[i], rotx, roty, rotz);
                        }
                        for(HDBlockPatchModel patchmod : pmodlist) {
                            patchmod.patches = newpatches;
                        }
                    }
                    else {
                        Log.severe("Invalid rotate error - line " + rdr.getLineNumber() + " of " + fname);
                        return;
                    }
                }
                else if(line.startsWith("linkmap:")) {
                    ArrayList<Integer> blkids = new ArrayList<Integer>();
                    line = line.substring(8);
                    String[] args = line.split(",");
                    List<Integer> map = new ArrayList<Integer>();
                    int linktype = 0;
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) {
                            blkids.add(getIntValue(varvals,av[1]));
                        }
                        else if(av[0].equals("linkalg")) {
                            linktype = Integer.parseInt(av[1]);
                        }
                        else if(av[0].equals("linkid")) {
                            map.add(getIntValue(varvals,av[1]));
                        }
                    }
                    if(linktype > 0) {
                        int[] mapids = new int[map.size()];
                        for(int i = 0; i < mapids.length; i++)
                            mapids[i] = map.get(i);
                        for(Integer bid : blkids) {
                            try {
                                linkalg[bid] = linktype;
                                linkmap[bid] = mapids;
                            } catch (ArrayIndexOutOfBoundsException aioobx) {
                                resizeTable(bid);
                                linkalg[bid] = linktype;
                                linkmap[bid] = mapids;
                            }
                        }
                    }
                }
                else if(line.startsWith("#") || line.startsWith(";")) {
                }
                else if(line.startsWith("enabled:")) {  /* Test if texture file is enabled */
                    line = line.substring(8).trim();
                    if(line.startsWith("true")) {   /* We're enabled? */
                        /* Nothing to do - keep processing */
                    }
                    else if(line.startsWith("false")) { /* Disabled */
                        return; /* Quit */
                    }
                    /* If setting is not defined or false, quit */
                    else if(config.getBoolean(line, false) == false) {
                        return;
                    }
                    else {
                        Log.info(line + " models enabled");
                    }
                }
                else if(line.startsWith("var:")) {  /* Test if variable declaration */
                    line = line.substring(4).trim();
                    String args[] = line.split(",");
                    for(int i = 0; i < args.length; i++) {
                        String[] v = args[i].split("=");
                        if(v.length < 2) {
                            Log.severe("Format error - line " + rdr.getLineNumber() + " of " + fname);
                            return;
                        }
                        try {
                            int val = Integer.valueOf(v[1]);    /* Parse default value */
                            int parmval = config.getInteger(v[0], val); /* Read value, with applied default */
                            varvals.put(v[0], parmval); /* And save value */
                        } catch (NumberFormatException nfx) {
                            Log.severe("Format error - line " + rdr.getLineNumber() + " of " + fname);
                            return;
                        }
                    }
                }
                else if(line.startsWith("patch:")) {
                    String patchid = null;
                    line = line.substring(6);
                    HDPatchDefinition pd = new HDPatchDefinition();
                    String[] args = line.split(",");
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) {
                            patchid = av[1];
                        }
                        else if(av[0].equals("Ox")) {
                            pd.x0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Oy")) {
                            pd.y0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Oz")) {
                            pd.z0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Ux")) {
                            pd.xu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Uy")) {
                            pd.yu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Uz")) {
                            pd.zu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vx")) {
                            pd.xv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vy")) {
                            pd.yv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vz")) {
                            pd.zv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Umin")) {
                            pd.umin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Umax")) {
                            pd.umax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vmin")) {
                            pd.vmin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vmax")) {
                            pd.vmax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("UplusVmax")) {
                            pd.uplusvmax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("visibility")) {
                            if(av[1].equals("top"))
                                pd.sidevis = SideVisibility.TOP;
                            else if(av[1].equals("bottom"))
                                pd.sidevis = SideVisibility.BOTTOM;
                            else
                                pd.sidevis = SideVisibility.BOTH;
                        }
                    }
                    /* If completed, add to map */
                    if(patchid != null) {
                        pd.update();    /* Finish cooking it */
                        if(pd.validate()) {
                            patchdefs.put(patchid,  pd);
                        }
                    }
                }
                else if(line.startsWith("patchblock:")) {
                    ArrayList<Integer> blkids = new ArrayList<Integer>();
                    int databits = 0;
                    line = line.substring(11);
                    String[] args = line.split(",");
                    ArrayList<HDPatchDefinition> patches = new ArrayList<HDPatchDefinition>();
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) {
                            blkids.add(getIntValue(varvals,av[1]));
                        }
                        else if(av[0].equals("data")) {
                            if(av[1].equals("*"))
                                databits = 0xFFFF;
                            else
                                databits |= (1 << getIntValue(varvals,av[1]));
                        }
                        else if(av[0].startsWith("patch")) {
                            int patchnum = Integer.parseInt(av[0].substring(5));    /* Get index */
                            if((patchnum < 0) || (patchnum >= HDPatchDefinition.MAX_PATCHES)) {
                                Log.severe("Invalid patch index " + patchnum + " - line " + rdr.getLineNumber() + " of " + fname);
                                return;
                            }
                            HDPatchDefinition pd = patchdefs.get(av[1]);
                            if(pd == null) {
                                /* See if ID@rotation */
                                int atidx = av[1].indexOf('@');
                                if(atidx > 0) {
                                    HDPatchDefinition pdorig = patchdefs.get(av[1].substring(0, atidx));
                                    int rotx = 0, roty = 0, rotz = 0;
                                    String[] rv = av[1].substring(atidx+1).split("/");
                                    if(rv.length == 1) {
                                        roty = Integer.parseInt(rv[0]);
                                    }
                                    else if(rv.length == 2) {
                                        rotx = Integer.parseInt(rv[0]);
                                        roty = Integer.parseInt(rv[1]);
                                    }
                                    else if(rv.length == 3) {
                                        rotx = Integer.parseInt(rv[0]);
                                        roty = Integer.parseInt(rv[1]);
                                        rotz = Integer.parseInt(rv[2]);
                                    }
                                    if(pdorig != null) {
                                        pd = new HDPatchDefinition(pdorig, rotx, roty, rotz);
                                        patchdefs.put(av[1],  pd);  /* Add to map so we reuse it */
                                    }
                                }
                            }
                            if(pd == null) {
                                Log.severe("Invalid patch ID " + av[1] + " - line " + rdr.getLineNumber() + " of " + fname);
                            }
                            else {
                                patches.add(patchnum,  pd);
                            }
                        }
                    }
                    /* If we have everything, build block */
                    pmodlist.clear();
                    if((blkids.size() > 0) && (databits != 0)) {
                        HDPatchDefinition[] patcharray = patches.toArray(new HDPatchDefinition[patches.size()]);
                        for(Integer id : blkids) {
                            if(id > 0) {
                                pmodlist.add(new HDBlockPatchModel(id.intValue(), databits, patcharray));
                                cnt++;
                            }
                        }
                    }
                    else {
                        Log.severe("Patch block model missing required parameters = line " + rdr.getLineNumber() + " of " + fname);
                    }
                }
                else if(layerbits != 0) {   /* If we're working pattern lines */
                    /* Layerbits determine Y, rows count from North to South (X=0 to X=N-1), columns Z are West to East (N-1 to 0) */
                    for(int i = 0; (i < scale) && (i < line.length()); i++) {
                        if(line.charAt(i) == '*') { /* If an asterix, set flag */
                            for(int y = 0; y < scale; y++) {
                                if((layerbits & (1<<y)) != 0) {
                                    for(HDBlockVolumetricModel mod : modlist) {
                                        mod.setSubblock(rownum, y, scale-i-1, true);
                                    }
                                }
                            }
                        }
                    }
                    /* See if we're done with layer */
                    rownum++;
                    if(rownum >= scale) {
                        rownum = 0;
                        layerbits = 0;
                    }
                }
            }
            Log.verboseinfo("Loaded " + cnt + " block models from " + fname);
        } catch (IOException iox) {
            Log.severe("Error reading models.txt - " + iox.toString());
        } catch (NumberFormatException nfx) {
            Log.severe("Format error - line " + rdr.getLineNumber() + " of " + fname);
        } finally {
            if(rdr != null) {
                try {
                    rdr.close();
                    rdr = null;
                } catch (IOException e) {
                }
            }
        }
    }
}

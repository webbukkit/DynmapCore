package org.dynmap.hdmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.debug.Debug;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.ForgeConfigFile;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

/**
 * Custom block models - used for non-cube blocks to represent the physical volume associated with the block
 * Used by perspectives to determine if rays have intersected a block that doesn't occupy its whole block
 */
public class HDBlockModels {
    private static int linkalg[] = new int[256];
    private static int linkmap[][] = new int[256][];
    private static int max_patches;
    private static HashMap<Integer, HDBlockModel> models_by_id_data = new HashMap<Integer, HDBlockModel>();
    private static PatchDefinitionFactory pdf = new PatchDefinitionFactory();
    private static BitSet customModelsRequestingTileData = new BitSet(); // Index by 16*id + data

    public static final int getMaxPatchCount() { return max_patches; }
    public static final PatchDefinitionFactory getPatchDefinitionFactory() { return pdf; }
    
    /* Reset model if defined by different block set */
    public static boolean resetIfNotBlockSet(int blkid, int blkdata, String blockset) {
        HDBlockModel bm = models_by_id_data.get((blkid << 4) | blkdata);
        if((bm != null) && (bm.getBlockSet().equals(blockset) == false)) {
            Debug.debug("Reset block model for " + blkid + ":" + blkdata + " from " + bm.getBlockSet() + " due to new def from " + blockset);
            models_by_id_data.remove((blkid << 4) | blkdata);
            bm.databits ^= (1 << blkdata);  /* Clear data bit for this model, in case used elsewhere */
            return true;
        }
        return false;
    }
    /* Get texture count needed for model */
    public static int getNeededTextureCount(int blkid, int blkdata) {
        HDBlockModel bm = models_by_id_data.get((blkid << 4) | blkdata);
        if(bm != null) {
            return bm.getTextureCount();
        }
        return 6;
    }
    
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
        private PatchDefinition[][][] patches;
        private CustomBlockModel[][] custom;

        public final short[] getScaledModel(int blocktype, int blockdata, int blockrenderdata) {
            short[][] m;
            try {
                if(modelvectors[blocktype] == null) {
                    return null;
                }
                m = modelvectors[blocktype];
            } catch (ArrayIndexOutOfBoundsException aioobx) {
                short[][][] newmodels = new short[blocktype+1][][];
                System.arraycopy(modelvectors, 0, newmodels, 0, modelvectors.length);
                modelvectors = newmodels;
                return null;
            }
            return m[(blockrenderdata>=0)?blockrenderdata:blockdata];
        }
        public PatchDefinition[] getPatchModel(int blocktype, int blockdata, int blockrenderdata) {
            try {
                if(patches[blocktype] == null) {
                    return null;
                }
                return patches[blocktype][(blockrenderdata>=0)?blockrenderdata:blockdata];
            } catch (ArrayIndexOutOfBoundsException aioobx) {
                PatchDefinition[][][] newpatches = new PatchDefinition[blocktype+1][][];
                System.arraycopy(patches, 0, newpatches, 0, patches.length);
                patches = newpatches;
                return null;
            }
        }
        public CustomBlockModel getCustomBlockModel(int blocktype, int blockdata) {
            try {
                if(custom[blocktype] == null) {
                    return null;
                }
                return custom[blocktype][blockdata];
            } catch (ArrayIndexOutOfBoundsException aioobx) {
                CustomBlockModel[][] newcustom = new CustomBlockModel[blocktype+1][];
                System.arraycopy(custom, 0, newcustom, 0, custom.length);
                custom = newcustom;
                return null;
            }
        }
    }
        
    
    private static HashMap<Integer, HDScaledBlockModels> scaled_models_by_scale = new HashMap<Integer, HDScaledBlockModels>();
    
    public static abstract class HDBlockModel {
        private int blockid;
        private int databits;
        private String blockset;
        /**
         * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
         * @param blockid - block ID
         * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
         * @param blockset - ID of block definition set
         */
        protected HDBlockModel(int blockid, int databits, String blockset) {
            this.blockid = blockid;
            this.databits = databits;
            this.blockset = blockset;
            if(blockid > 0) {
                for(int i = 0; i < 16; i++) {
                    if((databits & (1<<i)) != 0) {
                        HDBlockModel prev = models_by_id_data.put((blockid<<4)+i, this);
                        if((prev != null) && (prev != this)) {
                            prev.removed(blockid, i);
                        }
                    }
                }
            }
        }
        public String getBlockSet() {
            return blockset;
        }
        public abstract int getTextureCount();
        
        public void removed(int blkid, int blkdat) {
            this.databits &= (~(1 << blkdat));
        }
    }
    
    public static class CustomBlockModel extends HDBlockModel {
        public CustomRenderer render;
        
        public CustomBlockModel(int blockid, int databits, String classname, Map<String,String> classparm, String blockset) {
            super(blockid, databits, blockset);
            try {
                Class<?> cls = Class.forName(classname);   /* Get class */
                render = (CustomRenderer) cls.newInstance();
                if(render.initializeRenderer(pdf, blockid, databits, classparm) == false) {
                    Log.severe("Error loading custom renderer - " + classname);
                    render = null;
                }
                else {
                    if(render.getTileEntityFieldsNeeded() != null) {
                        for(int i = 0; i < 16; i++) {
                            if ((databits & (1 << i)) != 0) {
                                customModelsRequestingTileData.set((blockid<<4) | i);
                            }
                        }
                    }
                }
            } catch (Exception x) {
                Log.severe("Error loading custom renderer - " + classname, x);
                render = null;
            }
        }

        @Override
        public int getTextureCount() {
            return render.getMaximumTextureCount();
        }

        private static final RenderPatch[] empty_list = new RenderPatch[0];
        
        public RenderPatch[] getMeshForBlock(MapDataContext ctx) {
            if(render != null)
                return render.getRenderPatchList(ctx);
            else
                return empty_list;
        }
        @Override
        public void removed(int blkid, int blkdat) {
            super.removed(blkid, blkdat);
            customModelsRequestingTileData.clear((blkid<<4) | blkdat);
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
         * @param blockset - ID of set of blocks defining model
         */
        public HDBlockVolumetricModel(int blockid, int databits, int nativeres, long[] blockflags, String blockset) {
            super(blockid, databits, blockset);
            
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
        @Override
        public int getTextureCount() {
            return 6;
        }
    }

    public static class HDBlockPatchModel extends HDBlockModel {
        /* Patch model specific attributes */
        private PatchDefinition[] patches;
        private final int max_texture;
        /**
         * Block definition - positions correspond to Bukkit coordinates (+X is south, +Y is up, +Z is west)
         * (for patch models)
         * @param blockid - block ID
         * @param databits - bitmap of block data bits matching this model (bit N is set if data=N would match)
         * @param patches - list of patches (surfaces composing model)
         * @param blockset - ID of set of blocks defining model
         */
        public HDBlockPatchModel(int blockid, int databits, PatchDefinition[] patches, String blockset) {
            super(blockid, databits, blockset);
            this.patches = patches;
            int max = 0;
            for(int i = 0; i < patches.length; i++) {
                if(patches[i].textureindex > max)
                    max = patches[i].textureindex;
            }
            this.max_texture = max + 1;
        }
        /**
         * Get patches for block model (if patch model)
         */
        public final PatchDefinition[] getPatches() {
            return patches;
        }
        @Override
        public int getTextureCount() {
            return max_texture;
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
     * Get list of tile entity fields needed for custom renderer at given ID and data value, if any
     * @param blkid - block ID
     * @param blkdat - block data
     * @return null if none needed, else list of fields needed
     */
    public static final String[] getTileEntityFieldsNeeded(int blkid, int blkdat) {
        int idx = (blkid << 4) | blkdat;
        if(customModelsRequestingTileData.get(idx)) {
            HDBlockModel mod = models_by_id_data.get(idx);
            if(mod instanceof CustomBlockModel) {
                return ((CustomBlockModel)mod).render.getTileEntityFieldsNeeded();
            }
        }
        return null;
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
            PatchDefinition[][][] patches = new PatchDefinition[256][][];
            CustomBlockModel[][] custom = new CustomBlockModel[256][];
            
            for(HDBlockModel m : models_by_id_data.values()) {
                if(m.blockid >= blockmodels.length){
                    short[][][] newmodels = new short[m.blockid+1][][];
                    System.arraycopy(blockmodels,  0, newmodels, 0, blockmodels.length);
                    blockmodels = newmodels;
                    PatchDefinition[][][] newpatches = new PatchDefinition[m.blockid+1][][];
                    System.arraycopy(patches,  0, newpatches, 0, patches.length);
                    patches = newpatches;
                    CustomBlockModel[][] newcustom = new CustomBlockModel[m.blockid+1][];
                    System.arraycopy(custom,  0, newcustom, 0, custom.length);
                    custom = newcustom;
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
                    PatchDefinition[] patch = pm.getPatches();
                    PatchDefinition[][] row = patches[m.blockid];
                    if(row == null) {
                        row = new PatchDefinition[16][];
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
                else if(m instanceof CustomBlockModel) {
                    CustomBlockModel cbm = (CustomBlockModel)m;
                    CustomBlockModel[] row = custom[m.blockid];
                    if(row == null) {
                        row = new CustomBlockModel[16];
                        custom[m.blockid] = row; 
                    }
                    for(int i = 0; i < 16; i++) {
                        if((m.databits & (1 << i)) != 0) {
                            row[i] = cbm;
                        }
                    }
                }
            }
            model.modelvectors = blockmodels;
            model.patches = patches;
            model.custom = custom;
            scaled_models_by_scale.put(scale, model);
        }
        return model;
    }
    /**
     * Load models 
     */
    public static void loadModels(DynmapCore core, ConfigurationNode config) {
        File datadir = core.getDataFolder();
        max_patches = 6;    /* Reset to default */
        /* Reset models-by-ID-Data cache */
        models_by_id_data.clear();
        /* Reset scaled models by scale cache */
        scaled_models_by_scale.clear();
        
        /* Load block models */
        InputStream in = TexturePack.class.getResourceAsStream("/models.txt");
        if(in != null) {
            loadModelFile(in, "models.txt", config, core, "core");
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
                        loadModelFile(in, custom.getPath(), config, core, fn.substring(0, fn.indexOf("-models.txt")));
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
            int off = val.indexOf('+');
            int offset = 0;
            if (off > 0) {
                offset = Integer.valueOf(val.substring(off+1));
                val = val.substring(0,  off);
            }
            Integer v = vars.get(val);
            if(v == null)
                throw new NumberFormatException("invalid ID - " + val);
            if((offset != 0) && (v.intValue() > 0))
                v = v.intValue() + offset;
            return v;
        }
        else {
            return Integer.valueOf(val);
        }
    }
    /**
     * Load models from file
     * @param core 
     */
    private static void loadModelFile(InputStream in, String fname, ConfigurationNode config, DynmapCore core, String blockset) {
        LineNumberReader rdr = null;
        int cnt = 0;
        boolean need_mod_cfg = false;
        String modname = null;
        try {
            String line;
            ArrayList<HDBlockVolumetricModel> modlist = new ArrayList<HDBlockVolumetricModel>();
            ArrayList<HDBlockPatchModel> pmodlist = new ArrayList<HDBlockPatchModel>();
            HashMap<String,Integer> varvals = new HashMap<String,Integer>();
            HashMap<String, PatchDefinition> patchdefs = new HashMap<String, PatchDefinition>();
            pdf.setPatchNameMape(patchdefs);
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
                                modlist.add(new HDBlockVolumetricModel(id.intValue(), databits, scale, new long[0], blockset));
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
                        PatchDefinition patches[] = pmod.getPatches();
                        PatchDefinition newpatches[] = new PatchDefinition[patches.length];
                        for(int i = 0; i < patches.length; i++) {
                            newpatches[i] = (PatchDefinition)pdf.getRotatedPatch(patches[i], rotx, roty, rotz, patches[i].textureindex);
                        }
                        if(patches.length > max_patches)
                            max_patches = patches.length;
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
                else if(line.startsWith("cfgfile:")) { /* If config file */
                    File cfgfile = new File(line.substring(8).trim());
                    ForgeConfigFile cfg = new ForgeConfigFile(cfgfile);
                    if(cfg.load()) {
                        cfg.addBlockIDs(varvals);
                        need_mod_cfg = false;
                    }
                }
                else if(line.startsWith("patch:")) {
                    String patchid = null;
                    line = line.substring(6);
                    String[] args = line.split(",");
                    double p_x0 = 0.0, p_y0 = 0.0, p_z0 = 0.0;
                    double p_xu = 0.0, p_yu = 1.0, p_zu = 0.0;
                    double p_xv = 1.0, p_yv = 0.0, p_zv = 0.0;
                    double p_umin = 0.0, p_umax = 1.0;
                    double p_vmin = 0.0, p_vmax = 1.0;
                    double p_uplusvmax = 100.0;
                    SideVisible p_sidevis = SideVisible.BOTH;
                    
                    for(String a : args) {
                        String[] av = a.split("=");
                        if(av.length < 2) continue;
                        if(av[0].equals("id")) {
                            patchid = av[1];
                        }
                        else if(av[0].equals("Ox")) {
                            p_x0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Oy")) {
                            p_y0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Oz")) {
                            p_z0 = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Ux")) {
                            p_xu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Uy")) {
                            p_yu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Uz")) {
                            p_zu = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vx")) {
                            p_xv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vy")) {
                            p_yv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vz")) {
                            p_zv = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Umin")) {
                            p_umin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Umax")) {
                            p_umax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vmin")) {
                            p_vmin = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("Vmax")) {
                            p_vmax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("UplusVmax")) {
                            p_uplusvmax = Double.parseDouble(av[1]);
                        }
                        else if(av[0].equals("visibility")) {
                            if(av[1].equals("top"))
                                p_sidevis = SideVisible.TOP;
                            else if(av[1].equals("bottom"))
                                p_sidevis = SideVisible.BOTTOM;
                            else
                                p_sidevis = SideVisible.BOTH;
                        }
                    }
                    /* If completed, add to map */
                    if(patchid != null) {
                        PatchDefinition pd = pdf.getPatch(p_x0, p_y0, p_z0, p_xu, p_yu, p_zu, p_xv, p_yv, p_zv, p_umin, p_umax, p_vmin, p_vmax, p_uplusvmax, p_sidevis, 0);
                        if(pd != null) {
                            patchdefs.put(patchid,  pd);
                        }
                    }
                }
                else if(line.startsWith("patchblock:")) {
                    ArrayList<Integer> blkids = new ArrayList<Integer>();
                    int databits = 0;
                    line = line.substring(11);
                    String[] args = line.split(",");
                    ArrayList<PatchDefinition> patches = new ArrayList<PatchDefinition>();
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
                            int patchnum0, patchnum1;
                            String ids = av[0].substring(5);
                            String[] ids2 = ids.split("-");
                            if(ids2.length == 1) {
                                patchnum0 = patchnum1 = Integer.parseInt(ids2[0]);
                            }
                            else {
                                patchnum0 = Integer.parseInt(ids2[0]);
                                patchnum1 = Integer.parseInt(ids2[1]);
                            }
                            if(patchnum0 < 0) {
                                Log.severe("Invalid patch index " + patchnum0 + " - line " + rdr.getLineNumber() + " of " + fname);
                                return;
                            }
                            if(patchnum1 < patchnum0) {
                                Log.severe("Invalid patch index " + patchnum1 + " - line " + rdr.getLineNumber() + " of " + fname);
                                return;
                            }
                            String patchid = av[1];
                            /* Look up patch by name */
                            for(int i = patchnum0; i <= patchnum1; i++) {
                                PatchDefinition pd = pdf.getPatchByName(patchid, i);
                                if(pd == null) {
                                    Log.severe("Invalid patch ID " + patchid + " - line " + rdr.getLineNumber() + " of " + fname);
                                    return;
                                }
                                patches.add(i,  pd);
                            }
                        }
                    }
                    /* If we have everything, build block */
                    pmodlist.clear();
                    if((blkids.size() > 0) && (databits != 0)) {
                        PatchDefinition[] patcharray = patches.toArray(new PatchDefinition[patches.size()]);
                        if(patcharray.length > max_patches)
                            max_patches = patcharray.length;

                        for(Integer id : blkids) {
                            if(id > 0) {
                                pmodlist.add(new HDBlockPatchModel(id.intValue(), databits, patcharray, blockset));
                                cnt++;
                            }
                        }
                    }
                    else {
                        Log.severe("Patch block model missing required parameters = line " + rdr.getLineNumber() + " of " + fname);
                    }
                }
                else if(line.startsWith("customblock:")) {
                    ArrayList<Integer> blkids = new ArrayList<Integer>();
                    HashMap<String,String> custargs = new HashMap<String,String>();
                    int databits = 0;
                    line = line.substring(12);
                    String[] args = line.split(",");
                    String cls = null;
                    String clsarg = "";
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
                        else if(av[0].equals("class")) {
                            cls = av[1];
                        }
                        else {
                            /* See if substitution value available */
                            Integer vv = varvals.get(av[1]);
                            if(vv == null)
                                custargs.put(av[0], av[1]);
                            else
                                custargs.put(av[0], vv.toString());
                        }
                    }
                    /* If we have everything, build block */
                    if((blkids.size() > 0) && (databits != 0) && (cls != null)) {
                        for(Integer id : blkids) {
                            if(id > 0) {
                                CustomBlockModel cbm = new CustomBlockModel(id.intValue(), databits, cls, custargs, blockset);
                                if(cbm.render == null) {
                                    Log.severe("Custom block model failed to initialize = line " + rdr.getLineNumber() + " of " + fname);
                                }
                                else {
                                    /* Update maximum texture count */
                                    int texturecnt = cbm.getTextureCount();
                                    if(texturecnt > max_patches) {
                                        max_patches = texturecnt;
                                    }
                                }
                                cnt++;
                            }
                        }
                    }
                    else {
                        Log.severe("Custom block model missing required parameters = line " + rdr.getLineNumber() + " of " + fname);
                    }
                }
                else if(line.startsWith("modname:")) {
                    String[] names = line.substring(8).split(",");
                    boolean found = false;
                    for(String n : names) {
                        if(core.getServer().isModLoaded(n.trim()) == true) {
                            found = true;
                            Log.info(n + " models enabled");
                            need_mod_cfg = true;
                            modname = n.trim();
                            break;
                        }
                    }
                    if(!found) return;
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
            if(need_mod_cfg) {
                Log.severe("Error loading configuration file for " + modname);
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
            pdf.setPatchNameMape(null);
        }
    }
}

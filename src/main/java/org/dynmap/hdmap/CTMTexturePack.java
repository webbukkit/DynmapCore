package org.dynmap.hdmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.dynmap.Log;
import org.dynmap.hdmap.TexturePack.TileFileFormat;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.MapIterator;

/**
 * Connected Texture Mod (CTM) handler
 */
public class CTMTexturePack {
    private String[] ctpfiles;
    private File basedir;
    private ZipFile texturezip;
    private CTMProps[][] bytilelist;
    private CTMProps[][] byblocklist;

    public enum CTMMethod {
        NONE, CTM, HORIZONTAL, TOP, RANDOM, REPEAT, VERTICAL, FIXED
    }
    public enum CTMConnect {
        NONE, BLOCK, TILE, MATERIAL, UNKNOWN
    }
    public static final int FACE_BOTTOM = (1 << 0);
    public static final int FACE_TOP = (1 << 1);
    public static final int FACE_NORTH = (1 << 2);
    public static final int FACE_SOUTH = (1 << 3);
    public static final int FACE_WEST = (1 << 4);
    public static final int FACE_EAST = (1 << 5);
    public static final int FACE_SIDES = FACE_EAST | FACE_WEST | FACE_NORTH | FACE_SOUTH;
    public static final int FACE_ALL = FACE_SIDES | FACE_TOP | FACE_BOTTOM;
    public static final int FACE_UNKNOWN = (1 << 7);

    public enum CTMSymmetry {
        NONE(1),
        OPPOSITE(2),
        ALL(6),
        UNKNOWN(128);
        
        public final int shift;
        
        CTMSymmetry(int sh) {
            shift = sh;
        }
        
    }

    public static class CTMProps {
        public String name = null;
        public String basePath = null;
        public int[] matchBlocks = null;
        public String[] matchTiles = null;
        public CTMMethod method = CTMMethod.NONE;
        public String[] tiles = null;
        public CTMConnect connect = CTMConnect.NONE;
        public int faces = FACE_ALL;
        public int[] metadatas = null;
        public int[] biomes = null;
        public int minY = 0;
        public int maxY = 1024;
        public int renderPass = 0;
        public boolean innerSeams = false;
        public int width = 0;
        public int height = 0;
        public int[] weights = null;
        public CTMSymmetry symmetry = CTMSymmetry.NONE;
        public int[] sumWeights = null;
        public int sumAllWeights = 0;
        public int[] matchTileIcons = null;
        public int[] tileIcons = null;
        
        private String[] tokenize(String v, String split)
        {
            StringTokenizer tok = new StringTokenizer(v, split);
            ArrayList<String> rslt = new ArrayList<String>();

            while (tok.hasMoreTokens()) {
                rslt.add(tok.nextToken());
            }
            return rslt.toArray(new String[rslt.size()]);
        }

        private void getFaces(Properties p) {
            String v = p.getProperty("faces");
            if(v == null) {
                this.faces = FACE_ALL;
            }
            else {
                this.faces = 0;
                String[] tok = tokenize(v, " ,");
                for(String t : tok) {
                    if (t.equals("bottom")) {
                        this.faces |= FACE_BOTTOM;
                    }
                    else if (t.equals("top")) {
                        this.faces |= FACE_TOP;
                    }
                    else if (t.equals("north")) {
                        this.faces |= FACE_NORTH;
                    }
                    else if (t.equals("south")) {
                        this.faces |= FACE_SOUTH;
                    }
                    else if (t.equals("east")) {
                        this.faces |= FACE_EAST;
                    }
                    else if (t.equals("west")) {
                        this.faces |= FACE_WEST;
                    }
                    else if (t.equals("sides")) {
                        this.faces |= FACE_SIDES;
                    }
                    else if (t.equals("all")) {
                        this.faces |= FACE_ALL;
                    }
                    else {
                        Log.info("Unknown face in CTM file: " + t);
                        this.faces |= FACE_UNKNOWN;
                    }
                }
            }
        }
        private int parseInt(Properties p, String fld, int def) {
            String v = p.getProperty(fld);
            if (v == null) return def;
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException nfx) {
                Log.info("Bad integer: " + v);
                return def;
            }
        }
        private int[] parseInts(Properties p, String fld) {
            String v = p.getProperty(fld);
            if(v == null) return null;
            String[] tok = tokenize(v, ", ");
            ArrayList<Integer> rslt = new ArrayList<Integer>();
            for(String t : tok) {
                t = t.trim();
                String[] vtok = tokenize(t, "-");
                if(vtok.length == 1) {  /* One value */
                    try {
                        rslt.add(Integer.parseInt(vtok[0]));
                    } catch (NumberFormatException nfx) {
                        Log.info("Bad integer in list: " + vtok[0]);
                    }
                }
                else if(vtok.length == 2) { /* Range */
                    try {
                        int low = Integer.parseInt(vtok[0]);
                        int high = Integer.parseInt(vtok[1]);
                        for (int i = low; i <= high; i++) {
                            rslt.add(i);
                        }
                    } catch (NumberFormatException nfx) {
                        Log.info("Bad integer in range: " + t);
                    }
                }
            }
            int[] out = new int[rslt.size()];
            for(int i = 0; i < out.length; i++) {
                out[i] = rslt.get(i);
            }
            return out;
        }
        private void getMethod(Properties p) {
            String v = p.getProperty("method");
            if ((v == null) || (v.equals("ctm"))) {
                method = CTMMethod.CTM;
            }
            else if (v.equals("horizontal")) {
                method = CTMMethod.HORIZONTAL;
            }
            else if (v.equals("vertical")) {
                method = CTMMethod.VERTICAL;
            }
            else if (v.equals("top")) {
                method = CTMMethod.TOP;
            }
            else if (v.equals("random")) {
                method = CTMMethod.RANDOM;
            }
            else if (v.equals("repeat")) {
                method = CTMMethod.REPEAT;
            }
            else if (v.equals("fixed")) {
                method = CTMMethod.FIXED;
            }
            else {
                Log.info("Invalid CTM Method: " + v);
                method = CTMMethod.NONE;
            }
        }
        private void getConnect(Properties p) {
            String v = p.getProperty("connect");
            if (v == null) {
                this.connect = CTMConnect.NONE;
            }
            else if (v.equals("block")) {
                this.connect = CTMConnect.BLOCK;
            }
            else if (v.equals("tile")) {
                this.connect = CTMConnect.TILE;
            }
            else if (v.equals("material")) {
                this.connect = CTMConnect.MATERIAL;
            }
            else {
                Log.info("Invalid CTM Connect: " + v);
                this.connect = CTMConnect.UNKNOWN;
            }
        }
        private void getSymmetry(Properties p) {
            String v = p.getProperty("symmetry");
            if (v == null) {
                this.symmetry = CTMSymmetry.NONE;
            }
            else if (v.equals("opposite")) {
                this.symmetry = CTMSymmetry.OPPOSITE;
            }
            else if (v.equals("all")) {
                this.symmetry = CTMSymmetry.ALL;
            }
            else {
                Log.info("invalid CTM symmetry: " + v);
                this.symmetry = CTMSymmetry.NONE;
            }
        }
        private void getMatchTiles(Properties p) {
            String v = p.getProperty("matchTiles");
            if (v == null) {
                this.matchTiles = null;
            }
            else {
                String[] tok = tokenize(v, " ");
                for (int i = 0; i < tok.length; i++) {
                    String t = tok[i];
                    if (t.endsWith(".png")) {   /* Strip off PNG */
                        t = t.substring(0, t.length() - 4);
                    }
                    if (t.startsWith("/ctm/")) { /* If starts with / */
                        t = t.substring(1); // Strip off leading '/'
                    }
                    tok[i] = t;
                }
                this.matchTiles = tok;
            }
        }
        private String[] parseTileNames(String v) {
            if (v == null) {
                return null;
            }
            else {
                ArrayList<String> lst = new ArrayList<String>();
                String[] tok = tokenize(v, " ,");
                for (String t : tok) {
                    if (t.indexOf('-') >= 0) {
                        String[] vtok = tokenize(t, "-");
                        if (vtok.length == 2) {
                            try {
                                int low = Integer.parseInt(vtok[0]);
                                int high = Integer.parseInt(vtok[1]);
                                for (int i = low; i <= high; i++) {
                                    lst.add(String.valueOf(i));
                                }
                            } catch (NumberFormatException nfx) {
                                Log.info("Bad tile name range: " + t);
                            }
                        }
                    }
                    else {
                        lst.add(t);
                    }
                }
                String[] out = new String[lst.size()];
                for (int i = 0; i < out.length; i++) {
                    String vv = lst.get(i);
                    // If not absolute path
                    if (vv.startsWith("/") == false) {
                        vv = this.basePath + "/" + vv;    // Build path
                    }
                    if (vv.endsWith(".png")) {   // If needed, strip of png
                        vv = vv.substring(0,  vv.length() - 4);
                    }
                    if (vv.startsWith("/ctm/")) {    // If base relative
                        vv = vv.substring(1); // Strip off '/'
                    }
                    out[i] = vv;
                }
                return out;
            }
        }
        // Compute match blocks, based on name
        private void getMatchBlocks() {
            this.matchBlocks = null;
            if (this.name.startsWith("block")) { 
                /* Parse number after "block" */
                int id = -1;
                for (int i = 5; i < this.name.length(); i++) {
                    char c = this.name.charAt(i);
                    if (Character.isDigit(c)) {
                        if (id < 0) 
                            id = (c - '0');
                        else
                            id = (10 * id) + (c - '0');
                    }
                }
                if (id >= 0) {
                    this.matchBlocks = new int[] { id };
                }
            }
        }
        // Compute match tiles, based on name
        private void getMatchTiles() {
            this.matchTiles = null;
            if(TexturePack.findDynamicTile("textures/blocks/" + this.name + ".png", 0) >= 0) {
                this.matchTiles = new String[] { this.name };
            }
        }
        
        public CTMProps(Properties p, String fname) {
            String v;
            int last_sep = fname.lastIndexOf('/');
            this.name = fname;
            this.basePath = "";
            if(last_sep > 0) {
                this.name = fname.substring(last_sep+1);
                this.basePath = fname.substring(0,  last_sep);
            }
            int last_dot = this.name.lastIndexOf('.');
            if(last_dot > 0) {
                this.name = this.name.substring(0, last_dot);
            }
            this.matchBlocks = parseInts(p, "matchBlocks");
            
            getMatchTiles(p);
            getMethod(p);
            this.tiles = parseTileNames(p.getProperty("tiles"));
            getConnect(p);
            getFaces(p);
            getSymmetry(p);
            //TODO: see if biomes are actually handled - this.biomes = parseBiomes(p,getProperty("biomes"));

            this.metadatas = parseInts(p, "metadata");
            this.minY = parseInt(p, "minHeight", -1);
            this.maxY = parseInt(p, "maxHeight", -1);
            this.renderPass = parseInt(p, "renderPass", -1);
            this.width = parseInt(p, "width", -1);
            this.height = parseInt(p, "height", -1);
            this.weights = parseInts(p, "weights");
            /* Get innerSeams */
            v = p.getProperty("innerSeams");
            if(v != null) {
                this.innerSeams = v.equalsIgnoreCase("true");
            }
        }
        /**
         * Finish initialize - return true if good
         */
        public boolean isValid(String fname) {
            /* Must have name and base path */
            if ((this.name == null) || (this.name.length() == 0) || (this.basePath == null)) return false;
            // If no match blocks, detect value from name
            if (this.matchBlocks == null) {
                getMatchBlocks();
            }
            // If no match tiles, detect value from name
            if (this.matchTiles == null) {
                getMatchTiles();
            }
            // If no match blocks nor tiles, nothing to match
            if ((this.matchBlocks == null) && (this.matchTiles == null)) {
                Log.info("No matching tiles or blocks: " + fname);
                return false;
            }
            if (this.method == CTMMethod.NONE) {
                Log.info("No matching method: " + fname);
                return false;
            }
            if ((this.tiles == null) || (this.tiles.length == 0)) {
                Log.info("No tiles: " + fname);
                return false;
            }
            if (this.connect == CTMConnect.NONE) {
                if (this.matchBlocks != null) {
                    this.connect = CTMConnect.BLOCK;
                }
                else if (this.matchTiles != null) {
                    this.connect = CTMConnect.TILE;
                }
                else {
                    this.connect = CTMConnect.UNKNOWN;
                }
            }
            if (this.connect == CTMConnect.UNKNOWN) {
                Log.info("Bad connect: " + fname);
                return false;
            }
            if (this.renderPass > 0) {
                Log.info("Unsupported render pass: " + fname);
                return false;
            }
            if ((this.faces & FACE_UNKNOWN) > 0) {
                Log.info("Invalid face: " + fname);
                return false;
            }
            if (this.symmetry == CTMSymmetry.UNKNOWN) {
                Log.info("Invalid symmetry: " + fname);
                return false;
            }
            switch (this.method) {
                case CTM:
                    return isValidCtm(fname);
                    
                case HORIZONTAL:
                    return isValidHorizontal(fname);
                    
                case TOP:
                    return isValidTop(fname);
                    
                case RANDOM:
                    return isValidRandom(fname);
                    
                case REPEAT:
                    return isValidRepeat(fname);
                    
                case VERTICAL:
                    return isValidVertical(fname);
                    
                case FIXED:
                    return isValidFixed(fname);
                    
                default:
                    Log.info("Unknoen method: " + fname);
                    return false;
            }
        }
        private boolean isValidCtm(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("0-11 16-27 32-43 48-58");
            }
            if (this.tiles.length < 47) {   // Not enough for CTF
                Log.info("Not enough tiles for CTF method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidHorizontal(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("12-15");
            }
            if (this.tiles.length != 4) {
                Log.info("Incorrect tile count for Horizonal method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidVertical(String fname) {
            if ((this.tiles == null) || (this.tiles.length != 4)) {
                Log.info("Incorrect tile count for Vertical method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidRandom(String fname) {
            if ((this.tiles != null) && (this.tiles.length > 0)) {
                // Make sure same number of weights as tiles
                if ((this.weights != null) && (this.weights.length != this.tiles.length)) {
                    this.weights = null; // Ignore weights if not
                }
                // If weights, compute sums for faster lookup
                if (this.weights != null) {
                    this.sumWeights = new int[this.weights.length];
                    int sum = 0;
                    for (int i = 0; i < this.weights.length; i++) {
                        sum += this.weights[i];
                        this.sumWeights[i] = sum;
                    }
                    this.sumAllWeights = sum;
                }
                return true;
            }
            else {
                Log.info("Tiles required for Random method: " + fname);
                return false;
            }
        }
        private boolean isValidRepeat(String fname)
        {
            if (this.tiles == null) {
                Log.info("Tiles required for Repeat method: " + fname);
                return false;
            }
            // If valid width x height
            else if ((this.width > 0) && (this.width <= 16) && (this.height > 0) && (this.height <= 16)) {
                // If enough tiles for width x height
                if (this.tiles.length != this.width * this.height) {
                    Log.info("Number of tiles does not match repeat size: " + fname);
                    return false;
                }
                return true;
            }
            else {
                Log.info("Invalid dimensions for Repeat method: " + fname);
                return false;
            }
        }
        private boolean isValidFixed(String fname) {
            if ((this.tiles == null) || (this.tiles.length != 1)) {
                Log.info("Required 1 tile for Fixed method: " + fname);
                return false;
            }
            return true;
        }
        private boolean isValidTop(String fname) {
            if (this.tiles == null) {
                this.tiles = this.parseTileNames("66");
            }
            if (this.tiles.length != 1) {
                Log.info("Requires 1 tile for Top method: " + fname);
                return false;
            }
            return true;
        }
        private void registerTiles() {
            if (this.matchTiles != null) {  // If any matching tiles, register them
                this.matchTileIcons = registerTiles(this.matchTiles);
            }
            if (this.tiles != null) { // If any result tiles, register them
                this.tileIcons = registerTiles(this.tiles);
            }
        }
        private int[] registerTiles(String[] tilenames) {
            if (tilenames == null) return null;
            int[] rslt = new int[tilenames.length];
            for (int i = 0; i < tilenames.length; i++) {
                String tn = tilenames[i];
                String ftn = tn;
                if (ftn.indexOf('/') < 0) { // no path (base tile)
                    ftn = "textures/blocks/" + tn;
                }
                if (!ftn.endsWith(".png")) {
                    ftn = ftn + ".png"; // Add .png if needed
                }
                // Find file ID, add if needed
                int fid = TexturePack.findOrAddDynamicTileFile(ftn, 1, 1, TileFileFormat.GRID, new String[0]);
                rslt[i] = TexturePack.findOrAddDynamicTile(fid, 0); 
            }
            return rslt;
        }
    }
    
    /**
     * Constructor for CTP support, using ZIP based texture pack
     * @param zf - zip file
     * @param tp - texture pack
     */
    public CTMTexturePack(ZipFile zf, TexturePack tp) {
        ArrayList<String> files = new ArrayList<String>();
        texturezip = zf;
        @SuppressWarnings("unchecked")
        Enumeration<ZipEntry> iter = (Enumeration<ZipEntry>) zf.entries();
        while (iter.hasMoreElements()) {
            ZipEntry ze = iter.nextElement();
            String name = ze.getName(); /* Get file name */
            if(name.startsWith("ctm/") && name.endsWith(".properties")) {
                files.add(name);
            }
        }
        ctpfiles = files.toArray(new String[files.size()]);
        Arrays.sort(ctpfiles);
        processFiles();
    }
    /**
     * Constructor for CTP support, using directory-based texture pack
     * @param dir - base directory
     * @param tp - texture pack
     */
    public CTMTexturePack(File dir, TexturePack tp) {
        ArrayList<String> files = new ArrayList<String>();
        basedir = dir;
        File ctpdir = new File(dir, "ctm");
        if(ctpdir.isDirectory()) {
            addFiles(files, ctpdir, "ctm/");
        }
        ctpfiles = files.toArray(new String[files.size()]);
        Arrays.sort(ctpfiles);
        processFiles();
    }
    
    private void addFiles(ArrayList<String> files, File dir, String path) {
        File[] listfiles = dir.listFiles();
        if(listfiles == null) return;
        for(File f : listfiles) {
            String fn = f.getName();
            if(fn.equals(".") || (fn.equals(".."))) continue;
            if(f.isFile()) {
                if(fn.endsWith(".properties")) {
                    files.add(path + fn);
                }
            }
            else if(f.isDirectory()) {
                addFiles(files, f, path + f.getName() + "/");
            }
        }
    }
    /**
     * Test if enabled properly
     */
    public boolean isValid() {
        return (ctpfiles.length > 0);
    }
    
    private CTMProps[][] addToList(CTMProps[][] list, int[] keys, CTMProps p) {
        if (keys == null) return list;
        for (int k : keys) {
            if (k < 0) continue;
            if (k >= list.length) {
                list = Arrays.copyOf(list, k+1);
            }
            if (list[k] == null) {
                list[k] = new CTMProps[] { p };
            }
            else {
                int end = list[k].length;
                list[k] = Arrays.copyOf(list[k],  end + 1);
                list[k][end] = p;
            }
        }
        return list;
    }
    
    /**
     * Process property files
     */
    private void processFiles() {        
        bytilelist = new CTMProps[256][];
        byblocklist = new CTMProps[256][];
        
        for(String f : ctpfiles) {
            InputStream is = null;
            try {
                if(texturezip != null) {
                    ZipEntry ze = texturezip.getEntry(f);
                    if(ze != null) {
                        is = texturezip.getInputStream(ze);
                    }
                }
                else if (basedir != null) {
                    File pf = new File(basedir, f);
                    is = new FileInputStream(pf);
                }
                Properties p = new Properties();
                if(is != null) {
                    p.load(is);
                    
                    CTMProps ctmp = new CTMProps(p, f);
                    if(ctmp.isValid(f)) {
                        ctmp.registerTiles();
                        bytilelist = addToList(bytilelist, ctmp.matchTileIcons, ctmp);
                        byblocklist = addToList(byblocklist, ctmp.matchBlocks, ctmp);
                    }
                }
            } catch (IOException iox) {
                Log.severe("Cannot process CTM file - " + f, iox);
            } finally {
                if(is != null) {
                    try { is.close(); } catch (IOException iox) {}
                }
            }
        }
    }
    
    public int mapTexture(MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        int newtext = -1;
        if ((textid >= 0) && (textid < bytilelist.length)) {
            newtext = mapTextureByList(bytilelist[textid], mapiter, blkid, blkdata, laststep, textid);
        }
        if ((blkid > 0) && (blkid < byblocklist.length)) {
            newtext = mapTextureByList(byblocklist[blkid], mapiter, blkid, blkdata, laststep, textid);
        }
        if (newtext >= 0) {
            textid = newtext;
        }
        return textid;
    }
    
    private int mapTextureByList(CTMProps[] lst, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        if (lst == null) return -1;
        for (CTMProps p : lst) {
            if (p == null) continue;
            int newtxt = mapTextureByProp(p, mapiter, blkid, blkdata, laststep, textid);
            if (newtxt >= 0) {
                return newtxt;
            }
        }
        return -1;
    }
    // Adjust side for facing of a log
    private int fixWoodSide(int face, int meta) {
        int dir = (meta & 0xC) >> 2;
        switch (dir) {
            case 0: // No adjustement needed
                return face;
            case 1:
                switch (face) {
                    case 0:
                        return 4;
                    case 1:
                        return 5;
                    case 4:
                        return 1;
                    case 5:
                        return 0;
                    default:
                        return face;
                }
            case 2:
                switch (face) {
                    case 0:
                        return 2;
                    case 1:
                        return 3;
                    case 2:
                        return 1;
                    case 3:
                        return 0;
                    default:
                        return face;
                }
            case 3:
                return 2;
            default:
                return face;
        }
    }

    private int mapTextureByProp(CTMProps p, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        //TODO - need way to know if log (to rotate textures)
        boolean islog = (blkid == 17);
        
        // Test if right face
        if ((laststep != null) && (p.faces != FACE_ALL)) {
            int face = laststep.getFaceEntered();
            if (islog) {
                face = fixWoodSide(face, blkdata);
            }
            // If not handled side
            if ((p.faces & (1 << face)) == 0) {
                return -1;
            }
        }
        // Test if right metadata
        if (p.metadatas != null) {
            int meta = blkdata;
            if (islog) {
                meta = meta & 0x3;
            }
            boolean match = false;
            for (int m : p.metadatas) {
                if (m == meta) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                return -1;
            }
        }
        // Rest of it is based on method
        switch (p.method) {
            case CTM:
                return mapTextureCtm(p, mapiter, blkid, blkdata, laststep, textid);

            case HORIZONTAL:
                return mapTextureHorizontal(p, mapiter, blkid, blkdata, laststep, textid);

            case TOP:
                return mapTextureTop(p, mapiter, blkid, blkdata, laststep, textid);

            case RANDOM:
                return mapTextureRandom(p, mapiter, blkid, blkdata, laststep, textid);

            case REPEAT:
                return mapTextureRepeat(p, mapiter, blkid, blkdata, laststep, textid);

            case VERTICAL:
                return mapTextureVertical(p, mapiter, blkid, blkdata, laststep, textid);

            case FIXED:
                return mapTextureFixed(p, mapiter, blkid, blkdata, laststep, textid);

            default:
                return -1;
        }
    }
    // Map texture using CTM method
    private int mapTextureCtm(CTMProps p, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        return -1;
    }
    // Map texture using horizontal method
    private int mapTextureHorizontal(CTMProps p, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        return -1;
    }
    // Map texture using top method
    private int mapTextureTop(CTMProps p, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        return -1;
    }
    // Map texture using random method
    private int mapTextureRandom(CTMProps p, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        if (p.tileIcons.length == 1) { // Only one?
            return p.tileIcons[0];
        }
        int face = laststep.getFaceEntered();
        // Apply symmetry
        int facesym = (face / p.symmetry.shift) * p.symmetry.shift;
        int rnd = getRandom(mapiter.getX(), mapiter.getY(), mapiter.getZ(), facesym) & Integer.MAX_VALUE;
        int index = 0;
        // If no weights, consistent weight
        if (p.weights == null) {
            index = rnd % p.tileIcons.length;
        }
        else {
            rnd = rnd % p.sumAllWeights;
            int[] w = p.sumWeights;
            // Find which range matches
            for (int i = 0; i < w.length; ++i) {
                if (rnd < w[i]) {
                    index = i;
                    break;
                }
            }
        }
        return p.tileIcons[index];
    }
    // Map texture using repeat method
    private int mapTextureRepeat(CTMProps p, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        if (p.tileIcons.length == 1) {  // Only one icon
            return p.tileIcons[0];
        }
        else {
            int xx = 0;
            int yy = 0;

            switch (laststep.getFaceEntered()) {
                case 0:
                    xx = mapiter.getX();
                    yy = mapiter.getZ();
                    break;
                case 1:
                    xx = mapiter.getX();
                    yy = mapiter.getZ();
                    break;
                case 2:
                    xx = -mapiter.getX() - 1;
                    yy = -mapiter.getY();
                    break;
                case 3:
                    xx = mapiter.getX();
                    yy = -mapiter.getY();
                    break;
                case 4:
                    xx = mapiter.getZ();
                    yy = -mapiter.getY();
                    break;
                case 5:
                    xx = -mapiter.getZ() - 1;
                    yy = -mapiter.getY();
                    break;
            }
            // Compute wrap
            xx %= p.width;
            yy %= p.height;
            if (xx < 0) {
                xx += p.width;
            }
            if (yy < 0) {
                yy += p.height;
            }
            int index = yy * p.width + xx;
            return p.tileIcons[index];
        }
    }
    // Map texture using vertical method
    private int mapTextureVertical(CTMProps p, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        return -1;
    }
    // Map texture using fixed method
    private int mapTextureFixed(CTMProps p, MapIterator mapiter, int blkid, int blkdata, BlockStep laststep, int textid) {
        return p.tileIcons[0];
    }

    // Compute pseudorandom value from coords
    private static final int intHash(int v)
    {
        v = v ^ 61 ^ v >> 16;
        v += v << 3;
        v ^= v >> 4;
        v *= 668265261;
        v ^= v >> 15;
        return v;
    }
    private static final int getRandom(int x, int y, int z, int face)
    {
        int v = intHash(face + 37);
        v = intHash(v + x);
        v = intHash(v + z);
        v = intHash(v + y);
        return v;
    }
}

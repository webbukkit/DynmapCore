package org.dynmap.hdmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
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
    private String[] blocknames;
    private String[] biomenames;
    private static final int MAX_RECURSE = 4;

    static final int BOTTOM_FACE = 0; // 0, -1, 0
    static final int TOP_FACE = 1; // 0, 1, 0
    static final int NORTH_FACE = 2; // 0, 0, -1
    static final int SOUTH_FACE = 3; // 0, 0, 1
    static final int WEST_FACE = 4; // -1, 0, 0
    static final int EAST_FACE = 5; // 1, 0, 0

    private static final int META_MASK = 0xffff;
    private static final int ORIENTATION_U_D = 0;
    private static final int ORIENTATION_E_W = 1 << 16;
    private static final int ORIENTATION_N_S = 2 << 16;
    private static final int ORIENTATION_E_W_2 = 3 << 16;
    private static final int ORIENTATION_N_S_2 = 4 << 16;

    private static final int[][] ROTATE_UV_MAP = new int[][]{
        {WEST_FACE, EAST_FACE, NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, 2, -2, 2, -2, 0, 0},
        {NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, WEST_FACE, EAST_FACE, 0, 0, 0, 0, -2, 2},
        {WEST_FACE, EAST_FACE, NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, 2, -2, -2, -2, 0, 0},
        {NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, WEST_FACE, EAST_FACE, 0, 0, 0, 0, -2, -2},
    };

    private static final int[] GO_DOWN = new int[]{0, -1, 0};
    private static final int[] GO_UP = new int[]{0, 1, 0};
    private static final int[] GO_NORTH = new int[]{0, 0, -1};
    private static final int[] GO_SOUTH = new int[]{0, 0, 1};
    private static final int[] GO_WEST = new int[]{-1, 0, 0};
    private static final int[] GO_EAST = new int[]{1, 0, 0};

    private static final int[][] NORMALS = new int[][]{
        GO_DOWN,
        GO_UP,
        GO_NORTH,
        GO_SOUTH,
        GO_WEST,
        GO_EAST,
    };

    // NEIGHBOR_OFFSETS[a][b][c] = offset from starting block
    // a: face 0-5
    // b: neighbor 0-7
    //    7   6   5
    //    0   *   4
    //    1   2   3
    // c: coordinate (x,y,z) 0-2
    protected static final int[][][] NEIGHBOR_OFFSET = new int[][][]{
        // BOTTOM_FACE
        {
            GO_WEST,
            add(GO_WEST, GO_SOUTH),
            GO_SOUTH,
            add(GO_EAST, GO_SOUTH),
            GO_EAST,
            add(GO_EAST, GO_NORTH),
            GO_NORTH,
            add(GO_WEST, GO_NORTH),
        },
        // TOP_FACE
        {
            GO_WEST,
            add(GO_WEST, GO_SOUTH),
            GO_SOUTH,
            add(GO_EAST, GO_SOUTH),
            GO_EAST,
            add(GO_EAST, GO_NORTH),
            GO_NORTH,
            add(GO_WEST, GO_NORTH),
        },
        // NORTH_FACE
        {
            GO_EAST,
            add(GO_EAST, GO_DOWN),
            GO_DOWN,
            add(GO_WEST, GO_DOWN),
            GO_WEST,
            add(GO_WEST, GO_UP),
            GO_UP,
            add(GO_EAST, GO_UP),
        },
        // SOUTH_FACE
        {
            GO_WEST,
            add(GO_WEST, GO_DOWN),
            GO_DOWN,
            add(GO_EAST, GO_DOWN),
            GO_EAST,
            add(GO_EAST, GO_UP),
            GO_UP,
            add(GO_WEST, GO_UP),
        },
        // WEST_FACE
        {
            GO_NORTH,
            add(GO_NORTH, GO_DOWN),
            GO_DOWN,
            add(GO_SOUTH, GO_DOWN),
            GO_SOUTH,
            add(GO_SOUTH, GO_UP),
            GO_UP,
            add(GO_NORTH, GO_UP),
        },
        // EAST_FACE
        {
            GO_SOUTH,
            add(GO_SOUTH, GO_DOWN),
            GO_DOWN,
            add(GO_NORTH, GO_DOWN),
            GO_NORTH,
            add(GO_NORTH, GO_UP),
            GO_UP,
            add(GO_SOUTH, GO_UP),
        },
    };

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
        ALL(6);
        
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
        public int metadata = -1;
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
            String v = p.getProperty("faces", "all").trim().toLowerCase();
            this.faces = 0;
            String[] tok = v.split("\\s+");
            for(String t : tok) {
                t = t.toLowerCase();
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
                else if (t.equals("sides") || t.equals("side")) {
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
        
        private int[] getIDList(Properties properties, String key, String type, String[] mappings) {
            Set<Integer> list = new HashSet<Integer>();
            String property = properties.getProperty(key, "");
            for (String token : property.split("\\s+")) {
                if (token.equals("")) {
                } else if (token.matches("\\d+")) {
                    try {
                        int id = Integer.parseInt(token);
                        list.add(id);
                    } catch (NumberFormatException e) {
                        Log.info("Bad ID token: " + token);
                    }
                } else { // String mapping - look for block name
                    for (int i = 0; i < mappings.length; i++) {
                        if (token.equals(mappings[i])) {
                            list.add(i);
                            break;
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                Matcher m = Pattern.compile(type + "(\\d+)").matcher(name);
                if (m.find()) {
                    try {
                        list.add(Integer.parseInt(m.group(1)));
                    } catch (NumberFormatException e) {
                        Log.info("Bad block number: " + name);
                    }
                }
            }
            /* Make set into list */
            int[] rslt = new int[list.size()];
            int i = 0;
            for(Integer v : list) {
                rslt[i] = v;
                i++;
            }
            
            return rslt;
        }

        private void getMethod(Properties p) {
            String v = p.getProperty("method", "default").trim().toLowerCase();
            if (v.equals("ctm") || v.equals("glass") || v.equals("default")) {
                method = CTMMethod.CTM;
            }
            else if (v.equals("horizontal") || v.equals("bookshelf")) {
                method = CTMMethod.HORIZONTAL;
            }
            else if (v.equals("vertical")) {
                method = CTMMethod.VERTICAL;
            }
            else if (v.equals("top") || v.equals("sandstone")) {
                method = CTMMethod.TOP;
            }
            else if (v.equals("random")) {
                method = CTMMethod.RANDOM;
            }
            else if (v.equals("repeat") || v.equals("pattern")) {
                method = CTMMethod.REPEAT;
            }
            else if (v.equals("fixed") || v.equals("static")) {
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
        private void getBiomes(Properties p, CTMTexturePack tp) {
            String v = p.getProperty("biomes", "").trim().toLowerCase();
            if (!v.equals("")) {
                ArrayList<Integer> ids = new ArrayList<Integer>();
                String[] biomenames = tp.biomenames;
                for(String s : v.split("\\s+")) {
                    for(int i = 0; i < biomenames.length; i++) {
                        if(s.equals(biomenames[i])) {
                            ids.add(i);
                            s = null;
                            break;
                        }
                    }
                    if(s != null) {
                        Log.info("CTP Biome not matched: " + s);
                    }
                }
                this.biomes = new int[ids.size()];
                for(int i = 0; i < this.biomes.length; i++) {
                    this.biomes[i] = ids.get(i);
                }
            }
            else {
                this.biomes = null;
            }
        }
        private void getSymmetry(Properties p) {
            String v = p.getProperty("symmetry", "none").trim().toLowerCase();
            if (v.equals("none")) {
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
        
        public CTMProps(Properties p, String fname, CTMTexturePack tp) {
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
            this.matchBlocks = getIDList(p, "matchBlocks", "block", tp.blocknames); 
            getMatchTiles(p);
            getMethod(p);
            this.tiles = parseTileNames(p.getProperty("tiles"));
            getConnect(p);
            getFaces(p);
            getSymmetry(p);
            getBiomes(p, tp);

            int[] md = parseInts(p, "metadata");
            if (md != null) {
                this.metadata = 0;
                for(int m : md) {
                    this.metadata |= (1 << m);
                }
            }
            this.minY = parseInt(p, "minHeight", -1);
            this.maxY = parseInt(p, "maxHeight", Integer.MAX_VALUE);
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
            // If no match blocks nor tiles, assume name is tile
            if ((this.matchBlocks == null) && (this.matchTiles == null)) {
                this.matchTiles = new String[] { name };
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
    public CTMTexturePack(ZipFile zf, TexturePack tp, DynmapCore core) {
        ArrayList<String> files = new ArrayList<String>();
        texturezip = zf;
        blocknames = core.getBlockNames();
        biomenames = core.getBiomeNames();
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
        processFiles(core);
    }
    /**
     * Constructor for CTP support, using directory-based texture pack
     * @param dir - base directory
     * @param tp - texture pack
     */
    public CTMTexturePack(File dir, TexturePack tp, DynmapCore core) {
        ArrayList<String> files = new ArrayList<String>();
        basedir = dir;
        blocknames = core.getBlockNames();
        biomenames = core.getBiomeNames();
        File ctpdir = new File(dir, "ctm");
        if(ctpdir.isDirectory()) {
            addFiles(files, ctpdir, "ctm/");
        }
        ctpfiles = files.toArray(new String[files.size()]);
        Arrays.sort(ctpfiles);
        processFiles(core);
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
    private void processFiles(DynmapCore core) {        
        bytilelist = new CTMProps[256][];
        byblocklist = new CTMProps[256][];

        /* Fix biome names - all lower case, and strip spaces */
        String[] newbiomes = new String[biomenames.length];
        for(int i = 0; i < biomenames.length; i++) {
            if(biomenames[i] != null)
                newbiomes[i] = biomenames[i].toLowerCase().replace(" ", "");
            else
                biomenames[i] = "";
        }
        biomenames = newbiomes;
        
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
                    
                    CTMProps ctmp = new CTMProps(p, f, this);
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
        if (p.metadata != -1) {
            int meta = blkdata;
            if (islog) {
                meta = meta & 0x3;
            }
            if ((p.metadata & (1 << meta)) == 0) {
                return -1;
            }
        }
        // Test if Y coordinate is valid
        int y = mapiter.getY();
        if ((y < p.minY) || (y > p.maxY)) {
            return -1;
        }
        // Test if biome is valid
        if (p.biomes != null) {
            int ord = -1; 
            BiomeMap bio = mapiter.getBiome();
            if (bio != null) {
                ord = bio.ordinal();
            }
            for(int i = 0; i < p.biomes.length; i++) {
                if (p.biomes[i] == ord) {
                    ord = -2;
                    break;
                }
            }
            if(ord != -2) {
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
        int facesym = face / p.symmetry.shift;
        int index = 0;
        // If no weights, consistent weight
        if (p.weights == null) {
            index = getRandom(mapiter.getX(), mapiter.getY(), mapiter.getZ(), facesym, p.tileIcons.length);
        }
        else {
            int rnd = getRandom(mapiter.getX(), mapiter.getY(), mapiter.getZ(), facesym, p.sumAllWeights);
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

    private static final long P1 = 0x1c3764a30115L;
    private static final long P2 = 0x227c1adccd1dL;
    private static final long P3 = 0xe0d251c03ba5L;
    private static final long P4 = 0xa2fb1377aeb3L;
    private static final long MULTIPLIER = 0x5deece66dL;
    private static final long ADDEND = 0xbL;

    private static final int getRandom(int x, int y, int z, int face, int modulus)
    {
        long n = P1 * x * (x + ADDEND) + P2 * y * (y + ADDEND) + P3 * z * (z + ADDEND) + P4 * face * (face + ADDEND);
        n = MULTIPLIER * (n + x + y + z + face) + ADDEND;
        return (int) (((n >> 32) ^ n) & 0x7fffffff) % modulus;
    }
    
    private static int[] add(int[] a, int[] b) {
        if (a.length != b.length) {
            throw new RuntimeException("arrays to add are not same length");
        }
        int[] c = new int[a.length];
        for (int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
        return c;
    }

}

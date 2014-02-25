package org.dynmap.exporter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.HDShader;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.hdmap.HDBlockModels.CustomBlockModel;
import org.dynmap.hdmap.HDBlockModels.HDScaledBlockModels;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.IndexedVector3D;
import org.dynmap.utils.IndexedVector3DList;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

public class OBJExport {
    private final File destZipFile;     // Destination ZIP file
    private final HDShader shader;      // Shader to be used for textures
    private final DynmapWorld world;    // World to be rendered
    private final DynmapCore core;
    private int minX, minY, minZ;       // Minimum world coordinates to be rendered
    private int maxX, maxY, maxZ;       // Maximum world coordinates to be rendered
    private static Charset UTF8 = Charset.forName("UTF-8");
    private ZipOutputStream zos;        // Output stream ZIP for result
    private double originX, originY, originZ;   // Origin for exported model
    private double scale = 1.0;         // Scale for exported model
    private PatchDefinition[] defaultPathces;   // Default patches for solid block, indexed by BlockStep.ordinal()
    private String curMaterial = "";
    private HashSet<String> matIDs = new HashSet<String>();     // Set of defined material ids for RP
    
    // Vertex set
    private IndexedVector3DList vertices;
    // UV set
    private IndexedVector3DList uvs;
    // Scaled models
    private HDScaledBlockModels models;
    
    private static final double[][] pp = {
        { 0, 0, 0, 1, 0, 0, 0, 0, 1 },
        { 0, 1, 1, 1, 1, 1, 0, 1, 0 },
        { 1, 0, 0, 0, 0, 0, 1, 1, 0 },
        { 0, 0, 1, 1, 0, 1, 0, 1, 1 },
        { 0, 0, 0, 0, 0, 1, 0, 1, 0 },
        { 1, 0, 1, 1, 0, 0, 1, 1, 1 }
    };
    
    /**
     * Constructor for OBJ file export
     * @param dest - destination file (ZIP)
     * @param shader - shader to be used for coloring/texturing
     * @param world - world to be rendered
     */
    public OBJExport(File dest, HDShader shader, DynmapWorld world, DynmapCore core) {
        destZipFile = dest;
        this.shader = shader;
        this.world = world;
        this.core = core;
        this.defaultPathces = new PatchDefinition[6];
        PatchDefinitionFactory fact = HDBlockModels.getPatchDefinitionFactory();
        for (BlockStep s : BlockStep.values()) {
            double[] p = pp[s.getFaceEntered()];
            int ord = s.ordinal();
            defaultPathces[ord] = fact.getPatch(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], 0, 1, 0, 1, 100, SideVisible.TOP, ord);
        }
        vertices = new IndexedVector3DList(new IndexedVector3DList.ListCallback() {
            @Override
            public void elementAdded(IndexedVector3DList list, IndexedVector3D newElement) {
                try {
                    /* Minecraft XYZ maps to OBJ YZX */
                    addStringToExportedFile(String.format("v %.4f %.4f %.4f\n", 
                            (newElement.x - originX) * scale,
                            (newElement.y - originY) * scale,
                            (newElement.z - originZ) * scale
                            ));
                } catch (IOException iox) {
                }
            }
        });
        uvs = new IndexedVector3DList(new IndexedVector3DList.ListCallback() {
            @Override
            public void elementAdded(IndexedVector3DList list, IndexedVector3D newElement) {
                try {
                    addStringToExportedFile(String.format("vt %.4f %.4f\n", newElement.x, newElement.y));
                } catch (IOException iox) {
                }
            }
        });
        // Get models
        models = HDBlockModels.getModelsForScale(16);
    }
    /**
     * Set render bounds
     * 
     * @param minx - minimum X coord
     * @param miny - minimum Y coord
     * @param minz - minimum Z coord
     * @param maxx - maximum X coord
     * @param maxy - maximum Y coord
     * @param maxz - maximum Z coord
     */
    public void setRenderBounds(int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        // Force X and Z constraints to chunk boundaries
        minx = minx & 0xFFFFFFF0;
        minz = minz & 0xFFFFFFF0;
        maxx = maxx | 0x0000000F;
        maxz = maxz | 0x0000000F;
        if (minx < maxx) {
            minX = minx; maxX = maxx;
        }
        else {
            minX = maxx; maxX = minx;
        }
        if (miny < maxy) {
            minY = miny; maxY = maxy;
        }
        else {
            minY = maxy; maxY = miny;
        }
        if (minz < maxz) {
            minZ = minz; maxZ = maxz;
        }
        else {
            minZ = maxz; maxZ = minz;
        }
        if (minY < 0) minY = 0;
        if (maxY >= world.worldheight) maxY = world.worldheight - 1;
        if ((originX == 0.0) && (originY == 0.0) && (originZ == 0.0)) {
            originX = (maxX + minX) / 2.0;
            originY = (maxY + minY) / 2.0;
            originZ = (maxZ + minZ) / 2.0;
        }
    }
    /**
     * Set origin for exported model
     * @param ox - origin x
     * @param oy - origin y
     * @param oz - origin z
     */
    public void setOrigin(double ox, double oy, double oz) {
        originX = ox;
        originY = oy;
        originZ = oz;
    }
    /**
     * Set scale for exported model
     * @param scale = scale
     */
    public void setScale(double scale) {
        this.scale = scale;
    }
    /**
     * Process export
     * 
     * @param sennder - command sender: use for feedback messages
     * @return true if successful, false if not
     */
    public boolean processExport(DynmapCommandSender sender) {
        boolean good = false;
        try {
            // Open ZIP file destination
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destZipFile)));
            
            List<DynmapChunk> requiredChunks = new ArrayList<DynmapChunk>();
            int mincx = (minX >> 4);
            int maxcx = (maxX + 15) >> 4;
            int mincz = (minZ >> 4);
            int maxcz = (maxZ + 15) >> 4;

            startExportedFile("minecraft.obj");
            // Add material library
            addStringToExportedFile("mtllib " + shader.getName() + ".mtl\n");

            // Loop through - do 8x8 chunks at a time (plus 1 border each way)
            for (int cx = mincx; cx <= maxcx; cx += 4) {
                for (int cz = mincz; cz <= maxcz; cz += 4) {
                    // Build chunk cache for block of chunks
                    requiredChunks.clear();
                    for (int i = -1; i < 5; i++) {
                        for (int j = -1; j < 5; j++) {
                            if (((cx+i+1) <= maxcx) && ((cz+j+1) <= maxcz)) {
                                requiredChunks.add(new DynmapChunk(cx + i, cz + j));
                            }
                        }
                    }
                    // Get the chunk buffer
                    MapChunkCache cache = core.getServer().createMapChunkCache(world, requiredChunks, true, false, true, false);
                    if (cache == null) {
                        throw new IOException("Error loading chunk cache");
                    }
                    MapIterator iter = cache.getIterator(minX, minY, minZ);
                    for (int x = cx * 16; (x < (cx * 16 + 64)) && (x <= maxX); x++) {
                        for (int z = cz * 16; (z < (cz * 16 + 64)) && (z <= maxZ); z++) {
                            iter.initialize(x, minY, z);
                            for (int y = minY; y <= maxY; y++) {
                                iter.setY(y);
                                int id = iter.getBlockTypeID();
                                if (id > 0) {  // Not air
                                    handleBlock(id, iter);
                                }
                            }
                        }
                    }
                    // Clean up vertices we've moved past
                    vertices.resetSet(minX, minY, minZ, cx * 16 + 64, maxY, cz * 16 + 64);
                }
            }
            finishExportedFile();
            // If shader provided, add shader content to ZIP
            if (shader != null) {
                sender.sendMessage("Adding textures from shader " + shader.getName());
                shader.exportAsMaterialLibrary(sender, this);
                sender.sendMessage("Texture export completed");
            }
            // And close the ZIP
            zos.finish();
            zos.close();
            zos = null;
            good = true;
            sender.sendMessage("Export completed - " + destZipFile.getPath());
        } catch (IOException iox) {
            sender.sendMessage("Export failed: " + iox.getMessage());
        } finally {
            if (zos != null) {
                try { zos.close(); } catch (IOException e) {}
                zos = null;
                destZipFile.delete();
            }
        }
        return good;
    }
    /**
     * Start adding file to export
     * @param fname - path/name of file in destination zip
     * @throws IOException if error starting file
     */
    public void startExportedFile(String fname) throws IOException {
        ZipEntry ze = new ZipEntry(fname);
        zos.putNextEntry(ze);
    }
    /**
     * Add bytes to current exported file
     * @param buf - buffer with bytes
     * @param off - offset of start
     * @param len - length to be added
     * @throws IOException if error adding to file
     */
    public void addBytesToExportedFile(byte[] buf, int off, int len) throws IOException {
        zos.write(buf, off, len);
    }
    /**
     * Add string to curent exported file (UTF-8)
     * @param str - string to be written
     * @throws IOException if error adding to file
     */
    public void addStringToExportedFile(String str) throws IOException {
        byte[] b = str.getBytes(UTF8);
        zos.write(b, 0, b.length);
    }
    /**
     * Finish adding file to export
     * @throws IOException if error completing file
     */
    public void finishExportedFile() throws IOException {
        zos.closeEntry();
    }
    /**
     * Handle block at current iterator coord
     * @param id - block ID
     * @param iter - iterator
     */
    private void handleBlock(int blkid, MapIterator map) throws IOException {
        int data = map.getBlockData();             
        int renderdata = HDBlockModels.getBlockRenderData(blkid, map);  // Get render data, if needed
        // Get materials for patches
        String[] mats = shader.getCurrentBlockMaterials(blkid, data, renderdata, map);
        // See if the block has a patch model
        RenderPatch[] patches = models.getPatchModel(blkid,  data,  renderdata);
        /* If no patches, see if custom model */
        if(patches == null) {
            CustomBlockModel cbm = models.getCustomBlockModel(blkid,  data);
            if(cbm != null) {   /* If so, get our meshes */
                patches = cbm.getMeshForBlock(map);
            }
        }
        if (patches != null) {  // Patch based model?
            for (RenderPatch p : patches) {
                addPatch((PatchDefinition) p, map.getX(), map.getY(), map.getZ(), mats);
            }
        }
        else {
            boolean opaque = TexturePack.HDTextureMap.getTransparency(blkid) == BlockTransparency.OPAQUE;
            for (BlockStep s : BlockStep.values()) {
                int id2 = map.getBlockTypeIDAt(s.opposite());  // Get block in direction
                // If we're not solid, or adjacent block is not solid, draw side
                if ((!opaque) || (id2 == 0) || (TexturePack.HDTextureMap.getTransparency(id2) != BlockTransparency.OPAQUE)) {
                    addPatch(defaultPathces[s.ordinal()], map.getX(), map.getY(), map.getZ(), mats);
                }
            }
        }
    }
    /**
     * Add patch as face to output
     */
    private void addPatch(PatchDefinition pd, double x, double y, double z, String[] mats) throws IOException {
        // Look up material
        String material = null;
        if ((mats != null) && (mats.length > pd.textureindex)) {
            material = mats[pd.textureindex];
        }
        // No material?  No face
        if (material == null) {
            return;
        }
        int[] v = new int[4];
        int[] uv = new int[4];
        // Get offsets for U and V from origin
        double ux = pd.xu - pd.x0;
        double uy = pd.yu - pd.y0;
        double uz = pd.zu - pd.z0;
        double vx = pd.xv - pd.x0;
        double vy = pd.yv - pd.y0;
        double vz = pd.zv - pd.z0;
        // Offset to origin corner
        x = x + pd.x0;
        y = y + pd.y0;
        z = z + pd.z0;
        // Origin corner, offset by umin, vmin
        v[0] = vertices.getVectorIndex(x + ux*pd.umin + vx*pd.vmin, y + uy*pd.umin + vy*pd.vmin, z + uz*pd.umin + vz*pd.vmin);
        uv[0] = uvs.getVectorIndex(pd.umin, pd.vmin, 0);
        // Second is end of U (umax, vmin)
        v[1] = vertices.getVectorIndex(x + ux*pd.umax + vx*pd.vmin, y + uy*pd.umax + vy*pd.vmin, z + uz*pd.umax + vz*pd.vmin);
        uv[1] = uvs.getVectorIndex(pd.umax, pd.vmin, 0);
        // Third is end of U+V (umax, vmax)
        v[2] = vertices.getVectorIndex(x + ux*pd.umax + vx*pd.vmax, y + uy*pd.umax + vy*pd.vmax, z + uz*pd.umax + vz*pd.vmax);
        uv[2] = uvs.getVectorIndex(pd.umax, pd.vmax, 0);
        // Forth is end of V (umin, vmax)
        v[3] = vertices.getVectorIndex(x + ux*pd.umin + vx*pd.vmax, y + uy*pd.umin + vy*pd.vmax, z + uz*pd.umin + vz*pd.vmax);
        uv[3] = uvs.getVectorIndex(pd.umin, pd.vmax, 0);
        // Add patch to file
        addPatchToFile(v, uv, pd.sidevis, material);
    }
    private void addPatchToFile(int[] v, int[] uv, SideVisible sv, String material) throws IOException {
        if (curMaterial.equals(material) == false) {
            matIDs.add(material);   // Record material use
            addStringToExportedFile(String.format("usemtl %s\n", material)); 
            curMaterial = material;
        }
        switch (sv) {
            case TOP:
                addStringToExportedFile(String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[0], uv[0], v[1], uv[1], v[2], uv[2], v[3], uv[3])); 
                break;
            case BOTTOM:
                addStringToExportedFile(String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[3], uv[3], v[2], uv[2], v[1], uv[1], v[0], uv[0])); 
                break;
            case BOTH:
                addStringToExportedFile(String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[0], uv[0], v[1], uv[1], v[2], uv[2], v[3], uv[3])); 
                addStringToExportedFile(String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[3], uv[3], v[2], uv[2], v[1], uv[1], v[0], uv[0])); 
                break;
            case FLIP:
                addStringToExportedFile(String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[0], uv[0], v[1], uv[1], v[2], uv[2], v[3], uv[3])); 
                addStringToExportedFile(String.format("f %d/%d %d/%d %d/%d %d/%d\n", v[3], uv[2], v[2], uv[3], v[1], uv[0], v[0], uv[1])); 
                break;
        }
    }
    
    public Set<String> getMaterialIDs() {
        return matIDs;
    }
}

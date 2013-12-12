package org.dynmap.modsupport.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.modsupport.BoxBlockModel;
import org.dynmap.modsupport.CuboidBlockModel;
import org.dynmap.modsupport.ModModelDefinition;
import org.dynmap.modsupport.ModTextureDefinition;
import org.dynmap.modsupport.PaneBlockModel;
import org.dynmap.modsupport.PlantBlockModel;
import org.dynmap.modsupport.StairBlockModel;
import org.dynmap.modsupport.VolumetricBlockModel;
import org.dynmap.modsupport.WallFenceBlockModel;
import org.dynmap.modsupport.WallFenceBlockModel.FenceType;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

public class ModModelDefinitionImpl implements ModModelDefinition {
    private final ModTextureDefinitionImpl txtDef;
    private boolean published = false;
    private ArrayList<BlockModelImpl> blkModel = new ArrayList<BlockModelImpl>();
    private ArrayList<PatchDefinition> blkPatch = new ArrayList<PatchDefinition>();
    private PatchDefinitionFactory pdf;
    
    public ModModelDefinitionImpl(ModTextureDefinitionImpl txtDef) {
        this.txtDef = txtDef;
        this.pdf = HDBlockModels.getPatchDefinitionFactory();
    }
    
    @Override
    public String getModID() {
        return txtDef.getModID();
    }

    @Override
    public String getModVersion() {
        return txtDef.getModVersion();
    }

    @Override
    public ModTextureDefinition getTextureDefinition() {
        return txtDef;
    }

    @Override
    public boolean publishDefinition() {
        published = true;
        return true;
    }

    @Override
    public VolumetricBlockModel addVolumetricModel(int blockid, int scale) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public StairBlockModel addStairModel(int blockid) {
        StairBlockModelImpl mod = new StairBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public WallFenceBlockModel addWallFenceModel(int blockid, FenceType type) {
        WallFenceBlockModelImpl mod = new WallFenceBlockModelImpl(blockid, this, type);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public CuboidBlockModel addCuboidModel(int blockid) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public PaneBlockModel addPaneModel(int blockid) {
        PaneBlockModelImpl mod = new PaneBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public PlantBlockModel addPlantModel(int blockid) {
        PlantBlockModelImpl mod = new PlantBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }

    @Override
    public BoxBlockModel addBoxModel(int blockid) {
        BoxBlockModelImpl mod = new BoxBlockModelImpl(blockid, this);
        blkModel.add(mod);
        return mod;
    }

    public String getPatchID(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, double uplusvmax, SideVisible sidevis,
            int textureids) {
        PatchDefinition pd = pdf.getPatch(x0, y0, z0, xu, yu, zu, xv, yv, zv, umin, umax, vmin, vmax, uplusvmax, sidevis, textureids);
        for (int i = 0; i < blkPatch.size(); i++) {
            if (blkPatch.get(i) == pd) { return "patch" + i; }
        }
        blkPatch.add(pd);
        return "patch" + (blkPatch.size() - 1);
    }

    public boolean isPublished() {
        return published;
    }

    public void writeToFile(File destdir) throws IOException {
        if (blkModel.isEmpty()) {
            return;
        }
        File f = new File(destdir, this.txtDef.getModID() + "-models.txt");
        FileWriter fw = null;
        try {
            fw = new FileWriter(f);
            // Write modname line
            String s = "modname:" + this.txtDef.getModID();
            fw.write(s + "\n\n");
            // Loop through patch definitions
            for (int i = 0; i < blkPatch.size(); i++) {
                PatchDefinition pd = blkPatch.get(i);
                String line = String.format("patch:id=patch%d,Ox=%f,Oy=%f,Oz=%f,Ux=%f,Uy=%f,Uz=%f,Vx=%f,Vy=%f,Vz=%f,Umin=%f,Umax=%f,Vmin=%f,Vmax=%f,UplusVmax=%f",
                        i, pd.x0, pd.y0, pd.z0, pd.xu, pd.yu, pd.zu, pd.xv, pd.yv, pd.zv, pd.umin, pd.umax, pd.vmin, pd.vmax, pd.uplusvmax);
                switch (pd.sidevis) {
                    case BOTTOM:
                        line += ",visibility=bottom";
                        break;
                    case TOP:
                        line += ",visibility=top";
                        break;
                    case FLIP:
                        line += ",visibility=flip";
                        break;
                    case BOTH:
                        break;
                }
                if (line != null) {
                    fw.write(line + "\n");
                }
            }
            // Loop through block texture records
            for (BlockModelImpl btr : blkModel) {
                String line = btr.getLine();
                if (line != null) {
                    fw.write(line + "\n");
                }
            }
        } finally {
            if (fw != null) {
                fw.close(); 
            }
        }        
    }
}

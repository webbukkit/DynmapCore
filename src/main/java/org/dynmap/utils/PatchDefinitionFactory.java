package org.dynmap.utils;

import java.util.HashMap;

import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class PatchDefinitionFactory implements RenderPatchFactory {
    private HashMap<PatchDefinition,PatchDefinition> patches = new HashMap<PatchDefinition,PatchDefinition>();
    private Object lock = new Object();
    private PatchDefinition lookup = new PatchDefinition();
    
    public PatchDefinitionFactory() {
        
    }

    @Override
    public RenderPatch getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, SideVisible sidevis,
            int textureids) {
        return getPatch(x0, y0, z0, xu, yu, zu,xv, yv, zv, umin, umax, vmin, vmax, 100.0, sidevis, textureids);
    }

    @Override
    public RenderPatch getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv,
            double uplusvmax, SideVisible sidevis, int textureids) {
        return getPatch(x0, y0, z0, xu, yu, zu,xv, yv, zv, 0.0, 1.0, 0.0, 1.0, uplusvmax, sidevis, textureids);
    }

    public PatchDefinition getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, double uplusvmax, SideVisible sidevis,
            int textureids) {
        synchronized(lock) {
            lookup.update(x0, y0, z0, xu, yu, zu, xv, yv, zv, umin,
                    umax, vmin, vmax, uplusvmax, sidevis, textureids);
            if(lookup.validate() == false)
                return null;
            PatchDefinition pd2 = patches.get(lookup);  /* See if in cache already */
            if(pd2 == null) {
                PatchDefinition pd = new PatchDefinition(lookup);
                patches.put(pd,  pd);
                pd2 = pd;
            }
            return pd2;
        }

    }
    @Override
    public RenderPatch getRotatedPatch(RenderPatch patch, int xrot, int yrot,
            int zrot, int textureindex) {
        return getPatch((PatchDefinition)patch, xrot, yrot, zrot, textureindex);
    }
    
    public PatchDefinition getPatch(PatchDefinition patch, int xrot, int yrot,
            int zrot, int textureindex) {
        PatchDefinition pd = new PatchDefinition(patch, xrot, yrot, zrot, textureindex);
        if(pd.validate() == false)
            return null;
        synchronized(lock) {
            PatchDefinition pd2 = patches.get(pd);  /* See if in cache already */
            if(pd2 == null) {
                patches.put(pd,  pd);
                pd2 = pd;
            }
            return pd2;
        }
    }
}

package org.dynmap.markers.impl;

import java.util.HashMap;
import java.util.Map;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapWorld;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.impl.MarkerAPIImpl.MarkerUpdate;

class CircleMarkerImpl implements CircleMarker {
    private String markerid;
    private String label;
    private boolean markup;
    private String desc;
    private MarkerSetImpl markerset;
    private String world;
    private String normalized_world;
    private boolean ispersistent;
    private double x;
    private double y;
    private double z;
    private double xr;
    private double zr;
    private int lineweight = 3;
    private double lineopacity = 0.8;
    private int linecolor = 0xFF0000;
    private double fillopacity = 0.35;
    private int fillcolor = 0xFF0000;
    
    /** 
     * Create circle marker
     * @param id - marker ID
     * @param lbl - label
     * @param markup - if true, label is HTML markup
     * @param world - world id
     * @param x - x center
     * @param y - y center
     * @param z - z center
     * @param xr - radius on X axis
     * @param zr - radius on Z axis
     * @param persistent - true if persistent
     * @param set - marker set
     */
    CircleMarkerImpl(String id, String lbl, boolean markup, String world, double x, double y, double z, double xr, double zr, boolean persistent, MarkerSetImpl set) {
        markerid = id;
        if(lbl != null)
            label = lbl;
        else
            label = id;
        this.markup = markup;
        this.x = x; this.y = y; this.z = z;
        this.xr = xr; this.zr = zr;
        this.world = world;
        this.normalized_world = DynmapWorld.normalizeWorldName(world);
        this.desc = null;
        ispersistent = persistent;
        markerset = set;
    }
    /**
     * Make bare area marker - used for persistence load
     *  @param id - marker ID
     *  @param set - marker set
     */
    CircleMarkerImpl(String id, MarkerSetImpl set) {
        markerid = id;
        markerset = set;
        label = id;
        markup = false;
        desc = null;
        world = normalized_world = "world";
        x = z = 0;
        y = 64;
        xr = zr = 0;
    }
    /**
     *  Load marker from configuration node
     *  @param node - configuration node
     */
    boolean loadPersistentData(ConfigurationNode node) {
        label = node.getString("label", markerid);
        markup = node.getBoolean("markup", false);
        world = node.getString("world", "world");
        normalized_world = DynmapWorld.normalizeWorldName(world);
        x = node.getDouble("x", 0);
        y = node.getDouble("y", 64);
        z = node.getDouble("z", 0);
        xr = node.getDouble("xr", 0);
        zr = node.getDouble("zr", 0);
        desc = node.getString("desc", null);
        lineweight = node.getInteger("strokeWeight", -1);
        if(lineweight == -1) {	/* Handle typo-saved value */
        	 lineweight = node.getInteger("stokeWeight", 3);
        }
        lineopacity = node.getDouble("strokeOpacity", 0.8);
        linecolor = node.getInteger("strokeColor", 0xFF0000);
        fillopacity = node.getDouble("fillOpacity", 0.35);
        fillcolor = node.getInteger("fillColor", 0xFF0000);
        ispersistent = true;    /* Loaded from config, so must be */
        
        return true;
    }
    
    void cleanup() {
        markerset = null;
    }
    
    @Override
    public String getMarkerID() {
        return markerid;
    }

    @Override
    public MarkerSet getMarkerSet() {
        return markerset;
    }

    @Override
    public void deleteMarker() {
        if(markerset == null) return;
        markerset.removeCircleMarker(this);   /* Remove from our marker set (notified by set) */
        cleanup();
    }

    @Override
    public boolean isPersistentMarker() {
        return ispersistent;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public void setLabel(String lbl) {
        setLabel(lbl, false);
    }
    
    @Override
    public void setLabel(String lbl, boolean markup) {
        label = lbl;
        this.markup = markup;
        MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
        if(ispersistent)
            MarkerAPIImpl.saveMarkers();
    }

    /**
     * Get configuration node to be saved
     * @return node
     */
    Map<String, Object> getPersistentData() {
        if(!ispersistent)   /* Nothing if not persistent */
            return null;
        HashMap<String, Object> node = new HashMap<String, Object>();
        node.put("label", label);
        node.put("markup", markup);
        node.put("x", x);
        node.put("y", y);
        node.put("z", z);
        node.put("xr", xr);
        node.put("zr", zr);
        node.put("world", world);
        if(desc != null)
            node.put("desc", desc);
        node.put("strokeWeight", lineweight);
        node.put("strokeOpacity", lineopacity);
        node.put("strokeColor", linecolor);
        node.put("fillOpacity", fillopacity);
        node.put("fillColor", fillcolor);

        return node;
    }
    @Override
    public String getWorld() {
        return world;
    }
    @Override
    public String getNormalizedWorld() {
        return normalized_world;
    }
    @Override
    public boolean isLabelMarkup() {
        return markup;
    }
    @Override
    public void setDescription(String desc) {
        if((this.desc == null) || (this.desc.equals(desc) == false)) {
            this.desc = desc;
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    /**
     * Get marker description
     * @return descrption
     */
    public String getDescription() {
        return this.desc;
    }
    @Override
    public void setLineStyle(int weight, double opacity, int color) {
        if((weight != lineweight) || (opacity != lineopacity) || (color != linecolor)) {
            lineweight = weight;
            lineopacity = opacity;
            linecolor = color;
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public int getLineWeight() {
        return lineweight;
    }
    @Override
    public double getLineOpacity() {
        return lineopacity;
    }
    @Override
    public int getLineColor() {
        return linecolor;
    }
    @Override
    public void setFillStyle(double opacity, int color) {
        if((opacity != fillopacity) || (color != fillcolor)) {
            fillopacity = opacity;
            fillcolor = color;
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public double getFillOpacity() {
        return fillopacity;
    }
    @Override
    public int getFillColor() {
        return fillcolor;
    }
    @Override
    public double getCenterX() {
        return x;
    }
    @Override
    public double getCenterY() {
        return y;
    }
    @Override
    public double getCenterZ() {
        return z;
    }
    @Override
    public void setCenter(String worldid, double x, double y, double z) {
        boolean updated = false;
        if(!worldid.equals(world)) {
            world = worldid;
            normalized_world = DynmapWorld.normalizeWorldName(world);
            updated = true;
        }
        if(this.x != x) {
            this.x = x;
            updated = true;
        }
        if(this.y != y) {
            this.y = y;
            updated = true;
        }
        if(this.z != z) {
            this.z = z;
            updated = true;
        }
        if(updated) {
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public double getRadiusX() {
        return xr;
    }
    @Override
    public double getRadiusZ() {
        return zr;
    }
    @Override
    public void setRadius(double xr, double zr) {
        if((this.xr != xr) || (this.zr != zr)) {
            this.xr = xr;
            this.zr = zr;
            MarkerAPIImpl.circleMarkerUpdated(this, MarkerUpdate.UPDATED);
            if(ispersistent)
                MarkerAPIImpl.saveMarkers();
        }
    }
    @Override
    public void setMarkerSet(MarkerSet newset) {
        if(markerset != null) {
            markerset.removeCircleMarker(this);   /* Remove from our marker set (notified by set) */
        }
        markerset = (MarkerSetImpl)newset;
        markerset.insertCircleMarker(this);
    }
}

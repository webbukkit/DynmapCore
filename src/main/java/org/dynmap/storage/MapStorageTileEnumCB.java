package org.dynmap.storage;

public interface MapStorageTileEnumCB {
    /**
     * Callback for tile enumeration calls
     * @param tile - tile found
     */
    public void tileFound(MapStorageTile tile);
}

package org.dynmap.storage;

import org.dynmap.MapType;

public interface MapStorageTileEnumCB {
    /**
     * Callback for tile enumeration calls
     * @param tile - tile found
     * @param fmt - format of tile file
     */
    public void tileFound(MapStorageTile tile, MapType.ImageFormat fmt);
}

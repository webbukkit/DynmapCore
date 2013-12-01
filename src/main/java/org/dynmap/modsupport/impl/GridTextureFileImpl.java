package org.dynmap.modsupport.impl;

import java.io.IOException;
import java.io.Writer;

import org.dynmap.modsupport.GridTextureFile;
import org.dynmap.modsupport.TextureFileType;

public class GridTextureFileImpl extends TextureFileImpl implements GridTextureFile {

    public GridTextureFileImpl(String id, String filename, int xcount, int ycount) {
        super(id, filename, TextureFileType.GRID, xcount, ycount);
    }
}

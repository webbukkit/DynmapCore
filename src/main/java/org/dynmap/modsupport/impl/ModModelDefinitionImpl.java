package org.dynmap.modsupport.impl;

import java.io.File;
import java.io.IOException;

import org.dynmap.modsupport.ModModelDefinition;
import org.dynmap.modsupport.ModTextureDefinition;

public class ModModelDefinitionImpl implements ModModelDefinition {
    private final ModTextureDefinitionImpl txtDef;
    private boolean published = false;

    public ModModelDefinitionImpl(ModTextureDefinitionImpl txtDef) {
        this.txtDef = txtDef;
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

    public boolean isPublished() {
        return published;
    }

    public void writeToFile(File destdir) throws IOException {
        
    }
}

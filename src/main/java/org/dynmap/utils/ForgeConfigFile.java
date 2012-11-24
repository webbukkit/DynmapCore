package org.dynmap.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ForgeConfigFile {
    private File cfg;
    private HashMap<String, String> settings = new HashMap<String, String>();
    public static final String ALLOWED_CHARS = "._-";

    public ForgeConfigFile(File cfgfile) {
        cfg = cfgfile;
    }
    
    public boolean load() {
        settings.clear();
        FileInputStream fis = null;
        BufferedReader rdr = null;
        boolean rslt = true;
        
        try {
            fis = new FileInputStream(cfg);
            rdr = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line;
            ArrayList<String> section = new ArrayList<String>();
            while((line = rdr.readLine()) != null) {
                int nameStart = -1, nameEnd = -1;
                boolean skip = false;

                for (int i = 0; i < line.length() && !skip; ++i) {
                    if (Character.isLetterOrDigit(line.charAt(i)) || ALLOWED_CHARS.indexOf(line.charAt(i)) != -1) {
                        if (nameStart == -1) {
                            nameStart = i;
                        }
                        nameEnd = i;
                    }
                    else if (Character.isWhitespace(line.charAt(i))) {
                    }
                    else {
                        switch (line.charAt(i)) {
                            case '#':
                                skip = true;
                                break;
                            case '{':
                                section.add(line.substring(nameStart, nameEnd + 1).trim());
                                break;
                            case '}':
                                section.remove(section.size()-1);
                                break;
                            case '=':
                                String propertyName = line.substring(nameStart, nameEnd + 1);
                                propertyName = propertyName.replace(' ', '_');
                                for(int j = section.size()-1; j >= 0; j--) {
                                    propertyName = section.get(j) + "/" + propertyName;
                                }
                                settings.put(propertyName, line.substring(i + 1).trim());
                                break;
                        }
                    }
                }
            }
        } catch (IOException iox) {
            rslt = false;
        } finally {
            if(fis != null) {
                try { fis.close(); } catch (IOException iox) {}
                fis = null;
            }
        }
        return rslt;
    }
    
    public int getBlockID(String id) {
        String val = settings.get(id);
        if (val == null)
            val = settings.get("block/" + id);  /* Check for "block/" */
        if (val != null) {
            val = val.trim();
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException nfx) {
            }
        }
        return -1;
    }
    public void addBlockIDs(Map<String,Integer> map) {
        for(String k : settings.keySet()) {
            if(k.startsWith("block/")) {
                map.put(k.substring("block/".length()), getBlockID(k));
            }
            else if(k.startsWith("blocks/")) { /* RP2 */
                map.put(k.substring("blocks/".length()), getBlockID(k));
            }
            else if(k.startsWith("item/")) {    /* Item codes? */
                map.put("item_" + k.substring("item/".length()), getBlockID(k));
            }
            else if(k.indexOf("/") < 0) {
                map.put(k, getBlockID(k));
            }
        }
    }
}

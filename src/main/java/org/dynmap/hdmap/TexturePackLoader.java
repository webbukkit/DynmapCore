package org.dynmap.hdmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.dynmap.Log;

public class TexturePackLoader {
    private ZipFile zf;
    private File tpdir;
    private static final String RESOURCEPATH = "texturepacks/standard";
    
    public TexturePackLoader(File tp) {        
        if (tp.isFile() && tp.canRead()) {
            try {
                zf = new ZipFile(tp);
            } catch (IOException e) {
                Log.severe("Error opening texture pack - " + tp.getPath());
            }
        }
        else if (tp.isDirectory() && tp.canRead()) {
            tpdir = tp;
        }
        else {
            Log.info("Texture pack not found - " + tp.getPath());
        }
    }
    public InputStream openTPResource(String rname, String altname) {
        InputStream is = openTPResource(rname);
        if (is == null) {
            if (altname != null) {
                is = openTPResource(altname);
            }
        }
        return is;
    }
    public InputStream openTPResource(String rname) {
        try {
            if (zf != null) {
                ZipEntry ze = zf.getEntry(rname);
                if ((ze != null) && (!ze.isDirectory())) {
                    return zf.getInputStream(ze);
                }
            }
            else if (tpdir != null) {
                File f = new File(tpdir, rname);
                if (f.isFile() && f.canRead()) {
                    return new FileInputStream(f);
                }
            }
        } catch (IOException iox) {
        }
        // Fall through - load as resource from jar
        return getClass().getClassLoader().getResourceAsStream(RESOURCEPATH + "/" + rname);
    }
    public void close() {
        if(zf != null) {
            try { zf.close(); } catch (IOException iox) {}
            zf = null;
        }
    }
    public void closeResource(InputStream is) {
        try {
            if (is != null)
                is.close();
        } catch (IOException iox) {
        }
    }
    public Set<String> getEntries() {
        HashSet<String> rslt = new HashSet<String>();
        if (zf != null) {
            Enumeration<? extends ZipEntry> lst = zf.entries();
            while(lst.hasMoreElements()) {
                rslt.add(lst.nextElement().getName());
            }
        }
        if (tpdir != null) {
            addFiles(rslt, tpdir, "");
        }
        return rslt;
    }
    
    private void addFiles(HashSet<String> files, File dir, String path) {
        File[] listfiles = dir.listFiles();
        if(listfiles == null) return;
        for(File f : listfiles) {
            String fn = f.getName();
            if(fn.equals(".") || (fn.equals(".."))) continue;
            if(f.isFile()) {
                files.add(path + "/" + fn);
            }
            else if(f.isDirectory()) {
                addFiles(files, f, path + "/" + f.getName());
            }
        }
    }

}

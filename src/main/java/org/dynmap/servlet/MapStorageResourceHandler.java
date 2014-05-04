package org.dynmap.servlet;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType.ImageFormat;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTile.TileRead;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class MapStorageResourceHandler extends AbstractHandler {
    
    private DynmapCore core;

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = baseRequest.getPathInfo();
        int soff = 0, eoff;
        // We're handling this request
        baseRequest.setHandled(true);
        
        if (path.charAt(0) == '/') soff = 1;
        eoff = path.indexOf('/', soff);
        if (soff < 0) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        String world = path.substring(soff, eoff);
        String uri = path.substring(eoff+1);
        DynmapWorld w = null;
        if (core.mapManager != null) {
            w = core.mapManager.getWorld(world);
        }
        // If world not found quit
        if (w == null) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        MapStorage store = w.getMapStorage();    // Get storage handler
        // Get tile reference, based on URI and world
        MapStorageTile tile = store.getTile(w, uri);
        if (tile == null) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        // Read tile
        TileRead tr = null;
        if (tile.getReadLock(5000)) {
            tr = tile.read();
            tile.releaseReadLock();
        }
        if (tr == null) {
            response.sendError(HttpStatus.NOT_FOUND_404);
            return;
        }
        // Got tile, package up for response
        response.setDateHeader("Last-Modified", tr.lastModified);
        response.setIntHeader("Content-Length", tr.image.length());
        response.setHeader("ETag", "\"" + tr.hashCode + "\"");
        if (tr.format == ImageFormat.FORMAT_PNG) {
            response.setContentType("image/png");
        }
        else {
            response.setContentType("image/jpeg");
        }
        ServletOutputStream out = response.getOutputStream();
        out.write(tr.image.buffer(), 0, tr.image.length());
        out.flush();

    }
    
    public void setCore(DynmapCore core) {
        this.core = core;
    }
}

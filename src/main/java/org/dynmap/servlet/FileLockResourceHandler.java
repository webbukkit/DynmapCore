package org.dynmap.servlet;

import org.dynmap.Log;
import org.dynmap.utils.FileLockManager;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

public class FileLockResourceHandler extends ResourceHandler {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Resource resource;
        try {
            resource = getResource(target);
        } catch(MalformedURLException ex) {
            return;
        }
        if (resource == null) {
            return;
        }
        File file = resource.getFile();
        if (file == null) {
            return;
        }
        if (!FileLockManager.getReadLock(file, 5000)) {
            Log.severe("Timeout waiting for lock on file '" + file.getPath() + "' while handling HTTP-request.");
            response.sendError(HttpStatus.REQUEST_TIMEOUT_408);
            return;
        }
        try {
            super.handle(target, baseRequest, request, response);
        } finally {
            FileLockManager.releaseReadLock(file);
        }
    }
}

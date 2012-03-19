package org.dynmap.servlet;

import static org.dynmap.JSONUtils.s;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.dynmap.ClientUpdateEvent;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.web.HttpField;
import org.json.simple.JSONObject;

@SuppressWarnings("serial")
public class ClientUpdateServlet extends HttpServlet {
    private DynmapCore core;
    private Charset cs_utf8 = Charset.forName("UTF-8");
    
    public ClientUpdateServlet(DynmapCore plugin) {
        this.core = plugin;
    }

    Pattern updatePathPattern = Pattern.compile("/([^/]+)/([0-9]*)");
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        byte[] bytes;
        HttpSession sess = req.getSession(true);
        String user = (String) sess.getAttribute(LoginServlet.USERID_ATTRIB);
        if(user == null) user = LoginServlet.USERID_GUEST;
        if(core.getLoginRequired() && user.equals(LoginServlet.USERID_GUEST)) {
            JSONObject json = new JSONObject();
            s(json, "error", "login-required");
            bytes = json.toJSONString().getBytes(cs_utf8);
        }
        else {
            String path = req.getPathInfo();
            Matcher match = updatePathPattern.matcher(path);
        
            if (!match.matches()) {
                resp.sendError(404, "World not found");
                return;
            }
        
            String worldName = match.group(1);
            String timeKey = match.group(2);
        
            DynmapWorld dynmapWorld = null;
            if(core.mapManager != null) {
                dynmapWorld = core.mapManager.getWorld(worldName);
            }
            if (dynmapWorld == null || !dynmapWorld.isLoaded()) {
                resp.sendError(404, "World not found");
                return;
            }
            long current = System.currentTimeMillis();
            long since = 0;

            try {
                since = Long.parseLong(timeKey);
            } catch (NumberFormatException e) {
            }

            JSONObject u = new JSONObject();
            s(u, "timestamp", current);
            core.events.trigger("buildclientupdate", new ClientUpdateEvent(since, dynmapWorld, u));

            bytes = u.toJSONString().getBytes(cs_utf8);
        }
        
        String dateStr = new Date().toString();
        resp.addHeader(HttpField.Date, dateStr);
        resp.addHeader(HttpField.ContentType, "text/plain; charset=utf-8");
        resp.addHeader(HttpField.Expires, "Thu, 01 Dec 1994 16:00:00 GMT");
        resp.addHeader(HttpField.LastModified, dateStr);
        resp.addHeader(HttpField.ContentLength, Integer.toString(bytes.length));

        resp.getOutputStream().write(bytes);
    }
}

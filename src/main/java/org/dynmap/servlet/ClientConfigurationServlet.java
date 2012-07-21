package org.dynmap.servlet;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Event;
import org.json.simple.JSONObject;
import static org.dynmap.JSONUtils.s;

public class ClientConfigurationServlet extends HttpServlet {
    private static final long serialVersionUID = 9106801553080522469L;
    private DynmapCore core;
    private byte[] cachedConfiguration = null;
    private byte[] cachedConfigurationGuest = null;
    private int cached_config_hashcode = 0;
    private Charset cs_utf8 = Charset.forName("UTF-8");

    public ClientConfigurationServlet(DynmapCore plugin) {
        this.core = plugin;
        plugin.events.addListener("worldactivated", new Event.Listener<DynmapWorld>() {
            @Override
            public void triggered(DynmapWorld t) {
                cachedConfiguration = cachedConfigurationGuest = null;
            }
        });
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        byte[] outputBytes;

        HttpSession sess = req.getSession(true);
        String user = (String) sess.getAttribute(LoginServlet.USERID_ATTRIB);
        if(user == null) user = LoginServlet.USERID_GUEST;
        boolean guest = user.equals(LoginServlet.USERID_GUEST);
        if(core.getLoginRequired() && guest) {
            JSONObject json = new JSONObject();
            s(json, "error", "login-required");
            outputBytes = json.toJSONString().getBytes(cs_utf8);
        }
        else if(core.isLoginSupportEnabled()) { /* If login support enabled, don't cacne */
            JSONObject json = new JSONObject();
            if(guest) {
                s(json, "loggedin", false);
            }
            else {
                s(json, "loggedin", true);
                s(json, "player", user);
            }
            core.events.<JSONObject>triggerSync(core, "buildclientconfiguration", json);
            String s = json.toJSONString();
            outputBytes = s.getBytes(cs_utf8);
        }
        else { 
            if(cached_config_hashcode != core.getConfigHashcode()) {
                cachedConfiguration = cachedConfigurationGuest = null;
                cached_config_hashcode = core.getConfigHashcode();
            }
            if(guest) {
                if (cachedConfigurationGuest == null) {
                    JSONObject json = new JSONObject();
                    s(json, "loggedin", !guest);
                    core.events.<JSONObject>triggerSync(core, "buildclientconfiguration", json);
                    String s = json.toJSONString();
                    cachedConfigurationGuest = s.getBytes(cs_utf8);
                }
            }
            else {
                if (cachedConfiguration == null) {
                    JSONObject json = new JSONObject();
                    s(json, "loggedin", !guest);
                    core.events.<JSONObject>triggerSync(core, "buildclientconfiguration", json);
                    String s = json.toJSONString();
                    cachedConfiguration = s.getBytes(cs_utf8);
                }
            }
            outputBytes = (guest?cachedConfigurationGuest:cachedConfiguration);
        }
        String dateStr = new Date().toString();
        res.addHeader("Date", dateStr);
        res.setContentType("text/plain; charset=utf-8");
        res.addHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        res.addHeader("Last-modified", dateStr);
        res.setContentLength(outputBytes.length);
        res.getOutputStream().write(outputBytes);
    }
}

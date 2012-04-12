package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;
import static org.dynmap.JSONUtils.g;

import java.util.List;

import org.dynmap.Event.Listener;
import org.dynmap.common.DynmapPlayer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ClientConfigurationComponent extends Component {
    public ClientConfigurationComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        core.events.<JSONObject>addListener("buildclientconfiguration", new Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                ConfigurationNode c = core.configuration;
                s(t, "confighash", core.getConfigHashcode());
                s(t, "updaterate", c.getFloat("updaterate", 1.0f));
                s(t, "showplayerfacesinmenu", c.getBoolean("showplayerfacesinmenu", true));
                s(t, "joinmessage", c.getString("joinmessage", "%playername% joined"));
                s(t, "quitmessage", c.getString("quitmessage", "%playername% quit"));
                s(t, "spammessage", c.getString("spammessage", "You may only chat once every %interval% seconds."));
                s(t, "webprefix", unescapeString(c.getString("webprefix", "[WEB] ")));
                s(t, "defaultzoom", c.getInteger("defaultzoom", 0));
                s(t, "sidebaropened", c.getString("sidebaropened", "false"));
                s(t, "dynmapversion", core.getDynmapPluginVersion());
                s(t, "cyrillic", c.getBoolean("cyrillic-support", false));
                s(t, "showlayercontrol", c.getString("showlayercontrol", "true"));
                s(t, "grayplayerswhenhidden", c.getBoolean("grayplayerswhenhidden", true));
                s(t, "login-enabled", core.isLoginSupportEnabled());
                String sn = core.getServer().getServerName();
                if(sn.equals("Unknown Server"))
                    sn = "Minecraft Dynamic Map";
                s(t, "title", c.getString("webpage-title", sn));
                s(t, "msg-maptypes", c.getString("msg/maptypes", "Map Types"));
                s(t, "msg-players", c.getString("msg/players", "Players"));
                s(t, "msg-chatrequireslogin", c.getString("msg/chatrequireslogin", "Chat Requires Login"));
                s(t, "msg-chatnotallowed", c.getString("msg/chatnotallowed", "You are not permitted to send chat messages"));
                
                String player = null;
                boolean check_protected = false;
                if(core.isLoginSupportEnabled() && (g(t, "loggedin") != null)) {
                    check_protected = true;
                    player = (String) g(t, "player");
                }
                DynmapWorld defaultWorld = null;
                String defmap = null;
                for(DynmapWorld world : core.mapManager.getWorlds()) {
                    if(check_protected && world.isProtected() && ((player == null) || (!core.getServer().checkPlayerPermission(player, "world." + world.getName()))))
                        continue;
                    if (world.maps.size() == 0) continue;
                    if (defaultWorld == null) defaultWorld = world;
                    JSONObject wo = new JSONObject();
                    s(wo, "name", world.getName());
                    s(wo, "title", world.getTitle());
                    s(wo, "protected", world.isProtected());
                    DynmapLocation center = world.getCenterLocation();
                    s(wo, "center/x", center.x);
                    s(wo, "center/y", center.y);
                    s(wo, "center/z", center.z);
                    s(wo, "bigworld", world.bigworld);
                    s(wo, "extrazoomout", world.getExtraZoomOutLevels());
                    s(wo, "sealevel", world.sealevel);
                    s(wo, "worldheight", world.worldheight);
                    a(t, "worlds", wo);
                    
                    for(MapType mt : world.maps) {
                        mt.buildClientConfiguration(wo, world);
                        if(defmap == null) defmap = mt.getName();
                    }
                    if(check_protected) {
                        JSONArray maps = (JSONArray) g(wo, "maps");
                        if(maps != null) {
                            int cnt = maps.size();
                            for(int i = 0; i < cnt; i++) {
                                JSONObject obj = (JSONObject)maps.get(i);
                                Boolean prot = (Boolean) g(obj, "protected");
                                if((prot == null) || (prot.booleanValue() == false))
                                    continue;
                                String mname = (String) g(obj, "name");
                                if ((player == null) || (!core.getServer().checkPlayerPermission(player, "map." + world.getName() + "." + mname))) {
                                    maps.remove(i); /* Remove it */
                                    i--;
                                    cnt--;
                                }
                            }
                        }
                    }
                }
                s(t, "defaultworld", c.getString("defaultworld", defaultWorld == null ? "world" : defaultWorld.getName()));
                s(t, "defaultmap", c.getString("defaultmap", defmap == null ? "surface" : defmap));
                if(c.getString("followmap", null) != null)
                    s(t, "followmap", c.getString("followmap"));
                if(c.getInteger("followzoom",-1) >= 0)
                    s(t, "followzoom", c.getInteger("followzoom", 0));
            }
        });
    }
    
}

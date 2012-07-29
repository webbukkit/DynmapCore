package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.util.List;
import org.dynmap.common.DynmapPlayer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ClientUpdateComponent extends Component {
    private int hideifshadow;
    private int hideifunder;
    private boolean hideifsneaking;
    private boolean is_protected;
    
    public ClientUpdateComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        
        hideifshadow = configuration.getInteger("hideifshadow", 15);
        hideifunder = configuration.getInteger("hideifundercover", 15);
        hideifsneaking = configuration.getBoolean("hideifsneaking", false);
        is_protected = configuration.getBoolean("protected-player-info", false);
        
        core.events.addListener("buildclientupdate", new Event.Listener<ClientUpdateEvent>() {
            @Override
            public void triggered(ClientUpdateEvent e) {
                buildClientUpdate(e);
            }
        });
    }
    
    protected void buildClientUpdate(ClientUpdateEvent e) {
        DynmapWorld world = e.world;
        JSONObject u = e.update;
        long since = e.timestamp;
        String worldName = world.getName();
        boolean see_all = true;
        
        if(is_protected && (!e.include_all_users)) {
            if(e.user != null)
                see_all = core.getServer().checkPlayerPermission(e.user, "playermarkers.seeall");
            else
                see_all = false;
        }
        if((e.include_all_users) && is_protected) { /* If JSON request AND protected, leave mark for script */
            s(u, "protected", true);
        }
        
        s(u, "confighash", core.getConfigHashcode());

        s(u, "servertime", world.getTime() % 24000);
        s(u, "hasStorm", world.hasStorm());
        s(u, "isThundering", world.isThundering());

        s(u, "players", new JSONArray());
        List<DynmapPlayer> players = core.playerList.getVisiblePlayers();
        for(DynmapPlayer p : players) {
            boolean hide = false;
            DynmapLocation pl = p.getLocation();
            DynmapWorld pw = core.getWorld(pl.world);
            if(pw == null) {
                hide = true;
            }
            JSONObject jp = new JSONObject();
            
            s(jp, "type", "player");
            s(jp, "name", Client.stripColor(p.getDisplayName()));
            s(jp, "account", p.getName());
            if((!hide) && (hideifshadow < 15)) {
                if(pw.getLightLevel((int)pl.x, (int)pl.y, (int)pl.z) <= hideifshadow) {
                    hide = true;
                }
            }
            if((!hide) && (hideifunder < 15)) {
                if(pw.canGetSkyLightLevel()) { /* If we can get real sky level */
                    if(pw.getSkyLightLevel((int)pl.x, (int)pl.y, (int)pl.z) <= hideifunder) {
                        hide = true;
                    }
                }
                else if(pw.isNether() == false) {   /* Not nether */
                    if(pw.getHighestBlockYAt((int)pl.x, (int)pl.z) > pl.y) {
                        hide = true;
                    }
                }
            }
            if((!hide) && hideifsneaking && p.isSneaking()) {
                hide = true;
            }
            if((!hide) && is_protected && (!see_all)) {
                if(e.user != null) {
                    hide = !core.testIfPlayerVisibleToPlayer(e.user, p.getName());
                }
                else {
                    hide = true;
                }
            }
                
            /* Don't leak player location for world not visible on maps, or if sendposition disbaled */
            DynmapWorld pworld = MapManager.mapman.worldsLookup.get(pl.world);
            /* Fix typo on 'sendpositon' to 'sendposition', keep bad one in case someone used it */
            if(configuration.getBoolean("sendposition", true) && configuration.getBoolean("sendpositon", true) &&
                    (pworld != null) && pworld.sendposition && (!hide)) {
                s(jp, "world", pl.world);
                s(jp, "x", pl.x);
                s(jp, "y", pl.y);
                s(jp, "z", pl.z);
            }
            else {
                s(jp, "world", "-some-other-bogus-world-");
                s(jp, "x", 0.0);
                s(jp, "y", 64.0);
                s(jp, "z", 0.0);
            }
            /* Only send health if enabled AND we're on visible world */
            if (configuration.getBoolean("sendhealth", false) && (pworld != null) && pworld.sendhealth && (!hide)) {
                s(jp, "health", p.getHealth());
                s(jp, "armor", p.getArmorPoints());
            }
            else {
                s(jp, "health", 0);
                s(jp, "armor", 0);
            }
            a(u, "players", jp);
        }
        if(configuration.getBoolean("includehiddenplayers", false)) {
            List<DynmapPlayer> hidden = core.playerList.getHiddenPlayers();
            for(DynmapPlayer p : hidden) {
                JSONObject jp = new JSONObject();
                s(jp, "type", "player");
                s(jp, "name", Client.stripColor(p.getDisplayName()));
                s(jp, "account", p.getName());
                s(jp, "world", "-hidden-player-");
                s(jp, "x", 0.0);
                s(jp, "y", 64.0);
                s(jp, "z", 0.0);
                s(jp, "health", 0);
                s(jp, "armor", 0);
                a(u, "players", jp);
            }
        }

        s(u, "updates", new JSONArray());
        for(Object update : core.mapManager.getWorldUpdates(worldName, since)) {
            a(u, "updates", (Client.Update)update);
        }
    }

}

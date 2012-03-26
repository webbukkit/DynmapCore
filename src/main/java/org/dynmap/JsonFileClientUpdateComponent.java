package org.dynmap;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.dynmap.web.Json;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import static org.dynmap.JSONUtils.*;

import java.nio.charset.Charset;

public class JsonFileClientUpdateComponent extends ClientUpdateComponent {
    protected long jsonInterval;
    protected long currentTimestamp = 0;
    protected long lastTimestamp = 0;
    protected long lastChatTimestamp = 0;
    protected JSONParser parser = new JSONParser();
    private boolean hidewebchatip;
    private boolean useplayerloginip;
    private boolean requireplayerloginip;
    private boolean trust_client_name;
    private boolean checkuserban;
    private boolean req_login;
    private HashMap<String,String> useralias = new HashMap<String,String>();
    private int aliasindex = 1;
    private long last_confighash;
    private File outputFile;
    private File outputTempFile;
    private MessageDigest md;
    private File loginFile;
    private File loginTempFile;
    	
    private Charset cs_utf8 = Charset.forName("UTF-8");
    public JsonFileClientUpdateComponent(final DynmapCore core, final ConfigurationNode configuration) {
        super(core, configuration);
        final boolean allowwebchat = configuration.getBoolean("allowwebchat", false);
        jsonInterval = (long)(configuration.getFloat("writeinterval", 1) * 1000);
        hidewebchatip = configuration.getBoolean("hidewebchatip", false);
        useplayerloginip = configuration.getBoolean("use-player-login-ip", true);
        requireplayerloginip = configuration.getBoolean("require-player-login-ip", false);
        trust_client_name = configuration.getBoolean("trustclientname", false);
        checkuserban = configuration.getBoolean("block-banned-player-chat", true);
        req_login = configuration.getBoolean("webchat-requires-login", false);
        if(core.isLoginSupportEnabled()) {
            outputFile = getStandaloneFile("dynmap_config.php");
            outputTempFile = getStandaloneFile("dynmap_config.new.php");
        }
        else {
            outputFile = getStandaloneFile("dynmap_config.json");
            outputTempFile = getStandaloneFile("dynmap_config.json.new");
        }
        loginFile = getStandaloneFile("dynmap_login.php");
        loginTempFile = getStandaloneFile("dynmap_login.new.php");
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException nsax) {
            Log.severe("Unable to get message digest SHA-1");
        }
        
        core.getServer().scheduleServerTask(new Runnable() {
            @Override
            public void run() {
                currentTimestamp = System.currentTimeMillis();
                if(last_confighash != core.getConfigHashcode()) {
                    writeConfiguration();
                }
                writeUpdates();
                if (allowwebchat) {
                    handleWebChat();
                }
                if(core.isLoginSupportEnabled())
                    handleRegister();
                lastTimestamp = currentTimestamp;
                core.getServer().scheduleServerTask(this, jsonInterval/50);
            }}, jsonInterval/50);
        
        core.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "jsonfile", true);
                s(t, "allowwebchat", allowwebchat);
                s(t, "webchat-requires-login", req_login);
                s(t, "loginrequired", core.isLoginRequired());
                // For 'sendmessage.php'
                s(t, "webchat-interval", configuration.getFloat("webchat-interval", 5.0f));
            }
        });
        core.events.addListener("initialized", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
                writeLogins();
            }
        });
        core.events.addListener("worldactivated", new Event.Listener<DynmapWorld>() {
            @Override
            public void triggered(DynmapWorld t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
            }
        });
        core.events.addListener("loginupdated", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeLogins();
            }
        });
    }
    
    protected File getStandaloneFile(String filename) {
        File webpath = new File(core.configuration.getString("webpath", "web"), "standalone/" + filename);
        if (webpath.isAbsolute())
            return webpath;
        else
            return new File(core.getDataFolder(), webpath.toString());
    }
    
    private static final int RETRY_LIMIT = 5;
    protected void writeConfiguration() {
        JSONObject clientConfiguration = new JSONObject();
        core.events.trigger("buildclientconfiguration", clientConfiguration);
        last_confighash = core.getConfigHashcode();
        
        final byte[] content = clientConfiguration.toJSONString().getBytes(cs_utf8);
        
        MapManager.scheduleDelayedJob(new Runnable() {
            public void run() {
                int retrycnt = 0;
                boolean done = false;
                while(!done) {
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(outputTempFile);
                        if(core.isLoginSupportEnabled()) {
                            fos.write("<?php /*\n".getBytes(cs_utf8));
                        }
                        fos.write(content);
                        if(core.isLoginSupportEnabled()) {
                            fos.write("\n*/ ?>\n".getBytes(cs_utf8));
                        }
                        fos.close();
                        fos = null;
                        outputFile.delete();
                        outputTempFile.renameTo(outputFile);
                        done = true;
                    } catch (IOException ioe) {
                        if(retrycnt < RETRY_LIMIT) {
                            try { Thread.sleep(20 * (1 << retrycnt)); } catch (InterruptedException ix) {}
                            retrycnt++;
                        }
                        else {
                            Log.severe("Exception while writing JSON-configuration-file.", ioe);
                            done = true;
                        }
                    } finally {
                        if(fos != null) {
                            try {
                                fos.close();
                            } catch (IOException iox) {
                            }
                            fos = null;
                        }
                    }
                }
            }
        }, 0);
    }
    
    @SuppressWarnings("unchecked")
    protected void writeUpdates() {
        if(core.mapManager == null) return;
        //Handles Updates
        ArrayList<DynmapWorld> wlist = new ArrayList<DynmapWorld>(core.mapManager.getWorlds());	// Grab copy of world list
        for (int windx = 0; windx < wlist.size(); windx++) {
        	DynmapWorld dynmapWorld = wlist.get(windx);
            JSONObject update = new JSONObject();
            update.put("timestamp", currentTimestamp);
            ClientUpdateEvent clientUpdate = new ClientUpdateEvent(currentTimestamp - 30000, dynmapWorld, update);
            core.events.trigger("buildclientupdate", clientUpdate);

            final File outputFile = getStandaloneFile("dynmap_" + dynmapWorld.getName() + ".json");
            final File outputTempFile = getStandaloneFile("dynmap_" + dynmapWorld.getName() + ".json.new");
            final byte[] content = Json.stringifyJson(update).getBytes(cs_utf8);
            
            MapManager.scheduleDelayedJob(new Runnable() {
                public void run() {
                    int retrycnt = 0;
                    boolean done = false;
                    while(!done) {
                        FileOutputStream fos = null;
                        try {
                            fos = new FileOutputStream(outputTempFile);
                            fos.write(content);
                            fos.close();
                            fos = null;
                            outputFile.delete();
                            outputTempFile.renameTo(outputFile);
                            done = true;
                        } catch (IOException ioe) {
                            if(retrycnt < RETRY_LIMIT) {
                                try { Thread.sleep(20 * (1 << retrycnt)); } catch (InterruptedException ix) {}
                                retrycnt++;
                            }
                            else {
                                Log.severe("Exception while writing JSON-file.", ioe);
                                done = true;
                            }
                        } finally {
                            if(fos != null) {
                                try {
                                    fos.close();
                                } catch (IOException iox) {
                                }
                                fos = null;
                            }
                        }
                    }
                }
            }, 0);
        }
    }
    
    private byte[] loginhash = new byte[16];
    
    protected void writeLogins() {
        if(core.isLoginSupportEnabled()) {
            String s = core.getLoginPHP();
            if(s != null) {
                byte[] bytes = s.getBytes(cs_utf8);
                md.reset();
                byte[] hash = md.digest(bytes);
                if(Arrays.equals(hash, loginhash)) {
                    return;
                }
                FileOutputStream fos = null;
                boolean good = false;
                try {
                    fos = new FileOutputStream(loginTempFile);
                    fos.write(bytes);
                    good = true;
                } catch (IOException iox) {
                    Log.severe("Error writing " + loginFile.getPath() + ": " + iox.getMessage());
                } finally {
                    if(fos != null) {
                        try { fos.close(); } catch (IOException ix) {}
                    }
                    if(good) {
                        loginFile.delete();
                        loginTempFile.renameTo(loginFile);
                    }
                }
            }
        }
        else {
            loginFile.delete();
        }
    }
    
    protected void handleWebChat() {
        File webchatFile = getStandaloneFile("dynmap_webchat.json");
        if (webchatFile.exists() && lastTimestamp != 0) {
            JSONArray jsonMsgs = null;
            Reader inputFileReader = null;
            try {
                inputFileReader = new InputStreamReader(new FileInputStream(webchatFile), cs_utf8);
                jsonMsgs = (JSONArray) parser.parse(inputFileReader);
            } catch (IOException ex) {
                Log.severe("Exception while reading JSON-file.", ex);
            } catch (ParseException ex) {
                Log.severe("Exception while parsing JSON-file.", ex);
            } finally {
                if(inputFileReader != null) {
                    try {
                        inputFileReader.close();
                    } catch (IOException iox) {
                        
                    }
                    inputFileReader = null;
                }
            }

            if (jsonMsgs != null) {
                Iterator<?> iter = jsonMsgs.iterator();
                boolean init_skip = (lastChatTimestamp == 0);
                while (iter.hasNext()) {
                    boolean ok = true;
                    JSONObject o = (JSONObject) iter.next();
                    String ts = String.valueOf(o.get("timestamp"));
                    if(ts.equals("null")) ts = "0";
                    long cts = Long.parseLong(ts);
                    if (cts > lastChatTimestamp) {
                        String name = String.valueOf(o.get("name"));
                        String ip = String.valueOf(o.get("ip"));
                        String uid = null;
                        Object usr = o.get("userid");
                        if(usr != null) {
                            uid = String.valueOf(usr);
                        }
                        boolean isip = true;
                        lastChatTimestamp = cts;
                        if(init_skip)
                            continue;
                        if(uid == null) {
                            if((!trust_client_name) || (name == null) || (name.equals(""))) {
                                if(ip != null)
                                    name = ip;
                            }
                            if(useplayerloginip) {  /* Try to match using IPs of player logins */
                                List<String> ids = core.getIDsForIP(name);
                                if(ids != null) {
                                    name = ids.get(0);
                                    isip = false;
                                    if(checkuserban) {
                                        if(core.getServer().isPlayerBanned(name)) {
                                            Log.info("Ignore message from '" + ip + "' - banned player (" + name + ")");
                                            ok = false;
                                        }
                                    }
                                    if(!core.getServer().checkPlayerPermission(name, "webchat")) {
                                        Log.info("Rejected web chat from " + ip + ": not permitted (" + name + ")");
                                        ok = false;
                                    }
                                }
                                else if(requireplayerloginip) {
                                    Log.info("Ignore message from '" + name + "' - no matching player login recorded");
                                    ok = false;
                                }
                            }
                            if(hidewebchatip && isip) {
                                String n = useralias.get(name);
                                if(n == null) { /* Make ID */
                                    n = String.format("web-%03d", aliasindex);
                                    aliasindex++;
                                    useralias.put(name, n);
                                }
                                name = n;
                            }
                        }
                        else {
                            if(core.getServer().isPlayerBanned(uid)) {
                                Log.info("Ignore message from '" + uid + "' - banned user");
                                ok = false;
                            }
                            if(!core.getServer().checkPlayerPermission(uid, "webchat")) {
                                Log.info("Rejected web chat from " + uid + ": not permitted");
                                ok = false;
                            }
                            name = uid;
                        }
                        if(ok) {
                            String message = String.valueOf(o.get("message"));
                            core.webChat(name, message);
                        }
                    }
                }
            }
        }
    }
    protected void handleRegister() {
        if(core.pendingRegisters() == false)
            return;
        File regFile = getStandaloneFile("dynmap_reg.php");
        if (regFile.exists()) {
            FileInputStream fstream = null;
            ArrayList<String> lines = new ArrayList<String>();
            try {
                fstream = new FileInputStream(regFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
                String line;
                while ((line = br.readLine()) != null)   {
                    if(line.startsWith("<?") || line.startsWith("*/")) {
                        continue;
                    }
                    lines.add(line);
                }
            } catch (IOException iox) {
                Log.severe("Exception while reading " + regFile.getPath(), iox);
            } finally {
                if(fstream != null) {
                    try {
                        fstream.close();
                    } catch (IOException iox) {
                        
                    }
                    fstream = null;
                }
            }
            for(int i = 0; i < lines.size(); i++) {
                String[] vals = lines.get(i).split("=");
                if(vals.length == 3) {
                    core.processCompletedRegister(vals[0].trim(), vals[1].trim(), vals[2].trim());
                }
            }
        }
    }
    
    @Override
    public void dispose() {
        super.dispose();
    }
}

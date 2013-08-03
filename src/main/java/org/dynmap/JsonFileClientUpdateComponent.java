package org.dynmap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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
    private boolean chat_perms;
    private int lengthlimit;
    private HashMap<String,String> useralias = new HashMap<String,String>();
    private int aliasindex = 1;
    private long last_confighash;
    private MessageDigest md;

    private static class FileToWrite {
        File file;
        File newfile;
        File oldfile;
        byte[] content;
        boolean phpwrapper;
        @Override
        public boolean equals(Object o) {
            if(o instanceof FileToWrite) {
                return ((FileToWrite)o).file.equals(this.file);
            }
            return false;
        }
    }
    private class FileProcessor implements Runnable {
        public void run() {
            while(true) {
                FileToWrite f = null;
                synchronized(lock) {
                    if(files_to_write.isEmpty() == false) {
                        f = files_to_write.removeFirst();
                    }
                    else {
                        pending = null;
                        return;
                    }
                }
                int retrycnt = 0;
                boolean done = false;
                while(!done) {
                    RandomAccessFile fos = null;
                    boolean good = false;
                    try {
                        if(f.newfile.exists()) {
                            f.newfile.delete();
                        }
                        fos = new RandomAccessFile(f.newfile, "rw");
                        if(f.phpwrapper) {
                            fos.write("<?php /*\n".getBytes(cs_utf8));
                        }
                        fos.write(f.content);
                        if(f.phpwrapper) {
                            fos.write("\n*/ ?>\n".getBytes(cs_utf8));
                        }
                        good = true;
                        done = true;
                    } catch (IOException ioe) {
                        if(retrycnt < RETRY_LIMIT) {
                            try { Thread.sleep(20 * (1 << retrycnt)); } catch (InterruptedException ix) {}
                            retrycnt++;
                        }
                        else {
                            Log.severe("Exception while writing JSON-file - " + f.oldfile.getPath(), ioe);
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
                        if(good) {
                            f.file.renameTo(f.oldfile);
                            f.newfile.renameTo(f.file);
                            f.oldfile.delete();
                        }
                    }
                }
            }
        }
    }
    private Object lock = new Object();
    private FileProcessor pending;
    private LinkedList<FileToWrite> files_to_write = new LinkedList<FileToWrite>();

    private void enqueueFileWrite(File file, File newfile, File oldfile, byte[] content, boolean phpwrap) {
        FileToWrite ftw = new FileToWrite();
        ftw.file = file;
        ftw.newfile = newfile;
        ftw.oldfile = oldfile;
        ftw.content = content;
        ftw.phpwrapper = phpwrap;
        synchronized(lock) {
            boolean didadd = false;
            if(pending == null) {
                didadd = true;
                pending = new FileProcessor();
            }
            files_to_write.remove(ftw);
            files_to_write.add(ftw);
            if(didadd) {
                MapManager.scheduleDelayedJob(new FileProcessor(), 0);
            }
        }
    }
    
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
        chat_perms = configuration.getBoolean("webchat-permissions", false);
        lengthlimit = configuration.getInteger("chatlengthlimit", 256); 
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException nsax) {
            Log.severe("Unable to get message digest SHA-1");
        }
        /* Generate our config.js file */
        generateConfigJS(core);
        
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
                s(t, "chatlengthlimit", lengthlimit);
            }
        });
        core.events.addListener("initialized", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
                writeLogins();
                writeAccess();
            }
        });
        core.events.addListener("server-started", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
                writeLogins();
                writeAccess();
            }
        });
        core.events.addListener("worldactivated", new Event.Listener<DynmapWorld>() {
            @Override
            public void triggered(DynmapWorld t) {
                writeConfiguration();
                writeUpdates(); /* Make sure we stay in sync */
                writeAccess();
            }
        });
        core.events.addListener("loginupdated", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeLogins();
                writeAccess();
            }
        });
        core.events.addListener("playersetupdated", new Event.Listener<Object>() {
            @Override
            public void triggered(Object t) {
                writeAccess();
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
    
    private void generateConfigJS(DynmapCore core) {
        /* Test if login support is enabled */
        boolean login_enabled = core.isLoginSupportEnabled();

        // configuration: 'standalone/dynmap_config.json?_={timestamp}',
        // update: 'standalone/dynmap_{world}.json?_={timestamp}',
        // sendmessage: 'standalone/sendmessage.php',
        // login: 'standalone/login.php',
        // register: 'standalone/register.php',
        // tiles : 'tiles/',
        // markers : 'tiles/'

        // configuration: 'standalone/configuration.php',
        // update: 'standalone/update.php?world={world}&ts={timestamp}',
        // sendmessage: 'standalone/sendmessage.php',
        // login: 'standalone/login.php',
        // register: 'standalone/register.php',
        // tiles : 'standalone/tiles.php?tile=',
        // markers : 'standalone/markers.php?marker='
        
        Charset cs_utf8 = Charset.forName("UTF-8");
        StringBuilder sb = new StringBuilder();
        sb.append("var config = {\n");
        sb.append(" url : {\n");
        /* Get configuration URL */
        sb.append("  configuration: '");
        sb.append(core.configuration.getString("url/configuration", login_enabled?"standalone/configuration.php":"standalone/dynmap_config.json?_={timestamp}"));
        sb.append("',\n");
        /* Get update URL */
        sb.append("  update: '");
        sb.append(core.configuration.getString("url/update", login_enabled?"standalone/update.php?world={world}&ts={timestamp}":"standalone/dynmap_{world}.json?_={timestamp}"));
        sb.append("',\n");
        /* Get sendmessage URL */
        sb.append("  sendmessage: '");
        sb.append(core.configuration.getString("url/sendmessage", "standalone/sendmessage.php"));
        sb.append("',\n");
        /* Get login URL */
        sb.append("  login: '");
        sb.append(core.configuration.getString("url/login", "standalone/login.php"));
        sb.append("',\n");
        /* Get register URL */
        sb.append("  register: '");
        sb.append(core.configuration.getString("url/register", "standalone/register.php"));
        sb.append("',\n");
        /* Get tiles URL */
        sb.append("  tiles: '");
        sb.append(core.configuration.getString("url/tiles", login_enabled?"standalone/tiles.php?tile=":"tiles/"));
        sb.append("',\n");
        /* Get markers URL */
        sb.append("  markers: '");
        sb.append(core.configuration.getString("url/markers", login_enabled?"standalone/markers.php?marker=":"tiles/"));
        sb.append("'\n }\n};\n");
        
        byte[] outputBytes = sb.toString().getBytes(cs_utf8);
        File f = getStandaloneFile("config.js");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(outputBytes);
        } catch (IOException iox) {
            Log.severe("Exception while writing " + f.getPath(), iox);
        } finally {
            if(fos != null) {
                try {
                    fos.close();
                } catch (IOException x) {}
                fos = null;
            }
        }
    }
    
    private static final int RETRY_LIMIT = 5;
    protected void writeConfiguration() {
        JSONObject clientConfiguration = new JSONObject();
        core.events.trigger("buildclientconfiguration", clientConfiguration);
        last_confighash = core.getConfigHashcode();
        
        byte[] content = clientConfiguration.toJSONString().getBytes(cs_utf8);

        File outputFile, outputNewFile, outputOldFile;
        if(core.isLoginSupportEnabled()) {
            outputFile = getStandaloneFile("dynmap_config.php");
            outputNewFile = getStandaloneFile("dynmap_config.new.php");
            outputOldFile = getStandaloneFile("dynmap_config.old.php");
        }
        else {
            outputFile = getStandaloneFile("dynmap_config.json");
            outputNewFile = getStandaloneFile("dynmap_config.json.new");
            outputOldFile = getStandaloneFile("dynmap_config.json.old");
        }
        
        enqueueFileWrite(outputFile, outputNewFile, outputOldFile, content, core.isLoginSupportEnabled());
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
            clientUpdate.include_all_users = true;
            core.events.trigger("buildclientupdate", clientUpdate);

            File outputFile;
            File outputNewFile;
            File outputOldFile;

            if(core.isLoginSupportEnabled()) {
                outputFile = getStandaloneFile("updates_" + dynmapWorld.getName() + ".php");
                outputNewFile = getStandaloneFile("updates_" + dynmapWorld.getName() + ".new.php");
                outputOldFile = getStandaloneFile("updates_" + dynmapWorld.getName() + ".old.php");
            }
            else {
                outputFile = getStandaloneFile("dynmap_" + dynmapWorld.getName() + ".json");
                outputNewFile = getStandaloneFile("dynmap_" + dynmapWorld.getName() + ".json.new");
                outputOldFile = getStandaloneFile("dynmap_" + dynmapWorld.getName() + ".json.old");
            }
            byte[] content = Json.stringifyJson(update).getBytes(cs_utf8);

            enqueueFileWrite(outputFile, outputNewFile, outputOldFile, content, core.isLoginSupportEnabled());
        }
    }
    
    private byte[] loginhash = new byte[16];
    
    protected void writeLogins() {
        File loginFile = getStandaloneFile("dynmap_login.php");

        if(core.isLoginSupportEnabled()) {
            String s = core.getLoginPHP();
            if(s != null) {
                byte[] bytes = s.getBytes(cs_utf8);
                md.reset();
                byte[] hash = md.digest(bytes);
                if(Arrays.equals(hash, loginhash)) {
                    return;
                }
                File loginNewFile = getStandaloneFile("dynmap_login.new.php");
                File loginOldFile = getStandaloneFile("dynmap_login.old.php");

                enqueueFileWrite(loginFile, loginNewFile, loginOldFile, bytes, false);
                loginhash = hash;
            }
        }
        else {
            loginFile.delete();
        }
    }

    private byte[] accesshash = new byte[16];

    protected void writeAccess() {
        File accessFile = getStandaloneFile("dynmap_access.php");

        String s = core.getAccessPHP();
        if(s != null) {
            byte[] bytes = s.getBytes(cs_utf8);
            md.reset();
            byte[] hash = md.digest(bytes);
            if(Arrays.equals(hash, accesshash)) {
                return;
            }
            File accessNewFile = getStandaloneFile("dynmap_access.new.php");
            File accessOldFile = getStandaloneFile("dynmap_access.old.php");

            enqueueFileWrite(accessFile, accessNewFile, accessOldFile, bytes, false);
            accesshash = hash;
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
                    long cts;
                    try {
                        cts = Long.parseLong(ts);
                    } catch (NumberFormatException nfx) {
                        try {
                            cts = (long) Double.parseDouble(ts);
                        } catch (NumberFormatException nfx2) {
                            cts = 0;
                        }
                    }
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
                                    if(chat_perms && !core.getServer().checkPlayerPermission(name, "webchat")) {
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
                            if(chat_perms && !core.getServer().checkPlayerPermission(uid, "webchat")) {
                                Log.info("Rejected web chat from " + uid + ": not permitted");
                                ok = false;
                            }
                            name = uid;
                        }
                        if(ok) {
                            String message = String.valueOf(o.get("message"));
                            if((lengthlimit > 0) && (message.length() > lengthlimit))
                                message = message.substring(0, lengthlimit);
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
            BufferedReader br = null;
            ArrayList<String> lines = new ArrayList<String>();
            try {
                fstream = new FileInputStream(regFile);
                br = new BufferedReader(new InputStreamReader(fstream));
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
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException x) {
                    }
                    br = null;
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

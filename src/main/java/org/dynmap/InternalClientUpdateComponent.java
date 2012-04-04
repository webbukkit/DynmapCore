package org.dynmap;

import java.util.concurrent.Callable;

import org.dynmap.servlet.ClientUpdateServlet;
import org.dynmap.servlet.SendMessageServlet;
import org.json.simple.JSONObject;
import static org.dynmap.JSONUtils.*;

public class InternalClientUpdateComponent extends ClientUpdateComponent {

    public InternalClientUpdateComponent(final DynmapCore dcore, final ConfigurationNode configuration) {
        super(dcore, configuration);
        dcore.addServlet("/up/world/*", new ClientUpdateServlet(dcore));

        final Boolean allowwebchat = configuration.getBoolean("allowwebchat", false);
        final Boolean hidewebchatip = configuration.getBoolean("hidewebchatip", false);
        final Boolean trust_client_name = configuration.getBoolean("trustclientname", false);
        final float webchatInterval = configuration.getFloat("webchat-interval", 1);
        final String spammessage = dcore.configuration.getString("spammessage", "You may only chat once every %interval% seconds.");
        final Boolean use_player_ip = configuration.getBoolean("use-player-login-ip", true);
        final Boolean req_player_ip = configuration.getBoolean("require-player-login-ip", false);
        final Boolean block_banned_player_chat = configuration.getBoolean("block-banned-player-chat", false);
        final Boolean req_login = configuration.getBoolean("webchat-requires-login", false);
        final Boolean chat_perm = configuration.getBoolean("webchat-permissions", false);
        final int length_limit = configuration.getInteger("chatlengthlimit", 256);

        dcore.events.addListener("buildclientconfiguration", new Event.Listener<JSONObject>() {
            @Override
            public void triggered(JSONObject t) {
                s(t, "allowwebchat", allowwebchat);
                s(t, "webchat-interval", webchatInterval);
                s(t, "webchat-requires-login", req_login);
                s(t, "chatlengthlimit", length_limit);
            }
        });

        if (allowwebchat) {
            @SuppressWarnings("serial")
            SendMessageServlet messageHandler = new SendMessageServlet() {{
                maximumMessageInterval = (int)(webchatInterval * 1000);
                spamMessage = "\""+spammessage+"\"";
                hideip = hidewebchatip;
                this.trustclientname = trust_client_name;
                this.use_player_login_ip = use_player_ip;
                this.require_player_login_ip = req_player_ip;
                this.check_user_ban = block_banned_player_chat;
                this.require_login = req_login;
                this.chat_perms = chat_perm;
                this.lengthlimit = length_limit;
                this.core = dcore;
                
                onMessageReceived.addListener(new Event.Listener<Message> () {
                    @Override
                    public void triggered(Message t) {
                        core.webChat(t.name, t.message);
                    }
                });
            }};
            dcore.addServlet("/up/sendmessage", messageHandler);
        }
    }

}

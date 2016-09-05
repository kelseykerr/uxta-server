package com.impulsecontrol.lend.firebase;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jivesoftware.smack.packet.Message;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.util.UUID;
import org.json.JSONObject;
import org.jivesoftware.smackx.gcm.packet.GcmPacketExtension;
import org.jivesoftware.smack.roster.Roster;



/**
 * Created by kerrk on 8/31/16.
 * CCS = cloud connection server
 */
public class CcsServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CcsServer.class);

    private String fcmServer;

    private int fcmPort;

    private XMPPTCPConnection connection;

    // project number from google developers console
    private String projectNumber;

    // package name of client app
    private static final String CATEGORY = "superstartupteam.nearby";

    // GCM (FCM) registration id for test phone
    private String regId;

    // API key for google cloud messaging (GCM)/ firebase cloud messaging (FCM)
    private String apiKey;

    private String senderId;

    /**
     * Indicates whether the connection is in draining state, which means that it
     * will not accept any new downstream messages.
     */
    protected volatile boolean connectionDraining = false;

    public CcsServer() {

    }

    public CcsServer(String server, int port, String projectNum, String key, String sender) {
        fcmServer = server;
        fcmPort = port;
        projectNumber = projectNum;
        apiKey = key;
        senderId = sender;
    }

    /**
     * Sends a downstream message to FCM.
     *
     * @return true if the message has been successfully sent.
     */
    public boolean sendDownstreamMessage(String jsonRequest)
            throws SmackException.NotConnectedException, InterruptedException {

        if (!connectionDraining) {
            send(jsonRequest);
            return true;
        }

        LOGGER.info("Dropping downstream message since the connection is draining");
        return false;
    }

    /**
     * Sends a packet with contents provided.
     */
    protected void send(String jsonRequest) throws SmackException.NotConnectedException, InterruptedException {

        Stanza request = new Message();
        request.addExtension(new GcmPacketExtension(jsonRequest));

        connection.sendStanza(request);
    }

    /**
     * Handles an upstream data message from a device application.
     * <p>
     * <p>This sample echo server sends an echo message back to the device.
     * Subclasses should override this method to properly process upstream messages.
     */
    protected void handleUpstreamMessage(JSONObject json) {
        // PackageName of the application that sent this message.
        String category = json.get("category").toString();

        if (!category.equals(category)) {
            LOGGER.error("Incoming message category - " + category + ", not from my app !!!");
            return;
        }

        String from = json.get("from").toString();
        JSONObject payload = new JSONObject(json.get("data"));

        payload.put("ECHO", "Application: " + category);

        // Send an ECHO response back
        String echo = createJsonMessage(from, nextMessageId(), payload,
                "echo:CollapseKey", null, false);

        try {
            sendDownstreamMessage(echo);
        } catch (SmackException.NotConnectedException e) {
            LOGGER.error("Not connected anymore, got error: " + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * Handles an ACK.
     * <p>
     * <p>Logs a INFO message, but subclasses could override it to
     * properly handle ACKs.
     */
    protected void handleAckReceipt(JSONObject json) {
        String messageId = json.get("message_id").toString();
        String from = json.get("from").toString();
        LOGGER.info("handleAckReceipt() from: " + from + ",messageId: " + messageId);
    }

    /**
     * Handles a NACK.
     * <p>
     * <p>Logs a INFO message, but subclasses could override it to
     * properly handle NACKs.
     */
    protected void handleNackReceipt(JSONObject json) {
        String messageId = json.get("message_id").toString();
        String from = json.get("from").toString();
        LOGGER.info("handleNackReceipt() from: " + from + ",messageId: " + messageId);
    }

    protected void handleControlMessage(JSONObject json) {
        LOGGER.info("handleControlMessage(): " + json);
        String controlType = json.get("control_type").toString();
        if ("CONNECTION_DRAINING".equals(controlType)) {
            connectionDraining = true;
        } else {
            LOGGER.info("Unrecognized control type: %s. This could happen if new features are " + "added to the CCS protocol.",
                    controlType);
        }
    }

    /**
     * Connects to FCM Cloud Connection Server using the supplied credentials.
     */
    public void connect()
            throws XMPPException, IOException, SmackException, InterruptedException {

        XMPPTCPConnectionConfiguration config =
                XMPPTCPConnectionConfiguration.builder()
                        .setServiceName("localhost:8080")
                        .setHost(fcmServer)
                        .setPort(fcmPort)
                        .setCompressionEnabled(false)
                        .setConnectTimeout(30000)
                        .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
                        .setSendPresence(false)
                        .setSocketFactory(SSLSocketFactory.getDefault())
                        .build();

        connection = new XMPPTCPConnection(config);

        ReconnectionManager.getInstanceFor(connection).setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.RANDOM_INCREASING_DELAY);
        //disable Roster as I don't think this is supported by GCM
        Roster.getInstanceFor(connection).setRosterLoadedAtLogin(false);

        connection.addConnectionListener(connectionStatusLogger);
        // Handle incoming packets
        connection.addAsyncStanzaListener(incomingStanzaListener, stanzaFilter);
        // Log all outgoing packets
        connection.addPacketInterceptor(outgoingStanzaInterceptor, stanzaFilter);

        LOGGER.info("Connecting...");
        connection.connect();
        connection.login(senderId + "@gcm.googleapis.com", apiKey);
    }

    private final StanzaFilter stanzaFilter = new StanzaFilter() {

        @Override
        public boolean accept(Stanza stanza) {

            if (stanza.getClass() == Stanza.class)
                return true;
            else {
                if (stanza.getTo() != null)
                    if (stanza.getTo().toString().startsWith(projectNumber))
                        return true;
            }

            return false;
        }
    };

    private final StanzaListener incomingStanzaListener = new StanzaListener() {

        @Override
        public void processPacket(Stanza packet) {

            LOGGER.info("Received: " + packet.toXML());

            GcmPacketExtension gcmPacketExtension = GcmPacketExtension.from(packet);

            String jsonString = gcmPacketExtension.getJson();

            try {

                JSONObject json = new JSONObject(jsonString);

                // present for "ack"/"nack", null otherwise
                String messageType = json.get("message_type").toString();

                if (messageType == null) {
                    // Normal upstream data message
                    handleUpstreamMessage(json);

                    // Send ACK to CCS
                    String messageId = json.get("message_id").toString();
                    String from = json.get("from").toString();
                    String ack = createJsonAck(from, messageId);
                    send(ack);
                } else if ("ack".equals(messageType)) {
                    // Process Ack
                    handleAckReceipt(json);
                } else if ("nack".equals(messageType)) {
                    // Process Nack
                    handleNackReceipt(json);
                } else if ("control".equals(messageType)) {
                    // Process control message
                    handleControlMessage(json);
                } else {
                    LOGGER.error("Unrecognized message type (%s)", messageType);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process packet", e);
            }
        }
    };

    private final StanzaListener outgoingStanzaInterceptor = new StanzaListener() {
        @Override
        public void processPacket(Stanza packet) {
            LOGGER.info("Sent: {0} " + packet.toXML());
        }
    };

    private static final ConnectionListener connectionStatusLogger = new ConnectionListener() {

        @Override
        public void connected(XMPPConnection xmppConnection) {
            LOGGER.info("Connected");
        }

        @Override
        public void reconnectionSuccessful() {
            LOGGER.info("Reconnected");
        }

        @Override
        public void reconnectionFailed(Exception e) {
            LOGGER.warn("Reconnection failed.. " + e.getMessage());
        }

        @Override
        public void reconnectingIn(int seconds) {
            LOGGER.info("Reconnecting in %d secs " + seconds);
        }

        @Override
        public void connectionClosedOnError(Exception e) {
            LOGGER.error("Connection closed on error.. " + e.getMessage());
        }

        @Override
        public void connectionClosed() {
            LOGGER.info("Connection closed");
        }

        @Override
        public void authenticated(XMPPConnection arg0, boolean arg1) {
            LOGGER.info("authenticated");
        }
    };

    /**
     * Creates a JSON encoded FCM message.
     *
     * @param to             RegistrationId of the target device (Required).
     * @param messageId      Unique messageId for which CCS sends an
     *                       "ack/nack" (Required).
     * @param payload        Message content intended for the application. (Optional).
     * @param collapseKey    GCM collapse_key parameter (Optional).
     * @param timeToLive     GCM time_to_live parameter (Optional).
     * @param delayWhileIdle GCM delay_while_idle parameter (Optional).
     * @return JSON encoded GCM message.
     */
    public static String createJsonMessage(String to, String messageId,
                                           JSONObject payload, String collapseKey, Long timeToLive,
                                           Boolean delayWhileIdle) {

        JSONObject message = new JSONObject();

        message.put("to", to);
        if (collapseKey != null) {
            message.put("collapse_key", collapseKey);
        }
        if (timeToLive != null) {
            message.put("time_to_live", timeToLive);
        }
        if (delayWhileIdle != null && delayWhileIdle) {
            message.put("delay_while_idle", true);
        }
        message.put("message_id", messageId);
        message.put("data", payload);

        return message.toString();
    }

    /**
     * Creates a JSON encoded ACK message for an upstream message received
     * from an application.
     *
     * @param to        RegistrationId of the device who sent the upstream message.
     * @param messageId messageId of the upstream message to be acknowledged to CCS.
     * @return JSON encoded ack.
     */
    public static String createJsonAck(String to, String messageId) {
        JSONObject message = new JSONObject();
        message.put("message_type", "ack");
        message.put("to", to);
        message.put("message_id", messageId);

        return message.toString();
    }

    /**
     * Returns a random message id to uniquely identify a message.
     * <p>
     * <p>Note: This is generated by a pseudo random number generator for
     * illustration purpose, and is not guaranteed to be unique.
     */
    public static String nextMessageId() {
        return "rd-" + UUID.randomUUID().toString() + "-rc";
    }



}

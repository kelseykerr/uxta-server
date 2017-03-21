package com.iuxta.nearby.firebase;

import com.iuxta.nearby.dto.ResponseDto;
import com.iuxta.nearby.exception.InternalServerException;
import com.iuxta.nearby.model.User;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by kerrk on 10/12/16.
 */
public class FirebaseUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseUtils.class);

    public enum NotificationTypes {
        request_notification, offer_closed, offer_accepted, response_update, exchange_confirmed,
        cancelled_transaction, payment_confirmed
    }

    /**
     * We will send all android users "data messages" and all iOS users "notifications". Android will construct
     * a notification if the app is running in the background, and show a snackbar message if it's in the foreground
     *
     * @param recipient
     * @param dto
     * @param dataMessage
     * @param ccsServer
     */
    public static void sendFcmMessage(User recipient, ResponseDto dto, JSONObject dataMessage, CcsServer ccsServer) {
        if (recipient.getFcmRegistrationId() == null) {
            String msg = "could not send notification/message to [" + recipient.getFirstName() + "] " +
                    "because they have not allowed messages.";
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
        String messageId = CcsServer.nextMessageId();
        JSONObject payload = new JSONObject();

        /*//TODO: rethink this, send users in-app messages?
        if (hasMessage(dto)) {
            payload.put("message", dto.messages.get(0).getContent());
        }*/
        LOGGER.info("attempting to send message/notification to user [" + recipient.getName() + "] with fcm token [" +
                recipient.getFcmRegistrationId() + "].");
        // if the user is using an ios device, send a notification instead of a data message
        boolean sendNotification = StringUtils.isNotBlank(recipient.getUserAgent()) &&
                !recipient.getUserAgent().toLowerCase().contains("android");
        String jsonMessage = null;
        if (sendNotification) {
            payload.put("body", dataMessage.get("message"));
            payload.put("title", dataMessage.get("title"));
            jsonMessage = CcsServer.createJsonMessage(recipient.getFcmRegistrationId(), messageId, dataMessage,
                    payload, null, null, null);
        } else {
            jsonMessage = CcsServer.createJsonMessage(recipient.getFcmRegistrationId(), messageId, dataMessage,
                    payload, null, null, null);
        }
        try {
            Boolean sent = ccsServer.sendDownstreamMessage(jsonMessage);
            if (sent) {
                LOGGER.info("Successfully sent message!");
            } else {
                LOGGER.error("could not sent message :(");
            }
        } catch (Exception e) {
            String msg = "could not send message, got error: " + e.getMessage();
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
    }
}

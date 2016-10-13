package com.impulsecontrol.lend.firebase;

import com.impulsecontrol.lend.dto.ResponseDto;
import com.impulsecontrol.lend.exception.InternalServerException;
import com.impulsecontrol.lend.model.User;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by kerrk on 10/12/16.
 */
public class FirebaseUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseUtils.class);


    public static void sendFcmMessage(User recipient, ResponseDto dto, JSONObject notification, CcsServer ccsServer) {
        if (recipient.getFcmRegistrationId() == null) {
            String msg = "could not send notification/message to [" + recipient.getFirstName() + "] " +
                    "because they have not allowed messages.";
            LOGGER.error(msg);
            throw new InternalServerException(msg);
        }
        String messageId = CcsServer.nextMessageId();
        JSONObject payload = new JSONObject();

        /*//TODO: rethink this, should we send messages separately?
        if (hasMessage(dto)) {
            payload.put("message", dto.messages.get(0).getContent());
        }*/
        // we will currently send everything as a message and the client can construct the notification when needed

        LOGGER.info("attempting to send message/notification to user [" + recipient.getName() + "] with fcm token [" +
                recipient.getFcmRegistrationId() + "].");
        String jsonMessage = CcsServer.createJsonMessage(recipient.getFcmRegistrationId(), messageId, notification,
                payload, null, null, null);
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

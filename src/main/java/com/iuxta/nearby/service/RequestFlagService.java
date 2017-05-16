package com.iuxta.nearby.service;

import com.iuxta.nearby.dto.RequestFlagDto;
import com.iuxta.nearby.exception.NotAllowedException;
import com.iuxta.nearby.exception.NotFoundException;
import com.iuxta.nearby.firebase.CcsServer;
import com.iuxta.nearby.firebase.FirebaseUtils;
import com.iuxta.nearby.model.Request;
import com.iuxta.nearby.model.RequestFlag;
import com.iuxta.nearby.model.Response;
import com.iuxta.nearby.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Created by kelseykerr on 5/15/17.
 */
public class RequestFlagService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestFlagService.class);

    private JacksonDBCollection<Request, String> requestCollection;
    private JacksonDBCollection<RequestFlag, String> requestFlagCollection;
    private JacksonDBCollection<User, String> userCollection;
    private CcsServer ccsServer;


    public RequestFlagService(JacksonDBCollection<Request, String> requestCollection, JacksonDBCollection<RequestFlag, String> requestFlagCollection, JacksonDBCollection<User, String> userCollection, CcsServer ccsServer) {
        this.requestCollection = requestCollection;
        this.requestFlagCollection = requestFlagCollection;
        this.userCollection = userCollection;
        this.ccsServer = ccsServer;
    }

    public void canCreateNewFlag(User user, String requestId) {
        //if the user created a flag that is pending review, they cannot create a new flag
        BasicDBObject query = new BasicDBObject();
        query.put("reporterId", user.getId());
        query.put("requestId", requestId);
        query.put("status", RequestFlag.Status.PENDING.toString());
        DBCursor requestFlags  = requestFlagCollection.find(query);
        List<RequestFlag> flags = requestFlags.toArray();
        requestFlags.close();
        if (flags.size() > 0) {
            LOGGER.error("User [" + user.getId() + " attempted to re-flag request [" + requestId + "]");
            throw new NotAllowedException("You have already flagged this request & we will review in soon. Thanks!");
        }
    }

    public RequestFlag createRequestFlag(User user, RequestFlagDto requestFlagDto, String requestId) {
        Request request = requestCollection.findOneById(requestId);
        if (request == null) {
            String msg = "Could not flag request because request with id [" + requestId + "] was not found.";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        canCreateNewFlag(user, requestId);
        RequestFlag flag = new RequestFlag();
        flag.setRequestId(requestId);
        flag.setReporterId(user.getId());
        flag.setReporterNotes(requestFlagDto.reporterNotes);
        flag.setStatus(RequestFlag.Status.PENDING);
        flag.setReportedDate(new Date());
        sendAdminFlagNotification(request);
        WriteResult newFlag = requestFlagCollection.insert(flag);
        flag = (RequestFlag) newFlag.getSavedObject();
        return flag;
    }

    private void sendAdminFlagNotification(Request request) {
        DBObject findAdmins = new BasicDBObject("admin", true);
        DBCursor cursor = userCollection.find(findAdmins);
        List<User> admins = cursor.toArray();
        if (admins != null && admins.size() > 0) {
            JSONObject notification = new JSONObject();
            notification.put("title", "Request has been flagged!");
            String body = "Request [" + request.getId() + " - " + request.getItemName() + "] has been flagged! Review ASAP!";
            notification.put("message", body);
            notification.put("type", FirebaseUtils.NotificationTypes.new_user_notification.name());
            for (User admin:admins) {
                FirebaseUtils.sendFcmMessage(admin, null, notification, ccsServer);
            }        }
    }
}

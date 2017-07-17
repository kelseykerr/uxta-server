package com.iuxta.uxta.service;

import com.iuxta.uxta.firebase.CcsServer;
import com.iuxta.uxta.firebase.FirebaseUtils;
import com.iuxta.uxta.model.Community;
import com.iuxta.uxta.model.Request;
import com.iuxta.uxta.model.User;
import com.iuxta.uxta.resources.CommunitiesResource;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by kelseykerr on 7/9/17.
 */
public class CommunityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommunityService.class);

    private JacksonDBCollection<Community, String> communitiesCollection;
    private JacksonDBCollection<User, String> userCollection;
    private CcsServer ccsServer;


    public CommunityService(JacksonDBCollection<Community, String> communitiesCollection,
                            JacksonDBCollection<User, String> userCollection, CcsServer ccsServer) {
        this.communitiesCollection = communitiesCollection;
        this.userCollection = userCollection;
        this.ccsServer = ccsServer;
    }

    public List<Community> getCommunities(String term) {
        if (term == null || term.isEmpty()) {
            DBCursor communitiesCursor = communitiesCollection.find().limit(20);
            List<Community> communities = communitiesCursor.toArray();
            communitiesCursor.close();
            return communities;
        } else {
            BasicDBObject query = new BasicDBObject();
            BasicDBObject searchQuery = new BasicDBObject();
            searchQuery.append("$search", term);
            query.put("$text", searchQuery);
            DBCursor communitiesCursor = communitiesCollection.find().limit(20);
            List<Community> communities = communitiesCursor.toArray();
            communitiesCursor.close();
            return communities;
        }

    }

    public Community getCommunityById(String id) {
        Community community = communitiesCollection.findOneById(id);
        if (community == null) {
            String msg = "Community [" + id + "] was not found.";
            LOGGER.error(msg);
            throw new com.iuxta.uxta.exception.NotFoundException(msg);
        }
        return community;
    }

    public User requestAccess(String communityId, User user) {
        Community community = communitiesCollection.findOneById(communityId);
        if (community == null) {
            String msg = "Community [" + communityId + "] was not found.";
            LOGGER.error(msg);
            throw new com.iuxta.uxta.exception.NotFoundException(msg);
        }
        user.setCommunityId(communityId);
        userCollection.save(user);
        sendAdminsCommunityRequestNotification(user, community);
        return user;
    }

    public User removeAccess(String communityId, User user) {
        Community community = communitiesCollection.findOneById(communityId);
        if (community == null) {
            String msg = "Community [" + communityId + "] was not found.";
            LOGGER.error(msg);
            throw new com.iuxta.uxta.exception.NotFoundException(msg);
        }
        if (user.getCommunityId().equalsIgnoreCase(communityId)) {
            user.setCommunityId(null);
        }
        userCollection.save(user);
        sendAdminsCommunityRemoveNotification(user, community);
        return user;
    }

    public void sendAdminsCommunityRequestNotification(User user, Community community) {
        try {
            DBObject findAdmins = new BasicDBObject("admin", true);
            DBCursor cursor = userCollection.find(findAdmins);
            List<User> admins = cursor.toArray();
            if (admins != null && admins.size() > 0) {
                JSONObject notification = new JSONObject();
                notification.put("title", "Request to join community!");
                String body = "User [" + user.getName() + "] joined community [" + community.getId() + " -  " + community.getName() + "]!";
                notification.put("message", body);
                notification.put("type", FirebaseUtils.NotificationTypes.new_post_notification.name());
                for (User admin:admins) {
                    FirebaseUtils.sendFcmMessage(admin, null, notification, ccsServer);
                }        }
        } catch (Exception e) {
            //do nothing
        }

    }

    public void sendAdminsCommunityRemoveNotification(User user, Community community) {
        try {
            DBObject findAdmins = new BasicDBObject("admin", true);
            DBCursor cursor = userCollection.find(findAdmins);
            List<User> admins = cursor.toArray();
            if (admins != null && admins.size() > 0) {
                JSONObject notification = new JSONObject();
                notification.put("title", "Request to LEAVE community!");
                String body = "User [" + user.getName() + "] left community [" + community.getId() + " -  " + community.getName() + "]!";
                notification.put("message", body);
                notification.put("type", FirebaseUtils.NotificationTypes.new_post_notification.name());
                for (User admin:admins) {
                    FirebaseUtils.sendFcmMessage(admin, null, notification, ccsServer);
                }        }
        } catch (Exception e) {
            //do nothing
        }

    }
}

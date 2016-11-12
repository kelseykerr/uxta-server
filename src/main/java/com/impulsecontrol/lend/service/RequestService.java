package com.impulsecontrol.lend.service;

import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.exception.BadRequestException;
import com.impulsecontrol.lend.exception.NotFoundException;
import com.impulsecontrol.lend.firebase.CcsServer;
import com.impulsecontrol.lend.firebase.FirebaseUtils;
import com.impulsecontrol.lend.model.Category;
import com.impulsecontrol.lend.model.GeoJsonPoint;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by kerrk on 7/27/16.
 */
public class RequestService {

    private JacksonDBCollection<Category, String> categoriesCollection;
    private JacksonDBCollection<Request, String> requestCollection;
    private JacksonDBCollection<User, String> userCollection;
    private CcsServer ccsServer;
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestService.class);
    static final long ONE_MINUTE_IN_MILLIS=60000;

    public RequestService() {

    }

    public RequestService(JacksonDBCollection<Category, String> categoriesCollection,
                          JacksonDBCollection<Request, String> requestsCollection,
                          CcsServer ccsServer,
                          JacksonDBCollection<User, String> userCollection) {
        this.categoriesCollection = categoriesCollection;
        this.requestCollection = requestsCollection;
        this.userCollection = userCollection;
        this.ccsServer = ccsServer;
    }

    public Request transformRequestDto(RequestDto dto, User user) {
        Request request = new Request();
        request.setUser(user);
        request.setPostDate(dto.postDate != null ? dto.postDate : new Date());
        populateRequest(request, dto);
        request.setStatus(Request.Status.OPEN);
        return request;
    }

    public void populateRequest(Request request, RequestDto dto) {
        if (dto.itemName.isEmpty()) {
            String msg = "Could not create request because name cannot be empty";
            LOGGER.error(msg);
            throw new BadRequestException(msg);
        }
        request.setItemName(dto.itemName);
        request.setExpireDate(dto.expireDate);
        if (dto.expireDate != null && dto.expireDate.before(new Date())) {
            request.setStatus(Request.Status.CLOSED);
        }
        if (dto.category != null) {
            Category category = categoriesCollection.findOneById(dto.category.id);
            if (category == null) {
                throw new NotFoundException("Could not create request because category ["
                        + dto.category.id + "] was not found.");
            }
            request.setCategory(category);
        }
        if (dto.type != null) {
            try {
                request.setType(Request.Type.valueOf(dto.type));
            } catch (IllegalArgumentException e) {
                throw new NotFoundException("Could not create request because type [" + dto.type + "] is not recognized");
            }
        }
        request.setRental(dto.rental);
        request.setDescription(dto.description);
        GeoJsonPoint loc = new GeoJsonPoint(dto.longitude, dto.latitude);
        request.setLocation(loc);
    }

    public Double milesToMeters(Double radiusInMiles) {
        return radiusInMiles * 1609.344;
    }

    public void sendRecentRequestsNotification(User user, Double longitude, Double latitude) {
        LOGGER.info("Fetching recent requests for user [" + user.getId() + "]");
        JSONObject notification = new JSONObject();
        notification.put("title", "Recent Requests");
        notification.put("type", "request_notification");
        String body = "";
        boolean singleNearbyRequest = false;
        boolean multipleNearbyRequests = false;
        boolean newRequests = false;
        if (user.getCurrentLocationNotifications()) {
            if (longitude == null || latitude == null) {
                String msg = "query parameters [radius], [longitude] and [latitude] are required.";
                LOGGER.error(msg);
                throw new BadRequestException(msg);
            }
            BasicDBObject query = getLocationQuery(latitude, longitude, user.getNotificationRadius());
            setNotExpiredQuery(query);
            addNotMineQuery(query, user.getUserId());
            addLast15MinsQuery(query);
            DBCursor userRequests = requestCollection.find(query);
            List<Request> requestsNearby = userRequests.toArray();
            if (requestsNearby.size() > 1) {
                body += "There are " + requestsNearby.size() + " new requests in your area";
                multipleNearbyRequests = true;
                newRequests = true;
            } else if (requestsNearby.size() == 1) {
                Request req = requestsNearby.get(0);
                body += req.getUser().getFirstName() + " requested a " + req.getItemName() + ". Can you help out?";
                singleNearbyRequest = true;
                newRequests = true;
            }
            userRequests.close();
        }
        if (user.getHomeLocationNotifications()) {
            BasicDBObject query = getLocationQuery(user.getHomeLocation().getCoordinates()[1],
                    user.getHomeLocation().getCoordinates()[0], user.getNotificationRadius());
            setNotExpiredQuery(query);
            addNotMineQuery(query, user.getUserId());
            addLast15MinsQuery(query);
            DBCursor userRequests = requestCollection.find(query);
            List<Request> requestsNearHome = userRequests.toArray();
            Integer size = requestsNearHome.size();
            if (size > 1) {
                body += singleNearbyRequest ?
                        ("There are also " + size + " new requests near your home.") :
                        multipleNearbyRequests ? ( "and " + size + " requests near your home") :
                                ("There are " + size + " new requests near your home");
                newRequests = true;
            } else if (requestsNearHome.size() == 1) {
                Request req = requestsNearHome.get(0);
                body += (multipleNearbyRequests ? ". " : " ") +  req.getUser().getFirstName() + " requested a " +
                        req.getItemName() + ". Can you help out?";
                newRequests = true;
            }
            userRequests.close();
        }
        LOGGER.info("Notification for user [" + user.getId() + "]: " + body);
        notification.put("message", body);
        if (newRequests) {
            FirebaseUtils.sendFcmMessage(user, null, notification, ccsServer);
        }

    }

    private BasicDBObject getLocationQuery(Double latitude, Double longitude, Double radius) {
        BasicDBObject geometry = new BasicDBObject();
        geometry.append("type", "Point");
        double[] coords = {longitude, latitude};
        geometry.append("coordinates", coords);

        BasicDBObject near = new BasicDBObject();
        near.append("$geometry", geometry);
        near.append("$maxDistance", milesToMeters(radius));

        BasicDBObject location = new BasicDBObject();
        location.append("$near", near);

        BasicDBObject query = new BasicDBObject();
        query.append("location", location);
        return query;
    }

    public List<Request> findRequests(Double latitude, Double longitude, Double radius, Boolean expired, Boolean includeMine, User principal) {
        BasicDBObject query = getLocationQuery(latitude, longitude, radius);

        if (expired != null && expired) {
            BasicDBObject expiredQuery = new BasicDBObject();
            expiredQuery.append("$lte", new Date());
            query.put("expireDate", expiredQuery);
        } else if (expired != null && !expired) {
            setNotExpiredQuery(query);
        }

        if (includeMine != null && !includeMine) {
            BasicDBObject notMineQuery = new BasicDBObject();
            notMineQuery.append("$ne", principal.getUserId());
            query.put("user.userId", notMineQuery);
        }

        DBCursor userRequests = requestCollection.find(query).sort(new BasicDBObject("postDate", -1));
        List<Request> requests = userRequests.toArray();
        userRequests.close();
        //update the user's info
        requests.stream().forEach(r -> {
            User requester = userCollection.findOneById(r.getUser().getId());
            r.setUser(requester);
            requestCollection.save(r);
        });
        return requests;
    }

    private void setNotExpiredQuery(BasicDBObject query) {
        BasicDBList or = new BasicDBList();
        or.add(getNotExpiredQuery());
        or.add(getNoExpireDateQuery());
        query.put("$or", or);
    }

    private BasicDBObject getNotExpiredQuery() {
        // expire date is after current date
        BasicDBObject notExpiredQuery = new BasicDBObject();
        notExpiredQuery.append("$gt", new Date());
        BasicDBObject query = new BasicDBObject();
        query.append("expireDate", notExpiredQuery);
        return query;
    }

    private BasicDBObject getNoExpireDateQuery() {
        // expire date is not set
        BasicDBObject notSetQuery = new BasicDBObject();
        notSetQuery.append("$exists", false);
        BasicDBObject noExpireDateQuery = new BasicDBObject();
        noExpireDateQuery.append("expireDate", notSetQuery);
        return noExpireDateQuery;
    }

    private void addLast15MinsQuery(BasicDBObject query) {
        Calendar date = Calendar.getInstance();
        long t= date.getTimeInMillis();
        Date last15 = new Date(t - (15 * ONE_MINUTE_IN_MILLIS));
        BasicDBObject last15Query = new BasicDBObject();
        last15Query.append("$gt", last15);
        query.append("postDate", last15Query);
    }

    private void addNotMineQuery(BasicDBObject query, String userId) {
        BasicDBObject notMineQuery = new BasicDBObject();
        notMineQuery.append("$ne", userId);
        query.put("user.userId", notMineQuery);
    }
}

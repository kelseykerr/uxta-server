package com.iuxta.nearby.service;

import com.iuxta.nearby.NearbyUtils;
import com.iuxta.nearby.dto.RequestDto;
import com.iuxta.nearby.exception.BadRequestException;
import com.iuxta.nearby.exception.LocationNotAvailableException;
import com.iuxta.nearby.exception.NotFoundException;
import com.iuxta.nearby.firebase.CcsServer;
import com.iuxta.nearby.firebase.FirebaseUtils;
import com.iuxta.nearby.model.*;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by kerrk on 7/27/16.
 */
public class RequestService {

    private JacksonDBCollection<Category, String> categoriesCollection;
    private JacksonDBCollection<Request, String> requestCollection;
    private JacksonDBCollection<User, String> userCollection;
    private JacksonDBCollection<NearbyAvailableLocations, String> availableLocationsCollection;
    JacksonDBCollection<UnavailableSearches, String> unavailableSearchesCollection;
    private CcsServer ccsServer;
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestService.class);
    static final long ONE_MINUTE_IN_MILLIS=60000;
    public static final Double LOCATION_RADIUS = 25D;
    private ResponseService responseService;

    public RequestService() {

    }

    public RequestService(JacksonDBCollection<Category, String> categoriesCollection,
                          JacksonDBCollection<Request, String> requestsCollection,
                          CcsServer ccsServer,
                          JacksonDBCollection<User, String> userCollection,
                          ResponseService responseService,
                          JacksonDBCollection<NearbyAvailableLocations, String> locationsCollection,
                          JacksonDBCollection<UnavailableSearches, String> unavailableSearchesCollection) {
        this.categoriesCollection = categoriesCollection;
        this.requestCollection = requestsCollection;
        this.userCollection = userCollection;
        this.ccsServer = ccsServer;
        this.responseService = responseService;
        this.availableLocationsCollection = locationsCollection;
        this.unavailableSearchesCollection = unavailableSearchesCollection;
    }

    public Request transformRequestDto(RequestDto dto, User user) {
        Request request = new Request();
        request.setUser(user);
        request.setPostDate(dto.postDate != null ? dto.postDate : new Date());
        populateRequest(request, dto);
        request.setStatus(Request.Status.OPEN);
        request.setInappropriate(false);
        request.setPhotos(dto.photos);
        return request;
    }

    /**
     * Returns true if the user can make a new request. User CANNOT make a new request if they have 5 or more
     * open requests
     * @param user
     * @return
     */
    public boolean canCreateRequest(User user) {
        BasicDBObject query = new BasicDBObject();
        query.put("user.userId", user.getUserId());
        query.put("status", Request.Status.OPEN.name());
        DBCursor userRequests  = requestCollection.find(query);
        List<Request> userRs = userRequests.toArray();
        int openTs = responseService.getOpenTransactions(user);
        int totalOpen = openTs + userRs.size();
        String msg = "User [" + user.getFirstName() + ":" + user.getId() + "] has [" +
                userRequests.size() + "] open requests and [" + openTs + "] open transactions.";
        if (userRs.size() < NearbyUtils.MAX_OPEN_REQUESTS && (totalOpen < 20)) {
            LOGGER.info(msg);
            return true;
        } else {
            LOGGER.info("Cannot make request: " + msg);
            return false;
        }
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
            responseService.alertRespondersOfClosedRequest(request);
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
        request.setPhotos(dto.photos);
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
        notification.put("type", FirebaseUtils.NotificationTypes.request_notification.name());
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
            setAppropriateQuery(query);
            setNotBlockedQuery(query, user);
            setNotExpiredQuery(query);
            addNotMineQuery(query, user.getUserId());
            setRequestingQuery(query);
            addLast15MinsQuery(query);
            setRequestingQuery(query);
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
            setAppropriateQuery(query);
            setNotBlockedQuery(query, user);
            setNotExpiredQuery(query);
            addNotMineQuery(query, user.getUserId());
            setRequestingQuery(query);
            addLast15MinsQuery(query);
            DBCursor userRequests = requestCollection.find(query);
            List<Request> requestsNearHome = userRequests.toArray();
            Integer size = requestsNearHome.size();
            if (size > 1) {
                body += singleNearbyRequest ?
                        ("There are also " + size + " new requests near your home.") :
                        multipleNearbyRequests ? ( " and " + size + " new requests near your home!") :
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

    public void checkLocationIsAvailable(Double latitude, Double longitude) {
        //must be within 25 miles
        BasicDBObject query = getLocationQuery(latitude, longitude, LOCATION_RADIUS);
        DBCursor availableLocations = availableLocationsCollection.find(query);
        List<NearbyAvailableLocations> locations = availableLocations.toArray();
        availableLocations.close();
        if (locations.size() == 0) {
            UnavailableSearches search = new UnavailableSearches();
            GeoJsonPoint loc = new GeoJsonPoint(longitude, latitude);
            search.setLocation(loc);
            unavailableSearchesCollection.insert(search);
            throw new LocationNotAvailableException("Nearby is not available in this location yet");
        }
    }

    public List<Request> findRequests(Integer offset, Integer limit, Double latitude, Double longitude, Double radius, Boolean expired,
                                      Boolean includeMine, String searchTerm, String sort, User principal, String type) {
        checkLocationIsAvailable(latitude, longitude);
        BasicDBObject query = getLocationQuery(latitude, longitude, radius);
        offset = (offset != null ? offset : 0);
        limit = (limit == null || limit > NearbyUtils.MAX_LIMIT) ? NearbyUtils.DEFAULT_LIMIT : limit;
        setAppropriateQuery(query);
        setNotBlockedQuery(query, principal);
        query.put("duplicate", false);

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

        if (StringUtils.isNotBlank(type) && type.equalsIgnoreCase("requests")) {
            setRequestingQuery(query);
        } else if (StringUtils.isNotBlank(type) && type.equalsIgnoreCase("offers")) {
            setOffersQuery(query);
        }

        query.put("status", "OPEN");

        DBCursor userRequests;
        if (sort != null && sort.equals("newest")) {
            if (StringUtils.isBlank(searchTerm)) {
                //go ahead and add offset and limit here
                userRequests  = requestCollection.find(query)
                        .sort(new BasicDBObject("postDate", -1))
                        .skip(offset)
                        .limit(limit);
            } else {
                userRequests  = requestCollection.find(query).sort(new BasicDBObject("postDate", -1));
            }
        } else {
            // distance is the default sort, best match should also use this for the initial query
            if (StringUtils.isBlank(searchTerm)) {
                //go ahead and add offset and limit here
                userRequests = requestCollection.find(query).skip(offset).limit(limit);
            } else {
                userRequests = requestCollection.find(query);
            }
        }
        List<Request> requests = userRequests.toArray();
        userRequests.close();

        if (searchTerm != null && !searchTerm.isEmpty() && requests.size() > 0) {
            query = new BasicDBObject();
            BasicDBObject searchQuery = new BasicDBObject();
            searchQuery.append("$search", searchTerm);
            query.put("$text", searchQuery);

            BasicDBObject inQuery = new BasicDBObject();
            List<ObjectId> ids = requests.stream().map(r -> new ObjectId(r.getId())).collect(Collectors.toList());
            inQuery.put("$in", ids);
            query.put("_id", inQuery);
            if (sort != null && sort.equals("newest")) {
                userRequests = requestCollection.find(query)
                        .sort(new BasicDBObject("postDate", -1))
                        .skip(offset)
                        .limit(limit);
            } else if (sort != null && sort.equals("distance")) {
                // get those that match search in any order
                userRequests = requestCollection.find(query);
                requests = userRequests.toArray();
                userRequests.close();
                ids = requests.stream().map(r -> new ObjectId(r.getId())).collect(Collectors.toList());
                //redo location query on the matching results, this will automatically be ordered by closest distance
                query = getLocationQuery(latitude, longitude, radius);
                inQuery = new BasicDBObject();
                inQuery.put("$in", ids);
                query.put("_id", inQuery);
                userRequests = requestCollection.find(query).skip(offset).limit(limit);
            } else {
                BasicDBObject scoreProjection = new BasicDBObject();
                scoreProjection.append("$meta", "textScore");
                BasicDBObject projectionParent = new BasicDBObject();
                projectionParent.put("score", scoreProjection);
                userRequests = requestCollection.find(query, projectionParent)
                        .sort(new BasicDBObject("score", scoreProjection))
                        .skip(offset)
                        .limit(limit);
            }
            requests = userRequests.toArray();
            userRequests.close();
        }
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

    private BasicDBObject setAppropriateQuery(BasicDBObject query) {
        BasicDBObject notTrueQuery = new BasicDBObject();
        notTrueQuery.append("$ne", true);
        query.put("inappropriate", notTrueQuery);
        return query;
    }

    private BasicDBObject setNotBlockedQuery(BasicDBObject query, User principal) {
        if (principal.getBlockedUsers() == null) {
            return query;
        }
        BasicDBObject blockedUserIdsQuery = new BasicDBObject();
        List<ObjectId> blockedIds = principal.getBlockedUsers().stream().map(r -> new ObjectId(r)).collect(Collectors.toList());
        blockedUserIdsQuery.put("$nin", blockedIds);
        query.put("user._id", blockedUserIdsQuery);
        return query;
    }

    private BasicDBObject setRequestingQuery(BasicDBObject query) {
        BasicDBObject inQuery = new BasicDBObject();
        List<String> types = Stream.of("buying", "renting").collect(Collectors.toList());
        inQuery.put("$in", types);
        query.put("type", inQuery);
        return query;
    }

    private BasicDBObject setOffersQuery(BasicDBObject query) {
        BasicDBObject inQuery = new BasicDBObject();
        List<String> types = Stream.of("loaning", "selling").collect(Collectors.toList());
        inQuery.put("$in", types);
        query.put("type", inQuery);
        return query;
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

    public void sendAdminsNewRequestNotification(Request r) {
        try {
            DBObject findAdmins = new BasicDBObject("admin", true);
            DBCursor cursor = userCollection.find(findAdmins);
            List<User> admins = cursor.toArray();
            if (admins != null && admins.size() > 0) {
                JSONObject notification = new JSONObject();
                notification.put("title", "New Post!");
                String body = "User [" + r.getUser().getName() + "] added a [" + r.getType().toString() + "] post for a [" + r.getItemName() + "]!";
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

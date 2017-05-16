package com.iuxta.nearby.service;

import com.iuxta.nearby.dto.PaymentDto;
import com.iuxta.nearby.dto.UserDto;
import com.iuxta.nearby.dto.UserFlagDto;
import com.iuxta.nearby.exception.InternalServerException;
import com.iuxta.nearby.exception.NotFoundException;
import com.iuxta.nearby.firebase.CcsServer;
import com.iuxta.nearby.firebase.FirebaseUtils;
import com.iuxta.nearby.model.*;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by kerrk on 8/19/16.
 */
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private StripeService stripeService;
    private ResponseService responseService;
    private JacksonDBCollection<User, String> userCollection;
    private JacksonDBCollection<UserFlag, String> userFlagCollection;
    private CcsServer ccsServer;

    public UserService(StripeService stripeService,
                       ResponseService responseService,
                       JacksonDBCollection<User, String> userCollection,
                       JacksonDBCollection<UserFlag, String> userFlagCollection,
                       CcsServer ccsServer) {
        this.stripeService = stripeService;
        this.userCollection = userCollection;
        this.userFlagCollection = userFlagCollection;
        this.ccsServer = ccsServer;
        this.responseService = responseService;
    }


    public User updateUser(User user, UserDto dto) {
        user.setFirstName(dto.firstName);
        user.setLastName(dto.lastName);
        user.setName(dto.fullName);
        user.setEmail(dto.email);
        user.setPhone(dto.phone);
        user.setAddress(dto.address);
        user.setAddressLine2(dto.addressLine2);
        user.setCity(dto.city);
        user.setState(dto.state);
        user.setZip(dto.zip);
        if (StringUtils.isNotBlank(dto.address)) {
            setHomeLatLng(user, dto);
        }
        user.setNewRequestNotificationsEnabled(dto.newRequestNotificationsEnabled);
        user.setNotificationRadius(dto.notificationRadius);
        user.setCurrentLocationNotifications(dto.currentLocationNotifications);
        user.setHomeLocationNotifications(dto.homeLocationNotifications);
        user.setNotificationKeywords(dto.notificationKeywords);
        user.setDateOfBirth(dto.dateOfBirth);
        if (dto.tosAccepted == true && (user.getTosAccepted() == null || user.getTosAccepted() == false)) {
            user.setTosAccepted(dto.tosAccepted);
            user.setTimeTosAccepted(new Date());
            user.setTosAcceptIp(dto.tosAcceptIp);
        }
        user.setPictureUrl(dto.pictureUrl);
        return user;
    }


    public static Double[] getLatLongPositions(String address) throws Exception {
        int responseCode = 0;
        String api = "http://maps.googleapis.com/maps/api/geocode/xml?address=" + URLEncoder.encode(address, "UTF-8") +
                "&sensor=true";
        URL url = new URL(api);
        HttpURLConnection httpConnection = (HttpURLConnection)url.openConnection();
        httpConnection.connect();
        responseCode = httpConnection.getResponseCode();
        if(responseCode == 200) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(httpConnection.getInputStream());
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile("/GeocodeResponse/status");
            String status = (String)expr.evaluate(document, XPathConstants.STRING);
            if(status.equals("OK")) {
                expr = xpath.compile("//geometry/location/lat");
                String latitude = (String)expr.evaluate(document, XPathConstants.STRING);
                Double lat = Double.parseDouble(latitude);
                expr = xpath.compile("//geometry/location/lng");
                String longitude = (String)expr.evaluate(document, XPathConstants.STRING);
                Double lng = Double.parseDouble(longitude);
                return new Double[] {lat, lng};
            } else {
                throw new Exception("Error from the API - response status: " + status);
            }
        }
        return null;
    }

    private void setHomeLatLng(User user, UserDto dto) {
        String fullAddress = dto.address;
        if (StringUtils.isNotBlank(dto.addressLine2)) {
            fullAddress += (" " + dto.addressLine2);
        }
        if (StringUtils.isNotBlank(dto.city)) {
            fullAddress += (" " + dto.city);
        }
        if (StringUtils.isNotBlank(dto.state)) {
            fullAddress += (" " + dto.state);
        }
        if (StringUtils.isNotBlank(dto.zip)) {
            fullAddress += (" " + dto.zip);
        }
        try {
            Double[] latLng = getLatLongPositions(fullAddress);
            if (latLng != null) {
                GeoJsonPoint loc = new GeoJsonPoint(latLng[1], latLng[0]);
                user.setHomeLocation(loc);
            } else {
                LOGGER.info("No geolocation found for address [" + fullAddress + "].");
            }
        } catch (Exception e){
            String msg = "Unable to calculate latitude and longitude from address [" + fullAddress + "].";
            LOGGER.error(msg + " For user [" + dto.id + "].");
            throw new InternalServerException(msg);
        }
    }

    public PaymentDto getUserPaymentInfo(User user) {
        return stripeService.getPaymentDetails(user);
    }

    public UserFlag blockUser(User user, UserFlagDto dto, String flaggedUser) {
        User blockedUser = userCollection.findOneById(flaggedUser);
        if (blockedUser == null) {
            String msg = "Unable to block user [" + flaggedUser + "] because user was not found.";
            LOGGER.error(msg);
            throw new NotFoundException(msg);
        }
        //add each user to the other's blocked users
        addBlockedUser(user, flaggedUser);
        addBlockedUser(blockedUser, user.getId());
        //add a flag to the database
        UserFlag userFlag = new UserFlag();
        userFlag.setReportedDate(new Date());
        userFlag.setReporterId(user.getId());
        userFlag.setUserId(flaggedUser);
        userFlag.setReporterNotes(dto.reporterNotes);
        userFlag.setStatus(UserFlag.Status.PENDING);
        sendAdminFlagNotification(blockedUser);
        WriteResult newFlag = userFlagCollection.insert(userFlag);
        userFlag = (UserFlag) newFlag.getSavedObject();
        responseService.closeResponsesFromBlockedUsers(user, blockedUser);
        return userFlag;
    }

    public void addBlockedUser(User user, String userToBlock) {
        List<String> blockedUsers = user.getBlockedUsers();
        if (blockedUsers == null) {
            blockedUsers = new ArrayList<String>();
        }
        blockedUsers.add(userToBlock);
        user.setBlockedUsers(blockedUsers);
        userCollection.save(user);
    }

    private void sendAdminFlagNotification(User user) {
        DBObject findAdmins = new BasicDBObject("admin", true);
        DBCursor cursor = userCollection.find(findAdmins);
        List<User> admins = cursor.toArray();
        if (admins != null && admins.size() > 0) {
            JSONObject notification = new JSONObject();
            notification.put("title", "User has been flagged!");
            String body = "User [" + user.getId() + " - " + user.getName() + "] has been flagged! Review ASAP!";
            notification.put("message", body);
            notification.put("type", FirebaseUtils.NotificationTypes.new_user_notification.name());
            for (User admin:admins) {
                FirebaseUtils.sendFcmMessage(admin, null, notification, ccsServer);
            }        }
    }
}

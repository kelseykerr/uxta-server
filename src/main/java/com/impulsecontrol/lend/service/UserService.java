package com.impulsecontrol.lend.service;

import com.impulsecontrol.lend.dto.PaymentDto;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.exception.InternalServerException;
import com.impulsecontrol.lend.model.GeoJsonPoint;
import com.impulsecontrol.lend.model.User;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Date;

/**
 * Created by kerrk on 8/19/16.
 */
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private StripeService stripeService;

    public UserService(StripeService stripeService) {
        this.stripeService = stripeService;
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

    // Payment form: credit card
    // Merchant payments (getting paid): bank account or venmo
    public void getPaymentDetails(User user) {
    }

    public PaymentDto getUserPaymentInfo(User user) {
        PaymentDto dto = new PaymentDto();
        return dto;
    }
}

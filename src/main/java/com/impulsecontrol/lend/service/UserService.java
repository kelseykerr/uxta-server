package com.impulsecontrol.lend.service;

import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.FundingRequest;
import com.braintreegateway.IndividualRequest;
import com.braintreegateway.MerchantAccount;
import com.braintreegateway.MerchantAccountRequest;
import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.exception.IllegalArgumentException;
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

/**
 * Created by kerrk on 8/19/16.
 */
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private BraintreeService braintreeService;

    public UserService(BraintreeService braintreeService) {
        this.braintreeService = braintreeService;
    }


    public User updateUser(User user, UserDto dto) {
        MerchantAccountRequest braintreeRequest = new MerchantAccountRequest();
        IndividualRequest individualRequest = braintreeRequest.individual();
        user.setFirstName(dto.firstName);
        individualRequest.firstName(dto.firstName);
        user.setLastName(dto.lastName);
        individualRequest.lastName(dto.lastName);
        user.setName(dto.fullName);
        user.setEmail(dto.email);
        individualRequest.email(dto.email);
        user.setPhone(dto.phone);
        individualRequest.phone(dto.phone);
        user.setAddress(dto.address);
        individualRequest.address().streetAddress(dto.address);
        user.setAddressLine2(dto.addressLine2);
        user.setCity(dto.city);
        individualRequest.address().locality(dto.city);
        user.setState(dto.state);
        individualRequest.address().region(dto.state);
        user.setZip(dto.zip);
        individualRequest.address().postalCode(dto.zip);
        individualRequest.address().done();
        if (StringUtils.isNotBlank(dto.address)) {
            setHomeLatLng(user, dto);
        }
        user.setNewRequestNotificationsEnabled(dto.newRequestNotificationsEnabled);
        user.setNotificationRadius(dto.notificationRadius);
        user.setCurrentLocationNotifications(dto.currentLocationNotifications);
        user.setHomeLocationNotifications(dto.homeLocationNotifications);
        user.setNotificationKeywords(dto.notificationKeywords);
        user.setDateOfBirth(dto.dateOfBirth);
        individualRequest.dateOfBirth(dto.dateOfBirth);
        individualRequest.done();
        user.setTosAccepted(dto.tosAccepted);
        braintreeRequest.tosAccepted(dto.tosAccepted);
        if (dto.fundDestination != null && dto.tosAccepted) {
            saveMerchantAccount(user, dto, braintreeRequest);
        } if (dto.paymentMethodNonce != null) {
            saveCustomerAccount(user, dto);
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
            }
            LOGGER.info("No geolocation found for address [" + fullAddress + "].");
        } catch (Exception e){
            String msg = "Unable to calculate latitude and longitude from address [" + fullAddress + "].";
            LOGGER.error(msg + " For user [" + dto.id + "].");
            throw new InternalServerException(msg);
        }
    }

    private void saveCustomerAccount(User user, UserDto userDto) {
        CustomerRequest request = new CustomerRequest()
                .firstName(userDto.firstName)
                .lastName(userDto.lastName)
                .phone(userDto.phone)
                .email(userDto.email)
                .paymentMethodNonce(userDto.paymentMethodNonce);

        Customer customer = braintreeService.saveOrUpdateCustomer(request, userDto.customerId);
        user.setCustomerId(customer.getId());
    }

    private void saveMerchantAccount(User user, UserDto dto, MerchantAccountRequest braintreeRequest) {
        FundingRequest fundingRequest = braintreeRequest.funding();
        String destination = dto.fundDestination.toString();
        fundingRequest.destination(MerchantAccount.FundingDestination.valueOf(destination));
        switch (destination) {
            case "email": {
                if (dto.email == null) {
                    String msg = "You selected [email] as the destination for your Nearby funds, " +
                            "but did not enter an email address!";
                    LOGGER.error(dto.id + " " + msg);
                    throw new IllegalArgumentException(msg);
                }
                fundingRequest.email(dto.email);
                break;
            }
            case "mobile_phone": {
                if (dto.phone == null) {
                    String msg = "You selected [mobile phone] as the destination for your Nearby funds, " +
                            "but did not enter a phone number!";
                    LOGGER.error(dto.id + " " + msg);
                    throw new IllegalArgumentException(msg);
                }
                fundingRequest.mobilePhone(dto.phone.replace("-", ""));
                break;
            }
            case "bank": {
                if (dto.bankRoutingNumber == null || dto.bankAccountNumber == null) {
                    String msg = "To make bank deposits, you must provide both your routing number and your account number";
                    LOGGER.error(dto.id + " " + msg);
                    throw new IllegalArgumentException(msg);
                }
                fundingRequest.accountNumber(dto.bankAccountNumber);
                fundingRequest.routingNumber(dto.bankRoutingNumber);
                break;
            }
        }
        fundingRequest.done();
        if (user.getMerchantId() != null) {
            braintreeService.updateMerchantAccount(braintreeRequest, user.getMerchantId());
        } else {
            MerchantAccount ma = braintreeService.createNewMerchantAccount(braintreeRequest);
            user.setMerchantId(ma.getId());
        }

    }
}

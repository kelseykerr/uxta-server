package com.iuxta.nearby.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.security.Principal;
import java.util.Date;
import java.util.List;

/**
 * Created by kerrk on 7/5/16.
 */
public class User implements Serializable, Principal {

    @ObjectId
    @JsonProperty("_id")
    private String id;

    @NotNull
    private String firstName;

    @NotNull
    private String lastName;

    @NotNull
    private String userId; //this is the facebook user id

    private String fcmRegistrationId;

    private String name;


    @Pattern(regexp = ".+@.+\\..+",
            message = "you entered an invalid email address")
    private String email;

    @Pattern(regexp = "^\\d{3}-\\d{3}-\\d{4}$",
            message = "phone  number must be in the format: XXX-XXX-XXXXX")
    private String phone;

    private String address;

    private String addressLine2;

    private String city;

    @Size(max = 2)
    private String state;

    private String zip;

    private GeoJsonPoint homeLocation;

    private Boolean newRequestNotificationsEnabled;

    private Double notificationRadius;

    private List<String> notificationKeywords;

    private Boolean currentLocationNotifications;

    private Boolean homeLocationNotifications;

    private String dateOfBirth;

    // the user must accept the tos before getting a stripe account and being able to make requests/responses
    private Boolean tosAccepted;

    private String pictureUrl;

    //either facebook or google
    private String authMethod;

    private Date timeTosAccepted;

    private String tosAcceptIp;

    private String stripeManagedAccountId;

    private String stripeCustomerId;

    private String stripeSecretKey;

    private String stripePublishableKey;

    private String userAgent;

    private Date createdDate;

    private List<String> blockedUsers;

    public User() {}

    public User(String firstName, String lastName, String userId) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.userId = userId;
        this.name = this.firstName + " " + this.lastName;
    }

    @ObjectId
    @JsonProperty("_id")
    public String getId() {
        return id;
    }

    @ObjectId
    @JsonProperty("_id")
    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getFcmRegistrationId() {
        return fcmRegistrationId;
    }

    public void setFcmRegistrationId(String fcmRegistrationId) {
        this.fcmRegistrationId = fcmRegistrationId;
    }

    public GeoJsonPoint getHomeLocation() {
        return homeLocation;
    }

    public void setHomeLocation(GeoJsonPoint homeLocation) {
        this.homeLocation = homeLocation;
    }

    public Boolean getNewRequestNotificationsEnabled() {
        return newRequestNotificationsEnabled;
    }

    public void setNewRequestNotificationsEnabled(Boolean newRequestNotificationsEnabled) {
        this.newRequestNotificationsEnabled = newRequestNotificationsEnabled;
    }

    public Double getNotificationRadius() {
        return notificationRadius;
    }

    public void setNotificationRadius(Double notificationRadius) {
        this.notificationRadius = notificationRadius;
    }

    public List<String> getNotificationKeywords() {
        return notificationKeywords;
    }

    public void setNotificationKeywords(List<String> notificationKeywords) {
        this.notificationKeywords = notificationKeywords;
    }

    public Boolean getCurrentLocationNotifications() {
        return currentLocationNotifications;
    }

    public void setCurrentLocationNotifications(Boolean currentLocationNotifications) {
        this.currentLocationNotifications = currentLocationNotifications;
    }

    public Boolean getHomeLocationNotifications() {
        return homeLocationNotifications;
    }

    public void setHomeLocationNotifications(Boolean homeLocationNotifications) {
        this.homeLocationNotifications = homeLocationNotifications;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Boolean getTosAccepted() {
        return tosAccepted;
    }

    public void setTosAccepted(Boolean tosAccepted) {
        this.tosAccepted = tosAccepted;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public void setPictureUrl(String pictureUrl) {
        this.pictureUrl = pictureUrl;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public Date getTimeTosAccepted() {
        return timeTosAccepted;
    }

    public void setTimeTosAccepted(Date timeTosAccepted) {
        this.timeTosAccepted = timeTosAccepted;
    }

    public String getTosAcceptIp() {
        return tosAcceptIp;
    }

    public void setTosAcceptIp(String topAcceptIp) {
        this.tosAcceptIp = topAcceptIp;
    }

    public String getStripeManagedAccountId() {
        return stripeManagedAccountId;
    }

    public void setStripeManagedAccountId(String stripeManagedAccountId) {
        this.stripeManagedAccountId = stripeManagedAccountId;
    }

    public String getStripeSecretKey() {
        return stripeSecretKey;
    }

    public void setStripeSecretKey(String stripeSecretKey) {
        this.stripeSecretKey = stripeSecretKey;
    }

    public String getStripePublishableKey() {
        return stripePublishableKey;
    }

    public void setStripePublishableKey(String stripePublishableKey) {
        this.stripePublishableKey = stripePublishableKey;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public List<String> getBlockedUsers() {
        return blockedUsers;
    }

    public void setBlockedUsers(List<String> blockedUsers) {
        this.blockedUsers = blockedUsers;
    }
}

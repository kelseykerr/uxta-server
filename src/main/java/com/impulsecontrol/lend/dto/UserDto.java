package com.impulsecontrol.lend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.impulsecontrol.lend.model.User;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Created by kerrk on 8/17/16.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {

    public String id;

    public String userId;

    public String firstName;

    public String lastName;

    public String fullName;

    @Pattern(regexp = ".+@.+\\..+",
    message = "you entered an invalid email address")
    public String email;

    @Pattern(regexp = "^\\d{3}-\\d{3}-\\d{4}$",
            message = "phone  number must be in the format: XXX-XXX-XXXXX")
    public String phone;

    public String address;

    public String addressLine2;

    public String city;

    @Size(max = 2)
    public String state;

    public String zip;

    public Double homeLongitude;

    public Double homeLatitude;

    public Boolean newRequestNotificationsEnabled;

    public Double notificationRadius;

    public List<String> notificationKeywords;

    public Boolean currentLocationNotifications;

    public Boolean homeLocationNotifications;

    public String merchantId;

    public String merchantStatus;

    public String merchantStatusMessage;

    public String customerId;

    public Boolean isPaymentSetup;

    public String customerStatus;

    public String dateOfBirth;

    public String bankAccountNumber;

    public String bankRoutingNumber;

    public String fundDestination;

    public Boolean tosAccepted;

    public String paymentMethodNonce;

    public Boolean removedMerchantDestination;

    public String pictureUrl;

    //either facebook or google
    public String authMethod;

    public UserDto() {}

    public UserDto(User user) {
        this.id = user.getId();
        this.userId = user.getUserId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.fullName = user.getName();
        this.phone = user.getPhone();
        this.pictureUrl = user.getPictureUrl();
        this.authMethod = user.getAuthMethod();
    }

    public static UserDto getMyUserDto(User user) {
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.userId = user.getUserId();
        dto.firstName = user.getFirstName();
        dto.lastName = user.getLastName();
        dto.fullName = user.getName();
        dto.email = user.getEmail();
        dto.phone = user.getPhone();
        dto.address = user.getAddress();
        dto.addressLine2 = user.getAddressLine2();
        dto.city = user.getCity();
        dto.state = user.getState();
        dto.zip = user.getZip();
        dto.homeLongitude = user.getHomeLocation() != null ? user.getHomeLocation().getCoordinates()[0] : null;
        dto.homeLatitude = user.getHomeLocation() != null ? user.getHomeLocation().getCoordinates()[1] : null;
        dto.newRequestNotificationsEnabled = user.getNewRequestNotificationsEnabled();
        dto.notificationRadius = user.getNotificationRadius();
        dto.notificationKeywords = user.getNotificationKeywords();
        dto.currentLocationNotifications = user.getCurrentLocationNotifications();
        dto.homeLocationNotifications = user.getHomeLocationNotifications();
        dto.merchantId = user.getMerchantId();
        dto.merchantStatus = user.getMerchantStatus();
        dto.merchantStatusMessage = user.getMerchantStatusMessage();
        dto.customerId = user.getCustomerId();
        dto.tosAccepted = user.getTosAccepted();
        dto.paymentMethodNonce = user.getPaymentMethodNonce();
        dto.fundDestination = user.getFundDestination() != null ? user.getFundDestination().toString() : null;
        dto.dateOfBirth = user.getDateOfBirth();
        dto.isPaymentSetup = user.isPaymentSetup();
        dto.customerStatus = user.getCustomerStatus();
        dto.removedMerchantDestination = user.getRemovedMerchantDestination();
        dto.pictureUrl = user.getPictureUrl();
        dto.authMethod = user.getAuthMethod();
        return dto;
    }

    public static UserDto getOtherUserDto(User user) {
        //TODO: discuss what should be public here...perhaps we let the users configure this
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.firstName = user.getFirstName();
        dto.lastName = user.getLastName();
        dto.fullName = user.getName();
        dto.pictureUrl = user.getPictureUrl();
        return dto;
    }
}

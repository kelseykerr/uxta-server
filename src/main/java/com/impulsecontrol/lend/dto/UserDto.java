package com.impulsecontrol.lend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.impulsecontrol.lend.model.User;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

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

    @Max(2)
    public String state;

    public String zip;

    public UserDto() {}

    public UserDto(User user) {
        this.id = user.getId();
        this.userId = user.getUserId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
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
        return dto;
    }

    public static UserDto getOtherUserDto(User user) {
        //TODO: discuss what should be public here...perhaps we let the users configure this
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.firstName = user.getFirstName();
        dto.lastName = user.getLastName();
        dto.fullName = user.getName();
        return dto;
    }
}

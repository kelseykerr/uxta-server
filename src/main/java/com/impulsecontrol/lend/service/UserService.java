package com.impulsecontrol.lend.service;

import com.impulsecontrol.lend.dto.UserDto;
import com.impulsecontrol.lend.model.User;

/**
 * Created by kerrk on 8/19/16.
 */
public class UserService {

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
        return user;
    }
}

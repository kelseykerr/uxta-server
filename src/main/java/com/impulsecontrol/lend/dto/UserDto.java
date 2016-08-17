package com.impulsecontrol.lend.dto;

import com.impulsecontrol.lend.model.User;

import javax.validation.constraints.NotNull;

/**
 * Created by kerrk on 8/17/16.
 */
public class UserDto {

    public String id;

    public String firstName;

    public String lastName;

    public UserDto(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
    }
}

package com.impulsecontrol.lend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.security.Principal;

/**
 * Created by kerrk on 7/5/16.
 */
public class User implements Serializable, Principal {


    private String id;

    @NotNull
    private String firstName;

    @NotNull
    private String lastName;

    @NotNull
    private String userId;

    public User() {

    }

    public User(String firstName, String lastName, String userId) {
        this.firstName = firstName;
        this.lastName = lastName;
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
        return this.firstName + " " + this.getLastName();
    }
}

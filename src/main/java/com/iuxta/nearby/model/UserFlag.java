package com.iuxta.nearby.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Created by kelseykerr on 5/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserFlag extends FlagParent implements Serializable {
    @NotNull
    private String userId;

    private UserFlag.Status status;

    /**
     * PENDING: the report is waiting for someone to review it
     * BANNED: we decided to ban the user for their actions
     * DISMISSED: the user has been blocked from the reporter, but we didn't ban them from the app
     */
    public static enum Status {
        PENDING, INAPPROPRIATE, DISMISSED
    }

    public UserFlag() {

    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}

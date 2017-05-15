package com.iuxta.nearby.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Created by kelseykerr on 5/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestFlag extends FlagParent implements Serializable {

    @NotNull
    private String requestId;

    private Status status;

    public RequestFlag() {

    }


    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }


    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }


    /**
     * PENDING: the report is waiting for someone to review it
     * INAPPROPRIATE: the request is inappropriate and should not be displayed
     * DISMISSED: the request has not been found to be inappropriate
     */
    public static enum Status {
        PENDING, INAPPROPRIATE, DISMISSED
    }
}

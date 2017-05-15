package com.iuxta.nearby.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Created by kelseykerr on 5/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseFlag extends FlagParent implements Serializable {

    @NotNull
    private String responseId;

    private RequestFlag.Status status;

    public ResponseFlag() {

    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public RequestFlag.Status getStatus() {
        return status;
    }

    public void setStatus(RequestFlag.Status status) {
        this.status = status;
    }
}

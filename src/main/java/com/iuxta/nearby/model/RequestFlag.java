package com.iuxta.nearby.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 * Created by kelseykerr on 5/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestFlag implements Serializable {
    private String id;

    @NotNull
    private String requestId;

    @NotNull
    private String reporterId;

    private String reporterNotes;

    private Status status;

    private Date reportedDate;

    private Date reviewedDate;

    private String reviewerNotes;

    public RequestFlag() {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getReporterNotes() {
        return reporterNotes;
    }

    public void setReporterNotes(String reporterNotes) {
        this.reporterNotes = reporterNotes;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Date getReportedDate() {
        return reportedDate;
    }

    public void setReportedDate(Date reportedDate) {
        this.reportedDate = reportedDate;
    }

    public Date getReviewedDate() {
        return reviewedDate;
    }

    public void setReviewedDate(Date reviewedDate) {
        this.reviewedDate = reviewedDate;
    }

    public String getReviewerNotes() {
        return reviewerNotes;
    }

    public void setReviewerNotes(String reviewerNotes) {
        this.reviewerNotes = reviewerNotes;
    }

    /**
     * PENDING: the report is waiting for someone to review it
     * INAPPROPRIATE: the request is inappropriate and should not be displayed
     * DISMISSED: the request has not been found to be inappripriate
     */
    public static enum Status {
        PENDING, INAPPROPRIATE, DISMISSED
    }
}

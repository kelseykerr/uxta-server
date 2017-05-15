package com.iuxta.nearby.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 * Created by kelseykerr on 5/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlagParent implements Serializable {
    private String id;

    @NotNull
    private String reporterId;

    private String reporterNotes;

    private Date reportedDate;

    private Date reviewedDate;

    private String reviewerNotes;

    public FlagParent() {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
}

package com.iuxta.nearby.dto;

import com.iuxta.nearby.model.FlagParent;

import java.util.Date;

/**
 * Created by kelseykerr on 5/15/17.
 */
public class FlagParentDto {
    public String id;

    public String reporterNotes;

    public Date reportedDate;

    public Date reviewedDate;

    public String reviewerNotes;

    public FlagParentDto() {

    }

    public <T extends FlagParent> FlagParentDto(T flagObject) {
        this.id = flagObject.getId();
        this.reporterNotes = flagObject.getReporterNotes();
        this.reportedDate = flagObject.getReportedDate();
        this.reviewerNotes = flagObject.getReviewerNotes();
        this.reportedDate = flagObject.getReviewedDate();
    }


}

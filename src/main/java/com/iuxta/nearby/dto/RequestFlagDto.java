package com.iuxta.nearby.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iuxta.nearby.model.RequestFlag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by kelseykerr on 5/15/17.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestFlagDto extends FlagParentDto {

    public String requestId;

    public String status;

    public RequestFlagDto() {

    }

    public RequestFlagDto(RequestFlag requestFlag) {
        super(requestFlag);
        //this.id = requestFlag.getId();
        this.requestId = requestFlag.getRequestId();
        //this.reporterId = requestFlag.getReporterId();
       // this.reporterNotes = requestFlag.getReporterNotes();
        this.status = requestFlag.getStatus() != null ? requestFlag.getStatus().toString() : null;
        /*this.reportedDate = requestFlag.getReportedDate();
        this.reviewedDate = requestFlag.getReviewedDate();
        this.reviewerNotes = requestFlag.getReviewerNotes();*/
    }

    public static List<RequestFlagDto> transform(List<RequestFlag> flags) {
        return flags.stream()
                .map(f -> new RequestFlagDto(f)).collect(Collectors.toList());
    }
}

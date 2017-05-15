package com.iuxta.nearby.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iuxta.nearby.model.RequestFlag;
import com.iuxta.nearby.model.ResponseFlag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by kelseykerr on 5/15/17.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseFlagDto extends FlagParentDto {

    public String responseId;

    public String status;

    public ResponseFlagDto() {

    }

    public ResponseFlagDto(ResponseFlag responseFlag) {
        super(responseFlag);
        this.responseId = responseFlag.getResponseId();
        this.status = responseFlag.getStatus() != null ? responseFlag.getStatus().toString() : null;
    }

    public static List<ResponseFlagDto> transform(List<ResponseFlag> flags) {
        return flags.stream()
                .map(f -> new ResponseFlagDto(f)).collect(Collectors.toList());
    }
}

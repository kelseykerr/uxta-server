package com.iuxta.nearby.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iuxta.nearby.model.Request;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by kerrk on 7/27/16.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestDto {

    public String id;

    public UserDto user;

    @NotNull
    public String itemName;

    public String communityId;

    public Date postDate;

    public Date expireDate;

    public CategoryDto category;

    public String description;

    @NotNull
    public String type;

    public String status;

    public Boolean inappropriate;

    public Boolean duplicate;

    public List<String> photos;

    public RequestDto() {

    }

    public RequestDto(Request request) {
        this.id = request.getId();
        if (request.getUser() != null) {
            UserDto dto = new UserDto(request.getUser());
            this.user = dto;
        }
        this.itemName = request.getItemName();
        this.communityId = request.getCommunityId();
        this.postDate = request.getPostDate();
        this.expireDate = request.getExpireDate();
        if (request.getCategory() != null) {
            this.category = new CategoryDto(request.getCategory());
        }
        this.description = request.getDescription();
        if (request.getType() != null) {
            this.type = request.getType().toString();
        }
        this.status = request.getStatus() != null ? request.getStatus().toString() : null;
        this.inappropriate = request.getInappropriate() != null ? request.getInappropriate() : false;
        this.type = request.getType() != null ? request.getType().toString() : "renting";
        this.duplicate = request.getDuplicate();
        this.photos = request.getPhotos();
    }

    public static List<RequestDto> transform(List<Request> requests) {
        return requests.stream()
                .map(r -> new RequestDto(r)).collect(Collectors.toList());
    }

}

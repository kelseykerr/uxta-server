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

    @NotNull
    public Double longitude;

    @NotNull
    public Double latitude;

    public Date postDate;

    public Date expireDate;

    public CategoryDto category;

    //can phase this field out since the "type" field will now tell us if we are renting, buying, etc
    @Deprecated
    public Boolean rental;

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
        this.longitude = request.getLocation().getCoordinates()[0];
        this.latitude = request.getLocation().getCoordinates()[1];
        this.postDate = request.getPostDate();
        this.expireDate = request.getExpireDate();
        if (request.getCategory() != null) {
            this.category = new CategoryDto(request.getCategory());
        }
        this.rental = request.getRental();
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

    public static RequestDto PublicRequestDto(Request request) {
        RequestDto requestDto = new RequestDto();
        requestDto.itemName = request.getItemName();
        if (request.getCategory() != null) {
            requestDto.category = new CategoryDto(request.getCategory());
        }
        requestDto.description = request.getDescription();
        if (request.getType() != null) {
            requestDto.type = request.getType().toString();
        }
        requestDto.type = request.getType() != null ? request.getType().toString() : "renting";
        requestDto.photos = request.getPhotos();
        return requestDto;
    }

    public static List<RequestDto> transform(List<Request> requests) {
        return requests.stream()
                .map(r -> new RequestDto(r)).collect(Collectors.toList());
    }

    public static List<RequestDto> transformPublicResults(List<Request> requests) {
        return requests.stream()
                .map(r -> PublicRequestDto(r)).collect(Collectors.toList());
    }
}

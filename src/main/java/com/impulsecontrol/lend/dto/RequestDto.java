package com.impulsecontrol.lend.dto;

import com.impulsecontrol.lend.model.Category;
import com.impulsecontrol.lend.model.GeoJsonPoint;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by kerrk on 7/27/16.
 */
public class RequestDto {

    public String id;

    public User user;

    @NotNull
    public String itemName;

    @NotNull
    public Double longitude;

    @NotNull
    public Double latitude;

    public Date postDate;

    public Date expireDate;

    public Category category;

    @NotNull
    public Boolean rental;

    public String description;

    public RequestDto() {

    }

    public RequestDto(Request request) {
        this.id = request.getId();
        this.user = request.getUser();
        this.itemName = request.getItemName();
        this.longitude = request.getLocation().getCoordinates()[0];
        this.latitude = request.getLocation().getCoordinates()[1];
        this.postDate = request.getPostDate();
        this.expireDate = request.getExpireDate();
        this.category = request.getCategory();
        this.rental = request.getRental();
        this.description = request.getDescription();
    }

    public static List<RequestDto> transform(List<Request> requests) {
        List<RequestDto> dtos = new ArrayList<RequestDto>();
        return requests.stream()
                .map(r -> new RequestDto(r)).collect(Collectors.toList());
    }
}

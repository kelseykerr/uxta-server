package com.impulsecontrol.lend.service;

import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.model.GeoJsonPoint;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;

import java.util.Date;

/**
 * Created by kerrk on 7/27/16.
 */
public class RequestService {

    public Request transformRequestDto(RequestDto dto, User user) {
        Request request = new Request();
        request.setUser(user);
        request.setPostDate(dto.postDate != null ? dto.postDate : new Date());
        populateRequest(request, dto);
        return request;
    }

    public void populateRequest(Request request, RequestDto dto) {
        request.setItemName(dto.itemName);
        request.setExpireDate(dto.expireDate);
        request.setCategory(dto.category);
        request.setRental(dto.rental);
        request.setDescription(dto.description);
        GeoJsonPoint loc = new GeoJsonPoint(dto.longitude, dto.latitude);
        request.setLocation(loc);
    }
}

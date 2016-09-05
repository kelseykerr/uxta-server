package com.impulsecontrol.lend.service;

import com.impulsecontrol.lend.dto.RequestDto;
import com.impulsecontrol.lend.exception.NotFoundException;
import com.impulsecontrol.lend.model.Category;
import com.impulsecontrol.lend.model.GeoJsonPoint;
import com.impulsecontrol.lend.model.Request;
import com.impulsecontrol.lend.model.User;
import org.mongojack.JacksonDBCollection;

import java.util.Date;

/**
 * Created by kerrk on 7/27/16.
 */
public class RequestService {

    private JacksonDBCollection<Category, String> categoriesCollection;

    public RequestService() {

    }

    public RequestService(JacksonDBCollection<Category, String> categoriesCollection) {
        this.categoriesCollection = categoriesCollection;
    }

    public Request transformRequestDto(RequestDto dto, User user) {
        Request request = new Request();
        request.setUser(user);
        request.setPostDate(dto.postDate != null ? dto.postDate : new Date());
        populateRequest(request, dto);
        request.setStatus(Request.Status.OPEN);
        return request;
    }

    public void populateRequest(Request request, RequestDto dto) {
        request.setItemName(dto.itemName);
        request.setExpireDate(dto.expireDate);
        if (dto.category != null) {
            Category category = categoriesCollection.findOneById(dto.category.id);
            if (category == null) {
                throw new NotFoundException("Could not create request because category ["
                        + dto.category.id + "] was not found.");
            }
            request.setCategory(category);
        }
        if (dto.type != null) {
            try {
                request.setType(Request.Type.valueOf(dto.type));
            } catch (IllegalArgumentException e) {
                throw new NotFoundException("Could not create request because type [" + dto.type + "] is not recognized");
            }
        }
        request.setRental(dto.rental);
        request.setDescription(dto.description);
        GeoJsonPoint loc = new GeoJsonPoint(dto.longitude, dto.latitude);
        request.setLocation(loc);
    }
}

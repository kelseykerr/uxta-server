package com.impulsecontrol.lend.dto;

import com.impulsecontrol.lend.model.Category;
import com.impulsecontrol.lend.model.User;
import org.geojson.Point;

import javax.validation.constraints.NotNull;
import java.util.Date;

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
}

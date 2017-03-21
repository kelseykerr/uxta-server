package com.iuxta.nearby.model;

import javax.ws.rs.QueryParam;

/**
 * Created by kerrk on 7/27/16.
 */
public class SearchParams {

    @QueryParam("latitude")
    private Double latitude;

    @QueryParam("longitude")
    private Double longitude;

    @QueryParam("radius")
    private Double radius;

    public SearchParams() {

    }

    public SearchParams(Double latitude, Double longitude, Double radius) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getRadius() {
        return radius;
    }

    public void setRadius(Double radius) {
        this.radius = radius;
    }
}

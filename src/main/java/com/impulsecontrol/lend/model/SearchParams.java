package com.impulsecontrol.lend.model;

import java.lang.annotation.Annotation;

/**
 * Created by kerrk on 7/27/16.
 */
public class SearchParams implements CustomParams {

    public Double latitude;

    public Double longitude;

    public Double radius;

    public SearchParams() {

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

    public Class<? extends Annotation> annotationType() {
        return null;
    }

}

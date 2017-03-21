package com.iuxta.nearby.model;

import java.io.Serializable;

/**
 * Created by kerrk on 7/27/16.
 */
public class GeoJsonPoint implements Serializable {

    private String type = "Point";

    //always stored as longitude, latitude
    private Double[] coordinates = new Double[2];

    public GeoJsonPoint() {

    }

    public GeoJsonPoint(Double longitude, Double latitude) {
        coordinates[0] = longitude;
        coordinates[1] = latitude;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double[] getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Double[] coordinates) {
        this.coordinates = coordinates;
    }
}

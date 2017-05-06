package com.iuxta.nearby.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.constraints.NotNull;

/**
 * Created by kelseykerr on 5/6/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnavailableSearches {
    @NotNull
    private GeoJsonPoint location;

    public GeoJsonPoint getLocation() {
        return location;
    }

    public void setLocation(GeoJsonPoint location) {
        this.location = location;
    }
}

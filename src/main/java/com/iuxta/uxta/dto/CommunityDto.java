package com.iuxta.uxta.dto;

import com.iuxta.uxta.model.Category;
import com.iuxta.uxta.model.Community;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by kelseykerr on 7/9/17.
 */
public class CommunityDto {

    public String id;

    public String name;

    public String description;

    public String address;

    public CommunityDto() {

    }

    public CommunityDto(Community community) {
        this.id = community.getId() != null ? community.getId() : null;
        this.name = community.getName();
        this.description = community.getDescription();
        this.address = community.getAddress();
    }

    public static List<CommunityDto> transform(List<Community> communities) {
        return communities.stream().map(c -> new CommunityDto(c)).collect(Collectors.toList());
    }
}

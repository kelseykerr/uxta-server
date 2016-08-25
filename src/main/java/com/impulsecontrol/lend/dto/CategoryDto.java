package com.impulsecontrol.lend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.impulsecontrol.lend.model.Category;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by kerrk on 8/24/16.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryDto {

    public String id;

    public String name;

    public List<String> examples;

    public CategoryDto() {

    }

    public CategoryDto(Category category) {
        this.id = category.getId() != null ? category.getId() : null;
        this.name = category.getName();
        this.examples = category.getExamples();
    }

    public static List<CategoryDto> transform(List<Category> categories) {
        return categories.stream().map(c -> new CategoryDto(c)).collect(Collectors.toList());
    }
}

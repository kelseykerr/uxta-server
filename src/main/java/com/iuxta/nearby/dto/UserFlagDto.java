package com.iuxta.nearby.dto;

import com.iuxta.nearby.model.UserFlag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by kelseykerr on 5/15/17.
 */
public class UserFlagDto extends FlagParentDto {

    public String userId;

    public String status;

    public UserFlagDto() {

    }

    public UserFlagDto(UserFlag flag) {
        super(flag);
        this.userId = flag.getUserId();
        this.status = flag.getStatus() != null ? flag.getStatus().toString() : null;
    }

    public static List<UserFlagDto> transform(List<UserFlag> flags) {
        return flags.stream()
                .map(f -> new UserFlagDto(f)).collect(Collectors.toList());
    }
}

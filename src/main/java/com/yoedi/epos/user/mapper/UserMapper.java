package com.yoedi.epos.user.mapper;

import com.yoedi.epos.user.User;
import com.yoedi.epos.user.dto.UserListItemResponse;
import com.yoedi.epos.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "isActive", source = "active")
    UserListItemResponse toListItem(User user);
}

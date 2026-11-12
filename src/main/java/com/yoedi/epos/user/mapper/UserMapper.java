package com.yoedi.epos.user.mapper;

import com.yoedi.epos.user.User;
import com.yoedi.epos.user.dto.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}

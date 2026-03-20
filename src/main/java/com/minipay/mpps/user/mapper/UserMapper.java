package com.minipay.mpps.user.mapper;

import com.minipay.mpps.user.User;
import com.minipay.mpps.user.dto.UserResponse;

public class UserMapper {
    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFirstName(),
                user.getLastName(),
                user.getIsActive(),
                user.getIsVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}

package com.minipay.mpps.user;

import com.minipay.mpps.user.dto.CreateUserRequest;
import com.minipay.mpps.user.dto.UserResponse;

import java.util.UUID;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);

    UserResponse getUserById(UUID userId);

    UserResponse getUserByEmail(String email);
}

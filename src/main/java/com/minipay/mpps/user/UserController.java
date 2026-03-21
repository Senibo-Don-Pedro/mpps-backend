package com.minipay.mpps.user;

import com.minipay.mpps.common.dto.ApiSuccessResponse;
import com.minipay.mpps.user.dto.CreateUserRequest;
import com.minipay.mpps.user.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<UserResponse> createUser(
            @Valid
            @RequestBody
            CreateUserRequest request
    ) {

        UserResponse userResponse = userService.createUser(request);

        return new ApiSuccessResponse<>(
                true,
                "User created successfully",
                userResponse
        );

    }

    @GetMapping
    public ApiSuccessResponse<UserResponse> getUserByEmail(
            @RequestParam
            @Email(message = "Email should be valid")
            String email
    ) {
        UserResponse userResponse = userService.getUserByEmail(email);

        return new ApiSuccessResponse<>(
                true,
                "User returned successfully",
                userResponse
        );
    }

    @GetMapping("/{userId}")
    public ApiSuccessResponse<UserResponse> getUserById(
            @PathVariable UUID userId
            ) {
        UserResponse userResponse = userService.getUserById(userId);

        return new ApiSuccessResponse<>(
                true,
                "User returned successfully",
                userResponse
        );
    }


}

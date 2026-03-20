package com.minipay.mpps.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email,

        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$",
                message = "Password must be at least 8 characters, include upper, lower, number and special character"
        )
        @NotBlank
        String password,

        @Pattern(
                regexp = "^(\\+234|234|0)[789][01]\\d{8}$",
                message = "Invalid Nigerian phone number"
        )
        String phoneNumber,

        @NotBlank(message = "First Name is required")
        @Size(min = 2, message = "First Name must have at least two characters")
        String firstName,

        @NotBlank(message = "Last Name is required")
        @Size(min = 2, message = "Last Name must have at least two characters")
        String lastName
) {
}

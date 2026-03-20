package com.minipay.mpps.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        Boolean isActive,
        Boolean isVerified,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}

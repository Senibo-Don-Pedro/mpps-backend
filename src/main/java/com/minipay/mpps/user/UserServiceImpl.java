package com.minipay.mpps.user;

import com.minipay.mpps.common.exception.AlreadyExistsException;
import com.minipay.mpps.common.exception.NotFoundException;
import com.minipay.mpps.user.dto.CreateUserRequest;
import com.minipay.mpps.user.dto.UserResponse;
import com.minipay.mpps.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    /**
     * Creates a new user if the provided email and phone number is unique.
     */
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {

        //Check if user with the email already exists
        if (userRepository.existsByEmail(request.email())) {
            throw new AlreadyExistsException("User with email " + request.email() + " already exists");
        }

        //Check if user with the phone number already exists
        if (request.phoneNumber() != null && userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new AlreadyExistsException("User with phone number " + request.phoneNumber() + " already exists");
        }

        //Create and save the new user
        User user = User.builder()
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password())) // encode this
                        .phoneNumber(request.phoneNumber())
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .build();

        User savedUser = userRepository.save(user);

        //Map to the UserResponse and return
        return UserMapper.toResponse(savedUser);
    }

    /**
     * Retrieves a user by their unique identifier.
     */
    @Override
    public UserResponse getUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    /**
     * Retrieves a user by their email address.
     */
    @Override
    public UserResponse getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
    }


}

package com.minipay.mpps.common.exception;

import com.minipay.mpps.common.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Global exception handler.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles validation errors triggered by the @Valid annotation on request bodies. <br/>
     * Returns a 400 Bad Request status with a list of validation error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(FieldError::getDefaultMessage)
                                .toList();

        return new ApiErrorResponse(false, "Validation Failed", errors);
    }

    /**
     * Handles validation errors for path variables and request parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations()
                                .stream()
                                .map(ConstraintViolation::getMessage)
                                .toList();

        return new ApiErrorResponse(false, "Constraint Violation", errors);
    }

    /**
     * Ensures clients receive a clear message when mandatory query parameters are missing.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingParams(MissingServletRequestParameterException ex) {
        return new ApiErrorResponse(false, "Missing required parameter: " + ex.getParameterName(), null);
    }

    /**
     *Handles Malformed or invalid request body such as UUID passed as a string
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return new ApiErrorResponse(false, "Malformed or invalid request body", null);
    }

    /**
     *Handles general bad request exceptions
     */
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse badRequestException(BadRequestException ex) {
        return new ApiErrorResponse(false, ex.getMessage(), null);
    }

    /**
     * Handles 404 Not found exceptions
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotFoundException(NotFoundException ex) {
        return new ApiErrorResponse(false, ex.getMessage(), null);
    }

    /**
     * This also throws a 404 not found but for incorrect URL paths that do not match any controller mappings.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNoResourceFoundException(NoResourceFoundException ex) {
        return new ApiErrorResponse(false, "The requested resource was not found", null);
    }

    /**
     * Handles 409 Conflict exceptions when a resource already exists.
     */
    @ExceptionHandler(AlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleAlreadyExistsException(AlreadyExistsException ex) {
        return new ApiErrorResponse(false, ex.getMessage(), null);
    }

    /**
     * Handles 409 General Conflict Exceptions.
     */
    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse conflictException(ConflictException ex) {
        return new ApiErrorResponse(false, ex.getMessage(), null);
    }

    /**
     * A generic catch-all handler for any other unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleGenericException(Exception ex) {
        log.error("An unexpected error occurred : ", ex);
        return new ApiErrorResponse(false, "An unexpected internal server error occurred.", null);
    }
}

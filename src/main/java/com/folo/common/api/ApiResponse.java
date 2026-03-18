package com.folo.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        ApiError error,
        OffsetDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static ApiResponse<Void> successMessage(String message) {
        return success(null, message);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, null, new ApiError(code, message), OffsetDateTime.now(ZoneOffset.UTC));
    }
}

package com.folo.common.api;

public record ApiError(
        String code,
        String message
) {
}

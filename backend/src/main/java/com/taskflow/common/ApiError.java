package com.taskflow.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(String code, String message, String traceId, Instant timestamp,
                       Map<String, String> fieldErrors) {
    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, traceId, Instant.now(), null);
    }
}

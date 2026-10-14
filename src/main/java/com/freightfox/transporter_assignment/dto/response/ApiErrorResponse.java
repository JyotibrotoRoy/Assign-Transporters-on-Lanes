package com.freightfox.transporter_assignment.dto.response;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timeStamp,
        int status,
        String code,
        String message,
        List<FieldErrorDetail> details) {

    public record FieldErrorDetail(String field, String message) {}

    public static ApiErrorResponse of(int status, String code, String message) {
        return new ApiErrorResponse(Instant.now(), status, code, message, List.of());
    }

    public static ApiErrorResponse of(int status, String code, String message, List<FieldErrorDetail> details) {
        return new ApiErrorResponse(Instant.now(), status, code, message, details);
    }
}

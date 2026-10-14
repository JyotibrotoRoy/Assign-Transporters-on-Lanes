package com.freightfox.transporter_assignment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

public record LaneRequest(
        @NonNull @Positive Long id,
        @NotBlank @Size(max = 100) String origin,
        @NotBlank @Size(max = 100) String destination
) {
}

package com.freightfox.transporter_assignment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(@NotNull @Min(1) Integer maxTransporters) {
}

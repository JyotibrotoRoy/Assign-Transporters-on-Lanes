package com.freightfox.transporter_assignment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LaneQuoteRequest(
        @NotNull @Positive Long laneId,
        // zero is allowed; negatives are not.
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal quote) {
}

package com.freightfox.transporter_assignment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record TransporterRequest(
        @NonNull @Positive Long id,
        @NonNull @Size(max = 150) String name,
        @NonNull List<@NonNull @Valid LaneQuoteRequest> laneQuotes) {
}

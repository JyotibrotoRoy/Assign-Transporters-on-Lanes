package com.freightfox.transporter_assignment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InputRequest(
        @NotEmpty List<@NotNull @Valid LaneRequest> lanes,
        @NotEmpty List<@NotNull @Valid TransporterRequest> transporters) {
}

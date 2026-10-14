package com.freightfox.transporter_assignment.optimization;

import java.math.BigDecimal;
import java.util.Map;

public record TransporterInput(Long id, Map<Long, BigDecimal> quotesByLaneId) {

    public TransporterInput {
        quotesByLaneId = Map.copyOf(quotesByLaneId); // This is for defense so that it cannot be muted later
    }
}

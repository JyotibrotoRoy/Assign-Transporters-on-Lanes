package com.freightfox.transporter_assignment.optimization;

import java.math.BigDecimal;

public record LaneAssignment(Long laneId, Long transporterId, BigDecimal quote) {
}

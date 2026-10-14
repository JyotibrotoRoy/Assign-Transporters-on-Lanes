package com.freightfox.transporter_assignment.optimization;

import java.math.BigDecimal;
import java.util.List;

public record AssignmentResult(BigDecimal totalCost,
                               List<LaneAssignment> assignments,
                               List<Long> selectedTransporters) {
}

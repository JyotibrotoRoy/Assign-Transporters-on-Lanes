package com.freightfox.transporter_assignment.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AssignmentResponse(
        String status,
        BigDecimal totalCost,
        List<LaneAssignmentResponse> assignments,
        List<Long> selectedTransporters) {

    public static AssignmentResponse success(BigDecimal totalCost,
                                             List<LaneAssignmentResponse> assignments,
                                             List<Long> selectedTransporters) {
        return new AssignmentResponse("success", totalCost, assignments, selectedTransporters);
    }
}

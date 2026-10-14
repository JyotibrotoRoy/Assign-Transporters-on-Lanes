package com.freightfox.transporter_assignment.optimization;

import java.util.List;

public interface AssignmentOptimizer {

    AssignmentResult optimize(List<LaneInput> lanes,
                                 List<TransporterInput> transporters,
                                 int maxTransporters);
}

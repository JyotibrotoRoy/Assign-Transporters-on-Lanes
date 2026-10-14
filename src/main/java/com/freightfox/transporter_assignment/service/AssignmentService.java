package com.freightfox.transporter_assignment.service;

import com.freightfox.transporter_assignment.dto.request.AssignmentRequest;
import com.freightfox.transporter_assignment.dto.response.AssignmentResponse;
import com.freightfox.transporter_assignment.dto.response.LaneAssignmentResponse;
import com.freightfox.transporter_assignment.entity.InputBatch;
import com.freightfox.transporter_assignment.exception.DatasetNotFoundException;
import com.freightfox.transporter_assignment.optimization.AssignmentOptimizer;
import com.freightfox.transporter_assignment.optimization.AssignmentResult;
import com.freightfox.transporter_assignment.optimization.LaneInput;
import com.freightfox.transporter_assignment.optimization.TransporterInput;
import com.freightfox.transporter_assignment.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final InputBatchRepository inputBatchRepository;
    private final LaneRepository laneRepository;
    private final LaneQuoteRepository laneQuoteRepository;
    private final TransporterRepository transporterRepository;
    private final AssignmentOptimizer optimizer;

    public AssignmentService(InputBatchRepository inputBatchRepository,
                             LaneRepository laneRepository,
                             LaneQuoteRepository laneQuoteRepository,
                             TransporterRepository transporterRepository,
                             AssignmentOptimizer optimizer) {

        this.inputBatchRepository = inputBatchRepository;
        this.laneQuoteRepository = laneQuoteRepository;
        this.laneRepository = laneRepository;
        this.transporterRepository = transporterRepository;
        this.optimizer = optimizer;
    }

    @Transactional(readOnly = true)
    public AssignmentResponse assign(AssignmentRequest request) {
        InputBatch batch = inputBatchRepository.findFirstByOrderByIdDesc()
                .orElseThrow(() -> new DatasetNotFoundException(
                        "No input data has been submitted yet. Call POST /api/v1/transporters/input first."
                ));

        List<LaneInput> lanes = laneRepository.findByBatchIdOrderByExternalLaneId(batch.getId()).stream()
                .map(lane -> new LaneInput(lane.getExternalLaneId()))
                .toList();

        List<TransporterInput> transporters = buildTransporterInputs(batch.getId());

        log.info("Optimizing batch {}: {} lanes, {} transporters, maxTransporters={}",
                batch.getId(), lanes.size(), transporters.size(), request.maxTransporters());

        AssignmentResult result = optimizer.optimize(lanes, transporters, request.maxTransporters());
        return toResponse(result);

    }

    private List<TransporterInput> buildTransporterInputs(Long batchId) {
        Map<Long, Map<Long, BigDecimal>> quotesByTransporter = new HashMap<>();
        for (LaneQuoteView row : laneQuoteRepository.findViewsByBatchId(batchId)) {
            quotesByTransporter
                    .computeIfAbsent(row.externalTransporterId(), id -> new HashMap<>())
                    .put(row.externalLaneId(), row.quote());
        }
        // Transporters without any quote are kept; the optimizer simply never finds them eligible.
        return transporterRepository.findByBatchIdOrderByExternalTransporterId(batchId).stream()
                .map(t -> new TransporterInput(t.getExternalTransporterId(),
                        quotesByTransporter.getOrDefault(t.getExternalTransporterId(), Map.of())))
                .toList();
    }

    private AssignmentResponse toResponse(AssignmentResult result) {
        List<LaneAssignmentResponse> assignments = result.assignments().stream()
                .map(a -> new LaneAssignmentResponse(a.laneId(), a.transporterId()))
                .toList();
        return AssignmentResponse.success(normalize(result.totalCost()), assignments,
                result.selectedTransporters());
    }

    private static BigDecimal normalize(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

}

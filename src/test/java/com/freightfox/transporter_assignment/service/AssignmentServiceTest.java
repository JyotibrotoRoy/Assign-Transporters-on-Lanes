package com.freightfox.transporter_assignment.service;

import com.freightfox.transporter_assignment.dto.request.AssignmentRequest;
import com.freightfox.transporter_assignment.dto.response.AssignmentResponse;
import com.freightfox.transporter_assignment.dto.response.LaneAssignmentResponse;
import com.freightfox.transporter_assignment.entity.InputBatch;
import com.freightfox.transporter_assignment.entity.Lane;
import com.freightfox.transporter_assignment.entity.Transporter;
import com.freightfox.transporter_assignment.exception.DatasetNotFoundException;
import com.freightfox.transporter_assignment.exception.NoFeasibleAssignmentException;
import com.freightfox.transporter_assignment.optimization.AssignmentOptimizer;
import com.freightfox.transporter_assignment.optimization.AssignmentResult;
import com.freightfox.transporter_assignment.optimization.LaneAssignment;
import com.freightfox.transporter_assignment.optimization.LaneInput;
import com.freightfox.transporter_assignment.optimization.TransporterInput;
import com.freightfox.transporter_assignment.repository.InputBatchRepository;
import com.freightfox.transporter_assignment.repository.LaneQuoteRepository;
import com.freightfox.transporter_assignment.repository.LaneQuoteView;
import com.freightfox.transporter_assignment.repository.LaneRepository;
import com.freightfox.transporter_assignment.repository.TransporterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** I am mocking the repositories and optimizers */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    private static final long BATCH_ID = 5L;

    @Mock private InputBatchRepository inputBatchRepository;
    @Mock private LaneRepository laneRepository;
    @Mock private TransporterRepository transporterRepository;
    @Mock private LaneQuoteRepository laneQuoteRepository;
    @Mock private AssignmentOptimizer optimizer;

    @Captor private ArgumentCaptor<List<LaneInput>> lanesCaptor;
    @Captor private ArgumentCaptor<List<TransporterInput>> transportersCaptor;

    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(inputBatchRepository, laneRepository, laneQuoteRepository, transporterRepository, optimizer);
    }

    /** For Batch 5: lanes are 1 and 2 and transporter 10 quoted both lanes, transporter 20 quoted nothing, */
    private void stubBatchData() {
        InputBatch batch = new InputBatch(Instant.now());
        ReflectionTestUtils.setField(batch, "id", BATCH_ID); // ids are normally set by the database
        when(inputBatchRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(batch));
        when(laneRepository.findByBatchIdOrderByExternalLaneId(BATCH_ID)).thenReturn(List.of(
                new Lane(batch, 1L, "A", "B"), new Lane(batch, 2L, "C", "D")));
        when(transporterRepository.findByBatchIdOrderByExternalTransporterId(BATCH_ID)).thenReturn(List.of(
                new Transporter(batch, 10L, "Busy"), new Transporter(batch, 20L, "Idle")));
        when(laneQuoteRepository.findViewsByBatchId(BATCH_ID)).thenReturn(List.of(
                new LaneQuoteView(1L, 10L, new BigDecimal("5000.00")),
                new LaneQuoteView(2L, 10L, new BigDecimal("6000.00"))));
    }

    private static AssignmentResult resultWithTotal(String total) {
        return new AssignmentResult(new BigDecimal(total),
                List.of(new LaneAssignment(1L, 10L, new BigDecimal("5000.00")),
                        new LaneAssignment(2L, 10L, new BigDecimal("6000.00"))),
                List.of(10L));
    }

    @Test
    void noSavedDatasetThrowsNotFoundAndNeverCallsTheOptimizer() {
        when(inputBatchRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());

        assertThrows(DatasetNotFoundException.class, () -> assignmentService.assign(new AssignmentRequest(3)));

        verifyNoInteractions(optimizer);
    }

    @Test
    void optimizerReceivesLatestBatchLanesTransportersAndQuotesWithClientIds() {
        stubBatchData();
        when(optimizer.optimize(anyList(), anyList(), eq(3))).thenReturn(resultWithTotal("11000.00"));

        assignmentService.assign(new AssignmentRequest(3));

        verify(optimizer).optimize(lanesCaptor.capture(), transportersCaptor.capture(), eq(3));
        assertEquals(List.of(new LaneInput(1L), new LaneInput(2L)), lanesCaptor.getValue());

        List<TransporterInput> transporters = transportersCaptor.getValue();
        assertEquals(2, transporters.size());
        assertEquals(10L, transporters.get(0).id());
        assertEquals(Map.of(1L, new BigDecimal("5000.00"), 2L, new BigDecimal("6000.00")),
                transporters.get(0).quotesByLaneId());
        // a transporter with no quotes is passed on with an empty map and he is not eligible for nothing.
        assertEquals(20L, transporters.get(1).id());
        assertEquals(Map.of(), transporters.get(1).quotesByLaneId());
    }

    @Test
    void optimizerResultIsMappedToTheResponse() {
        stubBatchData();
        when(optimizer.optimize(anyList(), anyList(), eq(2))).thenReturn(resultWithTotal("11000.00"));

        AssignmentResponse response = assignmentService.assign(new AssignmentRequest(2));

        assertEquals("success", response.status());
        assertEquals(List.of(new LaneAssignmentResponse(1L, 10L), new LaneAssignmentResponse(2L, 10L)),
                response.assignments());
        assertEquals(List.of(10L), response.selectedTransporters());
    }

    @Test
    void totalCostDropsTrailingZerosButKeepsRealDecimals() {
        stubBatchData();
        when(optimizer.optimize(anyList(), anyList(), eq(1)))
                .thenReturn(resultWithTotal("11000.00"))
                .thenReturn(resultWithTotal("900.50"));

        assertEquals("11000", assignmentService.assign(new AssignmentRequest(1)).totalCost().toPlainString());
        assertEquals("900.5", assignmentService.assign(new AssignmentRequest(1)).totalCost().toPlainString());
    }

    @Test
    void noFeasibleAssignmentFromTheOptimizerPropagates() {
        stubBatchData();
        when(optimizer.optimize(anyList(), anyList(), eq(1)))
                .thenThrow(new NoFeasibleAssignmentException("no cover"));

        assertThrows(NoFeasibleAssignmentException.class, () -> assignmentService.assign(new AssignmentRequest(1)));
    }
}

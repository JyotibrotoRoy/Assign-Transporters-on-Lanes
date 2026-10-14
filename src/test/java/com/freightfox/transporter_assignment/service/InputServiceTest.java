package com.freightfox.transporter_assignment.service;

import com.freightfox.transporter_assignment.dto.request.InputRequest;
import com.freightfox.transporter_assignment.dto.request.LaneQuoteRequest;
import com.freightfox.transporter_assignment.dto.request.LaneRequest;
import com.freightfox.transporter_assignment.dto.request.TransporterRequest;
import com.freightfox.transporter_assignment.entity.InputBatch;
import com.freightfox.transporter_assignment.entity.Lane;
import com.freightfox.transporter_assignment.entity.LaneQuote;
import com.freightfox.transporter_assignment.entity.Transporter;
import com.freightfox.transporter_assignment.exception.InvalidInputException;
import com.freightfox.transporter_assignment.repository.InputBatchRepository;
import com.freightfox.transporter_assignment.repository.LaneQuoteRepository;
import com.freightfox.transporter_assignment.repository.LaneRepository;
import com.freightfox.transporter_assignment.repository.TransporterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** same with theis, mocking the repositories and optimizers */
@ExtendWith(MockitoExtension.class)
class InputServiceTest {

    @Mock private InputBatchRepository inputBatchRepository;
    @Mock private LaneRepository laneRepository;
    @Mock private TransporterRepository transporterRepository;
    @Mock private LaneQuoteRepository laneQuoteRepository;

    @Captor private ArgumentCaptor<List<Lane>> laneCaptor;
    @Captor private ArgumentCaptor<List<Transporter>> transporterCaptor;
    @Captor private ArgumentCaptor<List<LaneQuote>> quoteCaptor;

    private InputService inputService;

    @BeforeEach
    void setUp() {
        inputService = new InputService(inputBatchRepository, laneRepository,
                transporterRepository, laneQuoteRepository);
    }

    /** A fake database: save and saveAll just return what they were given. */
    private void stubPersistence() {
        when(inputBatchRepository.save(any(InputBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(laneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(transporterRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static LaneRequest lane(long id) {
        return new LaneRequest(id, "Origin" + id, "Destination" + id);
    }

    private static LaneQuoteRequest quote(long laneId, String amount) {
        return new LaneQuoteRequest(laneId, new BigDecimal(amount));
    }

    private static TransporterRequest transporter(long id, LaneQuoteRequest... quotes) {
        return new TransporterRequest(id, "Transporter " + id, List.of(quotes));
    }

    private void verifyNothingWasWritten() {
        verifyNoInteractions(inputBatchRepository, laneRepository, transporterRepository, laneQuoteRepository);
    }


    @Test
    void validRequestSavesLanesTransportersAndQuotesLinkedToTheirExternalIds() {
        stubPersistence();
        when(laneQuoteRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        InputRequest request = new InputRequest(
                List.of(new LaneRequest(1L, "  Mumbai ", "Delhi"), lane(2)),
                List.of(new TransporterRequest(10L, " Fast Freight ",
                                List.of(quote(1, "5000"), quote(2, "6000.50"))),
                        transporter(20, quote(1, "5500"))));

        inputService.saveInput(request);

        verify(laneRepository).saveAll(laneCaptor.capture());
        assertEquals(2, laneCaptor.getValue().size());
        assertEquals("Mumbai", laneCaptor.getValue().get(0).getOrigin()); // trimmed
        assertEquals(1L, laneCaptor.getValue().get(0).getExternalLaneId());

        verify(transporterRepository).saveAll(transporterCaptor.capture());
        assertEquals("Fast Freight", transporterCaptor.getValue().get(0).getName()); // trimmed
        assertEquals(10L, transporterCaptor.getValue().get(0).getExternalTransporterId());

        verify(laneQuoteRepository).saveAll(quoteCaptor.capture());
        List<LaneQuote> quotes = quoteCaptor.getValue();
        assertEquals(3, quotes.size());
        assertEquals(10L, quotes.get(1).getTransporter().getExternalTransporterId());
        assertEquals(2L, quotes.get(1).getLane().getExternalLaneId());
        assertEquals(0, new BigDecimal("6000.50").compareTo(quotes.get(1).getQuote()));
        assertEquals(20L, quotes.get(2).getTransporter().getExternalTransporterId());
    }

    @Test
    void transporterWithoutQuotesIsAccepted() {
        stubPersistence();
        when(laneQuoteRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        inputService.saveInput(new InputRequest(List.of(lane(1)),
                List.of(transporter(1, quote(1, "100")), new TransporterRequest(2L, "Idle", List.of()))));

        verify(transporterRepository).saveAll(transporterCaptor.capture());
        assertEquals(2, transporterCaptor.getValue().size());
    }

    @Test
    void duplicateLaneIdIsRejectedAndNothingIsWritten() {
        InputRequest request = new InputRequest(List.of(lane(1), lane(1)), List.of(transporter(1, quote(1, "5"))));

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> inputService.saveInput(request));

        assertEquals("lanes[1].id", e.getViolations().get(0).field());
        assertEquals("Duplicate lane id 1", e.getViolations().get(0).message());
        verifyNothingWasWritten();
    }

    @Test
    void duplicateTransporterIdIsRejectedAndNothingIsWritten() {
        InputRequest request = new InputRequest(List.of(lane(1)),
                List.of(transporter(7, quote(1, "5")), transporter(7, quote(1, "6"))));

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> inputService.saveInput(request));

        assertEquals("transporters[1].id", e.getViolations().get(0).field());
        verifyNothingWasWritten();
    }

    @Test
    void quoteForUnknownLaneIsRejected() {
        InputRequest request = new InputRequest(List.of(lane(1)), List.of(transporter(1, quote(99, "5"))));

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> inputService.saveInput(request));

        assertEquals("transporters[0].laneQuotes[0].laneId", e.getViolations().get(0).field());
        assertEquals("Quote refers to unknown lane id 99", e.getViolations().get(0).message());
        verifyNothingWasWritten();
    }

    @Test
    void sameTransporterQuotingSameLaneTwiceIsRejectedNotMerged() {
        InputRequest request = new InputRequest(List.of(lane(1)),
                List.of(transporter(1, quote(1, "5"), quote(1, "6"))));

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> inputService.saveInput(request));

        assertEquals("transporters[0].laneQuotes[1].laneId", e.getViolations().get(0).field());
        assertEquals("Transporter 1 quoted lane 1 more than once", e.getViolations().get(0).message());
        verifyNothingWasWritten();
    }

    @Test
    void allProblemsAreReportedTogether() {
        InputRequest request = new InputRequest(
                List.of(lane(1), lane(1)),
                List.of(transporter(1, quote(1, "5"), quote(1, "6")), transporter(1, quote(9, "5"))));

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> inputService.saveInput(request));

        assertEquals(4, e.getViolations().size());
        verifyNothingWasWritten();
    }


    @Test
    void databaseFailureIsNotSwallowed() {
        // The real rollback is done by @Transactional (covered by an integration test);
        // here we only prove the service lets the failure Generate instead of hiding it.
        stubPersistence();
        when(laneQuoteRepository.saveAll(anyList())).thenThrow(new IllegalStateException("db down"));
        InputRequest request = new InputRequest(List.of(lane(1)), List.of(transporter(1, quote(1, "5"))));

        assertThrows(IllegalStateException.class, () -> inputService.saveInput(request));
    }
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InputService {

    private static final Logger log = LoggerFactory.getLogger(InputService.class);

    private final InputBatchRepository inputBatchRepository;
    private final LaneRepository laneRepository;
    private final LaneQuoteRepository laneQuoteRepository;
    private final TransporterRepository transporterRepository;

    public InputService(InputBatchRepository inputBatchRepository,
                        LaneRepository laneRepository,
                        TransporterRepository transporterRepository,
                        LaneQuoteRepository laneQuoteRepository) {
        this.inputBatchRepository = inputBatchRepository;
        this.laneRepository = laneRepository;
        this.transporterRepository = transporterRepository;
        this.laneQuoteRepository = laneQuoteRepository;
    }

    @Transactional
    public void saveInput(InputRequest request) {
        validate(request);

        InputBatch batch = inputBatchRepository.save(new InputBatch(Instant.now()));
        Map<Long, Lane> lanesByExternalId = saveLanes(batch, request.lanes());
        Map<Long, Transporter> transportersByExternalId = saveTransporters(batch, request.transporters());
        int quoteCount = saveQuotes(request.transporters(), lanesByExternalId, transportersByExternalId);

        log.info("Saved input batch {} with {} lanes, {} transporters, {} quotes",
                batch.getId(), request.lanes().size(), request.transporters().size(), quoteCount);
    }

    private void validate(InputRequest request) {
        List<InvalidInputException.Violation> violations = new ArrayList<>();

        Set<Long> laneIds = new HashSet<>();
        List<LaneRequest> lanes = request.lanes();

        for(int i=0; i<lanes.size(); i++) {
            Long id = lanes.get(i).id();
            if(!laneIds.add(id)) {
                violations.add(new InvalidInputException.Violation("lanes[" + i + "].id", "Duplicate lane id " + id));
            }
        }

        Set<Long> transporterIds = new HashSet<>();
        List<TransporterRequest> transporters = request.transporters();
        for (int i = 0; i < transporters.size(); i++) {
            TransporterRequest transporter = transporters.get(i);
            if (!transporterIds.add(transporter.id())) {
                violations.add(new InvalidInputException.Violation("transporters[" + i + "].id",
                        "Duplicate transporter id " + transporter.id()));
            }
            validateQuotes(i, transporter, laneIds, violations);
        }

        if (!violations.isEmpty()) {
            throw new InvalidInputException("Input data failed validation", violations);
        }
    }

    private void validateQuotes(int transporterIndex, TransporterRequest transporter,
                                Set<Long> laneIds, List<InvalidInputException.Violation> violations) {

        Set<Long> quotedLanes = new HashSet<>();
        List<LaneQuoteRequest> quotes = transporter.laneQuotes();

        for (int j = 0; j < quotes.size(); j++) {
            Long laneId = quotes.get(j).laneId();
            String field = "transporters[" + transporterIndex + "].laneQuotes[" + j + "].laneId";
            boolean firstQuoteForLane = quotedLanes.add(laneId);
            if (!laneIds.contains(laneId)) {
                violations.add(new InvalidInputException.Violation(field, "Quote refers to unknown lane id " + laneId));
            } else if (!firstQuoteForLane) {
                // Reject rather than guess which of two prices the client meant.
                violations.add(new InvalidInputException.Violation(field,
                        "Transporter " + transporter.id() + " quoted lane " + laneId + " more than once"));
            }
        }
    }

    private Map<Long, Lane> saveLanes(InputBatch batch, List<LaneRequest> requests) {
        List<Lane> lanes = requests.stream()
                .map(r -> new Lane(batch, r.id(), r.origin().trim(), r.destination().trim()))
                .toList();
        return laneRepository.saveAll(lanes).stream()
                .collect(Collectors.toMap(Lane::getExternalLaneId, Function.identity()));
    }

    private Map<Long, Transporter> saveTransporters(InputBatch batch, List<TransporterRequest> requests) {
        List<Transporter> transporters = requests.stream()
                .map(r -> new Transporter(batch, r.id(), r.name().trim()))
                .toList();
        return transporterRepository.saveAll(transporters).stream()
                .collect(Collectors.toMap(Transporter::getExternalTransporterId, Function.identity()));
    }

    private int saveQuotes(List<TransporterRequest> requests,
                           Map<Long, Lane> lanesByExternalId,
                           Map<Long, Transporter> transportersByExternalId) {
        List<LaneQuote> quotes = new ArrayList<>();
        for (TransporterRequest transporter : requests) {
            for (LaneQuoteRequest quote : transporter.laneQuotes()) {
                quotes.add(new LaneQuote(
                        lanesByExternalId.get(quote.laneId()),
                        transportersByExternalId.get(transporter.id()),
                        quote.quote()));
            }
        }
        laneQuoteRepository.saveAll(quotes);
        return quotes.size();
    }

}

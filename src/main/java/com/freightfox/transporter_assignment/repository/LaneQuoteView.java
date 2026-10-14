package com.freightfox.transporter_assignment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

public record LaneQuoteView(
        Long externalLaneId,
        Long externalTransporterId,
        BigDecimal quote) {
}

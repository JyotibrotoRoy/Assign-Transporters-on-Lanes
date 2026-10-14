package com.freightfox.transporter_assignment.repository;

import com.freightfox.transporter_assignment.entity.LaneQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LaneQuoteRepository extends JpaRepository<LaneQuote, Long> {

    @Query("""
            select new com.freightfox.transporter_assignment.repository.LaneQuoteView(
                l.externalLaneId, t.externalTransporterId, q.quote)
            from LaneQuote q
            join q.lane l
            join q.transporter t
            where l.batch.id = :batchId
            """)
    List<LaneQuoteView> findViewsByBatchId(@Param("batchId") Long batchId);
}

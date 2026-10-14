package com.freightfox.transporter_assignment.repository;

import com.freightfox.transporter_assignment.entity.Lane;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LaneRepository extends JpaRepository<Lane, Long> {

    List<Lane> findByBatchIdOrderByExternalLaneId(Long batchId);
}

package com.freightfox.transporter_assignment.repository;

import com.freightfox.transporter_assignment.entity.Transporter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransporterRepository extends JpaRepository<Transporter, Long> {

    List<Transporter> findByBatchIdOrderByExternalTransporterId(Long batchId);
}

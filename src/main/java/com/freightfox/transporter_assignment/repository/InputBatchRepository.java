package com.freightfox.transporter_assignment.repository;

import com.freightfox.transporter_assignment.entity.InputBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InputBatchRepository extends JpaRepository<InputBatch, Long> {

    Optional<InputBatch> findFirstByOrderByIdDesc();
}

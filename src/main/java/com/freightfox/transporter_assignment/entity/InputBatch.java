package com.freightfox.transporter_assignment.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "input_batches")
public class InputBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InputBatch() {}

    public InputBatch(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

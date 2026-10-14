package com.freightfox.transporter_assignment.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "transporters")
public class Transporter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private InputBatch batch;

    @Column(name = "external_transporter_id", nullable = false, updatable = false)
    private Long externalTransporterId;

    @Column(nullable = false, length = 150)
    private String name;

    protected Transporter() {
    }

    public Transporter(InputBatch batch, Long externalTransporterId, String name) {
        this.batch = batch;
        this.externalTransporterId = externalTransporterId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public InputBatch getBatch() {
        return batch;
    }

    public Long getExternalTransporterId() {
        return externalTransporterId;
    }

    public String getName() {
        return name;
    }
}

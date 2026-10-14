package com.freightfox.transporter_assignment.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "lanes")
public class Lane {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private InputBatch batch;

    @Column(name = "external_lane_id", nullable = false, updatable = false)
    private Long externalLaneId;

    @Column(nullable = false, length = 100)
    private String origin;

    @Column(nullable = false, length = 100)
    private String destination;

    protected Lane() {}

    public Lane(InputBatch batch, Long externalLaneId,
                String origin, String destination) {
        this.batch = batch;
        this.externalLaneId = externalLaneId;
        this.origin = origin;
        this.destination = destination;
    }

    public Long getId() {
        return id;
    }

    public InputBatch getBatch() {
        return batch;
    }

    public Long getExternalLaneId() {
        return externalLaneId;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }
}

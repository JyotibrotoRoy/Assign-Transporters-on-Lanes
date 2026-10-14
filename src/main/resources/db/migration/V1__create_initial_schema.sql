-- One row per successfully submitted dataset. The highest id is the active dataset.
CREATE TABLE input_batches (
    id BIGSERIAL   PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Lane ids supplied by the client are only unique inside one batch.
CREATE TABLE lanes (
    id               BIGSERIAL    PRIMARY KEY,
    batch_id         BIGINT       NOT NULL,
    external_lane_id BIGINT       NOT NULL,
    origin           VARCHAR(100) NOT NULL,
    destination      VARCHAR(100) NOT NULL,
    CONSTRAINT fk_lanes_batch              FOREIGN KEY (batch_id) REFERENCES input_batches (id),
    CONSTRAINT uq_lanes_batch_external     UNIQUE (batch_id, external_lane_id),
    CONSTRAINT ck_lanes_origin_not_blank      CHECK (btrim(origin) <> ''),
    CONSTRAINT ck_lanes_destination_not_blank CHECK (btrim(destination) <> '')
);

CREATE TABLE transporters (
    id                      BIGSERIAL    PRIMARY KEY,
    batch_id                BIGINT       NOT NULL,
    external_transporter_id BIGINT       NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    CONSTRAINT fk_transporters_batch          FOREIGN KEY (batch_id) REFERENCES input_batches (id),
    CONSTRAINT uq_transporters_batch_external UNIQUE (batch_id, external_transporter_id),
    CONSTRAINT ck_transporters_name_not_blank CHECK (btrim(name) <> '')
);

-- A row here means "this transporter quoted this lane". No row means not eligible.
CREATE TABLE lane_quotes (
    id             BIGSERIAL      PRIMARY KEY,
    lane_id        BIGINT         NOT NULL,
    transporter_id BIGINT         NOT NULL,
    quote          NUMERIC(15, 2) NOT NULL,
    CONSTRAINT fk_lane_quotes_lane        FOREIGN KEY (lane_id)        REFERENCES lanes (id),
    CONSTRAINT fk_lane_quotes_transporter FOREIGN KEY (transporter_id) REFERENCES transporters (id),
    CONSTRAINT uq_lane_quotes_transporter_lane UNIQUE (transporter_id, lane_id),
    CONSTRAINT ck_lane_quotes_non_negative     CHECK (quote >= 0)
);

CREATE INDEX idx_lane_quotes_lane ON lane_quotes (lane_id);

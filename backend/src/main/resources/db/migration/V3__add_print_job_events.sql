ALTER TABLE print_jobs
    ADD COLUMN error_code VARCHAR(64) NULL;

CREATE TABLE print_job_events (
    id BINARY(16) NOT NULL,
    print_job_id BINARY(16) NOT NULL,
    event_id BINARY(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_print_job_events PRIMARY KEY (id),
    CONSTRAINT uq_print_job_events_job_event UNIQUE (print_job_id, event_id),
    CONSTRAINT fk_print_job_events_job FOREIGN KEY (print_job_id) REFERENCES print_jobs (id)
);

CREATE TABLE admins (
    id BINARY(16) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_admins PRIMARY KEY (id),
    CONSTRAINT uq_admins_username UNIQUE (username)
);

CREATE TABLE documents (
    id BINARY(16) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(128) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    size_bytes BIGINT NOT NULL,
    page_count INTEGER NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_documents_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_documents_page_count_positive CHECK (page_count IS NULL OR page_count > 0)
);

CREATE TABLE print_agents (
    id BINARY(16) NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    credential_hash VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    last_seen_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_print_agents PRIMARY KEY (id),
    CONSTRAINT uq_print_agents_code UNIQUE (agent_code)
);

CREATE TABLE printers (
    id BINARY(16) NOT NULL,
    agent_id BINARY(16) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    system_name VARCHAR(255) NOT NULL,
    default_printer BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_discovered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_printers PRIMARY KEY (id),
    CONSTRAINT uq_printers_agent_system_name UNIQUE (agent_id, system_name),
    CONSTRAINT fk_printers_agent FOREIGN KEY (agent_id) REFERENCES print_agents (id)
);

CREATE TABLE print_rates (
    id BINARY(16) NOT NULL,
    print_type VARCHAR(32) NOT NULL,
    price_per_page DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_print_rates PRIMARY KEY (id),
    CONSTRAINT uq_print_rates_type UNIQUE (print_type),
    CONSTRAINT ck_print_rates_price_nonnegative CHECK (price_per_page >= 0)
);

CREATE TABLE print_orders (
    id BINARY(16) NOT NULL,
    token VARCHAR(32) NOT NULL,
    document_id BINARY(16) NOT NULL,
    page_count INTEGER NOT NULL,
    print_type VARCHAR(32) NOT NULL,
    copies INTEGER NOT NULL,
    price_per_page DECIMAL(10, 2) NOT NULL,
    total_pages INTEGER NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    paper_size VARCHAR(32) NOT NULL,
    orientation VARCHAR(16) NOT NULL,
    page_range VARCHAR(128) NULL,
    double_sided BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    printed_at DATETIME(6) NULL,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_print_orders PRIMARY KEY (id),
    CONSTRAINT uq_print_orders_token UNIQUE (token),
    CONSTRAINT fk_print_orders_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT ck_print_orders_page_count_positive CHECK (page_count > 0),
    CONSTRAINT ck_print_orders_copies_positive CHECK (copies > 0),
    CONSTRAINT ck_print_orders_total_pages_positive CHECK (total_pages > 0),
    CONSTRAINT ck_print_orders_price_nonnegative CHECK (price_per_page >= 0 AND total_amount >= 0)
);

CREATE TABLE print_jobs (
    id BINARY(16) NOT NULL,
    print_order_id BINARY(16) NOT NULL,
    printer_id BINARY(16) NULL,
    agent_id BINARY(16) NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_message VARCHAR(2000) NULL,
    queued_at DATETIME(6) NOT NULL,
    claimed_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_print_jobs PRIMARY KEY (id),
    CONSTRAINT uq_print_jobs_order_attempt UNIQUE (print_order_id, attempt_number),
    CONSTRAINT fk_print_jobs_order FOREIGN KEY (print_order_id) REFERENCES print_orders (id),
    CONSTRAINT fk_print_jobs_printer FOREIGN KEY (printer_id) REFERENCES printers (id),
    CONSTRAINT fk_print_jobs_agent FOREIGN KEY (agent_id) REFERENCES print_agents (id),
    CONSTRAINT ck_print_jobs_attempt_positive CHECK (attempt_number > 0)
);

CREATE TABLE audit_logs (
    id BINARY(16) NOT NULL,
    actor_type VARCHAR(24) NOT NULL,
    actor_id BINARY(16) NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(64) NULL,
    details VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

CREATE INDEX idx_admins_enabled ON admins (enabled);
CREATE INDEX idx_documents_created_at ON documents (created_at);
CREATE INDEX idx_print_agents_status ON print_agents (status);
CREATE INDEX idx_printers_agent_id ON printers (agent_id);
CREATE INDEX idx_printers_default_enabled ON printers (default_printer, enabled);
CREATE INDEX idx_print_rates_active ON print_rates (active);
CREATE INDEX idx_print_orders_status_created ON print_orders (status, created_at);
CREATE INDEX idx_print_orders_document_id ON print_orders (document_id);
CREATE INDEX idx_print_jobs_status_queued ON print_jobs (status, queued_at);
CREATE INDEX idx_print_jobs_printer_status ON print_jobs (printer_id, status);
CREATE INDEX idx_print_jobs_agent_id ON print_jobs (agent_id);
CREATE INDEX idx_audit_logs_actor_time ON audit_logs (actor_id, created_at);
CREATE INDEX idx_audit_logs_target_time ON audit_logs (target_type, target_id, created_at);

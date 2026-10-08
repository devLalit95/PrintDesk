ALTER TABLE printers
    ADD COLUMN supports_color BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE printers
    ADD COLUMN supports_duplex BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE printers
    ADD COLUMN max_copies INTEGER NOT NULL DEFAULT 1;

ALTER TABLE printers
    ADD COLUMN paper_sizes VARCHAR(512) NOT NULL DEFAULT 'A4';

ALTER TABLE printers
    ADD CONSTRAINT ck_printers_max_copies_positive CHECK (max_copies > 0);

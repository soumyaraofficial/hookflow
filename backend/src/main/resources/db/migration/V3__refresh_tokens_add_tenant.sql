DELETE FROM refresh_tokens;

ALTER TABLE refresh_tokens
    ADD COLUMN tenant_id UUID NOT NULL REFERENCES tenants (id);
CREATE TABLE tenants (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE tenant_memberships (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    role        VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_membership_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_membership_role   CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT ck_membership_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_membership_user ON tenant_memberships (user_id);

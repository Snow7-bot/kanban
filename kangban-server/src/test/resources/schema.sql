-- H2 Test Schema: mirrors MySQL schema with H2-compatible syntax

CREATE TABLE IF NOT EXISTS users (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username          VARCHAR(50)  NOT NULL,
    phone             VARCHAR(20),
    email             VARCHAR(100),
    password          VARCHAR(200),
    name              VARCHAR(50),
    gender            VARCHAR(10),
    birthday          DATE,
    blood_type        VARCHAR(10),
    height            DOUBLE,
    weight            DOUBLE,
    avatar_url        VARCHAR(500),
    emergency_contact VARCHAR(50),
    status            INT          DEFAULT 1,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP    NULL
);

CREATE TABLE IF NOT EXISTS medications (
    id                 BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    member_id          BIGINT,
    name               VARCHAR(200) NOT NULL,
    dosage             VARCHAR(50),
    unit               VARCHAR(20),
    instruction        VARCHAR(500),
    frequency          VARCHAR(100),
    inventory          INT,
    times              VARCHAR(200),
    start_date         DATE,
    end_date           DATE,
    note               VARCHAR(500),
    standard_drug_id   VARCHAR(100) DEFAULT NULL,
    standard_drug_name VARCHAR(200) DEFAULT NULL,
    status             VARCHAR(20)  DEFAULT 'active',
    created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP    NULL
);

CREATE TABLE IF NOT EXISTS drug_interaction_rules (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    drug_a      VARCHAR(200) NOT NULL,
    drug_b      VARCHAR(200) NOT NULL,
    risk_level  VARCHAR(20)  NOT NULL,
    description TEXT         NOT NULL,
    advice      TEXT         NOT NULL,
    source      VARCHAR(200) DEFAULT '演示规则',
    version     VARCHAR(20)  DEFAULT '1.0',
    active      INT          DEFAULT 1,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (drug_a, drug_b)
);

CREATE TABLE IF NOT EXISTS drug_interaction_results (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    drug_names       VARCHAR(500) NOT NULL,
    result_data      TEXT,
    checked_drug_ids VARCHAR(500),
    matched_rule_ids VARCHAR(500),
    rule_version     VARCHAR(20),
    disclaimer       VARCHAR(500),
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS medical_records (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    member_id        BIGINT,
    record_name      VARCHAR(200),
    record_type      VARCHAR(20),
    hospital         VARCHAR(200),
    department       VARCHAR(100),
    doctor           VARCHAR(100),
    record_date      DATE,
    file_url         VARCHAR(500),
    file_size        BIGINT,
    file_type        VARCHAR(100),
    status           VARCHAR(20)  DEFAULT 'pending',
    confidence       INT,
    ocr_text         TEXT,
    diagnosis_data   TEXT,
    medications_data TEXT,
    advices_data     TEXT,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP    NULL
);

CREATE TABLE IF NOT EXISTS share_records (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    medical_record_id BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    token             VARCHAR(64)  NOT NULL,
    expires_at        TIMESTAMP    NOT NULL,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    revoked_at        TIMESTAMP    NULL,
    UNIQUE (token)
);

CREATE TABLE IF NOT EXISTS family_members (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    relation   VARCHAR(20),
    age        INT,
    gender     VARCHAR(10),
    avatar_url VARCHAR(500),
    note       VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS health_records (
    id            BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    member_id     BIGINT      NULL,
    member_name   VARCHAR(50),
    metric        VARCHAR(50) NOT NULL,
    value         VARCHAR(50) NOT NULL,
    unit          VARCHAR(20),
    recorded_date DATE        NOT NULL,
    recorded_time TIME,
    note          VARCHAR(200),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token      VARCHAR(500) NOT NULL,
    revoked    BOOLEAN     DEFAULT FALSE,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dose_records (
    id             BIGINT    AUTO_INCREMENT PRIMARY KEY,
    medication_id  BIGINT    NOT NULL,
    user_id        BIGINT    NOT NULL,
    scheduled_time TIME,
    confirmed_at   TIMESTAMP,
    status         VARCHAR(20) DEFAULT 'pending',
    created_at     TIMESTAMP  DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id           BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    subject_user_id BIGINT,
    member_id    BIGINT,
    title        VARCHAR(200),
    patient_data TEXT,
    status       VARCHAR(20) DEFAULT 'active',
    created_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP   NULL
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id                  BIGINT      AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT      NOT NULL,
    user_id             BIGINT      NOT NULL,
    role                VARCHAR(20) NOT NULL,
    content             TEXT        NOT NULL,
    attachment_url      VARCHAR(500),
    reply_to_message_id BIGINT,
    client_message_id   VARCHAR(64),
    created_at          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reply_to_message_id),
    UNIQUE (user_id, client_message_id)
);

CREATE TABLE IF NOT EXISTS family_groups (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    owner_user_id BIGINT       NOT NULL,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP    NULL
);

CREATE TABLE IF NOT EXISTS family_group_members (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    family_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    relation   VARCHAR(20),
    role       VARCHAR(20) DEFAULT 'member',
    status     VARCHAR(20) DEFAULT 'active',
    joined_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (family_id, user_id)
);

CREATE TABLE IF NOT EXISTS family_invitations (
    id                   BIGINT      AUTO_INCREMENT PRIMARY KEY,
    family_id            BIGINT      NOT NULL,
    inviter_user_id      BIGINT      NOT NULL,
    invitee_user_id      BIGINT      NOT NULL,
    relation             VARCHAR(20),
    can_view_health      BOOLEAN     DEFAULT TRUE,
    can_add_health       BOOLEAN     DEFAULT FALSE,
    can_view_records     BOOLEAN     DEFAULT FALSE,
    can_view_medications BOOLEAN     DEFAULT FALSE,
    can_view_reports     BOOLEAN     DEFAULT TRUE,
    can_use_ai           BOOLEAN     DEFAULT FALSE,
    can_modify           BOOLEAN     DEFAULT FALSE,
    can_delete           BOOLEAN     DEFAULT FALSE,
    status               VARCHAR(20) DEFAULT 'pending',
    expires_at           TIMESTAMP   NOT NULL,
    responded_at         TIMESTAMP,
    created_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS family_permissions (
    id                   BIGINT      AUTO_INCREMENT PRIMARY KEY,
    family_id            BIGINT      NOT NULL,
    subject_user_id      BIGINT      NOT NULL,
    grantee_user_id      BIGINT      NOT NULL,
    can_view_health      BOOLEAN     DEFAULT FALSE,
    can_add_health       BOOLEAN     DEFAULT FALSE,
    can_view_records     BOOLEAN     DEFAULT FALSE,
    can_view_medications BOOLEAN     DEFAULT FALSE,
    can_view_reports     BOOLEAN     DEFAULT FALSE,
    can_use_ai           BOOLEAN     DEFAULT FALSE,
    can_modify           BOOLEAN     DEFAULT FALSE,
    can_delete           BOOLEAN     DEFAULT FALSE,
    status               VARCHAR(20) DEFAULT 'active',
    created_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    revoked_at           TIMESTAMP,
    UNIQUE (family_id, subject_user_id, grantee_user_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id   BIGINT,
    detail        TEXT,
    ip_address    VARCHAR(50),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

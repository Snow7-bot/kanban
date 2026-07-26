-- ============================================================
-- KangBan Medical App – Flyway Migration V1
-- Init Schema: Creates all 13 core tables
-- Engine: InnoDB  |  Charset: utf8mb4  |  Timestamps: TIMESTAMP
-- ============================================================

-- -------------------------------------------------------
-- 1. users – platform user accounts
-- -------------------------------------------------------
CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    username          VARCHAR(50)  DEFAULT NULL             COMMENT 'Login username',
    phone             VARCHAR(20)  DEFAULT NULL             COMMENT 'Mobile phone number',
    email             VARCHAR(100) DEFAULT NULL             COMMENT 'Email address',
    password          VARCHAR(255) NOT NULL                 COMMENT 'BCrypt-hashed password',
    name              VARCHAR(50)  DEFAULT NULL             COMMENT 'Display name',
    gender            VARCHAR(10)  DEFAULT NULL             COMMENT 'Gender',
    birthday          DATE         DEFAULT NULL             COMMENT 'Date of birth',
    blood_type        VARCHAR(10)  DEFAULT NULL             COMMENT 'Blood type (A/B/AB/O)',
    height            DOUBLE       DEFAULT NULL             COMMENT 'Height in cm',
    weight            DOUBLE       DEFAULT NULL             COMMENT 'Weight in kg',
    avatar_url        VARCHAR(500) DEFAULT NULL             COMMENT 'Avatar image URL',
    emergency_contact VARCHAR(100) DEFAULT NULL             COMMENT 'Emergency contact info',
    status            TINYINT      NOT NULL DEFAULT 1       COMMENT '1=active 0=inactive -1=banned',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP    NULL     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    KEY idx_users_status (status),
    KEY idx_users_created_at (created_at),
    KEY idx_users_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Platform user accounts';

-- -------------------------------------------------------
-- 2. refresh_tokens – JWT refresh token store
-- -------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id    BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    token      VARCHAR(500) NOT NULL                 COMMENT 'Refresh token string',
    expires_at TIMESTAMP    NOT NULL                 COMMENT 'Token expiry moment',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP    NULL     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_rt_user_id (user_id),
    KEY idx_rt_expires_at (expires_at),
    KEY idx_rt_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='JWT refresh tokens';

-- -------------------------------------------------------
-- 3. family_members – user-managed family member profiles
-- -------------------------------------------------------
CREATE TABLE family_members (
    id         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id    BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    name       VARCHAR(50)  NOT NULL                 COMMENT 'Member display name',
    relation   VARCHAR(20)  DEFAULT NULL             COMMENT 'Relation (自己/父亲/母亲/配偶/子女/其他)',
    age        INT          DEFAULT NULL             COMMENT 'Age in years',
    gender     VARCHAR(10)  DEFAULT NULL             COMMENT 'Gender',
    avatar_url VARCHAR(500) DEFAULT NULL             COMMENT 'Member avatar URL',
    note       TEXT         DEFAULT NULL             COMMENT 'Free-text note',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP    NULL     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_fm_user_id (user_id),
    KEY idx_fm_deleted_at (deleted_at),
    CONSTRAINT fk_fm_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Family member profiles';

-- -------------------------------------------------------
-- 4. health_records – single-measurement health data points
-- -------------------------------------------------------
CREATE TABLE health_records (
    id           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id      BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    member_name  VARCHAR(50)  DEFAULT NULL             COMMENT 'Whom this record belongs to',
    metric       VARCHAR(50)  NOT NULL                 COMMENT 'Metric name (心率/血压/血糖/体重/… )',
    value        VARCHAR(50)  NOT NULL                 COMMENT 'Measured value(s)',
    unit         VARCHAR(20)  DEFAULT NULL             COMMENT 'Unit (bpm/mmHg/mmol/L/…)',
    recorded_date DATE        NOT NULL                 COMMENT 'Date of measurement',
    recorded_time TIME        DEFAULT NULL             COMMENT 'Time of measurement',
    note         TEXT         DEFAULT NULL             COMMENT 'User note',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP    NULL     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_hr_user_id (user_id),
    KEY idx_hr_metric (metric),
    KEY idx_hr_recorded_date (recorded_date),
    KEY idx_hr_created_at (created_at),
    KEY idx_hr_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Single-measurement health data points';

-- -------------------------------------------------------
-- 5. health_reports – periodic aggregated health reports
-- -------------------------------------------------------
CREATE TABLE health_reports (
    id           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id      BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    member_name  VARCHAR(50)  DEFAULT NULL             COMMENT 'Subject name',
    period       VARCHAR(10)  NOT NULL                 COMMENT 'Period type (week/month/quarter/year)',
    period_start DATE         NOT NULL                 COMMENT 'Period start date',
    period_end   DATE         NOT NULL                 COMMENT 'Period end date',
    report_data  JSON         DEFAULT NULL             COMMENT 'Aggregated report payload',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_hrep_user_id (user_id),
    KEY idx_hrep_period (period),
    KEY idx_hrep_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Periodic aggregated health reports';

-- -------------------------------------------------------
-- 6. medical_records – uploaded medical documents & OCR results
-- -------------------------------------------------------
CREATE TABLE medical_records (
    id               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id          BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    record_name      VARCHAR(200) NOT NULL                 COMMENT 'User-facing record title',
    record_type      VARCHAR(50)  DEFAULT NULL             COMMENT 'Type (prescription/lab_report/discharge/…)',
    hospital         VARCHAR(100) DEFAULT NULL             COMMENT 'Hospital / institution name',
    department       VARCHAR(50)  DEFAULT NULL             COMMENT 'Medical department',
    doctor           VARCHAR(50)  DEFAULT NULL             COMMENT 'Attending doctor',
    record_date      DATE         DEFAULT NULL             COMMENT 'Date of the record',
    file_url         VARCHAR(500) DEFAULT NULL             COMMENT 'Uploaded file URL',
    file_size        BIGINT       DEFAULT NULL             COMMENT 'File size in bytes',
    file_type        VARCHAR(20)  DEFAULT NULL             COMMENT 'MIME type or extension',
    status           VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending|analyzing|completed|failed',
    confidence       INT          DEFAULT NULL             COMMENT 'OCR confidence 0-100',
    ocr_text         TEXT         DEFAULT NULL             COMMENT 'Raw OCR extracted text',
    diagnosis_data   JSON         DEFAULT NULL             COMMENT 'Extracted diagnoses',
    medications_data JSON         DEFAULT NULL             COMMENT 'Extracted medications',
    advices_data     JSON         DEFAULT NULL             COMMENT 'Extracted medical advice',
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP    NULL     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_mr_user_id (user_id),
    KEY idx_mr_status (status),
    KEY idx_mr_record_type (record_type),
    KEY idx_mr_record_date (record_date),
    KEY idx_mr_created_at (created_at),
    KEY idx_mr_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Uploaded medical documents and OCR analysis results';

-- -------------------------------------------------------
-- 7. ocr_analysis_tasks – async OCR job tracking
-- -------------------------------------------------------
CREATE TABLE ocr_analysis_tasks (
    id            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    record_id     BIGINT       NOT NULL                 COMMENT 'FK to medical_records.id',
    user_id       BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    status        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending|processing|completed|failed',
    progress      INT          NOT NULL DEFAULT 0       COMMENT 'Progress percentage 0-100',
    result_data   JSON         DEFAULT NULL             COMMENT 'Final OCR result payload',
    error_message TEXT         DEFAULT NULL             COMMENT 'Error detail if failed',
    started_at    TIMESTAMP    NULL     DEFAULT NULL     COMMENT 'Processing start time',
    completed_at  TIMESTAMP    NULL     DEFAULT NULL     COMMENT 'Processing completion time',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_oat_record_id (record_id),
    KEY idx_oat_user_id (user_id),
    KEY idx_oat_status (status),
    KEY idx_oat_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Async OCR analysis job tracking';

-- -------------------------------------------------------
-- 8. medications – medication library & user schedules
-- -------------------------------------------------------
CREATE TABLE medications (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id     BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    name        VARCHAR(100) NOT NULL                 COMMENT 'Drug name',
    dosage      VARCHAR(50)  DEFAULT NULL             COMMENT 'Dosage amount (e.g. 500mg)',
    unit        VARCHAR(20)  DEFAULT NULL             COMMENT 'Dosage unit (片/粒/毫升/mg/…)',
    instruction VARCHAR(100) DEFAULT NULL             COMMENT 'Usage instruction (e.g. 饭后服用)',
    frequency   VARCHAR(50)  DEFAULT NULL             COMMENT 'Frequency description',
    inventory   INT          NOT NULL DEFAULT 0       COMMENT 'Current inventory count',
    times       JSON         DEFAULT NULL             COMMENT 'Scheduled times array ["08:00","20:00"]',
    start_date  DATE         DEFAULT NULL             COMMENT 'Treatment start date',
    end_date    DATE         DEFAULT NULL             COMMENT 'Treatment end date',
    note        TEXT         DEFAULT NULL             COMMENT 'Free-text notes',
    status      VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active|paused|completed|discontinued',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP    NULL     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_med_user_id (user_id),
    KEY idx_med_status (status),
    KEY idx_med_name (name),
    KEY idx_med_created_at (created_at),
    KEY idx_med_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Medication library and user schedules';

-- -------------------------------------------------------
-- 9. dose_records – individual dose intake log
-- -------------------------------------------------------
CREATE TABLE dose_records (
    id             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    medication_id  BIGINT       NOT NULL                 COMMENT 'FK to medications.id',
    user_id        BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    scheduled_time TIME         NOT NULL                 COMMENT 'Scheduled dose time',
    confirmed_at   TIMESTAMP    NULL     DEFAULT NULL     COMMENT 'Actual confirmation timestamp',
    status         VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending|completed|skipped',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dr_medication_id (medication_id),
    KEY idx_dr_user_id (user_id),
    KEY idx_dr_status (status),
    KEY idx_dr_scheduled_time (scheduled_time),
    KEY idx_dr_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Individual dose intake log';

-- -------------------------------------------------------
-- 10. drug_interaction_results – drug interaction check results
-- -------------------------------------------------------
CREATE TABLE drug_interaction_results (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id     BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    drug_names  VARCHAR(500) NOT NULL                 COMMENT 'Semicolon-separated drug names checked',
    result_data JSON         DEFAULT NULL             COMMENT 'Interaction analysis payload',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dir_user_id (user_id),
    KEY idx_dir_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Drug interaction check results';

-- -------------------------------------------------------
-- 11. chat_sessions – AI assistant conversation sessions
-- -------------------------------------------------------
CREATE TABLE chat_sessions (
    id           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id      BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    title        VARCHAR(200) DEFAULT NULL             COMMENT 'Session title',
    status       VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active|archived|deleted',
    patient_data JSON         DEFAULT NULL             COMMENT 'Contextual patient snapshot',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP    NULL     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_cs_user_id (user_id),
    KEY idx_cs_status (status),
    KEY idx_cs_created_at (created_at),
    KEY idx_cs_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI assistant conversation sessions';

-- -------------------------------------------------------
-- 12. chat_messages – individual chat messages
-- -------------------------------------------------------
CREATE TABLE chat_messages (
    id             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    session_id     BIGINT       NOT NULL                 COMMENT 'FK to chat_sessions.id',
    user_id        BIGINT       NOT NULL                 COMMENT 'FK to users.id',
    role           VARCHAR(20)  NOT NULL                 COMMENT 'user|assistant',
    content        TEXT         NOT NULL                 COMMENT 'Message body',
    attachment_url VARCHAR(500) DEFAULT NULL             COMMENT 'Optional attachment URL',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cm_session_id (session_id),
    KEY idx_cm_user_id (user_id),
    KEY idx_cm_role (role),
    KEY idx_cm_created_at (created_at),
    CONSTRAINT fk_cm_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Individual chat messages';

-- -------------------------------------------------------
-- 13. audit_logs – security & operation audit trail
-- -------------------------------------------------------
CREATE TABLE audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
    user_id       BIGINT       DEFAULT NULL             COMMENT 'FK to users.id (nullable for system actions)',
    action        VARCHAR(100) NOT NULL                 COMMENT 'Action identifier (LOGIN/LOGOUT/CREATE/UPDATE/DELETE/…)',
    resource_type VARCHAR(50)  DEFAULT NULL             COMMENT 'Target resource type (user/record/medication/…)',
    resource_id   BIGINT       DEFAULT NULL             COMMENT 'Target resource primary key',
    detail        TEXT         DEFAULT NULL             COMMENT 'JSON or text detail',
    ip_address    VARCHAR(50)  DEFAULT NULL             COMMENT 'Client IP address',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_al_user_id (user_id),
    KEY idx_al_action (action),
    KEY idx_al_resource_type (resource_type),
    KEY idx_al_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Security and operation audit trail';

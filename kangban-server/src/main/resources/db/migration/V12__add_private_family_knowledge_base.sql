-- Private RAG index for OCR-completed medical records.
-- Scope is stored with every document and enforced again during retrieval.
CREATE TABLE family_knowledge_documents (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    medical_record_id  BIGINT NOT NULL,
    owner_user_id     BIGINT NOT NULL,
    subject_user_id   BIGINT NOT NULL,
    family_id         BIGINT DEFAULT NULL,
    member_id         BIGINT DEFAULT NULL,
    title             VARCHAR(255) NOT NULL,
    source            VARCHAR(255) NOT NULL DEFAULT '家庭私有病历',
    version           INT NOT NULL DEFAULT 1,
    status            VARCHAR(30) NOT NULL DEFAULT 'READY',
    embedding_model   VARCHAR(100) DEFAULT NULL,
    record_updated_at TIMESTAMP NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    revoked_at        TIMESTAMP NULL,
    deleted_at        TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fkd_record (medical_record_id),
    KEY idx_fkd_scope (status, owner_user_id, subject_user_id, member_id),
    KEY idx_fkd_deleted (deleted_at, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='家庭私有病历 RAG 文档索引';

CREATE TABLE family_knowledge_chunks (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    document_id       BIGINT NOT NULL,
    owner_user_id     BIGINT NOT NULL,
    subject_user_id   BIGINT NOT NULL,
    family_id         BIGINT DEFAULT NULL,
    member_id         BIGINT DEFAULT NULL,
    chunk_index       INT NOT NULL,
    page_number       INT DEFAULT NULL,
    section           VARCHAR(255) DEFAULT NULL,
    content           TEXT NOT NULL,
    token_count       INT NOT NULL,
    embedding_json    LONGTEXT DEFAULT NULL,
    embedding_model   VARCHAR(100) DEFAULT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fkc_document_index (document_id, chunk_index),
    KEY idx_fkc_scope (subject_user_id, member_id, document_id),
    CONSTRAINT fk_fkc_document FOREIGN KEY (document_id) REFERENCES family_knowledge_documents(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='家庭私有病历 RAG 文本块和向量';

CREATE TABLE knowledge_outbox_events (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    event_key         VARCHAR(180) NOT NULL,
    event_type        VARCHAR(50) NOT NULL,
    medical_record_id  BIGINT NOT NULL,
    owner_user_id     BIGINT NOT NULL,
    subject_user_id   BIGINT NOT NULL,
    family_id         BIGINT DEFAULT NULL,
    member_id         BIGINT DEFAULT NULL,
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts          INT NOT NULL DEFAULT 0,
    last_error        VARCHAR(1000) DEFAULT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at      TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_koe_event_key (event_key),
    KEY idx_koe_status_created (status, created_at),
    KEY idx_koe_record (medical_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='家庭私有知识索引事件箱';

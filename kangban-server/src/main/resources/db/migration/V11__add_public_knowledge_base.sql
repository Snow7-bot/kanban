-- Public knowledge base lifecycle for the built-in Agent.
-- Embeddings are kept as JSON for the local MVP; production pgvector can replace this column.
CREATE TABLE knowledge_documents (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    title         VARCHAR(255) NOT NULL,
    source        VARCHAR(255) NOT NULL,
    source_url    VARCHAR(1000) DEFAULT NULL,
    file_name     VARCHAR(255) NOT NULL,
    media_type    VARCHAR(150) DEFAULT NULL,
    file_sha256   CHAR(64) NOT NULL,
    file_size     BIGINT NOT NULL,
    version       INT NOT NULL DEFAULT 1,
    status        VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    raw_content   MEDIUMBLOB NOT NULL,
    review_note   VARCHAR(1000) DEFAULT NULL,
    created_by    BIGINT NOT NULL,
    updated_by    BIGINT DEFAULT NULL,
    published_at  TIMESTAMP NULL,
    revoked_at    TIMESTAMP NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kd_sha256 (file_sha256),
    KEY idx_kd_status_updated (status, updated_at),
    KEY idx_kd_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Reviewed public knowledge documents';

CREATE TABLE knowledge_chunks (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    document_id    BIGINT NOT NULL,
    chunk_index    INT NOT NULL,
    page_number    INT DEFAULT NULL,
    section        VARCHAR(255) DEFAULT NULL,
    content        TEXT NOT NULL,
    token_count    INT NOT NULL,
    embedding_json LONGTEXT DEFAULT NULL,
    embedding_model VARCHAR(100) DEFAULT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kc_document_index (document_id, chunk_index),
    KEY idx_kc_document (document_id),
    CONSTRAINT fk_kc_document FOREIGN KEY (document_id) REFERENCES knowledge_documents(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Knowledge chunks and retrieval metadata';

CREATE TABLE ingestion_jobs (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    document_id      BIGINT NOT NULL,
    job_type         VARCHAR(30) NOT NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_chunks     INT NOT NULL DEFAULT 0,
    processed_chunks INT NOT NULL DEFAULT 0,
    error_message    VARCHAR(1000) DEFAULT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at     TIMESTAMP NULL,
    PRIMARY KEY (id),
    KEY idx_ij_document (document_id),
    KEY idx_ij_status (status),
    CONSTRAINT fk_ij_document FOREIGN KEY (document_id) REFERENCES knowledge_documents(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Knowledge ingestion and reindex jobs';

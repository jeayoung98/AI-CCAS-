CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_source (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_organization VARCHAR(150),
    source_url TEXT,
    published_at DATE,
    checked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_knowledge_source_source_type CHECK (
        source_type IN (
            'OFFICIAL_DOCUMENT',
            'TEAM_VERIFIED_CASESET',
            'CATEGORY_POLICY'
        )
    )
);

CREATE TABLE retrieval_item (
    id BIGSERIAL PRIMARY KEY,
    item_type VARCHAR(30) NOT NULL,
    source_id BIGINT REFERENCES knowledge_source(id),
    external_key VARCHAR(150),
    title VARCHAR(255),
    masked_text TEXT,
    structured_payload JSONB NOT NULL,
    search_text TEXT NOT NULL,
    subject_matter_codes TEXT[] NOT NULL DEFAULT '{}',
    requested_action_codes TEXT[] NOT NULL DEFAULT '{}',
    risk_signal_codes TEXT[] NOT NULL DEFAULT '{}',
    place_type VARCHAR(40),
    relationship_type VARCHAR(40),
    ongoing_status VARCHAR(50),
    verified_category_code VARCHAR(100),
    verified_route_code VARCHAR(100),
    review_status VARCHAR(30) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    embedding VECTOR(1536) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dimensions INT NOT NULL,
    structure_version VARCHAR(50) NOT NULL,
    search_text_version VARCHAR(50) NOT NULL,
    embedding_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_retrieval_item_item_type CHECK (
        item_type IN (
            'VERIFIED_CASE',
            'CATEGORY_REFERENCE',
            'OFFICIAL_GUIDE'
        )
    ),
    CONSTRAINT chk_retrieval_item_review_status CHECK (
        review_status IN (
            'DRAFT',
            'REVIEW_REQUIRED',
            'VERIFIED',
            'REJECTED'
        )
    ),
    CONSTRAINT chk_retrieval_item_embedding_dimensions CHECK (embedding_dimensions = 1536),
    CONSTRAINT uq_retrieval_item_type_external_embedding UNIQUE (item_type, external_key, embedding_version)
);

CREATE TABLE retrieval_query (
    id UUID PRIMARY KEY,
    input_source VARCHAR(20) NOT NULL,
    masking_completed BOOLEAN NOT NULL,
    masked_text TEXT NOT NULL,
    structured_payload JSONB NOT NULL,
    search_text TEXT NOT NULL,
    subject_matter_codes TEXT[] NOT NULL DEFAULT '{}',
    requested_action_codes TEXT[] NOT NULL DEFAULT '{}',
    risk_signal_codes TEXT[] NOT NULL DEFAULT '{}',
    place_type VARCHAR(40),
    relationship_type VARCHAR(40),
    ongoing_status VARCHAR(50),
    embedding VECTOR(1536) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dimensions INT NOT NULL,
    structure_version VARCHAR(50) NOT NULL,
    search_text_version VARCHAR(50) NOT NULL,
    embedding_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_retrieval_query_input_source CHECK (input_source IN ('TEXT', 'STT')),
    CONSTRAINT chk_retrieval_query_masking_completed CHECK (masking_completed = TRUE),
    CONSTRAINT chk_retrieval_query_embedding_dimensions CHECK (embedding_dimensions = 1536)
);

CREATE TABLE retrieval_hit (
    id BIGSERIAL PRIMARY KEY,
    query_id UUID NOT NULL REFERENCES retrieval_query(id),
    item_id BIGINT NOT NULL REFERENCES retrieval_item(id),
    channel VARCHAR(30) NOT NULL,
    result_rank INT NOT NULL,
    cosine_similarity DOUBLE PRECISION NOT NULL,
    passed_channel_cutoff BOOLEAN,
    rejection_reason VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_retrieval_hit_channel CHECK (
        channel IN (
            'VERIFIED_CASE',
            'CATEGORY_REFERENCE',
            'OFFICIAL_GUIDE'
        )
    ),
    CONSTRAINT chk_retrieval_hit_result_rank CHECK (result_rank > 0),
    CONSTRAINT uq_retrieval_hit_query_channel_rank UNIQUE (query_id, channel, result_rank)
);

CREATE TABLE retrieval_decision (
    query_id UUID PRIMARY KEY REFERENCES retrieval_query(id),
    evidence_status VARCHAR(40) NOT NULL,
    risk_signal_present BOOLEAN NOT NULL DEFAULT FALSE,
    best_case_score DOUBLE PRECISION,
    best_category_score DOUBLE PRECISION,
    best_guide_score DOUBLE PRECISION,
    category_margin DOUBLE PRECISION,
    case_category_agreement DOUBLE PRECISION,
    decision_reason_codes TEXT[] NOT NULL DEFAULT '{}',
    threshold_profile_version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_retrieval_decision_evidence_status CHECK (
        evidence_status IN (
            'CASE_AND_REFERENCE_SUPPORTED',
            'REFERENCE_SUPPORTED',
            'GUIDE_ONLY',
            'AMBIGUOUS',
            'INSUFFICIENT_EVIDENCE'
        )
    )
);

CREATE INDEX idx_retrieval_item_type_active_review
    ON retrieval_item (item_type, is_active, review_status);

CREATE INDEX idx_retrieval_item_subject_matter_codes
    ON retrieval_item USING GIN (subject_matter_codes);

CREATE INDEX idx_retrieval_item_requested_action_codes
    ON retrieval_item USING GIN (requested_action_codes);

CREATE INDEX idx_retrieval_query_created_at
    ON retrieval_query (created_at);

CREATE INDEX idx_retrieval_hit_query_channel
    ON retrieval_hit (query_id, channel);

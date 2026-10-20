-- W2 RAG 域迁移（ADR-004：pgvector HNSW + tsvector GIN，应用层 RRF）
-- 表：kb_doc / kb_chunk
-- embedding 锁定 1536 维（对应 text-embedding-3-small；HNSW 索引要求列声明维度，
-- 更换 embedding 模型时需新增迁移脚本改列类型，见 ADR-004）；
-- tsv 为普通列（非 generated）：simple 分析器不支持中文，应用层先做 CJK bigram 分词再写入（ADR-004）。

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE kb_doc (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title          varchar(300) NOT NULL,
    source_type    varchar(20)  NOT NULL,
    chunk_size     integer      NOT NULL,
    overlap        integer      NOT NULL,
    content_length integer      NOT NULL DEFAULT 0,
    status         varchar(20)  NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_kb_doc_source_type CHECK (source_type IN ('md', 'txt', 'pdf')),
    CONSTRAINT chk_kb_doc_status CHECK (status IN ('READY', 'FAILED'))
);

CREATE TABLE kb_chunk (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id     uuid     NOT NULL REFERENCES kb_doc (id) ON DELETE CASCADE,
    seq        integer  NOT NULL,
    content    text     NOT NULL,
    embedding  vector(1536),
    tsv        tsvector,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_kb_chunk_seq UNIQUE (doc_id, seq)
);

CREATE INDEX idx_kb_chunk_doc ON kb_chunk (doc_id);
CREATE INDEX idx_kb_chunk_hnsw ON kb_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_kb_chunk_tsv ON kb_chunk USING gin (tsv);

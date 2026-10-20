-- W2 任务域迁移（TASK_BOOK §5.2 PG 方言基线：uuid 主键 + gen_random_uuid() / timestamptz / jsonb）
-- 表：task / task_step / audit_log / approval / eval_case
-- 状态枚举以 varchar + CHECK 落地，避免 PG enum 类型的迁移演化负担。

CREATE TABLE task (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt           text         NOT NULL,
    status           varchar(20)  NOT NULL,
    pause_reason     varchar(200),
    result           text,
    steps            integer      NOT NULL DEFAULT 0,
    token_usage      jsonb,
    context_messages jsonb,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_task_status CHECK (
        status IN ('CREATED', 'RUNNING', 'PAUSED', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_task_status ON task (status);
CREATE INDEX idx_task_created_at ON task (created_at DESC);

-- 每步落盘的检查点（含工具入参出参摘要）
CREATE TABLE task_step (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id       uuid        NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    seq           integer     NOT NULL,
    tool_name     varchar(100) NOT NULL,
    tool_call_id  varchar(100),
    arguments     jsonb,
    result_status varchar(20),
    result_summary text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_task_step_seq UNIQUE (task_id, seq)
);

CREATE INDEX idx_task_step_task ON task_step (task_id);

-- 全链路审计留痕：谁、何时、做了什么、参数与结果
CREATE TABLE audit_log (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id    uuid,
    actor      varchar(100) NOT NULL,
    action     varchar(100) NOT NULL,
    detail     jsonb,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_task ON audit_log (task_id);
CREATE INDEX idx_audit_created_at ON audit_log (created_at DESC);

-- HITL 审批单（WRITE/DANGEROUS 工具拦截后生成）
CREATE TABLE approval (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id             uuid         NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    tool_call_id        varchar(100) NOT NULL,
    tool_name           varchar(100) NOT NULL,
    arguments           jsonb,
    required_permission varchar(20)  NOT NULL,
    status              varchar(20)  NOT NULL DEFAULT 'PENDING',
    reason              text,
    decided_by          varchar(100),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    decided_at          timestamptz,
    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_approval_status ON approval (status);
CREATE INDEX idx_approval_task ON approval (task_id);

-- 评测样本（W4 全量使用，W2 先建表）
CREATE TABLE eval_case (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    category     varchar(50),
    input        text     NOT NULL,
    ground_truth jsonb    NOT NULL,
    judge_rule   varchar(50),
    enabled      boolean  NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now()
);

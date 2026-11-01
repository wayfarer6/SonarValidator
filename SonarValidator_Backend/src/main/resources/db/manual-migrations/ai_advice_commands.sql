ALTER TABLE policy_advice ADD COLUMN IF NOT EXISTS commands_json VARCHAR(30000);
ALTER TABLE policy_advice ADD COLUMN IF NOT EXISTS context_fingerprint VARCHAR(64);
ALTER TABLE policy_advice ADD COLUMN IF NOT EXISTS target_agent VARCHAR(120);
CREATE TABLE IF NOT EXISTS advice_execution (
    execution_id VARCHAR(36) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    project_key VARCHAR(120) NOT NULL,
    advice_id VARCHAR(60) NOT NULL,
    proposal_index INTEGER NOT NULL,
    agent_id VARCHAR(120) NOT NULL,
    active_agent_id VARCHAR(120) UNIQUE,
    operation VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_session_id VARCHAR(120),
    requested_by VARCHAR(200) NOT NULL,
    plan_json VARCHAR(12000) NOT NULL,
    result_json VARCHAR(120000),
    detail VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    dispatched_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_advice_execution_history ON advice_execution(project_key,created_at);

CREATE TABLE workspace_rate_limit_windows
(
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    action VARCHAR(32) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0),
    PRIMARY KEY (workspace_id, action)
);

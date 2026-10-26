-- ═══════════════════════════════════════════════════════════════════════════
-- V1__init_schema.sql
-- Initial schema: users, workspaces, workspace_members, tasks
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── Extension ───────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";   -- for fast LIKE/ILIKE on task titles

-- ─── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            UUID        NOT NULL DEFAULT uuid_generate_v4(),
    username      VARCHAR(64) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(128),
    global_role   VARCHAR(32)  NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users          PRIMARY KEY (id),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_global_role   CHECK (global_role IN ('ADMIN', 'USER'))
);

-- B-Tree indexes for equality lookups (login, JoinFetch)
CREATE INDEX IF NOT EXISTS idx_users_email    ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);

-- ─── Workspaces ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workspaces (
    id          UUID         NOT NULL DEFAULT uuid_generate_v4(),
    name        VARCHAR(128) NOT NULL,
    slug        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    owner_id    UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_workspaces       PRIMARY KEY (id),
    CONSTRAINT uq_workspaces_slug  UNIQUE (slug),
    CONSTRAINT fk_workspace_owner  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workspaces_slug  ON workspaces (slug);
CREATE INDEX IF NOT EXISTS idx_workspaces_owner ON workspaces (owner_id);

-- ─── Workspace Members ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workspace_members (
    id           UUID        NOT NULL DEFAULT uuid_generate_v4(),
    workspace_id UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    role         VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_workspace_members    PRIMARY KEY (id),
    CONSTRAINT uq_workspace_member     UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_wm_workspace         FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_wm_user              FOREIGN KEY (user_id)      REFERENCES users(id)       ON DELETE CASCADE,
    CONSTRAINT chk_workspace_role      CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER'))
);

CREATE INDEX IF NOT EXISTS idx_wm_workspace_id ON workspace_members (workspace_id);
CREATE INDEX IF NOT EXISTS idx_wm_user_id      ON workspace_members (user_id);

-- ─── Tasks ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tasks (
    id                 UUID         NOT NULL DEFAULT uuid_generate_v4(),
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    status             VARCHAR(32)  NOT NULL DEFAULT 'TODO',
    priority           VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    deadline           TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0,          -- optimistic lock
    workspace_id       UUID         NOT NULL,
    creator_id         UUID,
    assignee_id        UUID,
    deadline_notified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tasks             PRIMARY KEY (id),
    CONSTRAINT fk_task_workspace    FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_creator      FOREIGN KEY (creator_id)   REFERENCES users(id)       ON DELETE SET NULL,
    CONSTRAINT fk_task_assignee     FOREIGN KEY (assignee_id)  REFERENCES users(id)       ON DELETE SET NULL,
    CONSTRAINT chk_task_status      CHECK (status   IN ('TODO','IN_PROGRESS','IN_REVIEW','DONE','CANCELLED')),
    CONSTRAINT chk_task_priority    CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

-- ── Task indexes — designed around the hot query paths ──────────────────────

-- Equality on workspace (always in WHERE clause for tenant isolation)
CREATE INDEX IF NOT EXISTS idx_tasks_workspace_id ON tasks (workspace_id);

-- Status filter (list active tasks in workspace)
CREATE INDEX IF NOT EXISTS idx_tasks_status       ON tasks (status);

-- Priority filter
CREATE INDEX IF NOT EXISTS idx_tasks_priority     ON tasks (priority);

-- Deadline-range scan (scheduler query + deadline filter)
CREATE INDEX IF NOT EXISTS idx_tasks_deadline     ON tasks (deadline) WHERE deadline IS NOT NULL;

-- Assignee filter (user's assigned tasks)
CREATE INDEX IF NOT EXISTS idx_tasks_assignee_id  ON tasks (assignee_id) WHERE assignee_id IS NOT NULL;

-- ── Composite indexes for the P99 hot-path queries ───────────────────────────
-- "List active tasks in workspace, by deadline" — most common list view
CREATE INDEX IF NOT EXISTS idx_tasks_ws_status_dl
    ON tasks (workspace_id, status, deadline);

-- "My tasks in workspace" — assignee workload view
CREATE INDEX IF NOT EXISTS idx_tasks_ws_assignee
    ON tasks (workspace_id, assignee_id);

-- ── Deadline notification scan — covers the scheduler query exactly ──────────
CREATE INDEX IF NOT EXISTS idx_tasks_deadline_notify
    ON tasks (deadline, deadline_notified)
    WHERE deadline_notified = FALSE;

-- ── Trigram index for ILIKE full-text search on title ────────────────────────
CREATE INDEX IF NOT EXISTS idx_tasks_title_trgm
    ON tasks USING gin (title gin_trgm_ops);

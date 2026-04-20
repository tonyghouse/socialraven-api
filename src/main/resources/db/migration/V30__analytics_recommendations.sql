create table if not exists analytics_recommendation (
    id bigserial primary key,
    workspace_id varchar(255) not null,
    slice_key varchar(500) not null,
    slice_fingerprint varchar(128) not null,
    recommendation_key varchar(500) not null,
    scope varchar(50) not null,
    metric varchar(50) not null,
    source_type varchar(100) not null,
    context_label varchar(255),
    title varchar(255) not null,
    action_summary varchar(2000) not null,
    evidence_summary varchar(4000) not null,
    confidence_tier varchar(20) not null,
    priority varchar(20) not null,
    expected_impact_score double precision not null,
    time_window_start_at timestamptz,
    time_window_end_at timestamptz,
    active boolean not null default true,
    dismissed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_analytics_recommendation_workspace_slice
    on analytics_recommendation (workspace_id, slice_key);

create index if not exists idx_analytics_recommendation_workspace_slice_active
    on analytics_recommendation (workspace_id, slice_key, active);

create unique index if not exists uq_analytics_recommendation_workspace_slice_key
    on analytics_recommendation (workspace_id, slice_key, recommendation_key);

-- FUSH ERP Mobile v202 — Local Yemen FX engine server-side cache.
-- Run once in the same Supabase project used by the mobile app.
-- The Android app NEVER writes these tables directly; the Edge Function uses service_role.

create table if not exists public.fx_rate_feed_batches (
    batch_id text primary key,
    fetched_at timestamptz not null default now(),
    fallback_mode text not null,
    primary_source text not null,
    comparison_source text not null default '',
    source_hash text not null default '',
    status text not null default 'OK',
    payload jsonb not null
);

create index if not exists idx_fx_rate_feed_batches_fetched_at
    on public.fx_rate_feed_batches(fetched_at desc);

create table if not exists public.fx_rate_feed_rates (
    id bigint generated always as identity primary key,
    batch_id text not null references public.fx_rate_feed_batches(batch_id) on delete cascade,
    market_region text not null check (market_region in ('ADEN','SANAA')),
    currency_code text not null check (currency_code in ('USD','SAR')),
    rate_type text not null check (rate_type in ('BUY','SELL')),
    rate_yer numeric(20,8) not null check (rate_yer > 0),
    source_published_at timestamptz not null,
    primary_source text not null,
    primary_source_url text not null default '',
    comparison_rate_yer numeric(20,8),
    comparison_source text not null default '',
    comparison_source_url text not null default '',
    comparison_published_at timestamptz,
    variance_percent numeric(12,6),
    source_status text not null default 'PRIMARY_ONLY',
    raw_hash text not null default '',
    unique(batch_id, market_region, currency_code, rate_type)
);

create index if not exists idx_fx_rate_feed_rates_batch
    on public.fx_rate_feed_rates(batch_id);

alter table public.fx_rate_feed_batches enable row level security;
alter table public.fx_rate_feed_rates enable row level security;

-- Deliberately no anon/authenticated policies. The Edge Function writes with service_role
-- and returns only the normalized JSON contract needed by the app.
revoke all on table public.fx_rate_feed_batches from anon, authenticated;
revoke all on table public.fx_rate_feed_rates from anon, authenticated;

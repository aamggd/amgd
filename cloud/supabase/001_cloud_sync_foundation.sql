-- FUSH ERP Mobile v155 - Cloud Sync Foundation reference schema
-- This script is idempotent and intentionally does not create a Supabase auth user.
-- Add organization membership separately for each auth.users.id.

begin;

create table if not exists public.fush_sync_devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    device_key text not null,
    device_name text,
    app_version text,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, device_key)
);

create table if not exists public.fush_sync_state (
    device_id uuid not null references public.fush_sync_devices(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    sync_scope text not null,
    last_server_version bigint not null default 0,
    last_synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (device_id, sync_scope)
);

create table if not exists public.fush_organizations (
    id uuid primary key,
    code text not null unique,
    name text not null,
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.fush_organization_members (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'USER',
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (organization_id, user_id)
);

-- v213 commercial baseline: do not seed a vendor/customer tenant.
-- Each customer organization is created after authentication by fush_create_organization().

alter table public.fush_sync_devices
    add column if not exists organization_id uuid references public.fush_organizations(id);

alter table public.fush_sync_state
    add column if not exists organization_id uuid references public.fush_organizations(id);

create or replace function public.fush_is_org_member(target_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.fush_organization_members m
        where m.organization_id = target_organization_id
          and m.user_id = auth.uid()
          and m.is_active = true
    );
$$;

revoke all on function public.fush_is_org_member(uuid) from public;
grant execute on function public.fush_is_org_member(uuid) to authenticated;

alter table public.fush_sync_devices enable row level security;
alter table public.fush_sync_state enable row level security;
alter table public.fush_organizations enable row level security;
alter table public.fush_organization_members enable row level security;

revoke all on table public.fush_sync_devices from anon;
revoke all on table public.fush_sync_state from anon;
revoke all on table public.fush_organizations from anon;
revoke all on table public.fush_organization_members from anon;

grant select, insert, update, delete on table public.fush_sync_devices to authenticated;
grant select, insert, update, delete on table public.fush_sync_state to authenticated;
grant select on table public.fush_organizations to authenticated;
grant select on table public.fush_organization_members to authenticated;

drop policy if exists "fush_org_select_member" on public.fush_organizations;
create policy "fush_org_select_member" on public.fush_organizations
for select to authenticated using (public.fush_is_org_member(id));

drop policy if exists "fush_member_select_self" on public.fush_organization_members;
create policy "fush_member_select_self" on public.fush_organization_members
for select to authenticated using (user_id = (select auth.uid()) and is_active = true);

drop policy if exists "fush_devices_select_own" on public.fush_sync_devices;
create policy "fush_devices_select_own" on public.fush_sync_devices
for select to authenticated
using (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id));

drop policy if exists "fush_devices_insert_own" on public.fush_sync_devices;
create policy "fush_devices_insert_own" on public.fush_sync_devices
for insert to authenticated
with check (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id));

drop policy if exists "fush_devices_update_own" on public.fush_sync_devices;
create policy "fush_devices_update_own" on public.fush_sync_devices
for update to authenticated
using (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id))
with check (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id));

drop policy if exists "fush_devices_delete_own" on public.fush_sync_devices;
create policy "fush_devices_delete_own" on public.fush_sync_devices
for delete to authenticated
using (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id));

drop policy if exists "fush_sync_state_select_own" on public.fush_sync_state;
create policy "fush_sync_state_select_own" on public.fush_sync_state
for select to authenticated
using (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id));

drop policy if exists "fush_sync_state_insert_own" on public.fush_sync_state;
create policy "fush_sync_state_insert_own" on public.fush_sync_state
for insert to authenticated
with check (
    user_id = (select auth.uid())
    and public.fush_is_org_member(organization_id)
    and exists (
        select 1 from public.fush_sync_devices d
        where d.id = device_id
          and d.user_id = (select auth.uid())
          and d.organization_id = organization_id
    )
);

drop policy if exists "fush_sync_state_update_own" on public.fush_sync_state;
create policy "fush_sync_state_update_own" on public.fush_sync_state
for update to authenticated
using (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id))
with check (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id));

drop policy if exists "fush_sync_state_delete_own" on public.fush_sync_state;
create policy "fush_sync_state_delete_own" on public.fush_sync_state
for delete to authenticated
using (user_id = (select auth.uid()) and public.fush_is_org_member(organization_id));

create index if not exists idx_fush_sync_devices_user on public.fush_sync_devices(user_id);
create index if not exists idx_fush_sync_state_user on public.fush_sync_state(user_id);
create index if not exists idx_fush_members_user on public.fush_organization_members(user_id);
create index if not exists idx_fush_devices_org on public.fush_sync_devices(organization_id);
create index if not exists idx_fush_sync_state_org on public.fush_sync_state(organization_id);

commit;

-- v213 commercial flow: after authentication call public.fush_create_organization(code, name).
-- Existing installations keep their historical organization; this file is the fresh-install baseline.

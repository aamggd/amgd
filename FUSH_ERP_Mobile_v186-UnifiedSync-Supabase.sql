-- ============================================================================
-- FUSH ERP Mobile v186 — Unified Supabase Sync Schema + RLS Repair
-- File: FUSH_ERP_Mobile_v186-UnifiedSync-Supabase.sql
--
-- PURPOSE
--   One-time/re-runnable Supabase SQL for the complete FUSH cloud-sync surface.
--   It installs any missing sync objects through v185 and then HARDENS transport
--   permissions so synchronization is based on ACTIVE ORGANIZATION MEMBERSHIP,
--   not ERP business role.
--
-- IMPORTANT DESIGN RULE
--   * Business permissions (SALES/PURCHASES/PRODUCTION/ACCOUNTING/INVENTORY)
--     continue to decide who can create/edit/post operations in Android.
--   * Cloud transport (download/upload of records already produced by the app)
--     is allowed to every authenticated ACTIVE organization member.
--   * Conflict resolution authority remains restricted where the existing RPC
--     intentionally treats resolution as changing canonical truth.
--   * Hard DELETE is not granted to ordinary sync transport.
--
-- SAFE TO RE-RUN
--   The component migrations use IF NOT EXISTS / CREATE OR REPLACE / policy
--   replacement patterns. The final v186 hardening section is idempotent.
-- ============================================================================


-- ============================================================================
-- BEGIN COMPONENT: 001_cloud_sync_foundation.sql
-- ============================================================================
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

-- v213 commercial packaging: no default organization is inserted.
-- Existing deployed organizations are preserved; fresh customer tenants are created later
-- with public.fush_create_organization(...).

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

-- v213 commercial flow: create the customer organization with
-- public.fush_create_organization(...) after authentication.

-- END COMPONENT: 001_cloud_sync_foundation.sql

-- ============================================================================
-- BEGIN COMPONENT: V156_MULTI_USER_CLOUD_IDENTITY.sql
-- ============================================================================
begin;

-- ============================================================================
-- FUSH ERP Mobile v156 - Multi-user Cloud Identity
-- Run once in Supabase SQL Editor after the v155 Cloud Sync Foundation scripts.
-- No ERP business data is created or deleted by this migration.
-- ============================================================================

create table if not exists public.fush_cloud_user_bindings (
    organization_id uuid not null
        references public.fush_organizations(id)
        on delete cascade,
    cloud_user_id uuid not null
        references auth.users(id)
        on delete cascade,
    email text not null,
    local_username text not null,
    display_name text,
    role text not null,
    is_active boolean not null default true,
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (organization_id, cloud_user_id),
    unique (organization_id, local_username),
    unique (organization_id, email)
);

alter table public.fush_cloud_user_bindings enable row level security;

revoke all on table public.fush_cloud_user_bindings from anon;
revoke all on table public.fush_cloud_user_bindings from authenticated;
grant select on table public.fush_cloud_user_bindings to authenticated;

create index if not exists idx_fush_cloud_binding_local_username
on public.fush_cloud_user_bindings(organization_id, local_username);

create index if not exists idx_fush_cloud_binding_email
on public.fush_cloud_user_bindings(organization_id, email);

-- Owner test is kept in a SECURITY DEFINER helper so it can read membership
-- without recursive RLS evaluation.
create or replace function public.fush_is_org_owner(target_organization_id uuid)
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
          and upper(m.role) in ('OWNER', 'ADMIN')
    );
$$;

revoke all on function public.fush_is_org_owner(uuid) from public;
grant execute on function public.fush_is_org_owner(uuid) to authenticated;

-- A user can read only their own binding. An OWNER/ADMIN can inspect all
-- bindings in the organization in order to provision employees.
drop policy if exists "fush_cloud_binding_select_self_or_owner"
on public.fush_cloud_user_bindings;

create policy "fush_cloud_binding_select_self_or_owner"
on public.fush_cloud_user_bindings
for select
to authenticated
using (
    cloud_user_id = (select auth.uid())
    or public.fush_is_org_owner(organization_id)
);

-- First upgrade from v155: the already-connected owner has no binding row yet.
-- This function lets that OWNER claim exactly one local ADMIN identity for their
-- own auth.uid(). It cannot claim another cloud user.
create or replace function public.fush_claim_owner_identity(
    target_organization_id uuid,
    target_local_username text,
    target_display_name text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    caller_email text;
    caller_role text;
    normalized_username text := trim(target_local_username);
begin
    if caller is null then
        raise exception 'Authentication required';
    end if;

    if normalized_username = '' then
        raise exception 'Local username is required';
    end if;

    select upper(m.role)
      into caller_role
      from public.fush_organization_members m
     where m.organization_id = target_organization_id
       and m.user_id = caller
       and m.is_active = true
     limit 1;

    if caller_role not in ('OWNER', 'ADMIN') then
        raise exception 'OWNER cloud role required';
    end if;

    select lower(u.email)
      into caller_email
      from auth.users u
     where u.id = caller;

    if caller_email is null or caller_email = '' then
        raise exception 'Cloud account has no email';
    end if;

    if exists (
        select 1
        from public.fush_cloud_user_bindings b
        where b.organization_id = target_organization_id
          and lower(b.local_username) = lower(normalized_username)
          and b.cloud_user_id <> caller
    ) then
        raise exception 'Local username is already linked to another cloud account';
    end if;

    insert into public.fush_cloud_user_bindings (
        organization_id,
        cloud_user_id,
        email,
        local_username,
        display_name,
        role,
        is_active,
        created_by,
        updated_at
    ) values (
        target_organization_id,
        caller,
        caller_email,
        normalized_username,
        nullif(trim(target_display_name), ''),
        caller_role,
        true,
        caller,
        now()
    )
    on conflict (organization_id, cloud_user_id)
    do update set
        email = excluded.email,
        local_username = excluded.local_username,
        display_name = excluded.display_name,
        role = excluded.role,
        is_active = true,
        updated_at = now();

    return jsonb_build_object(
        'cloud_user_id', caller,
        'email', caller_email,
        'local_username', normalized_username,
        'display_name', nullif(trim(target_display_name), ''),
        'role', caller_role,
        'is_active', true
    );
end;
$$;

revoke all on function public.fush_claim_owner_identity(uuid, text, text) from public;
grant execute on function public.fush_claim_owner_identity(uuid, text, text) to authenticated;

-- OWNER-only provisioning. The Android app creates the Supabase Auth account
-- through the normal public sign-up endpoint; this RPC then attaches that auth
-- user to the FUSH organization and to one local ERP username/role.
create or replace function public.fush_provision_member(
    target_organization_id uuid,
    target_email text,
    target_local_username text,
    target_display_name text,
    target_role text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    target_user uuid;
    normalized_email text := lower(trim(target_email));
    normalized_username text := trim(target_local_username);
    normalized_role text := upper(trim(target_role));
begin
    if caller is null then
        raise exception 'Authentication required';
    end if;

    if not public.fush_is_org_owner(target_organization_id) then
        raise exception 'OWNER cloud role required';
    end if;

    if normalized_email = '' or normalized_username = '' then
        raise exception 'Email and local username are required';
    end if;

    if normalized_role !~ '^[A-Z0-9_]{2,40}$' then
        raise exception 'Invalid role code';
    end if;

    select u.id
      into target_user
      from auth.users u
     where lower(u.email) = normalized_email
     limit 1;

    if target_user is null then
        raise exception 'Supabase Auth user not found for this email';
    end if;

    if exists (
        select 1
        from public.fush_cloud_user_bindings b
        where b.organization_id = target_organization_id
          and lower(b.local_username) = lower(normalized_username)
          and b.cloud_user_id <> target_user
    ) then
        raise exception 'Local username is already linked to another cloud account';
    end if;

    insert into public.fush_organization_members (
        organization_id,
        user_id,
        role,
        is_active,
        updated_at
    ) values (
        target_organization_id,
        target_user,
        normalized_role,
        true,
        now()
    )
    on conflict (organization_id, user_id)
    do update set
        role = excluded.role,
        is_active = true,
        updated_at = now();

    insert into public.fush_cloud_user_bindings (
        organization_id,
        cloud_user_id,
        email,
        local_username,
        display_name,
        role,
        is_active,
        created_by,
        updated_at
    ) values (
        target_organization_id,
        target_user,
        normalized_email,
        normalized_username,
        nullif(trim(target_display_name), ''),
        normalized_role,
        true,
        caller,
        now()
    )
    on conflict (organization_id, cloud_user_id)
    do update set
        email = excluded.email,
        local_username = excluded.local_username,
        display_name = excluded.display_name,
        role = excluded.role,
        is_active = true,
        updated_at = now();

    return jsonb_build_object(
        'cloud_user_id', target_user,
        'email', normalized_email,
        'local_username', normalized_username,
        'display_name', nullif(trim(target_display_name), ''),
        'role', normalized_role,
        'is_active', true
    );
end;
$$;

revoke all on function public.fush_provision_member(uuid, text, text, text, text) from public;
grant execute on function public.fush_provision_member(uuid, text, text, text, text) to authenticated;

commit;

-- END COMPONENT: V156_MULTI_USER_CLOUD_IDENTITY.sql

-- ============================================================================
-- BEGIN COMPONENT: 003_master_data_sync.sql
-- ============================================================================
-- FUSH ERP Mobile v158 - Master Data Cloud Sync
-- Run AFTER 001_cloud_sync_foundation.sql and V156_MULTI_USER_CLOUD_IDENTITY.sql.
-- This migration creates cloud copies of non-transactional master data only.
-- No local Android Room table is changed by this SQL.

begin;

create or replace function public.fush_has_org_role(
    target_organization_id uuid,
    allowed_roles text[]
)
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
          and upper(m.role) = any(allowed_roles)
    );
$$;

revoke all on function public.fush_has_org_role(uuid, text[]) from public;
grant execute on function public.fush_has_org_role(uuid, text[]) to authenticated;

create or replace function public.fush_touch_master_data_row()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    new.updated_at = now();
    new.updated_by = auth.uid();
    return new;
end;
$$;

create table if not exists public.fush_md_currencies (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    name_ar text not null,
    name_en text not null default '',
    symbol text not null default '',
    decimals integer not null default 2 check (decimals between 0 and 8),
    is_base boolean not null default false,
    is_active boolean not null default true,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code)
);

create table if not exists public.fush_md_units (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    name_ar text not null,
    name_en text not null default '',
    is_active boolean not null default true,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code)
);

create table if not exists public.fush_md_warehouses (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    name_ar text not null,
    name_en text not null default '',
    location text not null default '',
    is_active boolean not null default true,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code)
);

create table if not exists public.fush_md_items (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    name_ar text not null,
    name_en text not null default '',
    category text not null,
    base_unit_code text not null,
    reorder_level double precision not null default 0,
    shelf_life_days integer,
    lot_tracked boolean not null default false,
    expiry_tracked boolean not null default false,
    is_active boolean not null default true,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code),
    foreign key (organization_id, base_unit_code)
        references public.fush_md_units(organization_id, code)
        on update cascade on delete restrict
);

create table if not exists public.fush_md_item_unit_conversions (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    item_code text not null,
    unit_code text not null,
    factor_to_base double precision not null check (factor_to_base > 0),
    allow_purchase boolean not null default true,
    allow_sale boolean not null default false,
    barcode text,
    is_active boolean not null default true,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, item_code, unit_code),
    foreign key (organization_id, item_code)
        references public.fush_md_items(organization_id, code)
        on update cascade on delete cascade,
    foreign key (organization_id, unit_code)
        references public.fush_md_units(organization_id, code)
        on update cascade on delete restrict
);

create table if not exists public.fush_md_customers (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    name_ar text not null,
    name_en text not null default '',
    phone text not null default '',
    address text not null default '',
    province text not null default '',
    channel text not null default 'RETAIL',
    classification text not null default 'C',
    currency_code text not null default 'YER_NEW',
    credit_limit_base double precision not null default 0,
    credit_days integer not null default 0,
    allow_credit boolean not null default false,
    sales_rep_name text not null default '',
    is_active boolean not null default true,
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code)
);

create table if not exists public.fush_md_suppliers (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    name_ar text not null,
    name_en text not null default '',
    phone text not null default '',
    address text not null default '',
    currency_code text not null default 'YER_NEW',
    payment_terms_days integer not null default 0,
    is_active boolean not null default true,
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code)
);

-- No hard DELETE is granted. Deactivation is synchronized with is_active=false.
alter table public.fush_md_currencies enable row level security;
alter table public.fush_md_units enable row level security;
alter table public.fush_md_warehouses enable row level security;
alter table public.fush_md_items enable row level security;
alter table public.fush_md_item_unit_conversions enable row level security;
alter table public.fush_md_customers enable row level security;
alter table public.fush_md_suppliers enable row level security;

revoke all on table public.fush_md_currencies from anon;
revoke all on table public.fush_md_units from anon;
revoke all on table public.fush_md_warehouses from anon;
revoke all on table public.fush_md_items from anon;
revoke all on table public.fush_md_item_unit_conversions from anon;
revoke all on table public.fush_md_customers from anon;
revoke all on table public.fush_md_suppliers from anon;

revoke all on table public.fush_md_currencies from authenticated;
revoke all on table public.fush_md_units from authenticated;
revoke all on table public.fush_md_warehouses from authenticated;
revoke all on table public.fush_md_items from authenticated;
revoke all on table public.fush_md_item_unit_conversions from authenticated;
revoke all on table public.fush_md_customers from authenticated;
revoke all on table public.fush_md_suppliers from authenticated;

grant select, insert, update on table public.fush_md_currencies to authenticated;
grant select, insert, update on table public.fush_md_units to authenticated;
grant select, insert, update on table public.fush_md_warehouses to authenticated;
grant select, insert, update on table public.fush_md_items to authenticated;
grant select, insert, update on table public.fush_md_item_unit_conversions to authenticated;
grant select, insert, update on table public.fush_md_customers to authenticated;
grant select, insert, update on table public.fush_md_suppliers to authenticated;

-- Read policies: every active member of the organization may receive master data.
do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_md_currencies', 'fush_md_units', 'fush_md_warehouses', 'fush_md_items',
        'fush_md_item_unit_conversions', 'fush_md_customers', 'fush_md_suppliers'
    ]
    loop
        execute format('drop policy if exists %I on public.%I', t || '_read_member', t);
        execute format(
            'create policy %I on public.%I for select to authenticated using (public.fush_is_org_member(organization_id))',
            t || '_read_member', t
        );
    end loop;
end $$;

-- Currency management is administrative/geography configuration in this first sync wave.
drop policy if exists fush_md_currencies_write_role on public.fush_md_currencies;
create policy fush_md_currencies_write_role on public.fush_md_currencies
for all to authenticated
using (
    public.fush_has_org_role(organization_id, array['OWNER','ADMIN'])
)
with check (
    public.fush_has_org_role(organization_id, array['OWNER','ADMIN'])
    and (updated_by is null or updated_by = auth.uid())
);

-- Inventory master data writers. ACCOUNTANT is intentionally excluded because v154+ grants it inventory VIEW only.
do $$
declare
    t text;
begin
    foreach t in array array['fush_md_units', 'fush_md_warehouses', 'fush_md_items', 'fush_md_item_unit_conversions']
    loop
        execute format('drop policy if exists %I on public.%I', t || '_write_role', t);
        execute format(
            'create policy %I on public.%I for all to authenticated using (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN'',''INVENTORY''])) with check (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN'',''INVENTORY'']) and (updated_by is null or updated_by = auth.uid()))',
            t || '_write_role', t
        );
    end loop;
end $$;

-- Customer writers follow the local SALES/CUSTOMERS scope.
drop policy if exists fush_md_customers_write_role on public.fush_md_customers;
create policy fush_md_customers_write_role on public.fush_md_customers
for all to authenticated
using (
    public.fush_has_org_role(organization_id, array['OWNER','ADMIN','ACCOUNTANT','SALES'])
)
with check (
    public.fush_has_org_role(organization_id, array['OWNER','ADMIN','ACCOUNTANT','SALES'])
    and (updated_by is null or updated_by = auth.uid())
);

-- Supplier writers follow the local PURCHASES scope.
drop policy if exists fush_md_suppliers_write_role on public.fush_md_suppliers;
create policy fush_md_suppliers_write_role on public.fush_md_suppliers
for all to authenticated
using (
    public.fush_has_org_role(organization_id, array['OWNER','ADMIN','ACCOUNTANT','PURCHASING'])
)
with check (
    public.fush_has_org_role(organization_id, array['OWNER','ADMIN','ACCOUNTANT','PURCHASING'])
    and (updated_by is null or updated_by = auth.uid())
);

-- Keep server timestamps authoritative on every write.
do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_md_currencies', 'fush_md_units', 'fush_md_warehouses', 'fush_md_items',
        'fush_md_item_unit_conversions', 'fush_md_customers', 'fush_md_suppliers'
    ]
    loop
        execute format('drop trigger if exists trg_%I_touch on public.%I', t, t);
        execute format(
            'create trigger trg_%I_touch before insert or update on public.%I for each row execute function public.fush_touch_master_data_row()',
            t, t
        );
    end loop;
end $$;

create index if not exists idx_fush_md_customers_org_active on public.fush_md_customers(organization_id, is_active);
create index if not exists idx_fush_md_suppliers_org_active on public.fush_md_suppliers(organization_id, is_active);
create index if not exists idx_fush_md_items_org_active on public.fush_md_items(organization_id, is_active);

commit;

-- END COMPONENT: 003_master_data_sync.sql

-- ============================================================================
-- BEGIN COMPONENT: 004_sales_receivables_mirror.sql
-- ============================================================================
-- FUSH ERP Mobile v159 - Sales & Receivables Cloud Mirror
-- Run after v155/v156/v158 cloud migrations.
-- Safe scope: posted sales/receivables document mirror only. No Android Room schema change.

begin;

create table if not exists public.fush_tx_sales_invoices (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    invoice_no text not null,
    customer_code text not null,
    invoice_date_ms bigint not null,
    due_date_ms bigint,
    warehouse_code text not null,
    currency_code text not null,
    exchange_rate double precision not null,
    payment_type text not null,
    channel text not null default '',
    province text not null default '',
    sales_rep_name_snapshot text not null default '',
    sales_rep_rate_pct double precision not null default 0,
    free_qty_limit_pct_snapshot double precision not null default 0,
    free_qty_approval_reason text not null default '',
    discount_pct double precision not null default 0,
    gross_original double precision not null default 0,
    discount_original double precision not null default 0,
    transport_original double precision not null default 0,
    fees_original double precision not null default 0,
    risk_margin_original double precision not null default 0,
    total_original double precision not null default 0,
    total_base double precision not null default 0,
    status text not null default 'POSTED',
    below_floor_reason text not null default '',
    notes text not null default '',
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, invoice_no)
);

create table if not exists public.fush_tx_sales_lines (
    organization_id uuid not null,
    invoice_no text not null,
    line_no integer not null check (line_no > 0),
    item_code text not null,
    unit_code text not null,
    quantity double precision not null,
    free_quantity double precision not null default 0,
    factor_to_base double precision not null,
    base_quantity double precision not null,
    free_base_quantity double precision not null default 0,
    unit_price_original double precision not null,
    gross_original double precision not null,
    discount_original double precision not null,
    net_original double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, invoice_no, line_no),
    foreign key (organization_id, invoice_no)
        references public.fush_tx_sales_invoices(organization_id, invoice_no)
        on update cascade on delete cascade
);

create table if not exists public.fush_tx_sales_allocations (
    organization_id uuid not null,
    invoice_no text not null,
    line_no integer not null,
    allocation_no integer not null check (allocation_no > 0),
    item_code text not null,
    lot_no text,
    expiry_date_ms bigint,
    quantity_base double precision not null,
    free_quantity_base double precision not null default 0,
    unit_cost_base double precision not null,
    cost_base double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, invoice_no, line_no, allocation_no),
    foreign key (organization_id, invoice_no, line_no)
        references public.fush_tx_sales_lines(organization_id, invoice_no, line_no)
        on update cascade on delete cascade
);

create table if not exists public.fush_tx_customer_receipts (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    receipt_no text not null,
    customer_code text not null,
    receipt_date_ms bigint not null,
    currency_code text not null,
    exchange_rate double precision not null,
    amount_original double precision not null,
    amount_base double precision not null,
    discount_original double precision not null default 0,
    discount_base double precision not null default 0,
    discount_reason text not null default '',
    notes text not null default '',
    reversal_of_receipt_no text,
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, receipt_no)
);

create table if not exists public.fush_tx_customer_receipt_allocations (
    organization_id uuid not null,
    receipt_no text not null,
    allocation_no integer not null check (allocation_no > 0),
    invoice_no text not null,
    amount_base double precision not null,
    discount_original double precision not null default 0,
    discount_base double precision not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, receipt_no, allocation_no),
    foreign key (organization_id, receipt_no)
        references public.fush_tx_customer_receipts(organization_id, receipt_no)
        on update cascade on delete cascade,
    foreign key (organization_id, invoice_no)
        references public.fush_tx_sales_invoices(organization_id, invoice_no)
        on update cascade on delete restrict
);

create table if not exists public.fush_tx_sales_returns (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    return_no text not null,
    sales_invoice_no text not null,
    customer_code text not null,
    return_date_ms bigint not null,
    warehouse_code text not null,
    currency_code text not null,
    exchange_rate double precision not null,
    settlement_type text not null,
    total_original double precision not null,
    total_base double precision not null,
    total_cost_base double precision not null,
    reason text not null default '',
    status text not null default 'POSTED',
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, return_no),
    foreign key (organization_id, sales_invoice_no)
        references public.fush_tx_sales_invoices(organization_id, invoice_no)
        on update cascade on delete restrict
);

create table if not exists public.fush_tx_sales_return_lines (
    organization_id uuid not null,
    return_no text not null,
    line_no integer not null check (line_no > 0),
    sales_invoice_line_no integer not null check (sales_invoice_line_no > 0),
    item_code text not null,
    unit_code text not null,
    quantity double precision not null,
    free_quantity double precision not null default 0,
    factor_to_base double precision not null,
    base_quantity double precision not null,
    free_base_quantity double precision not null default 0,
    unit_price_original double precision not null,
    line_net_original double precision not null,
    cost_base double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, return_no, line_no),
    foreign key (organization_id, return_no)
        references public.fush_tx_sales_returns(organization_id, return_no)
        on update cascade on delete cascade
);

-- RLS and grants: all active company members may read; only OWNER/ADMIN may publish.
do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_tx_sales_invoices', 'fush_tx_sales_lines', 'fush_tx_sales_allocations',
        'fush_tx_customer_receipts', 'fush_tx_customer_receipt_allocations',
        'fush_tx_sales_returns', 'fush_tx_sales_return_lines'
    ]
    loop
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on table public.%I from anon', t);
        execute format('revoke all on table public.%I from authenticated', t);
        execute format('grant select, insert, update on table public.%I to authenticated', t);

        execute format('drop policy if exists %I on public.%I', t || '_read_member', t);
        execute format(
            'create policy %I on public.%I for select to authenticated using (public.fush_is_org_member(organization_id))',
            t || '_read_member', t
        );

        execute format('drop policy if exists %I on public.%I', t || '_write_owner', t);
        execute format(
            'create policy %I on public.%I for all to authenticated using (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN''])) with check (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN'']) and (updated_by is null or updated_by = auth.uid()))',
            t || '_write_owner', t
        );

        execute format('drop trigger if exists trg_%I_touch on public.%I', t, t);
        execute format(
            'create trigger trg_%I_touch before insert or update on public.%I for each row execute function public.fush_touch_master_data_row()',
            t, t
        );
    end loop;
end $$;

create index if not exists idx_fush_tx_sales_invoice_date
on public.fush_tx_sales_invoices(organization_id, invoice_date_ms);
create index if not exists idx_fush_tx_sales_customer
on public.fush_tx_sales_invoices(organization_id, customer_code);
create index if not exists idx_fush_tx_receipts_customer
on public.fush_tx_customer_receipts(organization_id, customer_code, receipt_date_ms);
create index if not exists idx_fush_tx_returns_customer
on public.fush_tx_sales_returns(organization_id, customer_code, return_date_ms);

commit;

-- END COMPONENT: 004_sales_receivables_mirror.sql

-- ============================================================================
-- BEGIN COMPONENT: 005_purchase_documents_mirror.sql
-- ============================================================================
-- FUSH ERP Mobile v161 - Purchase Documents Cloud Mirror
-- Run after v155/v156/v158/v159 cloud migrations.
-- Safe scope: posted purchase invoices/lines and purchase returns/lines only.
-- Supplier payments, stock movements, treasury and journals are intentionally excluded.

begin;

create table if not exists public.fush_tx_purchase_invoices (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    invoice_no text not null,
    supplier_invoice_no text not null default '',
    supplier_code text not null,
    invoice_date_ms bigint not null,
    due_date_ms bigint,
    warehouse_code text not null,
    currency_code text not null,
    exchange_rate double precision not null,
    payment_type text not null,
    subtotal_original double precision not null default 0,
    discount_original double precision not null default 0,
    freight_original double precision not null default 0,
    customs_original double precision not null default 0,
    other_charges_original double precision not null default 0,
    total_original double precision not null default 0,
    total_base double precision not null default 0,
    status text not null default 'POSTED',
    notes text not null default '',
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, invoice_no)
);

create table if not exists public.fush_tx_purchase_lines (
    organization_id uuid not null,
    invoice_no text not null,
    line_no integer not null check (line_no > 0),
    item_code text not null,
    unit_code text not null,
    quantity double precision not null,
    factor_to_base double precision not null,
    base_quantity double precision not null,
    unit_price_original double precision not null,
    line_total_original double precision not null,
    unit_cost_base double precision not null,
    lot_no text,
    expiry_date_ms bigint,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, invoice_no, line_no),
    foreign key (organization_id, invoice_no)
        references public.fush_tx_purchase_invoices(organization_id, invoice_no)
        on update cascade on delete cascade
);

create table if not exists public.fush_tx_purchase_returns (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    return_no text not null,
    purchase_invoice_no text not null,
    supplier_code text not null,
    return_date_ms bigint not null,
    warehouse_code text not null,
    currency_code text not null,
    exchange_rate double precision not null,
    settlement_type text not null,
    total_original double precision not null,
    total_base double precision not null,
    status text not null default 'POSTED',
    reason text not null default '',
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, return_no),
    foreign key (organization_id, purchase_invoice_no)
        references public.fush_tx_purchase_invoices(organization_id, invoice_no)
        on update cascade on delete restrict
);

create table if not exists public.fush_tx_purchase_return_lines (
    organization_id uuid not null,
    return_no text not null,
    line_no integer not null check (line_no > 0),
    purchase_invoice_line_no integer not null check (purchase_invoice_line_no > 0),
    item_code text not null,
    unit_code text not null,
    quantity double precision not null,
    factor_to_base double precision not null,
    base_quantity double precision not null,
    unit_price_original double precision not null,
    line_total_original double precision not null,
    unit_cost_base double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, return_no, line_no),
    foreign key (organization_id, return_no)
        references public.fush_tx_purchase_returns(organization_id, return_no)
        on update cascade on delete cascade
);

do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_tx_purchase_invoices', 'fush_tx_purchase_lines',
        'fush_tx_purchase_returns', 'fush_tx_purchase_return_lines'
    ]
    loop
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on table public.%I from anon', t);
        execute format('revoke all on table public.%I from authenticated', t);
        execute format('grant select, insert, update on table public.%I to authenticated', t);

        execute format('drop policy if exists %I on public.%I', t || '_read_member', t);
        execute format(
            'create policy %I on public.%I for select to authenticated using (public.fush_is_org_member(organization_id))',
            t || '_read_member', t
        );

        execute format('drop policy if exists %I on public.%I', t || '_write_owner', t);
        execute format(
            'create policy %I on public.%I for all to authenticated using (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN''])) with check (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN'']) and (updated_by is null or updated_by = auth.uid()))',
            t || '_write_owner', t
        );

        execute format('drop trigger if exists trg_%I_touch on public.%I', t, t);
        execute format(
            'create trigger trg_%I_touch before insert or update on public.%I for each row execute function public.fush_touch_master_data_row()',
            t, t
        );
    end loop;
end $$;

create index if not exists idx_fush_tx_purchase_invoice_date
on public.fush_tx_purchase_invoices(organization_id, invoice_date_ms);

create index if not exists idx_fush_tx_purchase_supplier
on public.fush_tx_purchase_invoices(organization_id, supplier_code);

create index if not exists idx_fush_tx_purchase_return_date
on public.fush_tx_purchase_returns(organization_id, return_date_ms);

commit;

-- END COMPONENT: 005_purchase_documents_mirror.sql

-- ============================================================================
-- BEGIN COMPONENT: 006_supplier_payments_treasury_mirror.sql
-- ============================================================================
-- FUSH ERP Mobile v163 - Supplier Payments & Treasury Directory Cloud Mirror
-- Run after 001, V156, 003, 004 and 005 cloud migrations.
-- Mirrors treasury directory metadata and supplier payment documents only.
-- It does NOT replay journal entries, party vouchers, bank reconciliation or cash-count side effects.

begin;

create table if not exists public.fush_md_treasury_accounts (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    name_ar text not null,
    kind text not null,
    ledger_account_code text not null,
    currency_code text not null,
    bank_name text not null default '',
    account_number text not null default '',
    is_active boolean not null default true,
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code)
);

create table if not exists public.fush_tx_supplier_payments (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    payment_no text not null,
    supplier_code text not null,
    treasury_code text not null,
    payment_date_ms bigint not null,
    currency_code text not null,
    exchange_rate double precision not null,
    amount_original double precision not null,
    cash_amount_base double precision not null,
    notes text not null default '',
    reversal_of_payment_no text,
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, payment_no)
);

create table if not exists public.fush_tx_supplier_payment_allocations (
    organization_id uuid not null,
    payment_no text not null,
    allocation_no integer not null check (allocation_no > 0),
    invoice_no text not null,
    amount_original double precision not null,
    allocated_base double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, payment_no, allocation_no),
    foreign key (organization_id, payment_no)
        references public.fush_tx_supplier_payments(organization_id, payment_no)
        on update cascade on delete cascade,
    foreign key (organization_id, invoice_no)
        references public.fush_tx_purchase_invoices(organization_id, invoice_no)
        on update cascade on delete restrict
);

alter table public.fush_md_treasury_accounts enable row level security;
alter table public.fush_tx_supplier_payments enable row level security;
alter table public.fush_tx_supplier_payment_allocations enable row level security;

revoke all on table public.fush_md_treasury_accounts from anon;
revoke all on table public.fush_tx_supplier_payments from anon;
revoke all on table public.fush_tx_supplier_payment_allocations from anon;

revoke all on table public.fush_md_treasury_accounts from authenticated;
revoke all on table public.fush_tx_supplier_payments from authenticated;
revoke all on table public.fush_tx_supplier_payment_allocations from authenticated;

grant select, insert, update on table public.fush_md_treasury_accounts to authenticated;
grant select, insert, update on table public.fush_tx_supplier_payments to authenticated;
grant select, insert, update on table public.fush_tx_supplier_payment_allocations to authenticated;

do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_md_treasury_accounts',
        'fush_tx_supplier_payments',
        'fush_tx_supplier_payment_allocations'
    ]
    loop
        execute format('drop policy if exists %I on public.%I', t || '_read_member', t);
        execute format(
            'create policy %I on public.%I for select to authenticated using (public.fush_is_org_member(organization_id))',
            t || '_read_member', t
        );

        execute format('drop policy if exists %I on public.%I', t || '_write_owner', t);
        execute format(
            'create policy %I on public.%I for all to authenticated using (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN''])) with check (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN'']) and (updated_by is null or updated_by = auth.uid()))',
            t || '_write_owner', t
        );

        execute format('drop trigger if exists trg_%I_touch on public.%I', t, t);
        execute format(
            'create trigger trg_%I_touch before insert or update on public.%I for each row execute function public.fush_touch_master_data_row()',
            t, t
        );
    end loop;
end $$;

create index if not exists idx_fush_md_treasury_org_active
on public.fush_md_treasury_accounts(organization_id, is_active);

create index if not exists idx_fush_tx_supplier_payment_supplier
on public.fush_tx_supplier_payments(organization_id, supplier_code, payment_date_ms);

create index if not exists idx_fush_tx_supplier_payment_date
on public.fush_tx_supplier_payments(organization_id, payment_date_ms);

commit;

-- END COMPONENT: 006_supplier_payments_treasury_mirror.sql

-- ============================================================================
-- BEGIN COMPONENT: V164_DOCUMENT_NUMBER_RESERVATION.sql
-- ============================================================================
-- FUSH ERP Mobile v164 - Company-wide document-number reservation guard
-- Run after v155/v156/v158/v159/v161/v163 SQL migrations.
-- Purpose: prevent two cloud-linked phones from posting different documents with the same business number.

begin;

create table if not exists public.fush_document_number_registry (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    document_type text not null,
    document_no text not null,
    reservation_id uuid not null default gen_random_uuid(),
    reserved_by uuid references auth.users(id),
    reserved_device_key text,
    source text not null default 'LIVE_RESERVATION',
    reserved_at timestamptz not null default now(),
    primary key (organization_id, document_type, document_no),
    unique (organization_id, reservation_id)
);

alter table public.fush_document_number_registry enable row level security;
revoke all on table public.fush_document_number_registry from anon;
revoke all on table public.fush_document_number_registry from authenticated;
grant select on table public.fush_document_number_registry to authenticated;

drop policy if exists fush_document_number_registry_read_member on public.fush_document_number_registry;
create policy fush_document_number_registry_read_member
on public.fush_document_number_registry
for select to authenticated
using (public.fush_is_org_member(organization_id));

create or replace function public.fush_reserve_document_number(
    target_organization_id uuid,
    target_document_type text,
    target_document_no text,
    target_device_key text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    normalized_type text := upper(trim(target_document_type));
    normalized_no text := trim(target_document_no);
    existing public.fush_document_number_registry%rowtype;
    inserted public.fush_document_number_registry%rowtype;
begin
    if caller is null then
        raise exception 'Authentication required';
    end if;
    if not public.fush_is_org_member(target_organization_id) then
        raise exception 'Organization membership required';
    end if;
    if normalized_type = '' or normalized_no = '' then
        raise exception 'Document type and number are required';
    end if;
    if length(normalized_type) > 50 or length(normalized_no) > 120 then
        raise exception 'Document type or number is too long';
    end if;

    insert into public.fush_document_number_registry(
        organization_id, document_type, document_no, reserved_by, reserved_device_key, source
    ) values (
        target_organization_id, normalized_type, normalized_no, caller, nullif(trim(target_device_key), ''), 'LIVE_RESERVATION'
    )
    on conflict (organization_id, document_type, document_no) do nothing
    returning * into inserted;

    if inserted.reservation_id is not null then
        return jsonb_build_object(
            'reserved', true,
            'reservation_id', inserted.reservation_id,
            'reserved_by', inserted.reserved_by,
            'reserved_device_key', inserted.reserved_device_key
        );
    end if;

    select * into existing
    from public.fush_document_number_registry r
    where r.organization_id = target_organization_id
      and r.document_type = normalized_type
      and r.document_no = normalized_no;

    -- Idempotent retry from the same signed-in user and Android installation.
    if existing.reserved_by = caller
       and coalesce(existing.reserved_device_key, '') = coalesce(nullif(trim(target_device_key), ''), '') then
        return jsonb_build_object(
            'reserved', true,
            'reservation_id', existing.reservation_id,
            'reserved_by', existing.reserved_by,
            'reserved_device_key', existing.reserved_device_key,
            'idempotent', true
        );
    end if;

    return jsonb_build_object(
        'reserved', false,
        'reason', 'DOCUMENT_NUMBER_IN_USE',
        'reservation_id', existing.reservation_id,
        'reserved_by', existing.reserved_by,
        'reserved_device_key', existing.reserved_device_key
    );
end;
$$;

revoke all on function public.fush_reserve_document_number(uuid, text, text, text) from public;
grant execute on function public.fush_reserve_document_number(uuid, text, text, text) to authenticated;

-- Backfill numbers that already exist in the cloud mirrors so they cannot be reused on another phone.
insert into public.fush_document_number_registry(organization_id, document_type, document_no, reserved_by, source)
select organization_id, 'SALE_INVOICE', invoice_no, updated_by, 'LEGACY_CLOUD_BACKFILL'
from public.fush_tx_sales_invoices
on conflict do nothing;

insert into public.fush_document_number_registry(organization_id, document_type, document_no, reserved_by, source)
select organization_id, 'CUSTOMER_RECEIPT', receipt_no, updated_by, 'LEGACY_CLOUD_BACKFILL'
from public.fush_tx_customer_receipts
on conflict do nothing;

insert into public.fush_document_number_registry(organization_id, document_type, document_no, reserved_by, source)
select organization_id, 'SALES_RETURN', return_no, updated_by, 'LEGACY_CLOUD_BACKFILL'
from public.fush_tx_sales_returns
on conflict do nothing;

insert into public.fush_document_number_registry(organization_id, document_type, document_no, reserved_by, source)
select organization_id, 'PURCHASE_INVOICE', invoice_no, updated_by, 'LEGACY_CLOUD_BACKFILL'
from public.fush_tx_purchase_invoices
on conflict do nothing;

insert into public.fush_document_number_registry(organization_id, document_type, document_no, reserved_by, source)
select organization_id, 'PURCHASE_RETURN', return_no, updated_by, 'LEGACY_CLOUD_BACKFILL'
from public.fush_tx_purchase_returns
on conflict do nothing;

insert into public.fush_document_number_registry(organization_id, document_type, document_no, reserved_by, source)
select organization_id, 'SUPPLIER_PAYMENT', payment_no, updated_by, 'LEGACY_CLOUD_BACKFILL'
from public.fush_tx_supplier_payments
on conflict do nothing;

create index if not exists idx_fush_document_number_registry_user
on public.fush_document_number_registry(organization_id, reserved_by);

commit;

-- END COMPONENT: V164_DOCUMENT_NUMBER_RESERVATION.sql

-- ============================================================================
-- BEGIN COMPONENT: 008_inventory_production_mirror.sql
-- ============================================================================
-- FUSH ERP Mobile v165 - Inventory & Production Cloud Mirror
-- Run after v158 master-data and v164 document-number guard migrations.
-- This creates an OWNER/ADMIN-authored production mirror plus a current lot-level inventory snapshot.
-- It does NOT replay accounting journals on employee phones.

begin;

create table if not exists public.fush_cloud_domain_state (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    domain text not null,
    initialized_at bigint not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, domain)
);

create table if not exists public.fush_md_recipes (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    recipe_code text not null,
    version_no integer not null,
    product_item_code text not null,
    effective_from_ms bigint not null,
    target_output_qty_base double precision not null,
    status text not null default 'ACTIVE',
    notes text not null default '',
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, recipe_code, version_no)
);

create table if not exists public.fush_md_recipe_components (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    recipe_code text not null,
    version_no integer not null,
    component_no integer not null check (component_no > 0),
    item_code text not null,
    quantity_base double precision not null,
    expected_loss_pct double precision not null default 0,
    stage text not null default 'PREPARATION',
    sequence_no integer not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, recipe_code, version_no, component_no),
    foreign key (organization_id, recipe_code, version_no)
        references public.fush_md_recipes(organization_id, recipe_code, version_no)
        on update cascade on delete cascade
);

create table if not exists public.fush_tx_production_orders (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    order_no text not null,
    recipe_code text not null,
    recipe_version_no integer not null,
    product_item_code text not null,
    planned_output_qty_base double precision not null,
    raw_warehouse_code text not null,
    finished_warehouse_code text not null,
    planned_date_ms bigint not null,
    status text not null,
    direct_labor_cost_base double precision not null default 0,
    notes text not null default '',
    created_at_ms bigint not null default 0,
    closed_at_ms bigint,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, order_no)
);

create table if not exists public.fush_tx_production_materials (
    organization_id uuid not null,
    order_no text not null,
    material_no integer not null check (material_no > 0),
    item_code text not null,
    standard_qty_base double precision not null,
    reserved_qty_base double precision not null default 0,
    issued_qty_base double precision not null default 0,
    issue_cost_base double precision not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, order_no, material_no),
    foreign key (organization_id, order_no)
        references public.fush_tx_production_orders(organization_id, order_no)
        on update cascade on delete cascade
);

create table if not exists public.fush_tx_production_batches (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    batch_no text not null,
    order_no text not null,
    manufacture_date_ms bigint not null,
    expiry_date_ms bigint not null,
    status text not null,
    actual_output_qty_base double precision not null default 0,
    accepted_qty_base double precision not null default 0,
    rejected_qty_base double precision not null default 0,
    scrap_qty_base double precision not null default 0,
    notes text not null default '',
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, batch_no),
    unique (organization_id, order_no),
    foreign key (organization_id, order_no)
        references public.fush_tx_production_orders(organization_id, order_no)
        on update cascade on delete restrict
);

create table if not exists public.fush_tx_production_issues (
    organization_id uuid not null,
    order_no text not null,
    issue_no integer not null check (issue_no > 0),
    material_no integer not null check (material_no > 0),
    item_code text not null,
    quantity_base double precision not null,
    unit_cost_base double precision not null,
    total_cost_base double precision not null,
    lot_no text,
    expiry_date_ms bigint,
    issue_kind text not null default 'ISSUE',
    correction_of_issue_no integer,
    reason text not null default '',
    issue_date_ms bigint not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, order_no, issue_no),
    foreign key (organization_id, order_no)
        references public.fush_tx_production_orders(organization_id, order_no)
        on update cascade on delete cascade
);

create table if not exists public.fush_inventory_snapshot (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    warehouse_code text not null,
    item_code text not null,
    lot_no text,
    lot_key text not null default '',
    expiry_date_ms bigint,
    expiry_key bigint not null default -1,
    quantity_base double precision not null,
    inventory_value_base double precision not null,
    snapshot_at bigint not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, warehouse_code, item_code, lot_key, expiry_key)
);

-- RLS: every active company member can read. Only OWNER/ADMIN can publish this wave.
do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_cloud_domain_state', 'fush_md_recipes', 'fush_md_recipe_components',
        'fush_tx_production_orders', 'fush_tx_production_materials',
        'fush_tx_production_batches', 'fush_tx_production_issues', 'fush_inventory_snapshot'
    ]
    loop
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on table public.%I from anon', t);
        execute format('revoke all on table public.%I from authenticated', t);
        execute format('grant select, insert, update on table public.%I to authenticated', t);

        execute format('drop policy if exists %I on public.%I', t || '_read_member', t);
        execute format(
            'create policy %I on public.%I for select to authenticated using (public.fush_is_org_member(organization_id))',
            t || '_read_member', t
        );

        execute format('drop policy if exists %I on public.%I', t || '_write_owner', t);
        execute format(
            'create policy %I on public.%I for all to authenticated using (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN''])) with check (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN'']) and (updated_by is null or updated_by = auth.uid()))',
            t || '_write_owner', t
        );

        execute format('drop trigger if exists trg_%I_touch on public.%I', t, t);
        execute format(
            'create trigger trg_%I_touch before insert or update on public.%I for each row execute function public.fush_touch_master_data_row()',
            t, t
        );
    end loop;
end $$;

create index if not exists idx_fush_prod_order_date
on public.fush_tx_production_orders(organization_id, planned_date_ms);

create index if not exists idx_fush_prod_batch_dates
on public.fush_tx_production_batches(organization_id, manufacture_date_ms, expiry_date_ms);

create index if not exists idx_fush_prod_issue_order_date
on public.fush_tx_production_issues(organization_id, order_no, issue_date_ms);

create index if not exists idx_fush_inventory_snapshot_item
on public.fush_inventory_snapshot(organization_id, item_code, warehouse_code);

commit;

-- END COMPONENT: 008_inventory_production_mirror.sql

-- ============================================================================
-- BEGIN COMPONENT: 009_bidirectional_inventory_production.sql
-- ============================================================================
-- FUSH ERP Mobile v167 - Bidirectional Inventory & Closed Production Sync
-- Run AFTER v165 inventory/production mirror and v166 app rollout.
-- No Android Room schema change.
--
-- Safety model:
--   * OWNER/ADMIN remain able to maintain the central production mirror.
--   * PRODUCTION may INSERT newly closed production documents only; employee-side app logic
--     never updates an existing cloud production document.
--   * Inventory outbound writes from OWNER/ADMIN/PRODUCTION/INVENTORY go through a CAS RPC.
--     If the cloud row changed since the phone's last baseline, the RPC returns false and the
--     app surfaces a conflict instead of silently overwriting the newer row.

begin;

-- Production users may publish new production documents. Existing v165 OWNER/ADMIN policies stay.
do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_tx_production_orders',
        'fush_tx_production_materials',
        'fush_tx_production_batches',
        'fush_tx_production_issues'
    ]
    loop
        execute format('drop policy if exists %I on public.%I', t || '_insert_production', t);
        execute format(
            'create policy %I on public.%I for insert to authenticated with check (public.fush_has_org_role(organization_id, array[''OWNER'',''ADMIN'',''PRODUCTION'']) and (updated_by is null or updated_by = auth.uid()))',
            t || '_insert_production', t
        );
    end loop;
end $$;

-- Optimistic compare-and-set for one lot-level inventory snapshot row.
create or replace function public.fush_publish_inventory_snapshot_cas(
    target_organization_id uuid,
    target_warehouse_code text,
    target_item_code text,
    target_lot_no text,
    target_lot_key text,
    target_expiry_date_ms bigint,
    target_expiry_key bigint,
    expected_updated_at timestamptz,
    new_quantity_base double precision,
    new_inventory_value_base double precision,
    new_snapshot_at bigint
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    affected integer := 0;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    if not public.fush_has_org_role(
        target_organization_id,
        array['OWNER','ADMIN','PRODUCTION','INVENTORY']
    ) then
        raise exception 'Role is not allowed to publish inventory';
    end if;

    if expected_updated_at is null then
        insert into public.fush_inventory_snapshot(
            organization_id,
            warehouse_code,
            item_code,
            lot_no,
            lot_key,
            expiry_date_ms,
            expiry_key,
            quantity_base,
            inventory_value_base,
            snapshot_at,
            updated_at,
            updated_by
        ) values (
            target_organization_id,
            target_warehouse_code,
            target_item_code,
            target_lot_no,
            coalesce(target_lot_key, ''),
            target_expiry_date_ms,
            target_expiry_key,
            new_quantity_base,
            new_inventory_value_base,
            new_snapshot_at,
            now(),
            auth.uid()
        )
        on conflict (organization_id, warehouse_code, item_code, lot_key, expiry_key)
        do nothing;

        get diagnostics affected = row_count;
        return affected = 1;
    end if;

    update public.fush_inventory_snapshot
       set lot_no = target_lot_no,
           expiry_date_ms = target_expiry_date_ms,
           quantity_base = new_quantity_base,
           inventory_value_base = new_inventory_value_base,
           snapshot_at = new_snapshot_at,
           updated_at = now(),
           updated_by = auth.uid()
     where organization_id = target_organization_id
       and warehouse_code = target_warehouse_code
       and item_code = target_item_code
       and lot_key = coalesce(target_lot_key, '')
       and expiry_key = target_expiry_key
       and updated_at = expected_updated_at;

    get diagnostics affected = row_count;
    return affected = 1;
end;
$$;

revoke all on function public.fush_publish_inventory_snapshot_cas(
    uuid, text, text, text, text, bigint, bigint, timestamptz,
    double precision, double precision, bigint
) from public;

grant execute on function public.fush_publish_inventory_snapshot_cas(
    uuid, text, text, text, text, bigint, bigint, timestamptz,
    double precision, double precision, bigint
) to authenticated;

commit;

-- END COMPONENT: 009_bidirectional_inventory_production.sql

-- ============================================================================
-- BEGIN COMPONENT: V177_ACCOUNTING_BIDIRECTIONAL_SYNC.sql
-- ============================================================================
-- FUSH ERP Mobile v177 - General Ledger + Treasury Bidirectional Cloud Sync
-- Apply in Supabase SQL Editor after the existing v163/v165 cloud migrations.
-- Posted journals are immutable cloud documents. Same natural key + different payload creates a conflict.

begin;

create table if not exists public.fush_tx_gl_journals (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    entry_no text not null,
    entry_date_ms bigint not null,
    source_type text not null,
    content jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, entry_no)
);

create table if not exists public.fush_tx_treasury_vouchers (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    voucher_no text not null,
    voucher_date_ms bigint not null,
    voucher_type text not null,
    treasury_code text not null,
    journal_entry_no text not null,
    content jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, voucher_no),
    foreign key (organization_id, journal_entry_no)
        references public.fush_tx_gl_journals(organization_id, entry_no)
        on update cascade on delete restrict
);

create table if not exists public.fush_accounting_sync_conflicts (
    id bigserial primary key,
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    entity_type text not null check (entity_type in ('JOURNAL','TREASURY_VOUCHER')),
    entity_key text not null,
    cloud_content jsonb not null,
    incoming_content jsonb not null,
    detected_by uuid references auth.users(id),
    detected_at timestamptz not null default now(),
    status text not null default 'OPEN' check (status in ('OPEN','RESOLVED')),
    resolution text,
    resolved_by uuid references auth.users(id),
    resolved_at timestamptz
);

create unique index if not exists uq_fush_accounting_open_conflict
on public.fush_accounting_sync_conflicts(organization_id, entity_type, entity_key)
where status = 'OPEN';

create index if not exists idx_fush_gl_journal_date
on public.fush_tx_gl_journals(organization_id, entry_date_ms);
create index if not exists idx_fush_gl_journal_source
on public.fush_tx_gl_journals(organization_id, source_type, entry_date_ms);
create index if not exists idx_fush_treasury_voucher_date
on public.fush_tx_treasury_vouchers(organization_id, voucher_date_ms);
create index if not exists idx_fush_treasury_voucher_treasury
on public.fush_tx_treasury_vouchers(organization_id, treasury_code, voucher_date_ms);

alter table public.fush_tx_gl_journals enable row level security;
alter table public.fush_tx_treasury_vouchers enable row level security;
alter table public.fush_accounting_sync_conflicts enable row level security;

revoke all on public.fush_tx_gl_journals from anon, authenticated;
revoke all on public.fush_tx_treasury_vouchers from anon, authenticated;
revoke all on public.fush_accounting_sync_conflicts from anon, authenticated;
grant select on public.fush_tx_gl_journals to authenticated;
grant select on public.fush_tx_treasury_vouchers to authenticated;
grant select on public.fush_accounting_sync_conflicts to authenticated;

-- Every active company member can read the canonical accounting truth.
drop policy if exists fush_tx_gl_journals_read_member on public.fush_tx_gl_journals;
create policy fush_tx_gl_journals_read_member on public.fush_tx_gl_journals
for select to authenticated using (public.fush_is_org_member(organization_id));

drop policy if exists fush_tx_treasury_vouchers_read_member on public.fush_tx_treasury_vouchers;
create policy fush_tx_treasury_vouchers_read_member on public.fush_tx_treasury_vouchers
for select to authenticated using (public.fush_is_org_member(organization_id));

drop policy if exists fush_accounting_sync_conflicts_read_member on public.fush_accounting_sync_conflicts;
create policy fush_accounting_sync_conflicts_read_member on public.fush_accounting_sync_conflicts
for select to authenticated using (public.fush_is_org_member(organization_id));

-- Security-definer helper: roles that can create posted business transactions and therefore journals.
create or replace function public.fush_can_publish_accounting(target_organization_id uuid)
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
          and upper(m.role) in ('OWNER','ADMIN','ACCOUNTANT','CASHIER','SALES','PURCHASING','INVENTORY','PRODUCTION')
    );
$$;
revoke all on function public.fush_can_publish_accounting(uuid) from public;
grant execute on function public.fush_can_publish_accounting(uuid) to authenticated;

create or replace function public.fush_can_resolve_accounting(target_organization_id uuid)
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
          and upper(m.role) in ('OWNER','ADMIN','ACCOUNTANT')
    );
$$;
revoke all on function public.fush_can_resolve_accounting(uuid) from public;
grant execute on function public.fush_can_resolve_accounting(uuid) to authenticated;

-- Atomic multi-document publication. Existing equal payload = unchanged. Existing different payload = conflict.
create or replace function public.fush_publish_accounting_batch(
    target_organization_id uuid,
    journal_payloads jsonb,
    voucher_payloads jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    p jsonb;
    existing jsonb;
    e_no text;
    v_no text;
    j_inserted int := 0;
    j_unchanged int := 0;
    j_conflicts int := 0;
    v_inserted int := 0;
    v_unchanged int := 0;
    v_conflicts int := 0;
    debit_total numeric;
    credit_total numeric;
begin
    if caller is null then raise exception 'Authentication required'; end if;
    if not public.fush_can_publish_accounting(target_organization_id) then
        raise exception 'Accounting publish role required';
    end if;

    if jsonb_typeof(coalesce(journal_payloads, '[]'::jsonb)) <> 'array' then
        raise exception 'journal_payloads must be an array';
    end if;
    if jsonb_typeof(coalesce(voucher_payloads, '[]'::jsonb)) <> 'array' then
        raise exception 'voucher_payloads must be an array';
    end if;

    for p in select value from jsonb_array_elements(coalesce(journal_payloads, '[]'::jsonb))
    loop
        e_no := nullif(trim(p->>'entry_no'), '');
        if e_no is null then raise exception 'Journal entry_no required'; end if;
        if jsonb_typeof(p->'lines') <> 'array' or jsonb_array_length(p->'lines') = 0 then
            raise exception 'Journal % has no lines', e_no;
        end if;
        select coalesce(sum((x->>'debit_scaled')::numeric),0),
               coalesce(sum((x->>'credit_scaled')::numeric),0)
          into debit_total, credit_total
          from jsonb_array_elements(p->'lines') x;
        if debit_total <= 0 or debit_total <> credit_total then
            raise exception 'Journal % is not balanced', e_no;
        end if;
        if exists (
            select 1 from jsonb_array_elements(p->'lines') x
            where coalesce((x->>'debit_scaled')::numeric,0) < 0
               or coalesce((x->>'credit_scaled')::numeric,0) < 0
               or (coalesce((x->>'debit_scaled')::numeric,0) > 0 and coalesce((x->>'credit_scaled')::numeric,0) > 0)
               or nullif(trim(x->>'account_code'),'') is null
        ) then
            raise exception 'Journal % contains invalid line', e_no;
        end if;

        select content into existing
        from public.fush_tx_gl_journals
        where organization_id = target_organization_id and entry_no = e_no;

        if existing is null then
            insert into public.fush_tx_gl_journals(
                organization_id, entry_no, entry_date_ms, source_type, content, updated_by
            ) values (
                target_organization_id, e_no, (p->>'entry_date_ms')::bigint,
                coalesce(p->>'source_type','MANUAL'), p, caller
            )
            on conflict (organization_id, entry_no) do nothing;

            select content into existing
            from public.fush_tx_gl_journals
            where organization_id = target_organization_id and entry_no = e_no;
            if existing = p then
                j_inserted := j_inserted + 1;
            else
                j_conflicts := j_conflicts + 1;
            end if;
        elsif existing = p then
            j_unchanged := j_unchanged + 1;
        else
            j_conflicts := j_conflicts + 1;
        end if;

        if existing is distinct from p then
            insert into public.fush_accounting_sync_conflicts(
                organization_id, entity_type, entity_key, cloud_content, incoming_content, detected_by
            ) values (target_organization_id, 'JOURNAL', e_no, existing, p, caller)
            on conflict (organization_id, entity_type, entity_key) where status='OPEN'
            do update set incoming_content=excluded.incoming_content,
                          cloud_content=excluded.cloud_content,
                          detected_by=caller,
                          detected_at=now();
        end if;
    end loop;

    for p in select value from jsonb_array_elements(coalesce(voucher_payloads, '[]'::jsonb))
    loop
        v_no := nullif(trim(p->>'voucher_no'), '');
        if v_no is null then raise exception 'Voucher voucher_no required'; end if;
        e_no := nullif(trim(p->>'journal_entry_no'), '');
        if e_no is null then raise exception 'Voucher % has no journal_entry_no', v_no; end if;
        if not exists (
            select 1 from public.fush_tx_gl_journals
            where organization_id=target_organization_id and entry_no=e_no
        ) then
            raise exception 'Voucher % references missing journal %', v_no, e_no;
        end if;

        select content into existing
        from public.fush_tx_treasury_vouchers
        where organization_id = target_organization_id and voucher_no = v_no;

        if existing is null then
            insert into public.fush_tx_treasury_vouchers(
                organization_id, voucher_no, voucher_date_ms, voucher_type,
                treasury_code, journal_entry_no, content, updated_by
            ) values (
                target_organization_id, v_no, (p->>'voucher_date_ms')::bigint,
                coalesce(p->>'voucher_type',''), coalesce(p->>'treasury_code',''), e_no, p, caller
            )
            on conflict (organization_id, voucher_no) do nothing;

            select content into existing
            from public.fush_tx_treasury_vouchers
            where organization_id = target_organization_id and voucher_no = v_no;
            if existing = p then
                v_inserted := v_inserted + 1;
            else
                v_conflicts := v_conflicts + 1;
            end if;
        elsif existing = p then
            v_unchanged := v_unchanged + 1;
        else
            v_conflicts := v_conflicts + 1;
        end if;

        if existing is distinct from p then
            insert into public.fush_accounting_sync_conflicts(
                organization_id, entity_type, entity_key, cloud_content, incoming_content, detected_by
            ) values (target_organization_id, 'TREASURY_VOUCHER', v_no, existing, p, caller)
            on conflict (organization_id, entity_type, entity_key) where status='OPEN'
            do update set incoming_content=excluded.incoming_content,
                          cloud_content=excluded.cloud_content,
                          detected_by=caller,
                          detected_at=now();
        end if;
    end loop;

    return jsonb_build_object(
        'journal_inserted', j_inserted,
        'journal_unchanged', j_unchanged,
        'journal_conflicts', j_conflicts,
        'voucher_inserted', v_inserted,
        'voucher_unchanged', v_unchanged,
        'voucher_conflicts', v_conflicts
    );
end;
$$;
revoke all on function public.fush_publish_accounting_batch(uuid,jsonb,jsonb) from public;
grant execute on function public.fush_publish_accounting_batch(uuid,jsonb,jsonb) to authenticated;

-- Explicit conflict resolution. KEEP_CLOUD preserves cloud truth; KEEP_LOCAL replaces the cloud
-- canonical document with the reviewed local payload. No silent last-write-wins path exists.
create or replace function public.fush_resolve_accounting_conflict(
    target_organization_id uuid,
    target_entity_type text,
    target_entity_key text,
    target_resolution text,
    replacement_content jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    et text := upper(trim(target_entity_type));
    res text := upper(trim(target_resolution));
begin
    if caller is null then raise exception 'Authentication required'; end if;
    if not public.fush_can_resolve_accounting(target_organization_id) then
        raise exception 'Accounting resolution authority required';
    end if;
    if et not in ('JOURNAL','TREASURY_VOUCHER') then raise exception 'Unsupported entity type'; end if;
    if res not in ('KEEP_LOCAL','KEEP_CLOUD') then raise exception 'Unsupported resolution'; end if;

    if res = 'KEEP_LOCAL' then
        if replacement_content is null then raise exception 'replacement_content required'; end if;
        if et = 'JOURNAL' then
            if replacement_content->>'entry_no' is distinct from target_entity_key then
                raise exception 'Journal replacement key mismatch';
            end if;
            update public.fush_tx_gl_journals
               set entry_date_ms=(replacement_content->>'entry_date_ms')::bigint,
                   source_type=coalesce(replacement_content->>'source_type','MANUAL'),
                   content=replacement_content,
                   updated_at=now(), updated_by=caller
             where organization_id=target_organization_id and entry_no=target_entity_key;
            if not found then raise exception 'Cloud journal not found'; end if;
        else
            if replacement_content->>'voucher_no' is distinct from target_entity_key then
                raise exception 'Voucher replacement key mismatch';
            end if;
            update public.fush_tx_treasury_vouchers
               set voucher_date_ms=(replacement_content->>'voucher_date_ms')::bigint,
                   voucher_type=coalesce(replacement_content->>'voucher_type',''),
                   treasury_code=coalesce(replacement_content->>'treasury_code',''),
                   journal_entry_no=coalesce(replacement_content->>'journal_entry_no',''),
                   content=replacement_content,
                   updated_at=now(), updated_by=caller
             where organization_id=target_organization_id and voucher_no=target_entity_key;
            if not found then raise exception 'Cloud treasury voucher not found'; end if;
        end if;
    end if;

    update public.fush_accounting_sync_conflicts
       set status='RESOLVED', resolution=res, resolved_by=caller, resolved_at=now()
     where organization_id=target_organization_id
       and entity_type=et and entity_key=target_entity_key and status='OPEN';

    return jsonb_build_object('resolved', true, 'entity_type', et, 'entity_key', target_entity_key, 'resolution', res);
end;
$$;
revoke all on function public.fush_resolve_accounting_conflict(uuid,text,text,text,jsonb) from public;
grant execute on function public.fush_resolve_accounting_conflict(uuid,text,text,text,jsonb) to authenticated;

commit;

-- END COMPONENT: V177_ACCOUNTING_BIDIRECTIONAL_SYNC.sql

-- ============================================================================
-- BEGIN COMPONENT: V179_SALES_AUXILIARY_CLOUD_SYNC.sql
-- ============================================================================
-- FUSH ERP Mobile v179
-- Conflict-safe bidirectional mirror for AdditionalCharges + Shipment Tracking.
-- Apply ONCE after v177 accounting cloud SQL. Download hydration never replays GL/Treasury.

begin;

create table if not exists public.fush_tx_sales_aux_documents (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    entity_type text not null,
    entity_key text not null,
    content jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, entity_type, entity_key),
    constraint fush_sales_aux_entity_type_ck check (entity_type in (
      'CHARGE_TYPE','ADDITIONAL_CHARGE','ADDITIONAL_CHARGE_PAYMENT','ADDITIONAL_CHARGE_SETTLEMENT',
      'SHIPMENT','SHIPMENT_ITEM','SHIPMENT_EXPENSE','SHIPMENT_ITEM_ALLOCATION','SHIPMENT_EXPENSE_ALLOCATION'
    ))
);

create index if not exists idx_fush_sales_aux_type
on public.fush_tx_sales_aux_documents(organization_id, entity_type);

create table if not exists public.fush_sales_aux_sync_conflicts (
    id bigserial primary key,
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    entity_type text not null,
    entity_key text not null,
    cloud_content jsonb not null,
    incoming_content jsonb not null,
    detected_by uuid references auth.users(id),
    detected_at timestamptz not null default now(),
    status text not null default 'OPEN' check (status in ('OPEN','RESOLVED')),
    resolution text,
    resolved_by uuid references auth.users(id),
    resolved_at timestamptz
);

create unique index if not exists uq_fush_sales_aux_open_conflict
on public.fush_sales_aux_sync_conflicts(organization_id, entity_type, entity_key)
where status='OPEN';

alter table public.fush_tx_sales_aux_documents enable row level security;
alter table public.fush_sales_aux_sync_conflicts enable row level security;

revoke all on public.fush_tx_sales_aux_documents from anon, authenticated;
revoke all on public.fush_sales_aux_sync_conflicts from anon, authenticated;
grant select on public.fush_tx_sales_aux_documents to authenticated;
grant select on public.fush_sales_aux_sync_conflicts to authenticated;

drop policy if exists fush_sales_aux_docs_read_member on public.fush_tx_sales_aux_documents;
create policy fush_sales_aux_docs_read_member
on public.fush_tx_sales_aux_documents for select to authenticated
using (public.fush_is_org_member(organization_id));

drop policy if exists fush_sales_aux_conflicts_read_member on public.fush_sales_aux_sync_conflicts;
create policy fush_sales_aux_conflicts_read_member
on public.fush_sales_aux_sync_conflicts for select to authenticated
using (public.fush_is_org_member(organization_id));

create or replace function public.fush_can_publish_sales_aux(target_organization_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$
  select exists (
    select 1 from public.fush_organization_members m
    where m.organization_id=target_organization_id
      and m.user_id=auth.uid() and m.is_active=true
      and upper(m.role) in ('OWNER','ADMIN','ACCOUNTANT','SALES','INVENTORY','CASHIER')
  );
$$;
revoke all on function public.fush_can_publish_sales_aux(uuid) from public;
grant execute on function public.fush_can_publish_sales_aux(uuid) to authenticated;

create or replace function public.fush_can_resolve_sales_aux(target_organization_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$
  select exists (
    select 1 from public.fush_organization_members m
    where m.organization_id=target_organization_id
      and m.user_id=auth.uid() and m.is_active=true
      and upper(m.role) in ('OWNER','ADMIN','ACCOUNTANT')
  );
$$;
revoke all on function public.fush_can_resolve_sales_aux(uuid) from public;
grant execute on function public.fush_can_resolve_sales_aux(uuid) to authenticated;

create or replace function public.fush_publish_sales_aux_batch(
    target_organization_id uuid,
    documents jsonb
)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare
  caller uuid := auth.uid();
  p jsonb;
  et text;
  ek text;
  incoming jsonb;
  existing jsonb;
  inserted_count int := 0;
  unchanged_count int := 0;
  conflict_count int := 0;
begin
  if caller is null then raise exception 'Authentication required'; end if;
  if not public.fush_can_publish_sales_aux(target_organization_id) then
    raise exception 'Sales auxiliary publish role required';
  end if;
  if jsonb_typeof(coalesce(documents,'[]'::jsonb)) <> 'array' then
    raise exception 'documents must be an array';
  end if;

  for p in select value from jsonb_array_elements(coalesce(documents,'[]'::jsonb)) loop
    et := upper(trim(p->>'entity_type'));
    ek := nullif(trim(p->>'entity_key'),'');
    incoming := p->'content';
    if et not in ('CHARGE_TYPE','ADDITIONAL_CHARGE','ADDITIONAL_CHARGE_PAYMENT','ADDITIONAL_CHARGE_SETTLEMENT',
                  'SHIPMENT','SHIPMENT_ITEM','SHIPMENT_EXPENSE','SHIPMENT_ITEM_ALLOCATION','SHIPMENT_EXPENSE_ALLOCATION') then
      raise exception 'Unsupported entity type: %', et;
    end if;
    if ek is null or incoming is null or jsonb_typeof(incoming) <> 'object' then
      raise exception 'Invalid auxiliary document';
    end if;

    select d.content into existing
      from public.fush_tx_sales_aux_documents d
      where d.organization_id=target_organization_id and d.entity_type=et and d.entity_key=ek;

    if existing is null then
      insert into public.fush_tx_sales_aux_documents(organization_id,entity_type,entity_key,content,updated_by)
      values(target_organization_id,et,ek,incoming,caller)
      on conflict (organization_id,entity_type,entity_key) do nothing;
      select d.content into existing
        from public.fush_tx_sales_aux_documents d
        where d.organization_id=target_organization_id and d.entity_type=et and d.entity_key=ek;
      if existing=incoming then inserted_count:=inserted_count+1;
      else conflict_count:=conflict_count+1; end if;
    elsif existing=incoming then
      unchanged_count:=unchanged_count+1;
    else
      conflict_count:=conflict_count+1;
    end if;

    if existing is distinct from incoming then
      insert into public.fush_sales_aux_sync_conflicts(
        organization_id,entity_type,entity_key,cloud_content,incoming_content,detected_by
      ) values(target_organization_id,et,ek,existing,incoming,caller)
      on conflict (organization_id,entity_type,entity_key) where status='OPEN'
      do update set cloud_content=excluded.cloud_content,
                    incoming_content=excluded.incoming_content,
                    detected_by=caller,
                    detected_at=now();
    end if;
  end loop;

  return jsonb_build_object('inserted',inserted_count,'unchanged',unchanged_count,'conflicts',conflict_count);
end;
$$;
revoke all on function public.fush_publish_sales_aux_batch(uuid,jsonb) from public;
grant execute on function public.fush_publish_sales_aux_batch(uuid,jsonb) to authenticated;

create or replace function public.fush_resolve_sales_aux_conflict(
    target_organization_id uuid,
    target_entity_type text,
    target_entity_key text,
    target_resolution text,
    replacement_content jsonb default null
)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare
  caller uuid := auth.uid();
  et text := upper(trim(target_entity_type));
  res text := upper(trim(target_resolution));
begin
  if caller is null then raise exception 'Authentication required'; end if;
  if not public.fush_can_resolve_sales_aux(target_organization_id) then
    raise exception 'Sales auxiliary conflict authority required';
  end if;
  if res not in ('KEEP_LOCAL','KEEP_CLOUD') then raise exception 'Unsupported resolution'; end if;

  if res='KEEP_LOCAL' then
    if replacement_content is null or jsonb_typeof(replacement_content)<>'object' then
      raise exception 'replacement_content required';
    end if;
    update public.fush_tx_sales_aux_documents
      set content=replacement_content, updated_at=now(), updated_by=caller
      where organization_id=target_organization_id and entity_type=et and entity_key=target_entity_key;
    if not found then raise exception 'Cloud auxiliary document not found'; end if;
  end if;

  update public.fush_sales_aux_sync_conflicts
    set status='RESOLVED',resolution=res,resolved_by=caller,resolved_at=now()
    where organization_id=target_organization_id and entity_type=et and entity_key=target_entity_key and status='OPEN';

  return jsonb_build_object('resolved',true,'entity_type',et,'entity_key',target_entity_key,'resolution',res);
end;
$$;
revoke all on function public.fush_resolve_sales_aux_conflict(uuid,text,text,text,jsonb) from public;
grant execute on function public.fush_resolve_sales_aux_conflict(uuid,text,text,text,jsonb) to authenticated;

commit;

-- END COMPONENT: V179_SALES_AUXILIARY_CLOUD_SYNC.sql

-- ============================================================================
-- BEGIN COMPONENT: V185_ORGANIZATION_MEMBERSHIP_UNIFIED_SYNC.sql
-- ============================================================================
-- FUSH ERP Mobile v185 — Organization-membership based unified sync
-- Apply AFTER existing cloud SQL through v179.
-- Principle:
--   Business permissions decide who may CREATE/EDIT/POST inside the ERP app.
--   Sync transport decides whether an already-existing company record may move between devices.
--   Every authenticated ACTIVE organization member may upload/download company sync records.
-- Conflict resolution authority remains role-restricted because resolution changes canonical truth.

begin;

-- Accounting publish transport: active membership, no business-role filter.
create or replace function public.fush_can_publish_accounting(target_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists(
        select 1
        from public.fush_organization_members m
        where m.organization_id = target_organization_id
          and m.user_id = auth.uid()
          and m.is_active = true
    );
$$;

grant execute on function public.fush_can_publish_accounting(uuid) to authenticated;

-- Sales auxiliary / shipment publish transport: active membership, no business-role filter.
create or replace function public.fush_can_publish_sales_aux(target_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists(
        select 1
        from public.fush_organization_members m
        where m.organization_id = target_organization_id
          and m.user_id = auth.uid()
          and m.is_active = true
    );
$$;

grant execute on function public.fush_can_publish_sales_aux(uuid) to authenticated;

-- Direct-table mirrors used by master data, sales, purchases, supplier payments,
-- inventory and production. Existing read policies remain untouched. These additive
-- INSERT/UPDATE policies permit transport for any active org member. Hard DELETE is
-- intentionally not granted here; reversals/status/tombstones remain the audit-safe path.
-- Domain permissions are still enforced before local rows can be created/posted by Android services.
do $$
declare
    t text;
    tables text[] := array[
        'fush_md_currencies',
        'fush_md_units',
        'fush_md_warehouses',
        'fush_md_items',
        'fush_md_item_unit_conversions',
        'fush_md_customers',
        'fush_md_suppliers',
        'fush_md_treasury_accounts',
        'fush_tx_sales_invoices',
        'fush_tx_sales_lines',
        'fush_tx_sales_allocations',
        'fush_tx_customer_receipts',
        'fush_tx_customer_receipt_allocations',
        'fush_tx_sales_returns',
        'fush_tx_sales_return_lines',
        'fush_tx_purchase_invoices',
        'fush_tx_purchase_lines',
        'fush_tx_purchase_returns',
        'fush_tx_purchase_return_lines',
        'fush_tx_supplier_payments',
        'fush_tx_supplier_payment_allocations',
        'fush_cloud_domain_state',
        'fush_md_recipes',
        'fush_md_recipe_components',
        'fush_tx_production_orders',
        'fush_tx_production_materials',
        'fush_tx_production_batches',
        'fush_tx_production_issues',
        'fush_inventory_snapshot'
    ];
begin
    foreach t in array tables loop
        if to_regclass('public.' || t) is null then
            raise notice 'v185 sync policy skipped missing table %', t;
            continue;
        end if;

        execute format('alter table public.%I enable row level security', t);

        if not exists(select 1 from pg_policies where schemaname='public' and tablename=t and policyname='v185_active_member_insert') then
            execute format(
                'create policy v185_active_member_insert on public.%I for insert to authenticated with check (public.fush_is_org_member(organization_id))', t
            );
        end if;
        if not exists(select 1 from pg_policies where schemaname='public' and tablename=t and policyname='v185_active_member_update') then
            execute format(
                'create policy v185_active_member_update on public.%I for update to authenticated using (public.fush_is_org_member(organization_id)) with check (public.fush_is_org_member(organization_id))', t
            );
        end if;
    end loop;
end $$;

commit;

-- END COMPONENT: V185_ORGANIZATION_MEMBERSHIP_UNIFIED_SYNC.sql

-- ============================================================================
-- v186 FINAL MEMBERSHIP-TRANSPORT HARDENING
-- This section intentionally runs LAST so legacy role-limited policies/functions
-- cannot remain the effective transport gate after a partial/old deployment.
-- ============================================================================

begin;

-- Always define active membership helper with explicit public schema lookup.
create or replace function public.fush_is_org_member(target_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
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

-- Accounting transport = active membership.
create or replace function public.fush_can_publish_accounting(target_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.fush_is_org_member(target_organization_id);
$$;
revoke all on function public.fush_can_publish_accounting(uuid) from public;
grant execute on function public.fush_can_publish_accounting(uuid) to authenticated;

-- Sales auxiliary / shipment transport = active membership.
create or replace function public.fush_can_publish_sales_aux(target_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.fush_is_org_member(target_organization_id);
$$;
revoke all on function public.fush_can_publish_sales_aux(uuid) from public;
grant execute on function public.fush_can_publish_sales_aux(uuid) to authenticated;

-- Recreate explicit membership transport policies for every direct REST mirror.
-- Existing older OWNER/ADMIN policies may remain, but PostgreSQL permissive policies
-- OR together; these v186 policies guarantee that an active member can sync.
do $$
declare
    t text;
    tables text[] := array[
        'fush_md_currencies',
        'fush_md_units',
        'fush_md_warehouses',
        'fush_md_items',
        'fush_md_item_unit_conversions',
        'fush_md_customers',
        'fush_md_suppliers',
        'fush_md_treasury_accounts',
        'fush_tx_sales_invoices',
        'fush_tx_sales_lines',
        'fush_tx_sales_allocations',
        'fush_tx_customer_receipts',
        'fush_tx_customer_receipt_allocations',
        'fush_tx_sales_returns',
        'fush_tx_sales_return_lines',
        'fush_tx_purchase_invoices',
        'fush_tx_purchase_lines',
        'fush_tx_purchase_returns',
        'fush_tx_purchase_return_lines',
        'fush_tx_supplier_payments',
        'fush_tx_supplier_payment_allocations',
        'fush_cloud_domain_state',
        'fush_md_recipes',
        'fush_md_recipe_components',
        'fush_tx_production_orders',
        'fush_tx_production_materials',
        'fush_tx_production_batches',
        'fush_tx_production_issues',
        'fush_inventory_snapshot'
    ];
begin
    foreach t in array tables loop
        if to_regclass('public.' || t) is null then
            raise exception 'v186 unified sync: required table public.% is missing', t;
        end if;

        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on table public.%I from anon', t);
        execute format('grant select, insert, update on table public.%I to authenticated', t);
        execute format('revoke delete on table public.%I from authenticated', t);

        execute format('drop policy if exists v186_active_member_select on public.%I', t);
        execute format(
            'create policy v186_active_member_select on public.%I for select to authenticated using (public.fush_is_org_member(organization_id))',
            t
        );

        execute format('drop policy if exists v186_active_member_insert on public.%I', t);
        execute format(
            'create policy v186_active_member_insert on public.%I for insert to authenticated with check (public.fush_is_org_member(organization_id))',
            t
        );

        execute format('drop policy if exists v186_active_member_update on public.%I', t);
        execute format(
            'create policy v186_active_member_update on public.%I for update to authenticated using (public.fush_is_org_member(organization_id)) with check (public.fush_is_org_member(organization_id))',
            t
        );
    end loop;
end $$;

-- v167 inventory CAS used by the Android sync engine had a legacy role filter.
-- Keep CAS/conflict semantics, but change ONLY the transport authorization gate
-- to active organization membership.
create or replace function public.fush_publish_inventory_snapshot_cas(
    target_organization_id uuid,
    target_warehouse_code text,
    target_item_code text,
    target_lot_no text,
    target_lot_key text,
    target_expiry_date_ms bigint,
    target_expiry_key bigint,
    expected_updated_at timestamptz,
    new_quantity_base double precision,
    new_inventory_value_base double precision,
    new_snapshot_at bigint
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    affected integer := 0;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    if not public.fush_is_org_member(target_organization_id) then
        raise exception 'Active organization membership required';
    end if;

    if expected_updated_at is null then
        insert into public.fush_inventory_snapshot(
            organization_id,
            warehouse_code,
            item_code,
            lot_no,
            lot_key,
            expiry_date_ms,
            expiry_key,
            quantity_base,
            inventory_value_base,
            snapshot_at,
            updated_at,
            updated_by
        ) values (
            target_organization_id,
            target_warehouse_code,
            target_item_code,
            target_lot_no,
            coalesce(target_lot_key, ''),
            target_expiry_date_ms,
            target_expiry_key,
            new_quantity_base,
            new_inventory_value_base,
            new_snapshot_at,
            now(),
            auth.uid()
        )
        on conflict (organization_id, warehouse_code, item_code, lot_key, expiry_key)
        do nothing;

        get diagnostics affected = row_count;
        return affected = 1;
    end if;

    update public.fush_inventory_snapshot
       set lot_no = target_lot_no,
           expiry_date_ms = target_expiry_date_ms,
           quantity_base = new_quantity_base,
           inventory_value_base = new_inventory_value_base,
           snapshot_at = new_snapshot_at,
           updated_at = now(),
           updated_by = auth.uid()
     where organization_id = target_organization_id
       and warehouse_code = target_warehouse_code
       and item_code = target_item_code
       and lot_key = coalesce(target_lot_key, '')
       and expiry_key = target_expiry_key
       and updated_at = expected_updated_at;

    get diagnostics affected = row_count;
    return affected = 1;
end;
$$;

revoke all on function public.fush_publish_inventory_snapshot_cas(
    uuid, text, text, text, text, bigint, bigint, timestamptz,
    double precision, double precision, bigint
) from public;

grant execute on function public.fush_publish_inventory_snapshot_cas(
    uuid, text, text, text, text, bigint, bigint, timestamptz,
    double precision, double precision, bigint
) to authenticated;

-- Sales auxiliary tables are read directly and published through SECURITY DEFINER RPC.
-- Reassert read grants/policies after any partial older deployment.
alter table public.fush_tx_sales_aux_documents enable row level security;
alter table public.fush_sales_aux_sync_conflicts enable row level security;
revoke all on table public.fush_tx_sales_aux_documents from anon;
revoke all on table public.fush_sales_aux_sync_conflicts from anon;
grant select on table public.fush_tx_sales_aux_documents to authenticated;
grant select on table public.fush_sales_aux_sync_conflicts to authenticated;

drop policy if exists v186_sales_aux_read_member on public.fush_tx_sales_aux_documents;
create policy v186_sales_aux_read_member
on public.fush_tx_sales_aux_documents for select to authenticated
using (public.fush_is_org_member(organization_id));

drop policy if exists v186_sales_aux_conflicts_read_member on public.fush_sales_aux_sync_conflicts;
create policy v186_sales_aux_conflicts_read_member
on public.fush_sales_aux_sync_conflicts for select to authenticated
using (public.fush_is_org_member(organization_id));

commit;

-- ============================================================================
-- POST-FLIGHT VERIFICATION
-- Fails visibly in SQL Editor if a required object was not installed.
-- ============================================================================
do $$
declare
    obj text;
    required_tables text[] := array[
        'fush_organizations','fush_organization_members','fush_cloud_user_bindings',
        'fush_md_currencies','fush_md_units','fush_md_warehouses','fush_md_items',
        'fush_md_customers','fush_md_suppliers','fush_md_treasury_accounts',
        'fush_tx_sales_invoices','fush_tx_sales_lines','fush_tx_sales_allocations',
        'fush_tx_customer_receipts','fush_tx_sales_returns',
        'fush_tx_purchase_invoices','fush_tx_purchase_returns',
        'fush_tx_supplier_payments','fush_cloud_domain_state',
        'fush_tx_production_orders','fush_inventory_snapshot',
        'fush_tx_gl_journals','fush_tx_treasury_vouchers',
        'fush_tx_sales_aux_documents','fush_sales_aux_sync_conflicts'
    ];
begin
    foreach obj in array required_tables loop
        if to_regclass('public.' || obj) is null then
            raise exception 'POST-FLIGHT FAILED: missing public.%', obj;
        end if;
    end loop;

    if to_regprocedure('public.fush_publish_sales_aux_batch(uuid,jsonb)') is null then
        raise exception 'POST-FLIGHT FAILED: fush_publish_sales_aux_batch is missing';
    end if;

    if to_regprocedure('public.fush_publish_inventory_snapshot_cas(uuid,text,text,text,text,bigint,bigint,timestamp with time zone,double precision,double precision,bigint)') is null then
        raise exception 'POST-FLIGHT FAILED: fush_publish_inventory_snapshot_cas is missing';
    end if;

    raise notice 'FUSH v186 unified Supabase sync schema installed successfully.';
    raise notice 'Transport authorization is ACTIVE ORGANIZATION MEMBERSHIP based.';
end $$;

-- End of FUSH_ERP_Mobile_v186-UnifiedSync-Supabase.sql

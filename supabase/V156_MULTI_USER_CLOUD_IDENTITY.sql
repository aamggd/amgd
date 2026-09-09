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

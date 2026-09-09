-- FUSH Customer v214 - Commercial Licensing / Activation / Device Entitlements
-- Apply after 010_commercial_multitenant_foundation.sql.
-- IMPORTANT: this migration is prepared for deployment but must not be applied to production
-- until the commercial release gate approves it.

begin;

create table if not exists public.fush_commercial_licenses (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null unique references public.fush_organizations(id) on delete cascade,
    activation_code_hash text not null unique,
    plan_code text not null default 'STANDARD',
    status text not null default 'ACTIVE'
        check (status in ('ACTIVE', 'TRIAL', 'SUSPENDED', 'CANCELLED', 'REVOKED')),
    is_trial boolean not null default false,
    starts_at timestamptz not null default now(),
    expires_at timestamptz not null,
    offline_grace_hours integer not null default 72
        check (offline_grace_hours between 0 and 720),
    max_devices integer not null default 1
        check (max_devices between 1 and 1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by uuid
);

create table if not exists public.fush_license_device_entitlements (
    id uuid primary key default gen_random_uuid(),
    license_id uuid not null references public.fush_commercial_licenses(id) on delete cascade,
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    user_id uuid not null,
    device_key text not null,
    device_name text not null default 'Android device',
    status text not null default 'ACTIVE'
        check (status in ('ACTIVE', 'REVOKED')),
    app_version text,
    activated_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz,
    updated_at timestamptz not null default now(),
    unique (license_id, device_key)
);

create index if not exists ix_fush_license_entitlements_org
    on public.fush_license_device_entitlements(organization_id, status);
create index if not exists ix_fush_license_entitlements_user
    on public.fush_license_device_entitlements(user_id, status);

alter table public.fush_commercial_licenses enable row level security;
alter table public.fush_license_device_entitlements enable row level security;

-- No direct Android writes. The RPC layer below is the mutation boundary.
revoke all on table public.fush_commercial_licenses from public, anon, authenticated;
revoke all on table public.fush_license_device_entitlements from public, anon, authenticated;

-- Owner/admin support may inspect their own organization's licensing data via SQL-backed tooling,
-- but Android itself uses only the two RPCs below.
drop policy if exists "fush_license_select_org_owner" on public.fush_commercial_licenses;
create policy "fush_license_select_org_owner"
on public.fush_commercial_licenses
for select to authenticated
using (public.fush_is_org_owner(organization_id));

drop policy if exists "fush_license_entitlement_select_org_owner" on public.fush_license_device_entitlements;
create policy "fush_license_entitlement_select_org_owner"
on public.fush_license_device_entitlements
for select to authenticated
using (public.fush_is_org_owner(organization_id));

-- Server/backend-only issuance. Plain activation codes are hashed before storage and never returned.
create or replace function public.fush_issue_commercial_license(
    target_organization_id uuid,
    activation_code text,
    target_plan_code text,
    target_status text,
    target_is_trial boolean,
    target_starts_at timestamptz,
    target_expires_at timestamptz,
    target_offline_grace_hours integer,
    target_max_devices integer
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    normalized_code text := upper(trim(activation_code));
    normalized_plan text := upper(trim(target_plan_code));
    normalized_status text := upper(trim(target_status));
    code_hash text;
    result_id uuid;
begin
    if normalized_code = '' or length(normalized_code) < 8 then
        raise exception 'Activation code must contain at least 8 characters';
    end if;
    if normalized_plan = '' then
        raise exception 'Plan code is required';
    end if;
    if normalized_status not in ('ACTIVE', 'TRIAL', 'SUSPENDED', 'CANCELLED', 'REVOKED') then
        raise exception 'Invalid license status';
    end if;
    if target_expires_at <= target_starts_at then
        raise exception 'License expiry must be after start';
    end if;
    if target_offline_grace_hours not between 0 and 720 then
        raise exception 'Offline grace is out of range';
    end if;
    if target_max_devices not between 1 and 1000 then
        raise exception 'Maximum devices is out of range';
    end if;
    if not exists (select 1 from public.fush_organizations where id = target_organization_id) then
        raise exception 'Organization not found';
    end if;

    code_hash := encode(sha256(convert_to(normalized_code, 'UTF8')), 'hex');

    insert into public.fush_commercial_licenses (
        organization_id,
        activation_code_hash,
        plan_code,
        status,
        is_trial,
        starts_at,
        expires_at,
        offline_grace_hours,
        max_devices,
        created_by,
        updated_at
    ) values (
        target_organization_id,
        code_hash,
        normalized_plan,
        normalized_status,
        target_is_trial,
        target_starts_at,
        target_expires_at,
        target_offline_grace_hours,
        target_max_devices,
        auth.uid(),
        now()
    )
    on conflict (organization_id) do update set
        activation_code_hash = excluded.activation_code_hash,
        plan_code = excluded.plan_code,
        status = excluded.status,
        is_trial = excluded.is_trial,
        starts_at = excluded.starts_at,
        expires_at = excluded.expires_at,
        offline_grace_hours = excluded.offline_grace_hours,
        max_devices = excluded.max_devices,
        updated_at = now()
    returning id into result_id;

    return result_id;
end;
$$;

revoke all on function public.fush_issue_commercial_license(uuid, text, text, text, boolean, timestamptz, timestamptz, integer, integer)
    from public, anon, authenticated;
grant execute on function public.fush_issue_commercial_license(uuid, text, text, text, boolean, timestamptz, timestamptz, integer, integer)
    to service_role;

create or replace function public.fush_activate_commercial_license(
    target_organization_id uuid,
    activation_code text,
    target_device_key text,
    target_device_name text,
    target_app_version text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    normalized_code text := upper(trim(activation_code));
    code_hash text;
    license_row public.fush_commercial_licenses%rowtype;
    entitlement_row public.fush_license_device_entitlements%rowtype;
    active_device_count integer;
    grace_until timestamptz;
begin
    if caller is null then
        raise exception 'Authentication required';
    end if;
    if trim(coalesce(target_device_key, '')) = '' then
        raise exception 'Device key is required';
    end if;
    if not exists (
        select 1
        from public.fush_organization_members m
        where m.organization_id = target_organization_id
          and m.user_id = caller
          and m.is_active = true
    ) then
        raise exception 'Active organization membership required';
    end if;

    code_hash := encode(sha256(convert_to(normalized_code, 'UTF8')), 'hex');

    select * into license_row
    from public.fush_commercial_licenses l
    where l.organization_id = target_organization_id
      and l.activation_code_hash = code_hash
    for update;

    if license_row.id is null then
        raise exception 'Invalid activation code';
    end if;
    if license_row.status not in ('ACTIVE', 'TRIAL') then
        raise exception 'License is not active';
    end if;
    if now() < license_row.starts_at then
        raise exception 'License has not started';
    end if;
    if now() > license_row.expires_at then
        raise exception 'License has expired';
    end if;

    select * into entitlement_row
    from public.fush_license_device_entitlements e
    where e.license_id = license_row.id
      and e.device_key = target_device_key
    for update;

    if entitlement_row.id is null then
        select count(*)::integer into active_device_count
        from public.fush_license_device_entitlements e
        where e.license_id = license_row.id
          and e.status = 'ACTIVE';

        if active_device_count >= license_row.max_devices then
            raise exception 'Maximum licensed devices reached';
        end if;

        insert into public.fush_license_device_entitlements (
            license_id,
            organization_id,
            user_id,
            device_key,
            device_name,
            status,
            app_version,
            activated_at,
            last_seen_at,
            updated_at
        ) values (
            license_row.id,
            target_organization_id,
            caller,
            target_device_key,
            left(coalesce(nullif(trim(target_device_name), ''), 'Android device'), 120),
            'ACTIVE',
            left(coalesce(target_app_version, ''), 120),
            now(),
            now(),
            now()
        ) returning * into entitlement_row;
    elsif entitlement_row.status <> 'ACTIVE' then
        raise exception 'Device entitlement is revoked';
    else
        update public.fush_license_device_entitlements
        set user_id = caller,
            device_name = left(coalesce(nullif(trim(target_device_name), ''), 'Android device'), 120),
            app_version = left(coalesce(target_app_version, ''), 120),
            last_seen_at = now(),
            updated_at = now()
        where id = entitlement_row.id
        returning * into entitlement_row;
    end if;

    grace_until := license_row.expires_at + make_interval(hours => license_row.offline_grace_hours);

    return jsonb_build_object(
        'organization_id', license_row.organization_id,
        'license_id', license_row.id,
        'device_entitlement_id', entitlement_row.id,
        'plan_code', license_row.plan_code,
        'status', license_row.status,
        'is_trial', license_row.is_trial,
        'starts_at', floor(extract(epoch from license_row.starts_at) * 1000)::bigint,
        'expires_at', floor(extract(epoch from license_row.expires_at) * 1000)::bigint,
        'offline_grace_until', floor(extract(epoch from grace_until) * 1000)::bigint,
        'max_devices', license_row.max_devices,
        'server_time', floor(extract(epoch from now()) * 1000)::bigint
    );
end;
$$;

revoke all on function public.fush_activate_commercial_license(uuid, text, text, text, text) from public, anon;
grant execute on function public.fush_activate_commercial_license(uuid, text, text, text, text) to authenticated;

create or replace function public.fush_get_commercial_license_snapshot(
    target_organization_id uuid,
    target_device_key text,
    target_device_name text,
    target_app_version text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    license_row public.fush_commercial_licenses%rowtype;
    entitlement_row public.fush_license_device_entitlements%rowtype;
    effective_status text;
    grace_until timestamptz;
begin
    if caller is null then
        raise exception 'Authentication required';
    end if;
    if not exists (
        select 1
        from public.fush_organization_members m
        where m.organization_id = target_organization_id
          and m.user_id = caller
          and m.is_active = true
    ) then
        raise exception 'Active organization membership required';
    end if;

    select * into license_row
    from public.fush_commercial_licenses l
    where l.organization_id = target_organization_id;

    if license_row.id is null then
        raise exception 'No commercial license for organization';
    end if;

    select * into entitlement_row
    from public.fush_license_device_entitlements e
    where e.license_id = license_row.id
      and e.device_key = target_device_key;

    if entitlement_row.id is null then
        raise exception 'Device is not activated';
    end if;

    effective_status := case
        when entitlement_row.status = 'REVOKED' then 'REVOKED'
        else license_row.status
    end;

    if entitlement_row.status = 'ACTIVE' then
        update public.fush_license_device_entitlements
        set user_id = caller,
            device_name = left(coalesce(nullif(trim(target_device_name), ''), device_name), 120),
            app_version = left(coalesce(target_app_version, app_version, ''), 120),
            last_seen_at = now(),
            updated_at = now()
        where id = entitlement_row.id
        returning * into entitlement_row;
    end if;

    grace_until := license_row.expires_at + make_interval(hours => license_row.offline_grace_hours);

    return jsonb_build_object(
        'organization_id', license_row.organization_id,
        'license_id', license_row.id,
        'device_entitlement_id', entitlement_row.id,
        'plan_code', license_row.plan_code,
        'status', effective_status,
        'is_trial', license_row.is_trial,
        'starts_at', floor(extract(epoch from license_row.starts_at) * 1000)::bigint,
        'expires_at', floor(extract(epoch from license_row.expires_at) * 1000)::bigint,
        'offline_grace_until', floor(extract(epoch from grace_until) * 1000)::bigint,
        'max_devices', license_row.max_devices,
        'server_time', floor(extract(epoch from now()) * 1000)::bigint
    );
end;
$$;

revoke all on function public.fush_get_commercial_license_snapshot(uuid, text, text, text) from public, anon;
grant execute on function public.fush_get_commercial_license_snapshot(uuid, text, text, text) to authenticated;

commit;

-- FUSH ERP Mobile v213 - Commercial Multi-Tenant Foundation
-- Apply after the existing cloud sync schema. This migration does NOT delete or rename
-- the historical FUSH tenant; it makes new tenants first-class and owner-created.

begin;

create or replace function public.fush_create_organization(
    target_code text,
    target_name text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    normalized_code text := upper(trim(target_code));
    normalized_name text := trim(target_name);
    new_org_id uuid := gen_random_uuid();
begin
    if caller is null then
        raise exception 'Authentication required';
    end if;
    if normalized_code = '' or normalized_name = '' then
        raise exception 'Organization code and name are required';
    end if;
    if normalized_code !~ '^[A-Z0-9][A-Z0-9_-]{1,39}$' then
        raise exception 'Invalid organization code';
    end if;

    insert into public.fush_organizations (id, code, name, created_by, updated_at)
    values (new_org_id, normalized_code, normalized_name, caller, now());

    insert into public.fush_organization_members (
        organization_id, user_id, role, is_active, updated_at
    ) values (
        new_org_id, caller, 'OWNER', true, now()
    );

    return jsonb_build_object(
        'organization_id', new_org_id,
        'code', normalized_code,
        'name', normalized_name,
        'role', 'OWNER'
    );
end;
$$;

revoke all on function public.fush_create_organization(text, text) from public;
grant execute on function public.fush_create_organization(text, text) to authenticated;

-- Explicit tenant-scoped uniqueness for device registration. The v155 foundation used
-- UNIQUE(user_id, device_key), which incorrectly couples the same auth account across tenants.
alter table public.fush_sync_devices
    drop constraint if exists fush_sync_devices_user_id_device_key_key;

create unique index if not exists uq_fush_sync_devices_org_user_device
on public.fush_sync_devices(organization_id, user_id, device_key);

-- Commercial privileged-function hardening. Older deployments may contain trigger/event-trigger
-- helpers that were inadvertently executable through the Data API. They are internal database hooks,
-- not application RPCs, so no client role needs EXECUTE on them.
do $$
begin
    if to_regprocedure('public.fush_register_shipment_document_number()') is not null then
        revoke execute on function public.fush_register_shipment_document_number() from public, anon, authenticated;
    end if;
    if to_regprocedure('public.rls_auto_enable()') is not null then
        revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
    end if;
    if to_regprocedure('public.fush_set_treasury_group_code()') is not null then
        alter function public.fush_set_treasury_group_code() set search_path = '';
    end if;
end $$;

-- Membership discovery remains self-only and ACTIVE-only. It is intentionally the one place where
-- Android may query without an organization filter in order to resolve the active tenant after authentication.
drop policy if exists "fush_member_select_self" on public.fush_organization_members;
create policy "fush_member_select_self" on public.fush_organization_members
for select to authenticated
using (user_id = (select auth.uid()) and is_active = true);

-- v156 allowed a user to read their own cloud binding even after organization membership had
-- been revoked. That is low sensitivity but violates the strict commercial tenant boundary.
-- Require ACTIVE organization membership for every binding read, including self reads.
drop policy if exists "fush_cloud_binding_select_self_or_owner" on public.fush_cloud_user_bindings;
create policy "fush_cloud_binding_select_self_or_owner"
on public.fush_cloud_user_bindings
for select to authenticated
using (
    public.fush_is_org_member(organization_id)
    and (
        cloud_user_id = (select auth.uid())
        or public.fush_is_org_owner(organization_id)
    )
);

commit;

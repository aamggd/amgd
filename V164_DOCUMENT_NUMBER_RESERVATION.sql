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

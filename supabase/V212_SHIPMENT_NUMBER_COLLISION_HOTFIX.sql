-- FUSH ERP Mobile v212 — Shipment Number Collision Hotfix
-- Idempotently backfill all existing shipment numbers and keep future synced numbers reserved.

insert into public.fush_document_number_registry(
    organization_id, document_type, document_no, reserved_by, reserved_device_key, source
)
select distinct
    organization_id,
    'SALE_SHIPMENT'::text,
    trim(entity_key),
    null::uuid,
    null::text,
    'SHIPMENT_SYNC_BACKFILL'::text
from public.fush_tx_sales_aux_documents
where entity_type = 'SHIPMENT'
  and trim(entity_key) <> ''
on conflict (organization_id, document_type, document_no) do nothing;

create or replace function public.fush_register_shipment_document_number()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.entity_type = 'SHIPMENT' and trim(new.entity_key) <> '' then
        insert into public.fush_document_number_registry(
            organization_id, document_type, document_no, reserved_by, reserved_device_key, source
        ) values (
            new.organization_id, 'SALE_SHIPMENT', trim(new.entity_key), null::uuid, null::text,
            'SHIPMENT_SYNC_TRIGGER'
        )
        on conflict (organization_id, document_type, document_no) do nothing;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_fush_register_shipment_document_number on public.fush_tx_sales_aux_documents;
create trigger trg_fush_register_shipment_document_number
after insert or update of entity_type, entity_key
on public.fush_tx_sales_aux_documents
for each row execute function public.fush_register_shipment_document_number();

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
    if caller is null then raise exception 'Authentication required'; end if;
    if not public.fush_is_org_member(target_organization_id) then
        raise exception 'Organization membership required';
    end if;
    if normalized_type = '' or normalized_no = '' then
        raise exception 'Document type and number are required';
    end if;
    if length(normalized_type) > 50 or length(normalized_no) > 120 then
        raise exception 'Document type or number is too long';
    end if;

    select * into existing
    from public.fush_document_number_registry r
    where r.organization_id = target_organization_id
      and r.document_type = normalized_type
      and r.document_no = normalized_no;

    if found then
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
    end if;

    -- Compatibility guard: older app versions may have synced a shipment before reserving its number.
    if normalized_type = 'SALE_SHIPMENT' and exists (
        select 1
        from public.fush_tx_sales_aux_documents d
        where d.organization_id = target_organization_id
          and d.entity_type = 'SHIPMENT'
          and upper(trim(d.entity_key)) = upper(normalized_no)
    ) then
        return jsonb_build_object('reserved', false, 'reason', 'DOCUMENT_NUMBER_IN_USE');
    end if;

    insert into public.fush_document_number_registry(
        organization_id, document_type, document_no, reserved_by, reserved_device_key, source
    ) values (
        target_organization_id, normalized_type, normalized_no, caller,
        nullif(trim(target_device_key), ''), 'LIVE_RESERVATION'
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

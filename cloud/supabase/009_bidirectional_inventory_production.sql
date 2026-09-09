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

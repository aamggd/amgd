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

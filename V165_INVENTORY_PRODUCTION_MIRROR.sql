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

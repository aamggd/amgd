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

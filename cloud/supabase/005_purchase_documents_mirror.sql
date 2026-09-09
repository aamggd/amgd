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

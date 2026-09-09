-- FUSH ERP Mobile v159 - Sales & Receivables Cloud Mirror
-- Run after v155/v156/v158 cloud migrations.
-- Safe scope: posted sales/receivables document mirror only. No Android Room schema change.

begin;

create table if not exists public.fush_tx_sales_invoices (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    invoice_no text not null,
    customer_code text not null,
    invoice_date_ms bigint not null,
    due_date_ms bigint,
    warehouse_code text not null,
    currency_code text not null,
    exchange_rate double precision not null,
    payment_type text not null,
    channel text not null default '',
    province text not null default '',
    sales_rep_name_snapshot text not null default '',
    sales_rep_rate_pct double precision not null default 0,
    free_qty_limit_pct_snapshot double precision not null default 0,
    free_qty_approval_reason text not null default '',
    discount_pct double precision not null default 0,
    gross_original double precision not null default 0,
    discount_original double precision not null default 0,
    transport_original double precision not null default 0,
    fees_original double precision not null default 0,
    risk_margin_original double precision not null default 0,
    total_original double precision not null default 0,
    total_base double precision not null default 0,
    status text not null default 'POSTED',
    below_floor_reason text not null default '',
    notes text not null default '',
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, invoice_no)
);

create table if not exists public.fush_tx_sales_lines (
    organization_id uuid not null,
    invoice_no text not null,
    line_no integer not null check (line_no > 0),
    item_code text not null,
    unit_code text not null,
    quantity double precision not null,
    free_quantity double precision not null default 0,
    factor_to_base double precision not null,
    base_quantity double precision not null,
    free_base_quantity double precision not null default 0,
    unit_price_original double precision not null,
    gross_original double precision not null,
    discount_original double precision not null,
    net_original double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, invoice_no, line_no),
    foreign key (organization_id, invoice_no)
        references public.fush_tx_sales_invoices(organization_id, invoice_no)
        on update cascade on delete cascade
);

create table if not exists public.fush_tx_sales_allocations (
    organization_id uuid not null,
    invoice_no text not null,
    line_no integer not null,
    allocation_no integer not null check (allocation_no > 0),
    item_code text not null,
    lot_no text,
    expiry_date_ms bigint,
    quantity_base double precision not null,
    free_quantity_base double precision not null default 0,
    unit_cost_base double precision not null,
    cost_base double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, invoice_no, line_no, allocation_no),
    foreign key (organization_id, invoice_no, line_no)
        references public.fush_tx_sales_lines(organization_id, invoice_no, line_no)
        on update cascade on delete cascade
);

create table if not exists public.fush_tx_customer_receipts (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    receipt_no text not null,
    customer_code text not null,
    receipt_date_ms bigint not null,
    currency_code text not null,
    exchange_rate double precision not null,
    amount_original double precision not null,
    amount_base double precision not null,
    discount_original double precision not null default 0,
    discount_base double precision not null default 0,
    discount_reason text not null default '',
    notes text not null default '',
    reversal_of_receipt_no text,
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, receipt_no)
);

create table if not exists public.fush_tx_customer_receipt_allocations (
    organization_id uuid not null,
    receipt_no text not null,
    allocation_no integer not null check (allocation_no > 0),
    invoice_no text not null,
    amount_base double precision not null,
    discount_original double precision not null default 0,
    discount_base double precision not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, receipt_no, allocation_no),
    foreign key (organization_id, receipt_no)
        references public.fush_tx_customer_receipts(organization_id, receipt_no)
        on update cascade on delete cascade,
    foreign key (organization_id, invoice_no)
        references public.fush_tx_sales_invoices(organization_id, invoice_no)
        on update cascade on delete restrict
);

create table if not exists public.fush_tx_sales_returns (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    return_no text not null,
    sales_invoice_no text not null,
    customer_code text not null,
    return_date_ms bigint not null,
    warehouse_code text not null,
    currency_code text not null,
    exchange_rate double precision not null,
    settlement_type text not null,
    total_original double precision not null,
    total_base double precision not null,
    total_cost_base double precision not null,
    reason text not null default '',
    status text not null default 'POSTED',
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, return_no),
    foreign key (organization_id, sales_invoice_no)
        references public.fush_tx_sales_invoices(organization_id, invoice_no)
        on update cascade on delete restrict
);

create table if not exists public.fush_tx_sales_return_lines (
    organization_id uuid not null,
    return_no text not null,
    line_no integer not null check (line_no > 0),
    sales_invoice_line_no integer not null check (sales_invoice_line_no > 0),
    item_code text not null,
    unit_code text not null,
    quantity double precision not null,
    free_quantity double precision not null default 0,
    factor_to_base double precision not null,
    base_quantity double precision not null,
    free_base_quantity double precision not null default 0,
    unit_price_original double precision not null,
    line_net_original double precision not null,
    cost_base double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, return_no, line_no),
    foreign key (organization_id, return_no)
        references public.fush_tx_sales_returns(organization_id, return_no)
        on update cascade on delete cascade
);

-- RLS and grants: all active company members may read; only OWNER/ADMIN may publish.
do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_tx_sales_invoices', 'fush_tx_sales_lines', 'fush_tx_sales_allocations',
        'fush_tx_customer_receipts', 'fush_tx_customer_receipt_allocations',
        'fush_tx_sales_returns', 'fush_tx_sales_return_lines'
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

create index if not exists idx_fush_tx_sales_invoice_date
on public.fush_tx_sales_invoices(organization_id, invoice_date_ms);
create index if not exists idx_fush_tx_sales_customer
on public.fush_tx_sales_invoices(organization_id, customer_code);
create index if not exists idx_fush_tx_receipts_customer
on public.fush_tx_customer_receipts(organization_id, customer_code, receipt_date_ms);
create index if not exists idx_fush_tx_returns_customer
on public.fush_tx_sales_returns(organization_id, customer_code, return_date_ms);

commit;

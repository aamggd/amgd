-- FUSH ERP Mobile v163 - Supplier Payments & Treasury Directory Cloud Mirror
-- Run after 001, V156, 003, 004 and 005 cloud migrations.
-- Mirrors treasury directory metadata and supplier payment documents only.
-- It does NOT replay journal entries, party vouchers, bank reconciliation or cash-count side effects.

begin;

create table if not exists public.fush_md_treasury_accounts (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    name_ar text not null,
    kind text not null,
    ledger_account_code text not null,
    currency_code text not null,
    bank_name text not null default '',
    account_number text not null default '',
    is_active boolean not null default true,
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code)
);

create table if not exists public.fush_tx_supplier_payments (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    payment_no text not null,
    supplier_code text not null,
    treasury_code text not null,
    payment_date_ms bigint not null,
    currency_code text not null,
    exchange_rate double precision not null,
    amount_original double precision not null,
    cash_amount_base double precision not null,
    notes text not null default '',
    reversal_of_payment_no text,
    created_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, payment_no)
);

create table if not exists public.fush_tx_supplier_payment_allocations (
    organization_id uuid not null,
    payment_no text not null,
    allocation_no integer not null check (allocation_no > 0),
    invoice_no text not null,
    amount_original double precision not null,
    allocated_base double precision not null,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, payment_no, allocation_no),
    foreign key (organization_id, payment_no)
        references public.fush_tx_supplier_payments(organization_id, payment_no)
        on update cascade on delete cascade,
    foreign key (organization_id, invoice_no)
        references public.fush_tx_purchase_invoices(organization_id, invoice_no)
        on update cascade on delete restrict
);

alter table public.fush_md_treasury_accounts enable row level security;
alter table public.fush_tx_supplier_payments enable row level security;
alter table public.fush_tx_supplier_payment_allocations enable row level security;

revoke all on table public.fush_md_treasury_accounts from anon;
revoke all on table public.fush_tx_supplier_payments from anon;
revoke all on table public.fush_tx_supplier_payment_allocations from anon;

revoke all on table public.fush_md_treasury_accounts from authenticated;
revoke all on table public.fush_tx_supplier_payments from authenticated;
revoke all on table public.fush_tx_supplier_payment_allocations from authenticated;

grant select, insert, update on table public.fush_md_treasury_accounts to authenticated;
grant select, insert, update on table public.fush_tx_supplier_payments to authenticated;
grant select, insert, update on table public.fush_tx_supplier_payment_allocations to authenticated;

do $$
declare
    t text;
begin
    foreach t in array array[
        'fush_md_treasury_accounts',
        'fush_tx_supplier_payments',
        'fush_tx_supplier_payment_allocations'
    ]
    loop
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

create index if not exists idx_fush_md_treasury_org_active
on public.fush_md_treasury_accounts(organization_id, is_active);

create index if not exists idx_fush_tx_supplier_payment_supplier
on public.fush_tx_supplier_payments(organization_id, supplier_code, payment_date_ms);

create index if not exists idx_fush_tx_supplier_payment_date
on public.fush_tx_supplier_payments(organization_id, payment_date_ms);

commit;

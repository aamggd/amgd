-- FUSH ERP Mobile v177 - General Ledger + Treasury Bidirectional Cloud Sync
-- Apply in Supabase SQL Editor after the existing v163/v165 cloud migrations.
-- Posted journals are immutable cloud documents. Same natural key + different payload creates a conflict.

begin;

create table if not exists public.fush_tx_gl_journals (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    entry_no text not null,
    entry_date_ms bigint not null,
    source_type text not null,
    content jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, entry_no)
);

create table if not exists public.fush_tx_treasury_vouchers (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    voucher_no text not null,
    voucher_date_ms bigint not null,
    voucher_type text not null,
    treasury_code text not null,
    journal_entry_no text not null,
    content jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, voucher_no),
    foreign key (organization_id, journal_entry_no)
        references public.fush_tx_gl_journals(organization_id, entry_no)
        on update cascade on delete restrict
);

create table if not exists public.fush_accounting_sync_conflicts (
    id bigserial primary key,
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    entity_type text not null check (entity_type in ('JOURNAL','TREASURY_VOUCHER')),
    entity_key text not null,
    cloud_content jsonb not null,
    incoming_content jsonb not null,
    detected_by uuid references auth.users(id),
    detected_at timestamptz not null default now(),
    status text not null default 'OPEN' check (status in ('OPEN','RESOLVED')),
    resolution text,
    resolved_by uuid references auth.users(id),
    resolved_at timestamptz
);

create unique index if not exists uq_fush_accounting_open_conflict
on public.fush_accounting_sync_conflicts(organization_id, entity_type, entity_key)
where status = 'OPEN';

create index if not exists idx_fush_gl_journal_date
on public.fush_tx_gl_journals(organization_id, entry_date_ms);
create index if not exists idx_fush_gl_journal_source
on public.fush_tx_gl_journals(organization_id, source_type, entry_date_ms);
create index if not exists idx_fush_treasury_voucher_date
on public.fush_tx_treasury_vouchers(organization_id, voucher_date_ms);
create index if not exists idx_fush_treasury_voucher_treasury
on public.fush_tx_treasury_vouchers(organization_id, treasury_code, voucher_date_ms);

alter table public.fush_tx_gl_journals enable row level security;
alter table public.fush_tx_treasury_vouchers enable row level security;
alter table public.fush_accounting_sync_conflicts enable row level security;

revoke all on public.fush_tx_gl_journals from anon, authenticated;
revoke all on public.fush_tx_treasury_vouchers from anon, authenticated;
revoke all on public.fush_accounting_sync_conflicts from anon, authenticated;
grant select on public.fush_tx_gl_journals to authenticated;
grant select on public.fush_tx_treasury_vouchers to authenticated;
grant select on public.fush_accounting_sync_conflicts to authenticated;

-- Every active company member can read the canonical accounting truth.
drop policy if exists fush_tx_gl_journals_read_member on public.fush_tx_gl_journals;
create policy fush_tx_gl_journals_read_member on public.fush_tx_gl_journals
for select to authenticated using (public.fush_is_org_member(organization_id));

drop policy if exists fush_tx_treasury_vouchers_read_member on public.fush_tx_treasury_vouchers;
create policy fush_tx_treasury_vouchers_read_member on public.fush_tx_treasury_vouchers
for select to authenticated using (public.fush_is_org_member(organization_id));

drop policy if exists fush_accounting_sync_conflicts_read_member on public.fush_accounting_sync_conflicts;
create policy fush_accounting_sync_conflicts_read_member on public.fush_accounting_sync_conflicts
for select to authenticated using (public.fush_is_org_member(organization_id));

-- Security-definer helper: roles that can create posted business transactions and therefore journals.
create or replace function public.fush_can_publish_accounting(target_organization_id uuid)
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
          and upper(m.role) in ('OWNER','ADMIN','ACCOUNTANT','CASHIER','SALES','PURCHASING','INVENTORY','PRODUCTION')
    );
$$;
revoke all on function public.fush_can_publish_accounting(uuid) from public;
grant execute on function public.fush_can_publish_accounting(uuid) to authenticated;

create or replace function public.fush_can_resolve_accounting(target_organization_id uuid)
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
          and upper(m.role) in ('OWNER','ADMIN','ACCOUNTANT')
    );
$$;
revoke all on function public.fush_can_resolve_accounting(uuid) from public;
grant execute on function public.fush_can_resolve_accounting(uuid) to authenticated;

-- Atomic multi-document publication. Existing equal payload = unchanged. Existing different payload = conflict.
create or replace function public.fush_publish_accounting_batch(
    target_organization_id uuid,
    journal_payloads jsonb,
    voucher_payloads jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    p jsonb;
    existing jsonb;
    e_no text;
    v_no text;
    j_inserted int := 0;
    j_unchanged int := 0;
    j_conflicts int := 0;
    v_inserted int := 0;
    v_unchanged int := 0;
    v_conflicts int := 0;
    debit_total numeric;
    credit_total numeric;
begin
    if caller is null then raise exception 'Authentication required'; end if;
    if not public.fush_can_publish_accounting(target_organization_id) then
        raise exception 'Accounting publish role required';
    end if;

    if jsonb_typeof(coalesce(journal_payloads, '[]'::jsonb)) <> 'array' then
        raise exception 'journal_payloads must be an array';
    end if;
    if jsonb_typeof(coalesce(voucher_payloads, '[]'::jsonb)) <> 'array' then
        raise exception 'voucher_payloads must be an array';
    end if;

    for p in select value from jsonb_array_elements(coalesce(journal_payloads, '[]'::jsonb))
    loop
        e_no := nullif(trim(p->>'entry_no'), '');
        if e_no is null then raise exception 'Journal entry_no required'; end if;
        if jsonb_typeof(p->'lines') <> 'array' or jsonb_array_length(p->'lines') = 0 then
            raise exception 'Journal % has no lines', e_no;
        end if;
        select coalesce(sum((x->>'debit_scaled')::numeric),0),
               coalesce(sum((x->>'credit_scaled')::numeric),0)
          into debit_total, credit_total
          from jsonb_array_elements(p->'lines') x;
        if debit_total <= 0 or debit_total <> credit_total then
            raise exception 'Journal % is not balanced', e_no;
        end if;
        if exists (
            select 1 from jsonb_array_elements(p->'lines') x
            where coalesce((x->>'debit_scaled')::numeric,0) < 0
               or coalesce((x->>'credit_scaled')::numeric,0) < 0
               or (coalesce((x->>'debit_scaled')::numeric,0) > 0 and coalesce((x->>'credit_scaled')::numeric,0) > 0)
               or nullif(trim(x->>'account_code'),'') is null
        ) then
            raise exception 'Journal % contains invalid line', e_no;
        end if;

        select content into existing
        from public.fush_tx_gl_journals
        where organization_id = target_organization_id and entry_no = e_no;

        if existing is null then
            insert into public.fush_tx_gl_journals(
                organization_id, entry_no, entry_date_ms, source_type, content, updated_by
            ) values (
                target_organization_id, e_no, (p->>'entry_date_ms')::bigint,
                coalesce(p->>'source_type','MANUAL'), p, caller
            )
            on conflict (organization_id, entry_no) do nothing;

            select content into existing
            from public.fush_tx_gl_journals
            where organization_id = target_organization_id and entry_no = e_no;
            if existing = p then
                j_inserted := j_inserted + 1;
            else
                j_conflicts := j_conflicts + 1;
            end if;
        elsif existing = p then
            j_unchanged := j_unchanged + 1;
        else
            j_conflicts := j_conflicts + 1;
        end if;

        if existing is distinct from p then
            insert into public.fush_accounting_sync_conflicts(
                organization_id, entity_type, entity_key, cloud_content, incoming_content, detected_by
            ) values (target_organization_id, 'JOURNAL', e_no, existing, p, caller)
            on conflict (organization_id, entity_type, entity_key) where status='OPEN'
            do update set incoming_content=excluded.incoming_content,
                          cloud_content=excluded.cloud_content,
                          detected_by=caller,
                          detected_at=now();
        end if;
    end loop;

    for p in select value from jsonb_array_elements(coalesce(voucher_payloads, '[]'::jsonb))
    loop
        v_no := nullif(trim(p->>'voucher_no'), '');
        if v_no is null then raise exception 'Voucher voucher_no required'; end if;
        e_no := nullif(trim(p->>'journal_entry_no'), '');
        if e_no is null then raise exception 'Voucher % has no journal_entry_no', v_no; end if;
        if not exists (
            select 1 from public.fush_tx_gl_journals
            where organization_id=target_organization_id and entry_no=e_no
        ) then
            raise exception 'Voucher % references missing journal %', v_no, e_no;
        end if;

        select content into existing
        from public.fush_tx_treasury_vouchers
        where organization_id = target_organization_id and voucher_no = v_no;

        if existing is null then
            insert into public.fush_tx_treasury_vouchers(
                organization_id, voucher_no, voucher_date_ms, voucher_type,
                treasury_code, journal_entry_no, content, updated_by
            ) values (
                target_organization_id, v_no, (p->>'voucher_date_ms')::bigint,
                coalesce(p->>'voucher_type',''), coalesce(p->>'treasury_code',''), e_no, p, caller
            )
            on conflict (organization_id, voucher_no) do nothing;

            select content into existing
            from public.fush_tx_treasury_vouchers
            where organization_id = target_organization_id and voucher_no = v_no;
            if existing = p then
                v_inserted := v_inserted + 1;
            else
                v_conflicts := v_conflicts + 1;
            end if;
        elsif existing = p then
            v_unchanged := v_unchanged + 1;
        else
            v_conflicts := v_conflicts + 1;
        end if;

        if existing is distinct from p then
            insert into public.fush_accounting_sync_conflicts(
                organization_id, entity_type, entity_key, cloud_content, incoming_content, detected_by
            ) values (target_organization_id, 'TREASURY_VOUCHER', v_no, existing, p, caller)
            on conflict (organization_id, entity_type, entity_key) where status='OPEN'
            do update set incoming_content=excluded.incoming_content,
                          cloud_content=excluded.cloud_content,
                          detected_by=caller,
                          detected_at=now();
        end if;
    end loop;

    return jsonb_build_object(
        'journal_inserted', j_inserted,
        'journal_unchanged', j_unchanged,
        'journal_conflicts', j_conflicts,
        'voucher_inserted', v_inserted,
        'voucher_unchanged', v_unchanged,
        'voucher_conflicts', v_conflicts
    );
end;
$$;
revoke all on function public.fush_publish_accounting_batch(uuid,jsonb,jsonb) from public;
grant execute on function public.fush_publish_accounting_batch(uuid,jsonb,jsonb) to authenticated;

-- Explicit conflict resolution. KEEP_CLOUD preserves cloud truth; KEEP_LOCAL replaces the cloud
-- canonical document with the reviewed local payload. No silent last-write-wins path exists.
create or replace function public.fush_resolve_accounting_conflict(
    target_organization_id uuid,
    target_entity_type text,
    target_entity_key text,
    target_resolution text,
    replacement_content jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    et text := upper(trim(target_entity_type));
    res text := upper(trim(target_resolution));
begin
    if caller is null then raise exception 'Authentication required'; end if;
    if not public.fush_can_resolve_accounting(target_organization_id) then
        raise exception 'Accounting resolution authority required';
    end if;
    if et not in ('JOURNAL','TREASURY_VOUCHER') then raise exception 'Unsupported entity type'; end if;
    if res not in ('KEEP_LOCAL','KEEP_CLOUD') then raise exception 'Unsupported resolution'; end if;

    if res = 'KEEP_LOCAL' then
        if replacement_content is null then raise exception 'replacement_content required'; end if;
        if et = 'JOURNAL' then
            if replacement_content->>'entry_no' is distinct from target_entity_key then
                raise exception 'Journal replacement key mismatch';
            end if;
            update public.fush_tx_gl_journals
               set entry_date_ms=(replacement_content->>'entry_date_ms')::bigint,
                   source_type=coalesce(replacement_content->>'source_type','MANUAL'),
                   content=replacement_content,
                   updated_at=now(), updated_by=caller
             where organization_id=target_organization_id and entry_no=target_entity_key;
            if not found then raise exception 'Cloud journal not found'; end if;
        else
            if replacement_content->>'voucher_no' is distinct from target_entity_key then
                raise exception 'Voucher replacement key mismatch';
            end if;
            update public.fush_tx_treasury_vouchers
               set voucher_date_ms=(replacement_content->>'voucher_date_ms')::bigint,
                   voucher_type=coalesce(replacement_content->>'voucher_type',''),
                   treasury_code=coalesce(replacement_content->>'treasury_code',''),
                   journal_entry_no=coalesce(replacement_content->>'journal_entry_no',''),
                   content=replacement_content,
                   updated_at=now(), updated_by=caller
             where organization_id=target_organization_id and voucher_no=target_entity_key;
            if not found then raise exception 'Cloud treasury voucher not found'; end if;
        end if;
    end if;

    update public.fush_accounting_sync_conflicts
       set status='RESOLVED', resolution=res, resolved_by=caller, resolved_at=now()
     where organization_id=target_organization_id
       and entity_type=et and entity_key=target_entity_key and status='OPEN';

    return jsonb_build_object('resolved', true, 'entity_type', et, 'entity_key', target_entity_key, 'resolution', res);
end;
$$;
revoke all on function public.fush_resolve_accounting_conflict(uuid,text,text,text,jsonb) from public;
grant execute on function public.fush_resolve_accounting_conflict(uuid,text,text,text,jsonb) to authenticated;

commit;

-- FUSH ERP Mobile v213 - Multi-Tenant RLS acceptance test template
-- Execute on a staging Supabase project as an elevated SQL operator after replacing the UUIDs.
-- The test is deliberately transactional and rolls back all fixtures.
--
-- Required placeholders:
--   11111111-1111-4111-8111-111111111111 = auth user A
--   22222222-2222-4222-8222-222222222222 = auth user B
--
-- Expected result: each user sees exactly one organization, representative cross-tenant writes are denied,
-- and a dynamic scan proves that no row belonging to the opposite organization is visible from ANY
-- public fush_* table carrying organization_id.

begin;

insert into public.fush_organizations(id, code, name)
values
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','V213_A','V213 Tenant A'),
('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','V213_B','V213 Tenant B')
on conflict (id) do nothing;

insert into public.fush_organization_members(organization_id,user_id,role,is_active)
values
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111','OWNER',true),
('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','22222222-2222-4222-8222-222222222222','OWNER',true)
on conflict (organization_id,user_id) do update set is_active=true;

insert into public.fush_md_currencies(organization_id,code,name_ar,name_en,symbol,decimals,is_base,is_active)
values
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','TSTA','Tenant A Currency','Tenant A Currency','A',2,true,true),
('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','TSTB','Tenant B Currency','Tenant B Currency','B',2,true,true)
on conflict (organization_id,code) do nothing;

-- Structural gate: every public FUSH table carrying organization_id must have RLS enabled.
do $$
declare missing text;
begin
    select string_agg(c.relname, ', ' order by c.relname) into missing
    from pg_class c
    join pg_namespace n on n.oid=c.relnamespace and n.nspname='public'
    join pg_attribute a on a.attrelid=c.oid and a.attname='organization_id' and not a.attisdropped
    where c.relkind='r' and c.relname like 'fush_%' and not c.relrowsecurity;
    if missing is not null then
        raise exception 'RLS FAIL: tenant tables without RLS: %', missing;
    end if;
end $$;

-- USER A context
set local role authenticated;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
select set_config('request.jwt.claim.role','authenticated',true);
select set_config('request.jwt.claims', json_build_object('sub','11111111-1111-4111-8111-111111111111','role','authenticated')::text, true);

do $$
declare
    n integer;
    r record;
begin
    select count(*) into n from public.fush_organizations;
    if n <> 1 then raise exception 'RLS FAIL: user A organization visibility = %', n; end if;
    select count(*) into n from public.fush_organizations where id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
    if n <> 0 then raise exception 'RLS FAIL: user A can see tenant B'; end if;

    -- Dynamic cross-read gate over every tenant-scoped FUSH table. If any Tenant B rows
    -- exist in a table, RLS must make them invisible to User A.
    for r in
        select c.relname as table_name
          from pg_class c
          join pg_namespace ns on ns.oid=c.relnamespace and ns.nspname='public'
          join pg_attribute a on a.attrelid=c.oid and a.attname='organization_id' and not a.attisdropped
         where c.relkind='r' and c.relname like 'fush_%'
         order by c.relname
    loop
        execute format('select count(*) from public.%I where organization_id = $1', r.table_name)
           into n using 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid;
        if n <> 0 then
            raise exception 'RLS FAIL: user A can read % Tenant B row(s) from %', n, r.table_name;
        end if;
    end loop;

    select count(*) into n from public.fush_md_currencies where organization_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
    if n <> 0 then raise exception 'RLS FAIL: user A can read tenant B master data'; end if;

    update public.fush_md_currencies set name_en='ILLEGAL UPDATE A'
    where organization_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' and code='TSTB';
    get diagnostics n = row_count;
    if n <> 0 then raise exception 'RLS FAIL: user A updated tenant B'; end if;

    delete from public.fush_md_currencies
    where organization_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' and code='TSTB';
    get diagnostics n = row_count;
    if n <> 0 then raise exception 'RLS FAIL: user A deleted tenant B'; end if;

    update public.fush_md_currencies set name_en='Tenant A Currency Updated'
    where organization_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' and code='TSTA';
    get diagnostics n = row_count;
    if n <> 1 then raise exception 'RLS FAIL: user A cannot update own tenant'; end if;

    begin
        insert into public.fush_md_currencies(organization_id,code,name_ar,name_en)
        values('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','ILLEGAL_A','Illegal','Illegal');
        raise exception 'RLS FAIL: user A inserted into tenant B';
    exception when insufficient_privilege then
        null;
    end;

    -- SECURITY DEFINER RPCs bypass table RLS, so test their own tenant authorization explicitly.
    begin
        perform public.fush_reserve_document_number(
            'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','V213_RLS','A-MUST-FAIL','v213-device-a'
        );
        raise exception 'RLS FAIL: user A invoked document RPC against tenant B';
    exception when others then
        if sqlerrm like 'RLS FAIL:%' then raise; end if;
    end;
    begin
        perform public.fush_publish_accounting_batch(
            'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','[]'::jsonb,'[]'::jsonb
        );
        raise exception 'RLS FAIL: user A invoked accounting RPC against tenant B';
    exception when others then
        if sqlerrm like 'RLS FAIL:%' then raise; end if;
    end;
    begin
        perform public.fush_publish_sales_aux_batch(
            'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','[]'::jsonb
        );
        raise exception 'RLS FAIL: user A invoked sales auxiliary RPC against tenant B';
    exception when others then
        if sqlerrm like 'RLS FAIL:%' then raise; end if;
    end;
    begin
        perform public.fush_publish_inventory_snapshot_cas(
            'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','NO-WH','NO-ITEM',null,'',null,-1,null,0,0,0
        );
        raise exception 'RLS FAIL: user A invoked inventory RPC against tenant B';
    exception when others then
        if sqlerrm like 'RLS FAIL:%' then raise; end if;
    end;
end $$;

reset role;

-- USER B context
set local role authenticated;
select set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
select set_config('request.jwt.claim.role','authenticated',true);
select set_config('request.jwt.claims', json_build_object('sub','22222222-2222-4222-8222-222222222222','role','authenticated')::text, true);

do $$
declare
    n integer;
    r record;
begin
    select count(*) into n from public.fush_organizations;
    if n <> 1 then raise exception 'RLS FAIL: user B organization visibility = %', n; end if;
    select count(*) into n from public.fush_organizations where id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    if n <> 0 then raise exception 'RLS FAIL: user B can see tenant A'; end if;

    -- Symmetric dynamic cross-read gate over every tenant-scoped FUSH table.
    for r in
        select c.relname as table_name
          from pg_class c
          join pg_namespace ns on ns.oid=c.relnamespace and ns.nspname='public'
          join pg_attribute a on a.attrelid=c.oid and a.attname='organization_id' and not a.attisdropped
         where c.relkind='r' and c.relname like 'fush_%'
         order by c.relname
    loop
        execute format('select count(*) from public.%I where organization_id = $1', r.table_name)
           into n using 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid;
        if n <> 0 then
            raise exception 'RLS FAIL: user B can read % Tenant A row(s) from %', n, r.table_name;
        end if;
    end loop;

    select count(*) into n from public.fush_md_currencies where organization_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    if n <> 0 then raise exception 'RLS FAIL: user B can read tenant A master data'; end if;

    update public.fush_md_currencies set name_en='ILLEGAL UPDATE B'
    where organization_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' and code='TSTA';
    get diagnostics n = row_count;
    if n <> 0 then raise exception 'RLS FAIL: user B updated tenant A'; end if;

    delete from public.fush_md_currencies
    where organization_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' and code='TSTA';
    get diagnostics n = row_count;
    if n <> 0 then raise exception 'RLS FAIL: user B deleted tenant A'; end if;

    update public.fush_md_currencies set name_en='Tenant B Currency Updated'
    where organization_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' and code='TSTB';
    get diagnostics n = row_count;
    if n <> 1 then raise exception 'RLS FAIL: user B cannot update own tenant'; end if;

    begin
        insert into public.fush_md_currencies(organization_id,code,name_ar,name_en)
        values('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','ILLEGAL_B','Illegal','Illegal');
        raise exception 'RLS FAIL: user B inserted into tenant A';
    exception when insufficient_privilege then
        null;
    end;

    begin
        perform public.fush_reserve_document_number(
            'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','V213_RLS','B-MUST-FAIL','v213-device-b'
        );
        raise exception 'RLS FAIL: user B invoked document RPC against tenant A';
    exception when others then
        if sqlerrm like 'RLS FAIL:%' then raise; end if;
    end;
end $$;

reset role;
rollback;

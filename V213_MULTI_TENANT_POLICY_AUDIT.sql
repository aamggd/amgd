-- FUSH ERP Mobile v213 - Structural tenant policy audit
-- Run on staging/production Supabase after the v213 migration.
-- PASS means every tenant table has RLS enabled and no authenticated/anon policy is an unconditional TRUE bypass.

do $$
declare
    missing_rls text;
    unsafe_policy text;
    anon_definer text;
    mutable_definer_path text;
    unguarded_org_rpc text;
begin
    select string_agg(format('%I.%I', n.nspname, c.relname), ', ' order by c.relname)
      into missing_rls
      from pg_class c
      join pg_namespace n on n.oid = c.relnamespace
      join pg_attribute a on a.attrelid = c.oid
                           and a.attname = 'organization_id'
                           and not a.attisdropped
     where n.nspname = 'public'
       and c.relkind = 'r'
       and c.relname like 'fush_%'
       and not c.relrowsecurity;

    if missing_rls is not null then
        raise exception 'TENANT AUDIT FAIL - RLS disabled on: %', missing_rls;
    end if;

    select string_agg(format('%I.%I:%I', schemaname, tablename, policyname), ', ' order by tablename, policyname)
      into unsafe_policy
      from pg_policies p
     where p.schemaname = 'public'
       and p.tablename like 'fush_%'
       and exists (
           select 1
             from information_schema.columns c
            where c.table_schema = p.schemaname
              and c.table_name = p.tablename
              and c.column_name = 'organization_id'
       )
       and ('authenticated' = any(p.roles) or 'anon' = any(p.roles) or 'public' = any(p.roles))
       and (
           lower(coalesce(p.qual, '')) in ('true', '(true)')
           or lower(coalesce(p.with_check, '')) in ('true', '(true)')
       );

    if unsafe_policy is not null then
        raise exception 'TENANT AUDIT FAIL - unconditional tenant policy: %', unsafe_policy;
    end if;

    -- SECURITY DEFINER functions bypass RLS. No FUSH privileged function may be callable by anon.
    select string_agg(format('%I.%I(%s)', n.nspname, p.proname, pg_get_function_identity_arguments(p.oid)), ', ' order by p.proname)
      into anon_definer
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.prosecdef
       and (p.proname like 'fush_%' or p.proname = 'rls_auto_enable')
       and has_function_privilege('anon', p.oid, 'EXECUTE');
    if anon_definer is not null then
        raise exception 'TENANT AUDIT FAIL - anon can execute SECURITY DEFINER: %', anon_definer;
    end if;

    -- Every FUSH SECURITY DEFINER must pin search_path to prevent object-shadowing attacks.
    select string_agg(format('%I.%I(%s)', n.nspname, p.proname, pg_get_function_identity_arguments(p.oid)), ', ' order by p.proname)
      into mutable_definer_path
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.prosecdef
       and p.proname like 'fush_%'
       and not exists (
           select 1 from unnest(coalesce(p.proconfig, array[]::text[])) cfg
            where cfg like 'search_path=%'
       );
    if mutable_definer_path is not null then
        raise exception 'TENANT AUDIT FAIL - SECURITY DEFINER without pinned search_path: %', mutable_definer_path;
    end if;

    -- Any privileged RPC that accepts target_organization_id must visibly perform a caller/tenant
    -- authorization check inside the function body, because SECURITY DEFINER bypasses table RLS.
    select string_agg(format('%I.%I(%s)', n.nspname, p.proname, pg_get_function_identity_arguments(p.oid)), ', ' order by p.proname)
      into unguarded_org_rpc
      from pg_proc p
      join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public'
       and p.prosecdef
       and p.proname like 'fush_%'
       and pg_get_function_identity_arguments(p.oid) ilike '%target_organization_id%'
       and not (
           pg_get_functiondef(p.oid) ilike '%fush_is_org_member%'
           or pg_get_functiondef(p.oid) ilike '%fush_has_org_role%'
           or pg_get_functiondef(p.oid) ilike '%fush_is_org_owner%'
           or pg_get_functiondef(p.oid) ilike '%fush_can_publish%'
           or pg_get_functiondef(p.oid) ilike '%fush_can_resolve%'
           or (pg_get_functiondef(p.oid) ilike '%fush_organization_members%' and pg_get_functiondef(p.oid) ilike '%auth.uid()%')
       );
    if unguarded_org_rpc is not null then
        raise exception 'TENANT AUDIT FAIL - privileged organization RPC lacks visible tenant guard: %', unguarded_org_rpc;
    end if;
end $$;

-- Human-readable inventory for release evidence.
select
    c.relname as tenant_table,
    c.relrowsecurity as rls_enabled,
    count(p.policyname) as policy_count,
    string_agg(distinct p.cmd, ', ' order by p.cmd) as policy_commands
from pg_class c
join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
join pg_attribute a on a.attrelid = c.oid and a.attname = 'organization_id' and not a.attisdropped
left join pg_policies p on p.schemaname = n.nspname and p.tablename = c.relname
where c.relkind = 'r' and c.relname like 'fush_%'
group by c.relname, c.relrowsecurity
order by c.relname;

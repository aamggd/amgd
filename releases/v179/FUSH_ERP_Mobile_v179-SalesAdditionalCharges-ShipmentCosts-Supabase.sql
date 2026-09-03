-- FUSH ERP Mobile v179
-- Conflict-safe bidirectional mirror for AdditionalCharges + Shipment Tracking.
-- Apply ONCE after v177 accounting cloud SQL. Download hydration never replays GL/Treasury.

begin;

create table if not exists public.fush_tx_sales_aux_documents (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    entity_type text not null,
    entity_key text not null,
    content jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, entity_type, entity_key),
    constraint fush_sales_aux_entity_type_ck check (entity_type in (
      'CHARGE_TYPE','ADDITIONAL_CHARGE','ADDITIONAL_CHARGE_PAYMENT','ADDITIONAL_CHARGE_SETTLEMENT',
      'SHIPMENT','SHIPMENT_ITEM','SHIPMENT_EXPENSE','SHIPMENT_ITEM_ALLOCATION','SHIPMENT_EXPENSE_ALLOCATION'
    ))
);

create index if not exists idx_fush_sales_aux_type
on public.fush_tx_sales_aux_documents(organization_id, entity_type);

create table if not exists public.fush_sales_aux_sync_conflicts (
    id bigserial primary key,
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    entity_type text not null,
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

create unique index if not exists uq_fush_sales_aux_open_conflict
on public.fush_sales_aux_sync_conflicts(organization_id, entity_type, entity_key)
where status='OPEN';

alter table public.fush_tx_sales_aux_documents enable row level security;
alter table public.fush_sales_aux_sync_conflicts enable row level security;

revoke all on public.fush_tx_sales_aux_documents from anon, authenticated;
revoke all on public.fush_sales_aux_sync_conflicts from anon, authenticated;
grant select on public.fush_tx_sales_aux_documents to authenticated;
grant select on public.fush_sales_aux_sync_conflicts to authenticated;

drop policy if exists fush_sales_aux_docs_read_member on public.fush_tx_sales_aux_documents;
create policy fush_sales_aux_docs_read_member
on public.fush_tx_sales_aux_documents for select to authenticated
using (public.fush_is_org_member(organization_id));

drop policy if exists fush_sales_aux_conflicts_read_member on public.fush_sales_aux_sync_conflicts;
create policy fush_sales_aux_conflicts_read_member
on public.fush_sales_aux_sync_conflicts for select to authenticated
using (public.fush_is_org_member(organization_id));

create or replace function public.fush_can_publish_sales_aux(target_organization_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$
  select exists (
    select 1 from public.fush_organization_members m
    where m.organization_id=target_organization_id
      and m.user_id=auth.uid() and m.is_active=true
      and upper(m.role) in ('OWNER','ADMIN','ACCOUNTANT','SALES','INVENTORY','CASHIER')
  );
$$;
revoke all on function public.fush_can_publish_sales_aux(uuid) from public;
grant execute on function public.fush_can_publish_sales_aux(uuid) to authenticated;

create or replace function public.fush_can_resolve_sales_aux(target_organization_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$
  select exists (
    select 1 from public.fush_organization_members m
    where m.organization_id=target_organization_id
      and m.user_id=auth.uid() and m.is_active=true
      and upper(m.role) in ('OWNER','ADMIN','ACCOUNTANT')
  );
$$;
revoke all on function public.fush_can_resolve_sales_aux(uuid) from public;
grant execute on function public.fush_can_resolve_sales_aux(uuid) to authenticated;

create or replace function public.fush_publish_sales_aux_batch(
    target_organization_id uuid,
    documents jsonb
)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare
  caller uuid := auth.uid();
  p jsonb;
  et text;
  ek text;
  incoming jsonb;
  existing jsonb;
  inserted_count int := 0;
  unchanged_count int := 0;
  conflict_count int := 0;
begin
  if caller is null then raise exception 'Authentication required'; end if;
  if not public.fush_can_publish_sales_aux(target_organization_id) then
    raise exception 'Sales auxiliary publish role required';
  end if;
  if jsonb_typeof(coalesce(documents,'[]'::jsonb)) <> 'array' then
    raise exception 'documents must be an array';
  end if;

  for p in select value from jsonb_array_elements(coalesce(documents,'[]'::jsonb)) loop
    et := upper(trim(p->>'entity_type'));
    ek := nullif(trim(p->>'entity_key'),'');
    incoming := p->'content';
    if et not in ('CHARGE_TYPE','ADDITIONAL_CHARGE','ADDITIONAL_CHARGE_PAYMENT','ADDITIONAL_CHARGE_SETTLEMENT',
                  'SHIPMENT','SHIPMENT_ITEM','SHIPMENT_EXPENSE','SHIPMENT_ITEM_ALLOCATION','SHIPMENT_EXPENSE_ALLOCATION') then
      raise exception 'Unsupported entity type: %', et;
    end if;
    if ek is null or incoming is null or jsonb_typeof(incoming) <> 'object' then
      raise exception 'Invalid auxiliary document';
    end if;

    select d.content into existing
      from public.fush_tx_sales_aux_documents d
      where d.organization_id=target_organization_id and d.entity_type=et and d.entity_key=ek;

    if existing is null then
      insert into public.fush_tx_sales_aux_documents(organization_id,entity_type,entity_key,content,updated_by)
      values(target_organization_id,et,ek,incoming,caller)
      on conflict (organization_id,entity_type,entity_key) do nothing;
      select d.content into existing
        from public.fush_tx_sales_aux_documents d
        where d.organization_id=target_organization_id and d.entity_type=et and d.entity_key=ek;
      if existing=incoming then inserted_count:=inserted_count+1;
      else conflict_count:=conflict_count+1; end if;
    elsif existing=incoming then
      unchanged_count:=unchanged_count+1;
    else
      conflict_count:=conflict_count+1;
    end if;

    if existing is distinct from incoming then
      insert into public.fush_sales_aux_sync_conflicts(
        organization_id,entity_type,entity_key,cloud_content,incoming_content,detected_by
      ) values(target_organization_id,et,ek,existing,incoming,caller)
      on conflict (organization_id,entity_type,entity_key) where status='OPEN'
      do update set cloud_content=excluded.cloud_content,
                    incoming_content=excluded.incoming_content,
                    detected_by=caller,
                    detected_at=now();
    end if;
  end loop;

  return jsonb_build_object('inserted',inserted_count,'unchanged',unchanged_count,'conflicts',conflict_count);
end;
$$;
revoke all on function public.fush_publish_sales_aux_batch(uuid,jsonb) from public;
grant execute on function public.fush_publish_sales_aux_batch(uuid,jsonb) to authenticated;

create or replace function public.fush_resolve_sales_aux_conflict(
    target_organization_id uuid,
    target_entity_type text,
    target_entity_key text,
    target_resolution text,
    replacement_content jsonb default null
)
returns jsonb language plpgsql security definer set search_path=''
as $$
declare
  caller uuid := auth.uid();
  et text := upper(trim(target_entity_type));
  res text := upper(trim(target_resolution));
begin
  if caller is null then raise exception 'Authentication required'; end if;
  if not public.fush_can_resolve_sales_aux(target_organization_id) then
    raise exception 'Sales auxiliary conflict authority required';
  end if;
  if res not in ('KEEP_LOCAL','KEEP_CLOUD') then raise exception 'Unsupported resolution'; end if;

  if res='KEEP_LOCAL' then
    if replacement_content is null or jsonb_typeof(replacement_content)<>'object' then
      raise exception 'replacement_content required';
    end if;
    update public.fush_tx_sales_aux_documents
      set content=replacement_content, updated_at=now(), updated_by=caller
      where organization_id=target_organization_id and entity_type=et and entity_key=target_entity_key;
    if not found then raise exception 'Cloud auxiliary document not found'; end if;
  end if;

  update public.fush_sales_aux_sync_conflicts
    set status='RESOLVED',resolution=res,resolved_by=caller,resolved_at=now()
    where organization_id=target_organization_id and entity_type=et and entity_key=target_entity_key and status='OPEN';

  return jsonb_build_object('resolved',true,'entity_type',et,'entity_key',target_entity_key,'resolution',res);
end;
$$;
revoke all on function public.fush_resolve_sales_aux_conflict(uuid,text,text,text,jsonb) from public;
grant execute on function public.fush_resolve_sales_aux_conflict(uuid,text,text,text,jsonb) to authenticated;

commit;

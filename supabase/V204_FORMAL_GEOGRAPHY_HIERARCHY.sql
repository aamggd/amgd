-- FUSH ERP Mobile v204 — Formal Yemen Geography Hierarchy
-- Non-destructive cloud schema upgrade. Legacy province text remains intact.

create table if not exists public.fush_md_governorates (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    geo_id text not null,
    name_ar text not null,
    name_en text not null default '',
    sort_order integer not null default 0,
    source text not null default 'USER',
    is_official_seed boolean not null default false,
    is_active boolean not null default true,
    updated_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code),
    unique (organization_id, geo_id)
);

create table if not exists public.fush_md_districts (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    geo_id text not null,
    governorate_code text not null,
    name_ar text not null,
    name_en text not null default '',
    sort_order integer not null default 0,
    source text not null default 'USER',
    is_official_seed boolean not null default false,
    is_active boolean not null default true,
    updated_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code),
    unique (organization_id, geo_id),
    constraint fush_md_districts_governorate_fk
        foreign key (organization_id, governorate_code)
        references public.fush_md_governorates(organization_id, geo_id)
        on update cascade on delete restrict
);

create table if not exists public.fush_md_areas (
    organization_id uuid not null references public.fush_organizations(id) on delete cascade,
    code text not null,
    geo_id text not null,
    district_code text not null,
    name_ar text not null,
    name_en text not null default '',
    sort_order integer not null default 0,
    source text not null default 'USER',
    is_official_seed boolean not null default false,
    is_active boolean not null default true,
    updated_at_ms bigint not null default 0,
    updated_at timestamptz not null default now(),
    updated_by uuid references auth.users(id),
    primary key (organization_id, code),
    unique (organization_id, geo_id),
    constraint fush_md_areas_district_fk
        foreign key (organization_id, district_code)
        references public.fush_md_districts(organization_id, geo_id)
        on update cascade on delete restrict
);

create index if not exists idx_fush_md_districts_parent on public.fush_md_districts(organization_id, governorate_code);
create index if not exists idx_fush_md_areas_parent on public.fush_md_areas(organization_id, district_code);
create index if not exists idx_fush_md_governorates_active on public.fush_md_governorates(organization_id, is_active, sort_order);
create index if not exists idx_fush_md_districts_active on public.fush_md_districts(organization_id, is_active, sort_order);
create index if not exists idx_fush_md_areas_active on public.fush_md_areas(organization_id, is_active, sort_order);

alter table public.fush_md_governorates enable row level security;
alter table public.fush_md_districts enable row level security;
alter table public.fush_md_areas enable row level security;

drop policy if exists fush_md_governorates_read_member on public.fush_md_governorates;
create policy fush_md_governorates_read_member on public.fush_md_governorates for select using (public.fush_is_org_member(organization_id));
drop policy if exists fush_md_governorates_insert_member on public.fush_md_governorates;
create policy fush_md_governorates_insert_member on public.fush_md_governorates for insert with check (public.fush_is_org_member(organization_id));
drop policy if exists fush_md_governorates_update_member on public.fush_md_governorates;
create policy fush_md_governorates_update_member on public.fush_md_governorates for update using (public.fush_is_org_member(organization_id)) with check (public.fush_is_org_member(organization_id));

drop policy if exists fush_md_districts_read_member on public.fush_md_districts;
create policy fush_md_districts_read_member on public.fush_md_districts for select using (public.fush_is_org_member(organization_id));
drop policy if exists fush_md_districts_insert_member on public.fush_md_districts;
create policy fush_md_districts_insert_member on public.fush_md_districts for insert with check (public.fush_is_org_member(organization_id));
drop policy if exists fush_md_districts_update_member on public.fush_md_districts;
create policy fush_md_districts_update_member on public.fush_md_districts for update using (public.fush_is_org_member(organization_id)) with check (public.fush_is_org_member(organization_id));

drop policy if exists fush_md_areas_read_member on public.fush_md_areas;
create policy fush_md_areas_read_member on public.fush_md_areas for select using (public.fush_is_org_member(organization_id));
drop policy if exists fush_md_areas_insert_member on public.fush_md_areas;
create policy fush_md_areas_insert_member on public.fush_md_areas for insert with check (public.fush_is_org_member(organization_id));
drop policy if exists fush_md_areas_update_member on public.fush_md_areas;
create policy fush_md_areas_update_member on public.fush_md_areas for update using (public.fush_is_org_member(organization_id)) with check (public.fush_is_org_member(organization_id));

drop trigger if exists trg_fush_md_governorates_touch on public.fush_md_governorates;
create trigger trg_fush_md_governorates_touch before insert or update on public.fush_md_governorates for each row execute function public.fush_touch_master_data_row();
drop trigger if exists trg_fush_md_districts_touch on public.fush_md_districts;
create trigger trg_fush_md_districts_touch before insert or update on public.fush_md_districts for each row execute function public.fush_touch_master_data_row();
drop trigger if exists trg_fush_md_areas_touch on public.fush_md_areas;
create trigger trg_fush_md_areas_touch before insert or update on public.fush_md_areas for each row execute function public.fush_touch_master_data_row();

-- Seed the 22 official governorates for every current organization so legacy backfill can be FK-safe.
insert into public.fush_md_governorates
(organization_id, code, geo_id, name_ar, name_en, sort_order, source, is_official_seed, is_active, updated_at_ms, updated_by)
select o.id, v.code, v.code, v.name_ar, v.name_en, v.sort_order, 'OPEN_ADMIN_DATA', true, true, 0, null
from public.fush_organizations o
cross join (values
 ('YE11','إب','Ibb',1),('YE12','أبين','Abyan',2),('YE13','أمانة العاصمة','Sana''a City',3),('YE14','البيضاء','Al Bayda',4),
 ('YE15','تعز','Ta''iz',5),('YE16','الجوف','Al Jawf',6),('YE17','حجة','Hajjah',7),('YE18','الحديدة','Al Hodeidah',8),
 ('YE19','حضرموت','Hadramawt',9),('YE20','ذمار','Dhamar',10),('YE21','شبوة','Shabwah',11),('YE22','صعدة','Sa''dah',12),
 ('YE23','صنعاء','Sana''a',13),('YE24','عدن','Aden',14),('YE25','لحج','Lahj',15),('YE26','مأرب','Ma''rib',16),
 ('YE27','المحويت','Al Mahwit',17),('YE28','المهرة','Al Maharah',18),('YE29','عمران','Amran',19),('YE30','الضالع','Ad Dali''',20),
 ('YE31','ريمة','Raymah',21),('YE32','سقطرى','Socotra',22)
) as v(code,name_ar,name_en,sort_order)
on conflict (organization_id, code) do nothing;

alter table public.fush_md_customers add column if not exists governorate_id text;
alter table public.fush_md_customers add column if not exists district_id text;
alter table public.fush_md_customers add column if not exists area_id text;

alter table public.fush_tx_sales_invoices add column if not exists governorate_id text;
alter table public.fush_tx_sales_invoices add column if not exists district_id text;
alter table public.fush_tx_sales_invoices add column if not exists area_id text;

create index if not exists idx_fush_md_customers_governorate on public.fush_md_customers(organization_id, governorate_id);
create index if not exists idx_fush_md_customers_district on public.fush_md_customers(organization_id, district_id);
create index if not exists idx_fush_md_customers_area on public.fush_md_customers(organization_id, area_id);
create index if not exists idx_fush_tx_sales_invoices_governorate on public.fush_tx_sales_invoices(organization_id, governorate_id);
create index if not exists idx_fush_tx_sales_invoices_district on public.fush_tx_sales_invoices(organization_id, district_id);
create index if not exists idx_fush_tx_sales_invoices_area on public.fush_tx_sales_invoices(organization_id, area_id);

-- Existing FUSH text is preserved; only the formal governorate key is backfilled.
update public.fush_md_customers
set governorate_id = case
    when trim(province) ~ '^تعز' or lower(trim(province)) in ('tauz','taiz','ta''iz','ta''izz') or trim(province)='الحوبان' then 'YE15'
    when trim(province) in ('إب','اب') then 'YE11'
    when trim(province)='عدن' then 'YE24'
    when trim(province)='صنعاء' then 'YE23'
    when trim(province)='أمانة العاصمة' then 'YE13'
    when trim(province)='أبين' then 'YE12'
    when trim(province)='البيضاء' then 'YE14'
    when trim(province)='الجوف' then 'YE16'
    when trim(province)='حجة' then 'YE17'
    when trim(province)='الحديدة' then 'YE18'
    when trim(province)='حضرموت' then 'YE19'
    when trim(province)='ذمار' then 'YE20'
    when trim(province)='شبوة' then 'YE21'
    when trim(province)='صعدة' then 'YE22'
    when trim(province)='لحج' then 'YE25'
    when trim(province)='مأرب' then 'YE26'
    when trim(province)='المحويت' then 'YE27'
    when trim(province)='المهرة' then 'YE28'
    when trim(province)='عمران' then 'YE29'
    when trim(province)='الضالع' then 'YE30'
    when trim(province)='ريمة' then 'YE31'
    when trim(province)='سقطرى' then 'YE32'
    else governorate_id end
where governorate_id is null and trim(coalesce(province,''))<>'';

-- Prefer the customer formal location for existing invoices, then fall back to invoice legacy text.
update public.fush_tx_sales_invoices i
set governorate_id = c.governorate_id,
    district_id = c.district_id,
    area_id = c.area_id
from public.fush_md_customers c
where i.organization_id=c.organization_id and i.customer_code=c.code and i.governorate_id is null and c.governorate_id is not null;

update public.fush_tx_sales_invoices
set governorate_id = case
    when trim(province) ~ '^تعز' or lower(trim(province)) in ('tauz','taiz','ta''iz','ta''izz') or trim(province)='الحوبان' then 'YE15'
    when trim(province) in ('إب','اب') then 'YE11'
    when trim(province)='عدن' then 'YE24'
    when trim(province)='صنعاء' then 'YE23'
    when trim(province)='أمانة العاصمة' then 'YE13'
    else governorate_id end
where governorate_id is null and trim(coalesce(province,''))<>'';

-- Add formal FK constraints only after safe backfill. District/area legacy values remain null until explicitly selected.
do $$ begin
    if not exists (select 1 from pg_constraint where conname='fush_md_customers_governorate_fk') then
        alter table public.fush_md_customers add constraint fush_md_customers_governorate_fk
            foreign key (organization_id, governorate_id) references public.fush_md_governorates(organization_id, geo_id) on update cascade on delete restrict;
    end if;
    if not exists (select 1 from pg_constraint where conname='fush_md_customers_district_fk') then
        alter table public.fush_md_customers add constraint fush_md_customers_district_fk
            foreign key (organization_id, district_id) references public.fush_md_districts(organization_id, geo_id) on update cascade on delete restrict;
    end if;
    if not exists (select 1 from pg_constraint where conname='fush_md_customers_area_fk') then
        alter table public.fush_md_customers add constraint fush_md_customers_area_fk
            foreign key (organization_id, area_id) references public.fush_md_areas(organization_id, geo_id) on update cascade on delete restrict;
    end if;
    if not exists (select 1 from pg_constraint where conname='fush_tx_sales_invoices_governorate_fk') then
        alter table public.fush_tx_sales_invoices add constraint fush_tx_sales_invoices_governorate_fk
            foreign key (organization_id, governorate_id) references public.fush_md_governorates(organization_id, geo_id) on update cascade on delete restrict;
    end if;
    if not exists (select 1 from pg_constraint where conname='fush_tx_sales_invoices_district_fk') then
        alter table public.fush_tx_sales_invoices add constraint fush_tx_sales_invoices_district_fk
            foreign key (organization_id, district_id) references public.fush_md_districts(organization_id, geo_id) on update cascade on delete restrict;
    end if;
    if not exists (select 1 from pg_constraint where conname='fush_tx_sales_invoices_area_fk') then
        alter table public.fush_tx_sales_invoices add constraint fush_tx_sales_invoices_area_fk
            foreign key (organization_id, area_id) references public.fush_md_areas(organization_id, geo_id) on update cascade on delete restrict;
    end if;
end $$;

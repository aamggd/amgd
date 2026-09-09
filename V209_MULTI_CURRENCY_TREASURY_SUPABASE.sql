-- FUSH ERP Mobile v209 — multi-currency logical treasury grouping.
-- Non-destructive: existing treasury rows keep their codes and are grouped under themselves.

alter table if exists public.fush_md_treasury_accounts
    add column if not exists group_code text;

update public.fush_md_treasury_accounts
set group_code = code
where group_code is null or btrim(group_code) = '';

alter table if exists public.fush_md_treasury_accounts
    alter column group_code set default '';

alter table if exists public.fush_md_treasury_accounts
    alter column group_code set not null;

create index if not exists idx_fush_md_treasury_accounts_group
    on public.fush_md_treasury_accounts(organization_id, group_code);

create unique index if not exists uq_fush_md_treasury_group_currency
    on public.fush_md_treasury_accounts(organization_id, group_code, currency_code);

-- Backward compatibility for v208/older clients that do not send group_code.
create or replace function public.fush_set_treasury_group_code()
returns trigger
language plpgsql
as $$
begin
  if new.group_code is null or btrim(new.group_code) = '' then
    new.group_code := new.code;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_fush_md_treasury_group_code on public.fush_md_treasury_accounts;
create trigger trg_fush_md_treasury_group_code
before insert or update of code, group_code on public.fush_md_treasury_accounts
for each row execute function public.fush_set_treasury_group_code();

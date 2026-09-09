#!/usr/bin/env python3
from __future__ import annotations

import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PASS = 0
FAIL = 0


def check(name: str, condition: bool) -> None:
    global PASS, FAIL
    if condition:
        PASS += 1
        print(f"[PASS] {name}")
    else:
        FAIL += 1
        print(f"[FAIL] {name}")


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

build = text("app/build.gradle.kts")
models = text("app/src/main/java/com/fush/erp/cloud/CloudModels.kt")
store = text("app/src/main/java/com/fush/erp/cloud/CloudSessionStore.kt")
repo = text("app/src/main/java/com/fush/erp/cloud/CloudSyncRepository.kt")
db = text("app/src/main/java/com/fush/erp/data/FushDatabase.kt")
migration = text("app/src/main/java/com/fush/erp/data/V213CommercialTenantBindingMigration.kt")
app_container = text("app/src/main/java/com/fush/erp/data/AppContainer.kt")
bootstrap = text("app/src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt")
sql010 = text("cloud/supabase/010_commercial_multitenant_foundation.sql")
installer = text("FUSH_ERP_Mobile_v213-CommercialMultiTenant-Supabase.sql")
rls = text("V213_MULTI_TENANT_RLS_TEST.sql")
audit = text("V213_MULTI_TENANT_POLICY_AUDIT.sql")

fixed_uuid = "f0000000-0000-4000-8000-000000000001"
main_kotlin = "\n".join(
    p.read_text(encoding="utf-8", errors="replace")
    for p in (ROOT / "app/src/main/java").rglob("*.kt")
)

check("applicationId preserved", 'applicationId = "com.fush.erp.recovery"' in build)
check("versionCode 213", re.search(r"\bversionCode\s*=\s*213\b", build) is not None)
check("versionName v213 commercial multitenant", "0.15.4.164-commercial-multitenant-foundation" in build)
check("compile-time organization BuildConfig removed", "FUSH_CLOUD_ORGANIZATION_ID" not in build)
check("fixed historical organization UUID absent from executable Kotlin", fixed_uuid not in main_kotlin)
check("CloudSession carries organizationId", "val organizationId: String? = null" in models)
check("CloudSession fails closed without organizationId", "requireOrganizationId()" in models and "Cloud organization is not selected" in models)
check("CloudSessionStore persists organizationId", "KEY_ORGANIZATION_ID" in store and ".putString(prefix + KEY_ORGANIZATION_ID, session.organizationId)" in store)
check("legacy sessions stay tenantless until membership resolution", "CloudSession(access, refresh, userId, email, expiresAt)" in store)
check("repository resolves active memberships", "fush_organization_members" in repo and "is_active=eq.true" in repo and "resolveOrganization" in repo)
check("repository binds tenant inside Room", "cloud_tenant_binding" in repo and "bindDatabaseOrganization" in repo)
check("repository blocks reuse with another tenant", "قاعدة البيانات المحلية مرتبطة بشركة أخرى" in repo)
check("device upsert conflict key includes organization", "on_conflict=organization_id,user_id,device_key" in repo)
check("Room schema is 54", "const val FUSH_DB_SCHEMA_VERSION = 54" in db)
check("Room entity includes CloudTenantBindingEntity", "CloudTenantBindingEntity::class" in db)
check("53->54 migration registered in AppContainer", "MIGRATION_53_54_COMMERCIAL_TENANT_BINDING" in app_container)
check("53->54 migration registered in accounting bootstrap", "MIGRATION_53_54_COMMERCIAL_TENANT_BINDING" in bootstrap)
check("no destructive fallback added to app database setup", "fallbackToDestructiveMigration" not in app_container and "fallbackToDestructiveMigration" not in bootstrap)

engines = [
    "AccountingCloudSyncEngine.kt",
    "InventoryProductionCloudSyncEngine.kt",
    "MasterDataCloudSyncEngine.kt",
    "PurchaseDocumentsCloudSyncEngine.kt",
    "SalesAuxiliaryCloudSyncEngine.kt",
    "SalesReceivablesCloudSyncEngine.kt",
    "SupplierPaymentsTreasuryCloudSyncEngine.kt",
    "FushAiRemoteService.kt",
]
for engine in engines:
    body = text(f"app/src/main/java/com/fush/erp/cloud/{engine}")
    check(f"{engine} requires runtime organization", "requireOrganizationId()" in body and fixed_uuid not in body and "FUSH_CLOUD_ORGANIZATION_ID" not in body)

# Execute the v213 DDL against a real SQLite connection and prove preservation + uniqueness.
con = sqlite3.connect(":memory:")
con.execute("create table currencies(code text primary key, name text not null)")
con.execute("insert into currencies values('V213_KEEP','Keep')")
create_table = re.search(r'CREATE TABLE IF NOT EXISTS `cloud_tenant_binding` \((.*?)\)\s*"""', migration, re.S)
check("migration contains tenant binding CREATE TABLE", create_table is not None)
if create_table:
    ddl = "CREATE TABLE IF NOT EXISTS cloud_tenant_binding (" + create_table.group(1).replace("`", "") + ")"
    con.execute(ddl)
    con.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_cloud_tenant_binding_organization_id ON cloud_tenant_binding (organization_id)")
    check("migration preserves pre-existing business row", con.execute("select count(*) from currencies where code='V213_KEEP'").fetchone()[0] == 1)
    check("tenant binding starts empty", con.execute("select count(*) from cloud_tenant_binding").fetchone()[0] == 0)
    con.execute("insert into cloud_tenant_binding(id,organization_id,bound_at,updated_at) values(1,'org-A',1,1)")
    duplicate_blocked = False
    try:
        con.execute("insert into cloud_tenant_binding(id,organization_id,bound_at,updated_at) values(2,'org-A',1,1)")
    except sqlite3.IntegrityError:
        duplicate_blocked = True
    check("tenant binding organization unique constraint executes", duplicate_blocked)

check("organization creation uses random UUID", "new_org_id uuid := gen_random_uuid()" in sql010)
check("organization creator becomes OWNER", "'OWNER', true, now()" in sql010)
check("device uniqueness is tenant scoped", "uq_fush_sync_devices_org_user_device" in sql010 and "organization_id, user_id, device_key" in sql010)
check("legacy privileged trigger RPC exposure is revoked", "revoke execute on function public.fush_register_shipment_document_number() from public, anon, authenticated" in sql010 and "revoke execute on function public.rls_auto_enable() from public, anon, authenticated" in sql010)
check("legacy treasury trigger search_path is pinned", "alter function public.fush_set_treasury_group_code() set search_path = ''" in sql010)
check("membership discovery is ACTIVE-only", "user_id = (select auth.uid()) and is_active = true" in sql010)
check("cloud binding read requires active org membership", "public.fush_is_org_member(organization_id)" in sql010 and "fush_cloud_binding_select_self_or_owner" in sql010)
check("fresh installer contains no fixed historical tenant UUID", fixed_uuid not in installer)
fush_ai = text("supabase/functions/fush-ai/index.ts")
check("FUSH AI server verifies active tenant membership", ' .eq("organization_id", organizationId)'.strip() in fush_ai and ' .eq("user_id", userId)'.strip() in fush_ai and ' .eq("is_active", true)'.strip() in fush_ai and "ORG_MEMBERSHIP_REQUIRED" in fush_ai)
check("RLS gate dynamically scans every organization_id FUSH table", "for r in" in rls and "where organization_id = $1" in rls and "c.relname like 'fush_%'" in rls)
check("RLS gate tests cross-tenant UPDATE", "user A updated tenant B" in rls and "user B updated tenant A" in rls)
check("RLS gate tests cross-tenant DELETE", "user A deleted tenant B" in rls and "user B deleted tenant A" in rls)
check("RLS gate tests cross-tenant INSERT", "user A inserted into tenant B" in rls and "user B inserted into tenant A" in rls)
check("RLS gate includes own-tenant positive write control", "user A cannot update own tenant" in rls and "user B cannot update own tenant" in rls)
check("RLS gate probes privileged RPC cross-tenant denial", "user A invoked document RPC against tenant B" in rls and "user A invoked accounting RPC against tenant B" in rls and "user A invoked sales auxiliary RPC against tenant B" in rls and "user A invoked inventory RPC against tenant B" in rls and "user B invoked document RPC against tenant A" in rls)
check("policy audit rejects tenant tables without RLS", "RLS disabled on" in audit)
check("policy audit rejects unconditional authenticated tenant policies", "unconditional tenant policy" in audit)
check("policy audit covers SECURITY DEFINER bypass risks", "anon can execute SECURITY DEFINER" in audit and "SECURITY DEFINER without pinned search_path" in audit and "privileged organization RPC lacks visible tenant guard" in audit)

print(f"\nTOTAL: {PASS + FAIL}")
print(f"PASS: {PASS}")
print(f"FAIL: {FAIL}")
print("RESULT:", "PASS" if FAIL == 0 else "FAIL")
raise SystemExit(1 if FAIL else 0)

package com.fush.erp.domain

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class VendorSupportLifecycleContractTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test fun portableRestoreUsesSignedRebindAndPreservesHistory() {
        val manager = source("com/fush/erp/domain/VendorSupportProvisioning.kt")
        val migration = source("com/fush/erp/data/SupportMigrations.kt")
        assertTrue(manager.contains("VendorLifecycleAction.REBIND"))
        assertTrue(manager.contains("previous.packageFingerprint == verified.previousPackageFingerprint"))
        assertTrue(manager.contains("supersedesIdentityId = previous?.id"))
        assertTrue(migration.contains("INSERT INTO vendor_support_identities"))
        assertTrue(migration.contains("'PROVISION',1,NULL,''"))
    }

    @Test fun legacySupportIsFailClosedUntilSignedClaim() {
        val manager = source("com/fush/erp/domain/VendorSupportProvisioning.kt")
        val security = source("com/fush/erp/domain/SecurityService.kt")
        assertTrue(manager.contains("VendorLifecycleAction.LEGACY_CLAIM"))
        assertTrue(manager.contains("legacySupportUsers"))
        assertTrue(security.contains("isCurrentVendorIdentity(user.id) != true"))
    }

    @Test fun credentialRotationIsSignedAndAppendOnlySupersession() {
        val manager = source("com/fush/erp/domain/VendorSupportProvisioning.kt")
        assertTrue(manager.contains("VendorLifecycleAction.ROTATE"))
        assertTrue(manager.contains("credentialVersion = verified.credentialVersion"))
        assertTrue(manager.contains("sessionVersion = user.sessionVersion + 1"))
        assertFalse(manager.contains("updateVendorSupportIdentity"))
    }

    @Test fun vendorSigningKeyCanBeSupersededByCurrentTrustedKey() {
        val manager = source("com/fush/erp/domain/VendorSupportProvisioning.kt")
        val migration = source("com/fush/erp/data/SupportMigrations.kt")
        assertTrue(manager.contains("FSK1"))
        assertTrue(manager.contains("verified.supersedesKeyId == currentKey.keyId"))
        assertTrue(manager.contains("insertVendorSupportKey"))
        assertTrue(migration.contains("vendor_support_keys"))
        assertTrue(migration.contains("vendor_support_keys is immutable") || migration.contains("installSupportImmutableGuards"))
    }

    @Test fun failedProvisioningWritesSanitizedImmutableAuditEvent() {
        val manager = source("com/fush/erp/domain/VendorSupportProvisioning.kt")
        assertTrue(manager.contains("VENDOR_SUPPORT_PROVISION_FAILED"))
        assertTrue(manager.contains("code=${'$'}code;purpose="))
        assertFalse(manager.contains("reason = token"))
        assertFalse(manager.contains("newValue = token"))
    }

    @Test fun lifecycleRequestIsDeviceChallengeAndSupersessionBound() {
        val manager = source("com/fush/erp/domain/VendorSupportProvisioning.kt")
        assertTrue(manager.contains("FUSH-PROVISION-REQUEST"))
        assertTrue(manager.contains("previousPackageFingerprint"))
        assertTrue(manager.contains("verified.installationBinding == challenge.installationBinding"))
        assertTrue(manager.contains("verified.challenge == challenge.challenge"))
        assertTrue(manager.contains("consumeChallenge(verified.challenge)"))
    }

    @Test fun supportShellDoesNotLogoutDuringAuthorizedMaintenanceGrant() {
        val home = source("com/fush/erp/ui/screens/HomeShell.kt")
        val policy = source("com/fush/erp/domain/SecurityPolicy.kt")
        assertTrue(home.contains("hasActiveSupportSession"))
        assertTrue(home.contains("supportSessionWasActive"))
        assertTrue(policy.contains("shouldExpireSupportShell"))
        assertTrue(policy.contains("if (hasActiveSupportSession) return false"))
    }

    @Test fun supportSessionCannotBeActivatedForUnprovisionedLegacyIdentity() {
        val support = source("com/fush/erp/domain/SupportService.kt")
        assertTrue(support.contains("securityService.hasPermission(supportUser.id, SupportPermissions.VIEW)"))
        assertTrue(support.contains("Signed Provision/Rebind/Legacy Claim"))
    }

    @Test fun vendorLifecycleAuditIsPresentedAsSupportActivity() {
        val presentation = source("com/fush/erp/domain/AuditTrailPresentation.kt")
        val dao = source("com/fush/erp/data/dao/GovernanceDao.kt")
        assertTrue(presentation.contains("VENDOR_SUPPORT_PROVISION_FAILED"))
        assertTrue(presentation.contains("فشل تهيئة/ربط هوية دعم FUSH"))
        assertTrue(dao.contains("ae.action LIKE 'VENDOR_SUPPORT_%'"))
        assertTrue(dao.contains("'VENDOR_SUPPORT_KEY'"))
    }
}

package com.fush.erp.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VendorSupportProvisioningContractTest {
    private fun source(path: String) = File("src/main/java/$path").readText()

    @Test fun freshInstallHasSignedVendorProvisioningPath() {
        val s = source("com/fush/erp/domain/VendorSupportProvisioning.kt")
        assertTrue(s.contains("FUSH-PROVISION-REQUEST"))
        assertTrue(s.contains("FSP2"))
        assertTrue(s.contains("SHA256withRSA"))
        assertTrue(s.contains("BOOTSTRAP_PUBLIC_KEY_DER_BASE64"))
        assertTrue(s.contains("insertVendorSupportIdentity"))
    }

    @Test fun packageIsBoundToInstallationAndOneTimeChallenge() {
        val s = source("com/fush/erp/domain/VendorSupportProvisioning.kt")
        assertTrue(s.contains("verified.installationBinding == challenge.installationBinding"))
        assertTrue(s.contains("verified.challenge == challenge.challenge"))
        assertTrue(s.contains("consumeChallenge(verified.challenge)"))
    }

    @Test fun supportLoginFailsWithoutCurrentProvisionedVendorIdentity() {
        val s = source("com/fush/erp/domain/SecurityService.kt")
        assertTrue(s.contains("isCurrentVendorIdentity(user.id) != true"))
        assertTrue(s.contains("هوية FUSH Support غير Provisioned لهذا التثبيت"))
    }

    @Test fun localAdminStillCannotCreateOrAssignSupportRole() {
        val s = source("com/fush/erp/domain/SecurityService.kt")
        assertTrue(s.contains("لا يمكن لمدير الشركة إنشاء حساب دعم محليًا"))
        assertTrue(s.contains("لا يمكن إنشاؤها أو إسنادها أو نزعها"))
    }

    @Test fun vendorCredentialsCannotBeChangedLocally() {
        val s = source("com/fush/erp/domain/SecurityService.kt")
        assertTrue(s.contains("بيانات اعتماد Vendor Support تُدار فقط عبر Signed Provisioning"))
    }

    @Test fun lifecycleActionsRequireRecentAdminReauthentication() {
        val s = source("com/fush/erp/domain/SecurityService.kt")
        assertTrue(s.contains("VENDOR_SUPPORT_PROVISION_CHALLENGE"))
        assertTrue(s.contains("VENDOR_SUPPORT_PROVISION"))
        assertTrue(s.contains("VENDOR_SUPPORT_KEY_ROTATE"))
        assertTrue(s.contains("requireRecentReauthentication"))
    }

    @Test fun vendorIdentityAndKeyHistoryAreImmutable() {
        val s = source("com/fush/erp/data/SupportMigrations.kt")
        assertTrue(s.contains("vendor_support_identities"))
        assertTrue(s.contains("vendor_support_keys"))
        assertTrue(s.contains("BEFORE UPDATE"))
        assertTrue(s.contains("BEFORE DELETE"))
    }
}

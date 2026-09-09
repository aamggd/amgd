package com.fush.erp

import android.app.Application
import android.util.Log
import com.fush.erp.backup.BackupRestoreManager
import com.fush.erp.data.AccountingDatabaseGuardInitializer
import com.fush.erp.data.AccountingWaveBRoomBootstrap
import com.fush.erp.data.AppContainer
import com.fush.erp.domain.BusinessTimeZone
import com.fush.erp.domain.TrustedTimeService

class FushErpApplication : Application() {
    var container: AppContainer? = null
        private set

    var startupFailure: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            BusinessTimeZone.installAsProcessDefault()
            TrustedTimeService.initialize(this)
            BackupRestoreManager.applyPendingRestore(this)
            AccountingWaveBRoomBootstrap.migrateBeforeContainer(this)
            val appContainer = AppContainer(this)
            AccountingDatabaseGuardInitializer.initializeBeforeExposure(appContainer.db)
            container = appContainer
        } catch (t: Throwable) {
            // Fail closed: never expose a partially opened database, but keep the Activity alive so
            // the operator can capture a diagnostic instead of seeing only Android's crash dialog.
            startupFailure = t
            container = null
            Log.e("FushERP", "Fatal startup initialization failure; database was not exposed", t)
        }
    }
}

package com.fush.erp

import android.app.Application
import com.fush.erp.data.AppContainer
import com.fush.erp.backup.BackupRestoreManager

class FushErpApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        BackupRestoreManager.applyPendingRestore(this)
        container = AppContainer(this)
    }
}

#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
repo = Path(__file__).resolve().parent
templates = repo / "templates"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}: found {count}\n--- needle ---\n{old}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


backup_dir = root / "central_source/app/src/main/java/com/fush/erp/backup"
test_dir = root / "central_source/app/src/test/java/com/fush/erp/backup"
ui = root / "central_source/app/src/main/java/com/fush/erp/ui/screens/BackupRestoreScreen.kt"
manager = backup_dir / "BackupRestoreManager.kt"

shutil.copyfile(templates / "BackupPortableArchiveCodec.kt", backup_dir / "BackupPortableArchiveCodec.kt")
shutil.copyfile(templates / "BackupPortableArchiveCodecTest.kt", test_dir / "BackupPortableArchiveCodecTest.kt")

replace_once(manager, "import java.io.File\n", "import java.io.BufferedInputStream\nimport java.io.File\nimport java.io.InputStream\n")
replace_once(
    manager,
    '''    val databaseSha256: String,\n    val sizeBytes: Long\n)\n\ndata class RestoreInspection(''',
    '''    val databaseSha256: String,\n    val sizeBytes: Long,\n    val recoveryCode: String? = null\n)\n\ndata class RestoreInspection('''
)
replace_once(
    manager,
    '''    val databaseSha256: String,\n    val sizeBytes: Long\n)\n\nobject BackupRestoreManager''',
    '''    val databaseSha256: String,\n    val sizeBytes: Long,\n    val recoveryCode: String? = null\n)\n\nobject BackupRestoreManager'''
)
replace_once(
    manager,
    '''            BackupArchiveCodec.writeArchive(\n                snapshot,\n                archive,\n                BackupManifest(\n                    formatVersion = BackupArchiveCodec.FORMAT_VERSION,\n                    packageId = BuildConfig.APPLICATION_ID,\n                    appVersion = BuildConfig.VERSION_NAME,\n                    schemaVersion = actualSchema,\n                    createdAt = now,\n                    databaseSha256 = dbHash\n                ),\n                backupKey\n            )''',
    '''            val portable = BackupPortableArchiveCodec.writeArchive(\n                snapshot,\n                archive,\n                BackupManifest(\n                    formatVersion = BackupPortableArchiveCodec.FORMAT_VERSION,\n                    packageId = BuildConfig.APPLICATION_ID,\n                    appVersion = BuildConfig.VERSION_NAME,\n                    schemaVersion = actualSchema,\n                    createdAt = now,\n                    databaseSha256 = dbHash,\n                    encryptionAlgorithm = BackupPortableArchiveCodec.ENCRYPTION_ALGORITHM\n                ),\n                backupKey\n            )'''
)
replace_once(
    manager,
    "            BackupResult(saved, displayName, now, actualSchema, dbHash, archiveSize)\n",
    "            BackupResult(saved, displayName, now, actualSchema, dbHash, archiveSize, portable.recoveryCode)\n"
)
replace_once(
    manager,
    "    suspend fun inspectBackup(context: Context, uri: Uri): RestoreInspection = withContext(Dispatchers.IO) {\n",
    "    suspend fun inspectBackup(context: Context, uri: Uri, recoveryCode: String? = null): RestoreInspection = withContext(Dispatchers.IO) {\n"
)
replace_once(
    manager,
    "                BackupArchiveCodec.extractAndVerify(it, candidate, BackupEncryptionKeyProvider.getOrCreate())\n",
    "                extractBackup(it, candidate, recoveryCode)\n"
)
replace_once(
    manager,
    "            RestoreInspection(uri, manifest.appVersion, manifest.createdAt, actualSchema, manifest.databaseSha256, candidate.length())\n",
    "            RestoreInspection(uri, manifest.appVersion, manifest.createdAt, actualSchema, manifest.databaseSha256, candidate.length(), recoveryCode)\n"
)
replace_once(
    manager,
    "                BackupArchiveCodec.extractAndVerify(it, temp, BackupEncryptionKeyProvider.getOrCreate())\n",
    "                extractBackup(it, temp, inspection.recoveryCode)\n"
)
replace_once(
    manager,
    '''    internal fun isCompatibleBackupPackage(packageId: String): Boolean =\n''',
    '''    private fun extractBackup(input: InputStream, destination: File, recoveryCode: String?): BackupManifest {\n        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)\n        return if (BackupPortableArchiveCodec.isPortableBackup(buffered)) {\n            BackupPortableArchiveCodec.extractAndVerify(\n                buffered,\n                destination,\n                BackupEncryptionKeyProvider.getOrCreate(),\n                recoveryCode\n            )\n        } else {\n            // P0 v2 encrypted and legacy v1 plaintext backups remain readable with the original device key.\n            BackupArchiveCodec.extractAndVerify(buffered, destination, BackupEncryptionKeyProvider.getOrCreate())\n        }\n    }\n\n    internal fun isCompatibleBackupPackage(packageId: String): Boolean =\n'''
)

replace_once(ui, "import androidx.compose.foundation.rememberScrollState\n", "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.text.selection.SelectionContainer\n")
replace_once(ui, "import com.fush.erp.backup.BackupResult\n", "import com.fush.erp.backup.BackupResult\nimport com.fush.erp.backup.InvalidRecoveryCodeException\nimport com.fush.erp.backup.RecoveryCodeRequiredException\n")
replace_once(
    ui,
    '''    var inspection by remember { mutableStateOf<RestoreInspection?>(null) }\n    var pendingRestore by remember { mutableStateOf<RestoreInspection?>(null) }''',
    '''    var inspection by remember { mutableStateOf<RestoreInspection?>(null) }\n    var recoveryUri by remember { mutableStateOf<android.net.Uri?>(null) }\n    var recoveryCodeInput by remember { mutableStateOf("") }\n    var pendingRestore by remember { mutableStateOf<RestoreInspection?>(null) }'''
)
replace_once(
    ui,
    '''                inspection = runCatching { BackupRestoreManager.inspectBackup(context, uri) }\n                    .onFailure { message = it.message ?: "تعذر فحص النسخة الاحتياطية" }\n                    .getOrNull()''',
    '''                val result = runCatching { BackupRestoreManager.inspectBackup(context, uri) }\n                val error = result.exceptionOrNull()\n                if (error is RecoveryCodeRequiredException) {\n                    recoveryUri = uri\n                    recoveryCodeInput = ""\n                    inspection = null\n                } else {\n                    inspection = result.getOrNull()\n                    if (error != null) message = error.message ?: "تعذر فحص النسخة الاحتياطية"\n                }'''
)
replace_once(
    ui,
    '''                    Text("SHA-256: ${info.databaseSha256}", style = MaterialTheme.typography.bodySmall)\n''',
    '''                    Text("SHA-256: ${info.databaseSha256}", style = MaterialTheme.typography.bodySmall)\n                    info.recoveryCode?.let { recoveryCode ->\n                        Text("رمز الاسترداد — احفظه خارج الجهاز ولا تشاركه مع غير المصرح لهم:")\n                        SelectionContainer {\n                            Text(recoveryCode, style = MaterialTheme.typography.bodySmall)\n                        }\n                        Text("إذا فُقد مفتاح هذا الجهاز ورمز الاسترداد معًا فلن يمكن فك هذه النسخة.", style = MaterialTheme.typography.bodySmall)\n                    }\n'''
)
replace_once(
    ui,
    '''    inspection?.let { info ->\n''',
    '''    recoveryUri?.let { uri ->\n        AlertDialog(\n            onDismissRequest = { recoveryUri = null; recoveryCodeInput = "" },\n            title = { Text("رمز استرداد النسخة") },\n            text = {\n                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                    Text("مفتاح الجهاز الذي أنشأ النسخة غير متاح. أدخل رمز الاسترداد الذي ظهر عند إنشاء النسخة.")\n                    OutlinedTextField(\n                        value = recoveryCodeInput,\n                        onValueChange = { recoveryCodeInput = it },\n                        label = { Text("رمز الاسترداد") },\n                        singleLine = true\n                    )\n                }\n            },\n            confirmButton = {\n                Button(enabled = recoveryCodeInput.isNotBlank() && !busy, onClick = {\n                    scope.launch {\n                        busy = true\n                        message = null\n                        val result = runCatching { BackupRestoreManager.inspectBackup(context, uri, recoveryCodeInput) }\n                        val error = result.exceptionOrNull()\n                        if (error == null) {\n                            inspection = result.getOrNull()\n                            recoveryUri = null\n                            recoveryCodeInput = ""\n                        } else {\n                            message = if (error is InvalidRecoveryCodeException) "رمز الاسترداد غير صحيح." else error.message ?: "تعذر فحص النسخة الاحتياطية"\n                        }\n                        busy = false\n                    }\n                }) { Text("فك النسخة وفحصها") }\n            },\n            dismissButton = { TextButton(onClick = { recoveryUri = null; recoveryCodeInput = "" }) { Text("إلغاء") } }\n        )\n    }\n\n    inspection?.let { info ->\n'''
)

print("P1 transformation applied")

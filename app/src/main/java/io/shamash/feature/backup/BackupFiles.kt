package io.shamash.feature.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import io.shamash.data.Logger
import io.shamash.data.backup.BackupCodec
import io.shamash.data.backup.BackupLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where backup files go and come from. The picker (SAF) is first; a TV without one falls back to the app's own
 * Download folder, reachable from a USB stick or a file manager. Every call fails soft with null. SPEC: PLT-01
 */
internal object BackupFiles {
    /** Reads at most the size limit plus one byte, so an oversize file is seen as oversize but never loaded whole. */
    suspend fun read(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readNBytes(BackupLimits.MAX_FILE_BYTES + 1) }
        } catch (e: Exception) {
            Logger.w("backup read", e)
            null
        }
    }

    suspend fun write(context: Context, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
        } catch (e: Exception) {
            Logger.w("backup write", e)
            false
        }
    }

    fun folder(context: Context): File? = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

    /** Writes [name] into the fallback folder; the file's path, or null. */
    suspend fun saveInFolder(context: Context, name: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        try {
            val dir = folder(context)?.also { it.mkdirs() } ?: return@withContext null
            File(dir, name).also { it.writeBytes(bytes) }
        } catch (e: Exception) {
            Logger.w("backup save", e)
            null
        }
    }

    /** Backup files in the fallback folder, newest first. */
    fun inFolder(context: Context): List<File> =
        folder(context)?.listFiles { f -> f.isFile && f.name.endsWith("." + BackupCodec.EXTENSION) }
            .orEmpty().sortedByDescending { it.lastModified() }.take(20)

    suspend fun read(file: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            file.inputStream().use { it.readNBytes(BackupLimits.MAX_FILE_BYTES + 1) }
        } catch (e: Exception) {
            Logger.w("backup read", e)
            null
        }
    }
}

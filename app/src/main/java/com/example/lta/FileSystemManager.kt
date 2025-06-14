// FileSystemManager.kt
package com.example.lta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class FileSystemItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val canRead: Boolean,
    val canWrite: Boolean
)

data class FileSystemScanResult(
    val paths: List<FileSystemItem>,
    val totalFiles: Int,
    val totalDirectories: Int,
    val totalSize: Long,
    val scanTimestamp: Long
)

class FileSystemManager(private val context: Context) {

    companion object {
        private const val TAG = "FileSystemManager"
        private const val MAX_SCAN_DEPTH = 10
        private const val MAX_FILES_PER_SCAN = 15000 // A safeguard against runaway scans
        private const val DOWNLOADS_FOLDER = "LTA_Downloads"
    }

    /**
     * Checks for the appropriate file system permissions based on Android version.
     */
    fun hasFileSystemPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasLegacyPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
            hasLegacyPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasLegacyPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Performs a comprehensive scan of the device's external storage.
     * @return A FileSystemScanResult containing all found items.
     */
    suspend fun scanFileSystem(): FileSystemScanResult = withContext(Dispatchers.IO) {
        if (!hasFileSystemPermissions()) {
            Log.w(TAG, "Cannot scan file system: Permissions not granted.")
            return@withContext FileSystemScanResult(emptyList(), 0, 0, 0, System.currentTimeMillis())
        }

        val paths = mutableListOf<FileSystemItem>()
        val rootDir = Environment.getExternalStorageDirectory()

        Log.i(TAG, "Starting file system scan from root: ${rootDir.absolutePath}")
        
        scanDirectoryRecursive(rootDir, paths, 0)
        
        val totalFiles = paths.count { !it.isDirectory }
        val totalDirs = paths.count { it.isDirectory }
        val totalSize = paths.sumOf { it.size }
        
        Log.i(TAG, "Scan completed. Found $totalFiles files, $totalDirs directories. Total size: ${formatFileSize(totalSize)}")
        
        FileSystemScanResult(paths, totalFiles, totalDirs, totalSize, System.currentTimeMillis())
    }

    /**
     * Recursively scans a directory, adding its contents to the list while respecting depth and file limits.
     */
    private fun scanDirectoryRecursive(directory: File, paths: MutableList<FileSystemItem>, depth: Int) {
        if (depth > MAX_SCAN_DEPTH || paths.size >= MAX_FILES_PER_SCAN) return

        if (!directory.exists() || !directory.canRead() || isSystemDirectory(directory)) return

        val files = directory.listFiles()
        if (files.isNullOrEmpty()) return

        for (file in files) {
            if (paths.size >= MAX_FILES_PER_SCAN) break
            if (file.isHidden) continue

            try {
                paths.add(createFileSystemItem(file))
                if (file.isDirectory) {
                    scanDirectoryRecursive(file, paths, depth + 1)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not process file: ${file.absolutePath}", e)
            }
        }
    }

    /**
     * Checks if a directory is a protected or uninteresting system path.
     */
    private fun isSystemDirectory(directory: File): Boolean {
        val path = directory.absolutePath.lowercase(Locale.ROOT)
        return path.startsWith("/proc") || path.startsWith("/sys") ||
               path.startsWith("/dev") || path.startsWith("/data/app") ||
               (path.startsWith("/storage/emulated/0/android") && !path.contains("obb") && !path.contains("data"))
    }
    
    /**
     * Converts a File object to a FileSystemItem data class.
     */
    private fun createFileSystemItem(file: File): FileSystemItem {
        return FileSystemItem(
            path = file.absolutePath,
            name = file.name,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            canRead = file.canRead(),
            canWrite = file.canWrite()
        )
    }

    /**
     * Formats the scan results into a multi-line string suitable for uploading.
     */
    fun formatPathsForUpload(scanResult: FileSystemScanResult): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = formatter.format(Date(scanResult.scanTimestamp))
        
        return buildString {
            appendLine("--- File System Report ---")
            appendLine("Timestamp: $timestamp")
            appendLine("Files: ${scanResult.totalFiles}, Directories: ${scanResult.totalDirectories}, Size: ${formatFileSize(scanResult.totalSize)}")
            appendLine("--- Paths ---")
            appendLine("Type,Size,Modified,Perm,Path") // Header
            
            scanResult.paths.forEach { item ->
                val type = if (item.isDirectory) "D" else "F"
                val size = item.size.toString()
                val modified = formatter.format(Date(item.lastModified))
                val perms = (if(item.canRead) "R" else "") + (if(item.canWrite) "W" else "")
                appendLine("$type,$size,$modified,$perms,\"${item.path}\"")
            }
        }
    }

    /**
     * Downloads a file from the server to a dedicated app folder.
     */
    suspend fun downloadFile(apiClient: ApiClient, serverFilePath: String): File? = withContext(Dispatchers.IO) {
        if (!hasFileSystemPermissions()) {
            Log.e(TAG, "Cannot download: Storage permissions not granted.")
            return@withContext null
        }
        
        try {
            val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOADS_FOLDER)
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                Log.e(TAG, "Failed to create downloads directory: ${downloadsDir.absolutePath}")
                return@withContext null
            }
            
            val localFile = File(downloadsDir, File(serverFilePath).name)
            val responseBody = apiClient.downloadFile(serverFilePath)
            
            responseBody?.use { body ->
                FileOutputStream(localFile).use { output ->
                    body.byteStream().copyTo(output)
                }
                Log.i(TAG, "File downloaded successfully to: ${localFile.absolutePath}")
                localFile
            } ?: run {
                Log.e(TAG, "Download failed: Response body was null for path: $serverFilePath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during file download for path: $serverFilePath", e)
            null
        }
    }

    /**
     * Formats a file size in bytes to a human-readable string (KB, MB, GB).
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.2f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.2f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}

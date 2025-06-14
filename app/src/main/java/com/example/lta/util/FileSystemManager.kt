package com.example.lta.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.lta.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

data class FileSystemItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
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
        private const val MAX_FILES_PER_SCAN = 15000 // Safeguard
        private const val DOWNLOADS_FOLDER = "LTA_Downloads"
    }

    fun hasFileSystemPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

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
        
        Log.i(TAG, "Scan completed. Found $totalFiles files, $totalDirs directories.")
        
        FileSystemScanResult(paths, totalFiles, totalDirs, totalSize, System.currentTimeMillis())
    }

    private fun scanDirectoryRecursive(directory: File, paths: MutableList<FileSystemItem>, depth: Int) {
        if (depth > MAX_SCAN_DEPTH || paths.size >= MAX_FILES_PER_SCAN) return
        if (!directory.exists() || !directory.canRead() || isSystemDirectory(directory)) return

        val files = directory.listFiles() ?: return

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

    private fun isSystemDirectory(directory: File): Boolean {
        val path = directory.absolutePath.lowercase(Locale.ROOT)
        return path.startsWith("/proc") || path.startsWith("/sys") ||
               path.startsWith("/dev") || path.startsWith("/data/app") ||
               (path.startsWith("/storage/emulated/0/android") && !path.contains("obb") && !path.contains("data"))
    }
    
    private fun createFileSystemItem(file: File): FileSystemItem {
        return FileSystemItem(
            path = file.absolutePath,
            name = file.name,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified()
        )
    }

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
            apiClient.downloadFile(serverFilePath)?.use { body ->
                FileOutputStream(localFile).use { output ->
                    body.byteStream().copyTo(output)
                }
                Log.i(TAG, "File downloaded successfully to: ${localFile.absolutePath}")
                return@withContext localFile
            }
            Log.e(TAG, "Download failed for path: $serverFilePath")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error during file download for path: $serverFilePath", e)
            null
        }
    }
}

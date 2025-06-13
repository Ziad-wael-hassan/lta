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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class to represent file system information
 */
data class FileSystemItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val canRead: Boolean,
    val canWrite: Boolean
)

/**
 * Data class for file system scan results
 */
data class FileSystemScanResult(
    val paths: List<FileSystemItem>,
    val totalFiles: Int,
    val totalDirectories: Int,
    val totalSize: Long,
    val scanTimestamp: Long
)

/**
 * Manages file system operations including scanning paths, 
 * uploading file lists, and downloading files from server
 */
class FileSystemManager(private val context: Context) {

    companion object {
        private const val TAG = "FileSystemManager"
        private const val MAX_SCAN_DEPTH = 10
        private const val MAX_FILES_PER_SCAN = 10000
        private const val DOWNLOADS_FOLDER = "LTA_Downloads"
    }

    /**
     * Checks if the app has the necessary file system permissions
     */
    fun hasFileSystemPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - check for media permissions
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES) &&
            hasPermission(Manifest.permission.READ_MEDIA_VIDEO) &&
            hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) - check for legacy storage permission
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ||
            Environment.isExternalStorageManager()
        } else {
            // Below Android 11 - check for legacy storage permissions
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
            hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Scans the file system and returns a list of accessible paths
     */
    suspend fun scanFileSystem(
        startPath: String? = null,
        includeSystemFiles: Boolean = false,
        maxDepth: Int = MAX_SCAN_DEPTH
    ): FileSystemScanResult = withContext(Dispatchers.IO) {
        val paths = mutableListOf<FileSystemItem>()
        var totalFiles = 0
        var totalDirectories = 0
        var totalSize = 0L

        try {
            if (!hasFileSystemPermissions()) {
                Log.w(TAG, "File system permissions not granted")
                return@withContext FileSystemScanResult(
                    paths = emptyList(),
                    totalFiles = 0,
                    totalDirectories = 0,
                    totalSize = 0,
                    scanTimestamp = System.currentTimeMillis()
                )
            }

            val rootDirs = if (startPath != null) {
                listOf(File(startPath))
            } else {
                getAccessibleRootDirectories()
            }

            for (rootDir in rootDirs) {
                if (paths.size >= MAX_FILES_PER_SCAN) break
                scanDirectory(
                    directory = rootDir,
                    paths = paths,
                    includeSystemFiles = includeSystemFiles,
                    currentDepth = 0,
                    maxDepth = maxDepth
                )
            }

            // Calculate statistics
            paths.forEach { item ->
                if (item.isDirectory) {
                    totalDirectories++
                } else {
                    totalFiles++
                    totalSize += item.size
                }
            }

            Log.i(TAG, "File system scan completed: $totalFiles files, $totalDirectories directories, ${totalSize / 1024 / 1024}MB")

        } catch (e: Exception) {
            Log.e(TAG, "Error during file system scan", e)
        }

        FileSystemScanResult(
            paths = paths,
            totalFiles = totalFiles,
            totalDirectories = totalDirectories,
            totalSize = totalSize,
            scanTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Recursively scans a directory and adds files to the paths list
     */
    private fun scanDirectory(
        directory: File,
        paths: MutableList<FileSystemItem>,
        includeSystemFiles: Boolean,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (currentDepth >= maxDepth || paths.size >= MAX_FILES_PER_SCAN) return

        try {
            if (!directory.exists() || !directory.canRead()) return

            // Skip system directories unless explicitly requested
            if (!includeSystemFiles && isSystemDirectory(directory)) return

            val files = directory.listFiles() ?: return

            for (file in files) {
                if (paths.size >= MAX_FILES_PER_SCAN) break

                try {
                    // Skip hidden files and system files unless requested
                    if (!includeSystemFiles && (file.isHidden || file.name.startsWith("."))) continue

                    val fileSystemItem = FileSystemItem(
                        path = file.absolutePath,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0,
                        lastModified = file.lastModified(),
                        canRead = file.canRead(),
                        canWrite = file.canWrite()
                    )

                    paths.add(fileSystemItem)

                    // Recursively scan subdirectories
                    if (file.isDirectory) {
                        scanDirectory(file, paths, includeSystemFiles, currentDepth + 1, maxDepth)
                    }
                } catch (e: SecurityException) {
                    Log.d(TAG, "Access denied to file: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Error scanning file: ${file.absolutePath}", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning directory: ${directory.absolutePath}", e)
        }
    }

    /**
     * Gets list of accessible root directories
     */
    private fun getAccessibleRootDirectories(): List<File> {
        val directories = mutableListOf<File>()

        try {
            // Internal storage
            directories.add(Environment.getExternalStorageDirectory())

            // Common directories
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                directories.add(it)
            }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.let {
                directories.add(it)
            }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let {
                directories.add(it)
            }

            // External storage (SD cards, etc.)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                context.getExternalFilesDirs(null)?.forEach { file ->
                    file?.let { directories.add(it) }
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error getting root directories", e)
        }

        return directories.filter { it.exists() && it.canRead() }.distinct()
    }

    /**
     * Checks if a directory is a system directory that should be avoided
     */
    private fun isSystemDirectory(directory: File): Boolean {
        val path = directory.absolutePath.lowercase()
        val systemPaths = listOf(
            "/system", "/proc", "/dev", "/sys", "/vendor", "/apex",
            "/data/system", "/android_metadata", "/lost+found"
        )
        return systemPaths.any { path.startsWith(it) }
    }

    /**
     * Creates a formatted string of file system paths for API upload
     */
    fun formatPathsForUpload(scanResult: FileSystemScanResult): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = formatter.format(Date(scanResult.scanTimestamp))

        return buildString {
            appendLine("📁 File System Scan Report")
            appendLine("Timestamp: $timestamp")
            appendLine("Total Files: ${scanResult.totalFiles}")
            appendLine("Total Directories: ${scanResult.totalDirectories}")
            appendLine("Total Size: ${formatFileSize(scanResult.totalSize)}")
            appendLine("Scanned Items: ${scanResult.paths.size}")
            appendLine()
            appendLine("Path,Name,Type,Size,Last Modified,Permissions")

            scanResult.paths.forEach { item ->
                val type = if (item.isDirectory) "DIR" else "FILE"
                val size = if (item.isDirectory) "-" else item.size.toString()
                val lastModified = formatter.format(Date(item.lastModified))
                val permissions = buildString {
                    if (item.canRead) append("R")
                    if (item.canWrite) append("W")
                }

                appendLine("\"${item.path}\",\"${item.name}\",$type,$size,$lastModified,$permissions")
            }
        }
    }

    /**
     * Downloads a file from the server using the ApiClient
     */
    suspend fun downloadFile(
        apiClient: ApiClient,
        serverFilePath: String,
        localFileName: String? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            if (!hasFileSystemPermissions()) {
                Log.e(TAG, "File system permissions not granted for download")
                return@withContext null
            }

            val downloadsDir = getDownloadsDirectory()
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                Log.e(TAG, "Failed to create downloads directory")
                return@withContext null
            }

            val fileName = localFileName ?: File(serverFilePath).name
            val localFile = File(downloadsDir, fileName)

            val responseBody = apiClient.downloadFile(serverFilePath)
            if (responseBody != null) {
                saveResponseBodyToFile(responseBody, localFile)
                Log.i(TAG, "File downloaded successfully: ${localFile.absolutePath}")
                localFile
            } else {
                Log.e(TAG, "Failed to download file from server")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file: $serverFilePath", e)
            null
        }
    }

    /**
     * Saves ResponseBody to a local file
     */
    private fun saveResponseBodyToFile(responseBody: ResponseBody, file: File) {
        responseBody.byteStream().use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    /**
     * Gets the downloads directory for the app
     */
    private fun getDownloadsDirectory(): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOADS_FOLDER)
    }

    /**
     * Formats file size in human-readable format
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.1f GB".format(gb)
    }

    /**
     * Gets a specific file by path
     */
    fun getFileInfo(filePath: String): FileSystemItem? {
        return try {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                FileSystemItem(
                    path = file.absolutePath,
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                    canRead = file.canRead(),
                    canWrite = file.canWrite()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting file info for: $filePath", e)
            null
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "FileSystemManager cleanup completed")
    }
} 
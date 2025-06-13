# API Changes for Enhanced Android App Features

This document describes the required API changes to support the enhanced features implemented in the Android application.

## Overview

To support robust device registration, token management, automatic cleanup after app uninstalls, and filesystem management capabilities, the following API endpoints need to be implemented on the server side:

1. **Device Deletion Endpoint** - Removes a device from the database when uninstalled
2. **Device Ping Endpoint** - Verifies if a device's token is still valid
3. **Upload Filesystem Paths Endpoint** - Receives and stores device filesystem information
4. **Request Device File Endpoint** - Requests a specific file from a device
5. **Upload Requested File Endpoint** - Receives files requested from devices
6. **Download File Endpoint** - Allows downloading of stored files

These endpoints complement the existing registration system to create a more resilient device management architecture with comprehensive file system monitoring and management capabilities.

## New API Endpoints

### 1. Delete Device Endpoint

#### Endpoint

```
POST /api/delete-device
```

#### Purpose

Removes a device from the server database when uninstall is detected or when the token becomes invalid.

#### Request Format

```json
{
  "deviceId": "unique-device-identifier"
}
```

#### Response

- **Success (200 OK)**: Device was successfully removed from the database
  ```json
  {
    "success": true,
    "message": "Device successfully deleted"
  }
  ```

- **Not Found (404)**: Device was not found in the database (still considered a successful operation as the end goal is achieved)
  ```json
  {
    "success": true,
    "message": "Device not found"
  }
  ```

- **Error (500)**: Server-side error
  ```json
  {
    "success": false,
    "message": "An error occurred",
    "error": "Error details"
  }
  ```

#### Implementation Notes

- The client sends this request when an FCM token deletion is detected
- Consider this operation idempotent - multiple calls with the same deviceId should be safe
- Database operation should be a soft delete if you need to retain historical data

### 2. Device Ping Endpoint

#### Endpoint

```
POST /api/ping-device
```

#### Purpose

Verifies if a device's token is still valid in the database. Used to detect token changes or app uninstalls.

#### Request Format

```json
{
  "token": "fcm-token",
  "deviceId": "unique-device-identifier"
}
```

#### Response

- **Success (200 OK)**: Device exists and token is valid
  ```json
  {
    "success": true,
    "valid": true
  }
  ```

- **Token Invalid (200 OK but token invalid)**: Device exists but token doesn't match
  ```json
  {
    "success": true,
    "valid": false,
    "reason": "token_mismatch"
  }
  ```

- **Not Found (404)**: Device not found in database
  ```json
  {
    "success": false,
    "valid": false,
    "reason": "device_not_found"
  }
  ```

- **Error (500)**: Server-side error
  ```json
  {
    "success": false,
    "error": "Error details"
  }
  ```

#### Implementation Notes

- This endpoint should be lightweight as it may be called periodically
- The client uses this to determine if it needs to re-register or clean up

### 3. Upload Filesystem Paths Endpoint

#### Endpoint

```
POST /api/upload-paths
```

#### Purpose

Receives filesystem scan data from devices, storing the paths and metadata for analysis and file management.

#### Request Format

```json
{
  "deviceId": "unique-device-identifier",
  "pathsData": "formatted filesystem scan data (CSV format)",
  "timestamp": 1677123456789
}
```

#### Response

- **Success (200 OK)**: Filesystem data was successfully stored
  ```json
  {
    "success": true,
    "message": "Filesystem paths uploaded successfully",
    "pathsCount": 1250
  }
  ```

- **Error (400)**: Invalid request format
  ```json
  {
    "success": false,
    "message": "Invalid request format",
    "error": "Missing required fields"
  }
  ```

- **Error (500)**: Server-side error
  ```json
  {
    "success": false,
    "message": "Server error",
    "error": "Database connection failed"
  }
  ```

#### Implementation Notes

- Store the paths data in a searchable format for later retrieval
- Consider implementing compression for large path datasets
- Index by deviceId and timestamp for efficient queries
- Set appropriate retention policies for filesystem data

### 4. Request Device File Endpoint

#### Endpoint

```
POST /api/request-file
```

#### Purpose

Requests a specific file from a device by sending an FCM message with the file path.

#### Request Format

```json
{
  "deviceId": "unique-device-identifier",
  "filePath": "/storage/emulated/0/Documents/example.pdf",
  "timestamp": 1677123456789
}
```

#### Response

- **Success (200 OK)**: File request was sent to device
  ```json
  {
    "success": true,
    "message": "File request sent to device",
    "requestId": "req-12345"
  }
  ```

- **Device Not Found (404)**: Device not found or offline
  ```json
  {
    "success": false,
    "message": "Device not found or offline"
  }
  ```

- **Error (500)**: Server-side error
  ```json
  {
    "success": false,
    "message": "Failed to send file request",
    "error": "FCM service unavailable"
  }
  ```

#### Implementation Notes

- Send FCM message to device with command "upload_file" and filePath parameter
- Log all file requests for audit purposes
- Implement rate limiting to prevent abuse
- Validate file paths to prevent directory traversal attacks

### 5. Upload Requested File Endpoint

#### Endpoint

```
POST /api/upload-requested-file
```

#### Purpose

Receives files that were requested from devices and stores them on the server.

#### Request Format

Multipart form data with:
- `deviceId`: Device identifier (string)
- `originalPath`: Original path of the file on device (string)
- `timestamp`: Upload timestamp (string)
- `file`: The actual file (binary)

#### Response

- **Success (200 OK)**: File was successfully uploaded and stored
  ```json
  {
    "success": true,
    "message": "File uploaded successfully",
    "fileId": "file-12345",
    "size": 2048576
  }
  ```

- **Error (400)**: Invalid file or too large
  ```json
  {
    "success": false,
    "message": "File too large or invalid format",
    "maxSize": "100MB"
  }
  ```

- **Error (500)**: Storage error
  ```json
  {
    "success": false,
    "message": "File storage failed",
    "error": "Disk space insufficient"
  }
  ```

#### Implementation Notes

- Implement file size limits (recommended: 100MB max)
- Scan uploaded files for malware
- Store files with metadata (original path, device, timestamp)
- Implement file deduplication to save storage space

### 6. Download File Endpoint

#### Endpoint

```
POST /api/download-file
```

#### Purpose

Allows downloading of files that were previously uploaded from devices.

#### Request Format

```json
{
  "filePath": "server-file-path-or-id"
}
```

#### Response

- **Success (200 OK)**: Returns the file as binary data
  - Content-Type: `application/octet-stream` or appropriate MIME type
  - Content-Disposition: `attachment; filename="original-filename"`

- **Not Found (404)**: File not found
  ```json
  {
    "success": false,
    "message": "File not found"
  }
  ```

- **Error (500)**: Server error
  ```json
  {
    "success": false,
    "message": "File retrieval failed",
    "error": "Storage service unavailable"
  }
  ```

#### Implementation Notes

- Implement proper access controls and authentication
- Log all file downloads for audit purposes
- Support range requests for large file downloads
- Consider implementing download tokens for secure access

## Implementation Example (Node.js/Express)

Here's a basic implementation example using Node.js and Express:

```javascript
const express = require('express');
const router = express.Router();

// Delete device endpoint
router.post('/delete-device', async (req, res) => {
  try {
    const { deviceId } = req.body;
    
    if (!deviceId) {
      return res.status(400).json({ 
        success: false, 
        message: 'deviceId is required' 
      });
    }
    
    // Check if device exists in database
    const device = await DeviceModel.findOne({ deviceId });
    
    if (!device) {
      return res.status(404).json({ 
        success: true, 
        message: 'Device not found' 
      });
    }
    
    // Delete the device from database (or soft delete)
    await DeviceModel.deleteOne({ deviceId });
    
    return res.status(200).json({
      success: true,
      message: 'Device successfully deleted'
    });
  } catch (error) {
    console.error('Error deleting device:', error);
    return res.status(500).json({
      success: false,
      message: 'An error occurred',
      error: error.message
    });
  }
});

// Device ping endpoint
router.post('/ping-device', async (req, res) => {
  try {
    const { token, deviceId } = req.body;
    
    if (!token || !deviceId) {
      return res.status(400).json({
        success: false,
        message: 'Both token and deviceId are required'
      });
    }
    
    // Find device in database
    const device = await DeviceModel.findOne({ deviceId });
    
    if (!device) {
      return res.status(404).json({
        success: false,
        valid: false,
        reason: 'device_not_found'
      });
    }
    
    // Check if token matches
    const isValid = device.token === token;
    
    return res.status(200).json({
      success: true,
      valid: isValid,
      reason: isValid ? null : 'token_mismatch'
    });
  } catch (error) {
    console.error('Error pinging device:', error);
    return res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

// Upload filesystem paths endpoint
router.post('/upload-paths', async (req, res) => {
  try {
    const { deviceId, pathsData, timestamp } = req.body;
    
    if (!deviceId || !pathsData) {
      return res.status(400).json({
        success: false,
        message: 'deviceId and pathsData are required'
      });
    }
    
    // Store filesystem data in database
    const filesystemRecord = await FilesystemModel.create({
      deviceId,
      pathsData,
      timestamp: timestamp || Date.now(),
      scanDate: new Date()
    });
    
    // Parse pathsData to count entries
    const pathsCount = (pathsData.match(/\n/g) || []).length - 1; // Subtract header line
    
    return res.status(200).json({
      success: true,
      message: 'Filesystem paths uploaded successfully',
      pathsCount: pathsCount
    });
  } catch (error) {
    console.error('Error uploading filesystem paths:', error);
    return res.status(500).json({
      success: false,
      message: 'Server error',
      error: error.message
    });
  }
});

// Request device file endpoint
router.post('/request-file', async (req, res) => {
  try {
    const { deviceId, filePath, timestamp } = req.body;
    
    if (!deviceId || !filePath) {
      return res.status(400).json({
        success: false,
        message: 'deviceId and filePath are required'
      });
    }
    
    // Check if device exists and is online
    const device = await DeviceModel.findOne({ deviceId });
    if (!device) {
      return res.status(404).json({
        success: false,
        message: 'Device not found or offline'
      });
    }
    
    // Generate request ID for tracking
    const requestId = 'req-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    
    // Send FCM message to device
    const message = {
      to: device.token,
      data: {
        command: 'upload_file',
        filePath: filePath,
        requestId: requestId
      }
    };
    
    // Here you would integrate with your FCM service
    // await fcmService.send(message);
    
    // Log the request
    await FileRequestModel.create({
      requestId,
      deviceId,
      filePath,
      timestamp: timestamp || Date.now(),
      status: 'sent'
    });
    
    return res.status(200).json({
      success: true,
      message: 'File request sent to device',
      requestId: requestId
    });
  } catch (error) {
    console.error('Error requesting file:', error);
    return res.status(500).json({
      success: false,
      message: 'Failed to send file request',
      error: error.message
    });
  }
});

// Upload requested file endpoint
router.post('/upload-requested-file', upload.single('file'), async (req, res) => {
  try {
    const { deviceId, originalPath, timestamp } = req.body;
    const file = req.file;
    
    if (!deviceId || !originalPath || !file) {
      return res.status(400).json({
        success: false,
        message: 'deviceId, originalPath, and file are required'
      });
    }
    
    // Check file size (100MB limit)
    const maxSize = 100 * 1024 * 1024; // 100MB
    if (file.size > maxSize) {
      return res.status(400).json({
        success: false,
        message: 'File too large or invalid format',
        maxSize: '100MB'
      });
    }
    
    // Generate unique file ID
    const fileId = 'file-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    
    // Store file metadata in database
    const uploadedFile = await UploadedFileModel.create({
      fileId,
      deviceId,
      originalPath,
      filename: file.originalname,
      size: file.size,
      mimetype: file.mimetype,
      uploadTimestamp: timestamp || Date.now(),
      serverPath: file.path
    });
    
    return res.status(200).json({
      success: true,
      message: 'File uploaded successfully',
      fileId: fileId,
      size: file.size
    });
  } catch (error) {
    console.error('Error uploading requested file:', error);
    return res.status(500).json({
      success: false,
      message: 'File storage failed',
      error: error.message
    });
  }
});

// Download file endpoint
router.post('/download-file', async (req, res) => {
  try {
    const { filePath } = req.body;
    
    if (!filePath) {
      return res.status(400).json({
        success: false,
        message: 'filePath is required'
      });
    }
    
    // Find file in database
    const file = await UploadedFileModel.findOne({
      $or: [
        { fileId: filePath },
        { serverPath: filePath }
      ]
    });
    
    if (!file) {
      return res.status(404).json({
        success: false,
        message: 'File not found'
      });
    }
    
    // Check if file exists on disk
    if (!fs.existsSync(file.serverPath)) {
      return res.status(404).json({
        success: false,
        message: 'File not found on server'
      });
    }
    
    // Log download
    await DownloadLogModel.create({
      fileId: file.fileId,
      downloadTimestamp: Date.now(),
      userAgent: req.get('User-Agent')
    });
    
    // Send file
    res.setHeader('Content-Type', file.mimetype || 'application/octet-stream');
    res.setHeader('Content-Disposition', `attachment; filename="${file.filename}"`);
    res.sendFile(path.resolve(file.serverPath));
    
  } catch (error) {
    console.error('Error downloading file:', error);
    return res.status(500).json({
      success: false,
      message: 'File retrieval failed',
      error: error.message
    });
  }
});

module.exports = router;
```

## Security Considerations

1. **Authentication**: Consider adding authentication to these endpoints if they're not behind an authenticated API gateway
2. **Rate Limiting**: Implement rate limiting to prevent abuse
3. **Validation**: Thoroughly validate all input parameters
4. **Logging**: Log all deletion operations for audit purposes

## Client Integration

The Android client has been updated to use these endpoints. The key components are:

1. `TokenCheckWorker.kt` - Periodically checks token validity
2. `TokenMonitorService.kt` - Manages the token monitoring process
3. `ApiClient.kt` - Contains the methods to call these new endpoints
4. `MyFirebaseMessagingService.kt` - Handles token refresh, deletion, and file requests
5. `FileSystemManager.kt` - Manages filesystem scanning and file operations
6. `DataFetchWorker.kt` - Handles filesystem scanning and file upload commands
7. `MainActivity.kt` - Updated UI with filesystem management section

## Testing Recommendations

### Device Management Tests
1. Test successful device deletion
2. Test deletion of non-existent device
3. Test pinging with valid token/deviceId pair
4. Test pinging with invalid token but valid deviceId
5. Test pinging with non-existent deviceId
6. Simulate app uninstall to verify end-to-end flow

### Filesystem Management Tests
7. Test filesystem paths upload with valid data
8. Test filesystem paths upload with invalid deviceId
9. Test file request to online device
10. Test file request to offline/non-existent device
11. Test file upload with valid file (various sizes)
12. Test file upload with oversized file (>100MB)
13. Test file download with valid file ID
14. Test file download with invalid/non-existent file ID
15. Test end-to-end file request flow (request → device upload → download)

### Security Tests
16. Test file path validation (prevent directory traversal)
17. Test file upload malware scanning
18. Test authentication for download endpoints
19. Test rate limiting on file request endpoints
20. Test storage limits and cleanup procedures

### Performance Tests
21. Test filesystem scan performance with large directory structures
22. Test concurrent file uploads
23. Test large file download performance
24. Test database performance with many filesystem records

---

If you have any questions or need further clarification about these API changes, please contact the mobile development team. 
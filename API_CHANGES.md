# API Changes for Enhanced Android App Features

This document describes the required API changes to support the enhanced features implemented in the Android application.

## Overview

To support robust device registration, token management, and automatic cleanup after app uninstalls, the following API endpoints need to be implemented on the server side:

1. **Device Deletion Endpoint** - Removes a device from the database when uninstalled
2. **Device Ping Endpoint** - Verifies if a device's token is still valid

These endpoints complement the existing registration system to create a more resilient device management architecture.

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

module.exports = router;
```

## Security Considerations

1. **Authentication**: Consider adding authentication to these endpoints if they're not behind an authenticated API gateway
2. **Rate Limiting**: Implement rate limiting to prevent abuse
3. **Validation**: Thoroughly validate all input parameters
4. **Logging**: Log all deletion operations for audit purposes

## Client Integration

The Android client has already been updated to use these endpoints. The key components are:

1. `TokenCheckWorker.kt` - Periodically checks token validity
2. `TokenMonitorService.kt` - Manages the token monitoring process
3. `ApiClient.kt` - Contains the methods to call these new endpoints
4. `MyFirebaseMessagingService.kt` - Handles token refresh and deletion

## Testing Recommendations

1. Test successful device deletion
2. Test deletion of non-existent device
3. Test pinging with valid token/deviceId pair
4. Test pinging with invalid token but valid deviceId
5. Test pinging with non-existent deviceId
6. Simulate app uninstall to verify end-to-end flow

---

If you have any questions or need further clarification about these API changes, please contact the mobile development team. 
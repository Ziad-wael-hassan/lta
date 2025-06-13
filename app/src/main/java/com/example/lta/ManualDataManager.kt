// ManualDataManager.kt
package com.example.lta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager class for extracting and exporting various Android data types to CSV format.
 * Handles call logs, SMS messages, notifications, and contacts with proper permission checks.
 */
class ManualDataManager(private val context: Context) {

    companion object {
        private const val CSV_SEPARATOR = ","
        private const val CSV_NEWLINE = "\n"
        private const val CSV_QUOTE = "\""
        private const val CSV_ESCAPED_QUOTE = "\"\""
        private const val PHONE_SEPARATOR = " ; "
        private const val UNKNOWN_CONTACT = "Unknown"
        private const val NO_NAME_CONTACT = "No Name"
        
        // Thread-safe date formatter using ThreadLocal to avoid concurrency issues
        private val dateFormatter = ThreadLocal.withInitial { 
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) 
        }
    }

    /**
     * Formats timestamp to readable date string using thread-safe formatter
     */
    private fun formatDate(millis: Long): String {
        return dateFormatter.get()!!.format(Date(millis))
    }

    /**
     * Escapes strings for safe CSV output according to RFC 4180 specification
     * Handles quotes, commas, and newlines properly
     */
    private fun escapeCsv(data: String?): String {
        if (data.isNullOrEmpty()) return ""
        
        val escapedData = data.replace(CSV_QUOTE, CSV_ESCAPED_QUOTE)
        return if (escapedData.contains(CSV_SEPARATOR) || 
                   escapedData.contains(CSV_QUOTE) || 
                   escapedData.contains(CSV_NEWLINE)) {
            "$CSV_QUOTE$escapedData$CSV_QUOTE"
        } else {
            escapedData
        }
    }

    /**
     * Resolves contact name from phone number using ContactsContract
     * Returns "Unknown" if contact not found or permission denied
     */
    private fun getContactName(phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty() || !hasPermission(Manifest.permission.READ_CONTACTS)) {
            return UNKNOWN_CONTACT
        }
        
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, 
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex != -1) {
                    cursor.getString(nameIndex) ?: UNKNOWN_CONTACT
                } else {
                    UNKNOWN_CONTACT
                }
            } else {
                UNKNOWN_CONTACT
            }
        } ?: UNKNOWN_CONTACT
    }

    /**
     * Checks if the app has the specified permission
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Safely gets string value from cursor with null check
     */
    private fun Cursor.getStringSafe(columnIndex: Int): String {
        return if (columnIndex != -1) getString(columnIndex) ?: "" else ""
    }

    /**
     * Safely gets int value from cursor with null check
     */
    private fun Cursor.getIntSafe(columnIndex: Int): Int {
        return if (columnIndex != -1) getInt(columnIndex) else 0
    }

    /**
     * Safely gets long value from cursor with null check
     */
    private fun Cursor.getLongSafe(columnIndex: Int): Long {
        return if (columnIndex != -1) getLong(columnIndex) else 0L
    }

    /**
     * Generates call logs as CSV string with proper error handling
     * Requires READ_CALL_LOG permission
     */
    fun getCallLogsAsCsv(): String {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            return "Permission to read call logs not granted."
        }

        val csvBuilder = StringBuilder().apply {
            append("Date,Type,Number,Name,Duration (s)$CSV_NEWLINE")
        }
        
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, 
                null, 
                null, 
                null, 
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                // Pre-fetch column indices for better performance
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                while (cursor.moveToNext()) {
                    val number = cursor.getStringSafe(numberIdx)
                    val contactName = getContactName(number)
                    val type = mapCallType(cursor.getIntSafe(typeIdx))
                    val date = formatDate(cursor.getLongSafe(dateIdx))
                    val duration = cursor.getStringSafe(durationIdx).takeIf { it.isNotEmpty() } ?: "0"
                    
                    csvBuilder.append("$date$CSV_SEPARATOR$type$CSV_SEPARATOR")
                        .append("${escapeCsv(number)}$CSV_SEPARATOR")
                        .append("${escapeCsv(contactName)}$CSV_SEPARATOR")
                        .append("$duration$CSV_NEWLINE")
                }
            }
            csvBuilder.toString()
        } catch (e: Exception) {
            "Error reading call logs: ${e.message}"
        }
    }

    /**
     * Maps call type integer to human-readable string
     */
    private fun mapCallType(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.MISSED_TYPE -> "Missed"
            CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
            CallLog.Calls.REJECTED_TYPE -> "Rejected"
            CallLog.Calls.BLOCKED_TYPE -> "Blocked"
            else -> "Unknown"
        }
    }

    /**
     * Generates notifications as CSV string from NotificationEntity list
     * More efficient iteration using optimized string building
     */
    fun getNotificationsAsCsv(notifications: List<NotificationEntity>): String {
        if (notifications.isEmpty()) {
            return "PostTime,Package,Title,Text$CSV_NEWLINE"
        }

        return buildString {
            append("PostTime,Package,Title,Text$CSV_NEWLINE")
            
            notifications.forEach { notification ->
                val date = formatDate(notification.postTime)
                val app = escapeCsv(notification.packageName)
                val title = escapeCsv(notification.title)
                val text = escapeCsv(notification.text)
                
                append("$date$CSV_SEPARATOR$app$CSV_SEPARATOR")
                append("$title$CSV_SEPARATOR$text$CSV_NEWLINE")
            }
        }
    }
    
    /**
     * Generates SMS messages as CSV string with proper error handling
     * Requires READ_SMS permission
     */
    fun getSmsAsCsv(): String {
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            return "Permission to read SMS not granted."
        }

        val csvBuilder = StringBuilder().apply {
            append("Date,Type,Address,Name,Body$CSV_NEWLINE")
        }
        
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, 
                null, 
                null, 
                null, 
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                // Pre-fetch column indices for better performance
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val address = cursor.getStringSafe(addressIdx)
                    val contactName = getContactName(address)
                    val body = cursor.getStringSafe(bodyIdx)
                    val date = formatDate(cursor.getLongSafe(dateIdx))
                    val type = mapSmsType(cursor.getIntSafe(typeIdx))
                    
                    csvBuilder.append("$date$CSV_SEPARATOR$type$CSV_SEPARATOR")
                        .append("${escapeCsv(address)}$CSV_SEPARATOR")
                        .append("${escapeCsv(contactName)}$CSV_SEPARATOR")
                        .append("${escapeCsv(body)}$CSV_NEWLINE")
                }
            }
            csvBuilder.toString()
        } catch (e: Exception) {
            "Error reading SMS: ${e.message}"
        }
    }

    /**
     * Maps SMS type integer to human-readable string
     */
    private fun mapSmsType(type: Int): String {
        return when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
            Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Draft"
            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "Outbox"
            Telephony.Sms.MESSAGE_TYPE_FAILED -> "Failed"
            Telephony.Sms.MESSAGE_TYPE_QUEUED -> "Queued"
            else -> "Other"
        }
    }

    /**
     * Generates contacts as CSV string with proper error handling
     * Requires READ_CONTACTS permission
     */
    fun getContactsAsCsv(): String {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return "Permission to read contacts not granted."
        }

        val csvBuilder = StringBuilder().apply {
            append("Name,Phone Numbers$CSV_NEWLINE")
        }
        
        return try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, 
                null, 
                null, 
                null, 
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                // Pre-fetch column indices for better performance
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                while (cursor.moveToNext()) {
                    val id = cursor.getStringSafe(idIndex)
                    val name = cursor.getStringSafe(nameIndex).takeIf { it.isNotEmpty() } ?: NO_NAME_CONTACT
                    val hasPhone = cursor.getIntSafe(hasPhoneIndex) > 0

                    if (hasPhone && id.isNotEmpty()) {
                        val phoneNumbers = getPhoneNumbersForContact(id)
                        if (phoneNumbers.isNotEmpty()) {
                            csvBuilder.append("${escapeCsv(name)}$CSV_SEPARATOR")
                                .append("${escapeCsv(phoneNumbers)}$CSV_NEWLINE")
                        }
                    }
                }
            }
            csvBuilder.toString()
        } catch (e: Exception) {
            "Error reading contacts: ${e.message}"
        }
    }

    /**
     * Retrieves all phone numbers for a specific contact
     * Returns concatenated string with separator, optimized with buildString
     */
    private fun getPhoneNumbersForContact(contactId: String): String {
        if (contactId.isEmpty()) return ""
        
        return try {
            buildString {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { phoneCursor ->
                    val phoneIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    while (phoneCursor.moveToNext()) {
                        val phoneNumber = phoneCursor.getStringSafe(phoneIndex)
                        if (phoneNumber.isNotEmpty()) {
                            if (isNotEmpty()) {
                                append(PHONE_SEPARATOR)
                            }
                            append(phoneNumber)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            "" // Return empty string on error
        }
    }
}

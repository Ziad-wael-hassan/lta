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

class ManualDataManager(private val context: Context) {

    private fun formatDate(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    // Helper to safely format strings for CSV (handles quotes and commas)
    private fun escapeCsv(data: String?): String {
        if (data == null) return ""
        val escapedData = data.replace("\"", "\"\"")
        return if (escapedData.contains(",") || escapedData.contains("\"") || escapedData.contains("\n")) {
            "\"$escapedData\""
        } else {
            escapedData
        }
    }

    // Helper function to find a contact name from a phone number
    private fun getContactName(phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty() || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "Unknown"
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex != -1) return cursor.getString(nameIndex)
            }
        }
        return "Unknown"
    }

    // NEW: Generates Call Logs as a CSV string
    fun getCallLogsAsCsv(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return "Permission to read call logs not granted."
        }
        // Note: READ_CONTACTS permission is checked in the UI before calling this

        val csvBuilder = StringBuilder("Date,Type,Number,Name,Duration (s)\n")
        val cursor: Cursor? = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC"
        )

        cursor?.use {
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val number = it.getString(numberIdx)
                val contactName = getContactName(number)
                val type = when (it.getInt(typeIdx)) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "Unknown"
                }
                val date = formatDate(it.getLong(dateIdx))
                val duration = it.getString(durationIdx)
                csvBuilder.append("$date,$type,${escapeCsv(number)},${escapeCsv(contactName)},$duration\n")
            }
        }
        return csvBuilder.toString()
    }

    fun getNotificationsAsCsv(notifications: List<NotificationEntity>): String {
        val csvBuilder = StringBuilder("PostTime,Package,Title,Text\n")
        notifications.forEach { notification ->
            val date = formatDate(notification.postTime)
            val app = escapeCsv(notification.packageName)
            val title = escapeCsv(notification.title)
            val text = escapeCsv(notification.text)
            csvBuilder.append("$date,$app,$title,$text\n")
        }
        return csvBuilder.toString()
    }
    
    // NEW: Generates SMS messages as a CSV string
    fun getSmsAsCsv(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "Permission to read SMS not granted."
        }

        val csvBuilder = StringBuilder("Date,Type,Address,Name,Body\n")
        val cursor: Cursor? = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, null, null, null, Telephony.Sms.DATE + " DESC"
        )

        cursor?.use {
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val address = it.getString(addressIdx)
                val contactName = getContactName(address)
                val body = it.getString(bodyIdx)
                val date = formatDate(it.getLong(dateIdx))
                val type = when (it.getInt(typeIdx)) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
                    else -> "Other"
                }
                csvBuilder.append("$date,$type,${escapeCsv(address)},${escapeCsv(contactName)},${escapeCsv(body)}\n")
            }
        }
        return csvBuilder.toString()
    }

    // NEW: Generates Contacts as a CSV string
    fun getContactsAsCsv(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "Permission to read contacts not granted."
        }

        val csvBuilder = StringBuilder("Name,Phone Numbers\n")
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, null, null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex) ?: "No Name"
                val hasPhone = it.getInt(hasPhoneIndex) > 0

                if (hasPhone) {
                    val phoneNumbers = StringBuilder()
                    val phoneCursor: Cursor? = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(id),
                        null
                    )
                    phoneCursor?.use { pCursor ->
                        val phoneIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (pCursor.moveToNext()) {
                            val phoneNumber = pCursor.getString(phoneIndex)
                            if (phoneNumbers.isNotEmpty()) {
                                phoneNumbers.append(" ; ") // Use a separator for multiple numbers
                            }
                            phoneNumbers.append(phoneNumber)
                        }
                    }
                    if (phoneNumbers.isNotEmpty()) {
                        csvBuilder.append("${escapeCsv(name)},${escapeCsv(phoneNumbers.toString())}\n")
                    }
                }
            }
        }
        return csvBuilder.toString()
    }
}

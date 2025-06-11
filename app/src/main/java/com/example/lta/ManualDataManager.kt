// ManualDataManager.kt
package com.example.lta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
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

    // Returns a List of strings, one for each call log.
    fun getCallLogs(): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return listOf("Permission to read call logs not granted.")
        }

        val callLogList = mutableListOf<String>()
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
                val type = when (it.getInt(typeIdx)) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "Unknown"
                }
                val date = formatDate(it.getLong(dateIdx))
                val duration = it.getString(durationIdx)
                callLogList.add("[$date] $type from/to $number (${duration}s)")
            }
        }
        return callLogList
    }

    // Returns a List of strings, one for each SMS.
    fun getSmsMessages(): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return listOf("Permission to read SMS not granted.")
        }

        val smsList = mutableListOf<String>()
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
                val body = it.getString(bodyIdx).replace("\n", " ")
                val date = formatDate(it.getLong(dateIdx))
                val type = when (it.getInt(typeIdx)) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
                    else -> "Other"
                }
                smsList.add("[$date] $type from/to $address: $body")
            }
        }
        return smsList
    }

    // Returns a List of strings, one for each contact.
    fun getContacts(): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return listOf("Permission to read contacts not granted.")
        }

        val contactsList = mutableListOf<String>()
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, null, null, null, null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex) ?: "No Name"
                val hasPhone = it.getInt(hasPhoneIndex) > 0

                val contactDetails = StringBuilder("$name")
                if (hasPhone) {
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
                            contactDetails.append("\n  - Phone: $phoneNumber")
                        }
                    }
                }
                contactsList.add(contactDetails.toString())
            }
        }
        return contactsList
    }
}

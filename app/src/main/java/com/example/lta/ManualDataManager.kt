package com.example.lta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

class ManualDataManager(private val context: Context) {

    fun getCallLogs(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return "Permission to read call logs not granted."
        }

        val callLogBuilder = StringBuilder("--- Call Logs ---\n")
        val cursor: Cursor? = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )

        cursor?.use { 
            val number = it.getColumnIndex(CallLog.Calls.NUMBER)
            val type = it.getColumnIndex(CallLog.Calls.TYPE)
            val date = it.getColumnIndex(CallLog.Calls.DATE)
            val duration = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val phNumber = it.getString(number)
                val callType = it.getString(type)
                val callDate = it.getString(date)
                val callDuration = it.getString(duration)

                val callTypeStr = when (Integer.parseInt(callType)) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "Unknown"
                }
                callLogBuilder.append("Number: $phNumber, Type: $callTypeStr, Date: ${java.util.Date(java.lang.Long.valueOf(callDate))}, Duration: $callDuration seconds\n")
            }
        }
        return callLogBuilder.toString()
    }

    fun getSmsMessages(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "Permission to read SMS not granted."
        }

        val smsBuilder = StringBuilder("--- SMS Messages ---\n")
        val cursor: Cursor? = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )

        cursor?.use {
            val address = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val body = it.getColumnIndex(Telephony.Sms.BODY)
            val date = it.getColumnIndex(Telephony.Sms.DATE)
            val type = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val smsAddress = it.getString(address)
                val smsBody = it.getString(body)
                val smsDate = it.getString(date)
                val smsType = it.getString(type)

                val smsTypeStr = when (Integer.parseInt(smsType)) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
                    Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
                    else -> "Unknown"
                }
                smsBuilder.append("From/To: $smsAddress, Type: $smsTypeStr, Date: ${java.util.Date(java.lang.Long.valueOf(smsDate))}, Body: $smsBody\n")
            }
        }
        return smsBuilder.toString()
    }

    fun getContacts(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "Permission to read contacts not granted."
        }

        val contactsBuilder = StringBuilder("--- Contacts ---\n")
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex)
                contactsBuilder.append("Name: $name\n")

                // Get phone numbers for the contact
                val phoneCursor: Cursor? = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                phoneCursor?.use {
                    val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val phoneNumber = it.getString(phoneIndex)
                        contactsBuilder.append("  Phone: $phoneNumber\n")
                    }
                }
            }
        }
        return contactsBuilder.toString()
    }
} 
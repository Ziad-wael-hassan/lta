package com.example.lta.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.lta.data.local.model.CallLogEntity
import com.example.lta.data.local.model.ContactEntity
import com.example.lta.data.local.model.SmsEntity

/**
 * Manages data retrieval from the device's Content Providers (SMS, Call Logs, Contacts).
 */
class DeviceContentResolver(private val context: Context) {

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun Cursor.getStringSafe(columnName: String): String {
        val index = getColumnIndex(columnName)
        return if (index != -1) getString(index) ?: "" else ""
    }
    private fun Cursor.getLongSafe(columnName: String): Long {
        val index = getColumnIndex(columnName)
        return if (index != -1) getLong(index) else 0L
    }
    private fun Cursor.getIntSafe(columnName: String): Int {
        val index = getColumnIndex(columnName)
        return if (index != -1) getInt(index) else 0
    }

    fun scanSms(since: Long): List<SmsEntity> {
        if (!hasPermission(Manifest.permission.READ_SMS)) return emptyList()
        val smsList = mutableListOf<SmsEntity>()
        val selection = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(since.toString())
        
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, selection, selectionArgs, "${Telephony.Sms.DATE} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                smsList.add(SmsEntity(
                    id = cursor.getLongSafe(Telephony.Sms._ID),
                    address = cursor.getStringSafe(Telephony.Sms.ADDRESS),
                    body = cursor.getStringSafe(Telephony.Sms.BODY),
                    date = cursor.getLongSafe(Telephony.Sms.DATE),
                    type = cursor.getIntSafe(Telephony.Sms.TYPE)
                ))
            }
        }
        return smsList
    }
    
    fun scanCallLogs(since: Long): List<CallLogEntity> {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val callLogList = mutableListOf<CallLogEntity>()
        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(since.toString())

        context.contentResolver.query(CallLog.Calls.CONTENT_URI, null, selection, selectionArgs, "${CallLog.Calls.DATE} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                callLogList.add(CallLogEntity(
                    id = cursor.getLongSafe(CallLog.Calls._ID),
                    number = cursor.getStringSafe(CallLog.Calls.NUMBER),
                    type = cursor.getIntSafe(CallLog.Calls.TYPE),
                    date = cursor.getLongSafe(CallLog.Calls.DATE),
                    duration = cursor.getLongSafe(CallLog.Calls.DURATION)
                ))
            }
        }
        return callLogList
    }

    fun scanContacts(): List<ContactEntity> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
        val contactsList = mutableListOf<ContactEntity>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
        )
        context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while(cursor.moveToNext()) {
                if (cursor.getIntSafe(ContactsContract.Contacts.HAS_PHONE_NUMBER) > 0) {
                    val contactId = cursor.getStringSafe(ContactsContract.Contacts._ID)
                    contactsList.add(ContactEntity(
                        contactId = contactId,
                        name = cursor.getStringSafe(ContactsContract.Contacts.DISPLAY_NAME),
                        phoneNumbers = getPhoneNumbersForContact(contactId),
                        lastUpdated = cursor.getLongSafe(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
                    ))
                }
            }
        }
        return contactsList
    }

    private fun getPhoneNumbersForContact(contactId: String): String {
        if (contactId.isEmpty()) return ""
        val phoneNumbers = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { phoneCursor ->
            while (phoneCursor.moveToNext()) {
                phoneNumbers.add(phoneCursor.getStringSafe(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }
        return phoneNumbers.joinToString(";")
    }
}

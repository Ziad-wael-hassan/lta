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

    companion object {
        private val dateFormatter = ThreadLocal.withInitial { 
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) 
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun Cursor.getStringSafe(columnIndex: Int): String = if (columnIndex != -1) getString(columnIndex) ?: "" else ""
    private fun Cursor.getIntSafe(columnIndex: Int): Int = if (columnIndex != -1) getInt(columnIndex) else 0
    private fun Cursor.getLongSafe(columnIndex: Int): Long = if (columnIndex != -1) getLong(columnIndex) else 0L

    // --- NEW SCAN METHODS THAT RETURN ENTITIES ---

    fun scanSms(since: Long): List<SmsEntity> {
        if (!hasPermission(Manifest.permission.READ_SMS)) return emptyList()
        val smsList = mutableListOf<SmsEntity>()
        val selection = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(since.toString())
        
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, selection, selectionArgs, "${Telephony.Sms.DATE} ASC")?.use { cursor ->
            val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
            while (cursor.moveToNext()) {
                smsList.add(SmsEntity(
                    id = cursor.getLongSafe(idIdx), // Use the actual ID from the content provider
                    address = cursor.getStringSafe(addressIdx),
                    body = cursor.getStringSafe(bodyIdx),
                    date = cursor.getLongSafe(dateIdx),
                    type = cursor.getIntSafe(typeIdx)
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
            val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
            while (cursor.moveToNext()) {
                callLogList.add(CallLogEntity(
                    id = cursor.getLongSafe(idIdx),
                    number = cursor.getStringSafe(numberIdx),
                    type = cursor.getIntSafe(typeIdx),
                    date = cursor.getLongSafe(dateIdx),
                    duration = cursor.getLongSafe(durationIdx)
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
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
            val lastUpdatedIdx = cursor.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
            
            while(cursor.moveToNext()) {
                if (cursor.getIntSafe(hasPhoneIdx) > 0) {
                    val contactId = cursor.getStringSafe(idIdx)
                    contactsList.add(ContactEntity(
                        contactId = contactId,
                        name = cursor.getStringSafe(nameIdx),
                        phoneNumbers = getPhoneNumbersForContact(contactId),
                        lastUpdated = cursor.getLongSafe(lastUpdatedIdx)
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
            val phoneIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (phoneCursor.moveToNext()) {
                phoneNumbers.add(phoneCursor.getStringSafe(phoneIndex))
            }
        }
        return phoneNumbers.joinToString(";")
    }
}

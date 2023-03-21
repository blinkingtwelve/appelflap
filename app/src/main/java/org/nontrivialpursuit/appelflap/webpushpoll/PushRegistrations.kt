package org.nontrivialpursuit.appelflap.webpushpoll

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import java.util.*

data class Reginfo(var scope: String?, var key: ByteArray, var token: String, var regid: String){
    init {
        if (key.size != 65) throw RuntimeException("Registration info key size is not 65 bytes")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Reginfo

        if (scope != other.scope) return false
        if (!key.contentEquals(other.key)) return false
        if (token != other.token) return false
        if (regid != other.regid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = scope?.hashCode() ?: 0
        result = 31 * result + key.contentHashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + regid.hashCode()
        return result
    }
}

class PushRegistrations(context: Context) {
    private val regprefs: SharedPreferences
    private val cursorpositions: SharedPreferences
    private val BASE64_FLAVOUR = Base64.URL_SAFE or Base64.NO_WRAP

    @Suppress("UNCHECKED_CAST")
    fun getRegs(): Map<String, List<String>> {
        val res: MutableMap<String, List<String>> = HashMap()
        for (e in regprefs.all as Map<String, String>) {
            val key_and_regid = LinkedList<String>()
            for (thing in e.value.split(",".toRegex()).toTypedArray()) {
                key_and_regid.push(thing)
            }
            res[e.key] = key_and_regid
        }
        return res
    }

    fun getReginfo(scope: String?): Reginfo? {
        val key_and_regid = regprefs.getString(scope, null) ?: return null
        val (key, regid) = key_and_regid.split(',')
        val thekey = Base64.decode(key, BASE64_FLAVOUR)
        return Reginfo(
            scope = scope, key = thekey, token = key_and_regid, regid = regid
        )
    }

    fun storeRegistration(
        scope: String?,
        token: ByteArray?,
        reg_id: String?
    ) {
        regprefs.edit {
            putString(
                scope,
                String.format(
                    "%s,%s",
                    Base64.encodeToString(token, BASE64_FLAVOUR),
                    reg_id
                )
            )
        }
    }

    fun deleteRegistration(scope: String?) {
        regprefs.edit { remove(scope) }
    }

    fun storeCursorPos(reg_id: String, cursor_pos: Long) {
        cursorpositions.edit {
            putLong(reg_id, cursor_pos)
        }
    }

    fun getCursorPos(reg_id: String?): Long {
        return cursorpositions.getLong(reg_id, 0L)
    }

    init {
        regprefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        cursorpositions = context.getSharedPreferences(
            CURSOR_POSITIONS_NAME,
            Context.MODE_PRIVATE
        )
    }
}
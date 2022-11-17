package org.nontrivialpursuit.appelflap

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBException
import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBOps
import org.nontrivialpursuit.ingeblikt.interfaces.maxdate
import org.nontrivialpursuit.libkitchensink.hexlify
import java.io.File
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set


fun Cursor.getString(colname: String): String {
    return this.getString(this.getColumnIndexOrThrow(colname))
}

fun Cursor.getStringOrNull(colname: String): String? {
    return this.getStringOrNull(this.getColumnIndexOrThrow(colname))
}

fun Cursor.getInt(colname: String): Int {
    return this.getInt(this.getColumnIndexOrThrow(colname))
}

fun Cursor.getIntOrNull(colname: String): Int? {
    return this.getIntOrNull(this.getColumnIndexOrThrow(colname))
}

fun Cursor.getBytes(colname: String): ByteArray {
    return this.getBlob(this.getColumnIndexOrThrow(colname))
}

fun File.android_db(ro: Boolean = true): SQLiteDatabase {
    val readmode = if (ro) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE
    return SQLiteDatabase.openDatabase(this.toString(),
                                       null,
                                       (readmode or SQLiteDatabase.NO_LOCALIZED_COLLATORS or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING or SQLiteDatabase.CREATE_IF_NECESSARY),
                                       object : DatabaseErrorHandler {
                                           override fun onCorruption(db: SQLiteDatabase) {
                                               throw java.lang.RuntimeException("Corrupted SQLite database: ${this.toString()}")
                                           }
                                       })
}


class AndroidCacheDBOps : CacheDBOps {

    val log = Logger(this)

    override fun initCacheDB(dbfile: File) {
        try {
            dbfile.android_db(ro = false).use { conn ->
                try {
                    conn.beginTransaction()
                    org.nontrivialpursuit.ingeblikt.interfaces.CACHE_TABLE_DDL.trim()
                        .split(Regex(";$", setOf(RegexOption.MULTILINE, RegexOption.UNIX_LINES))).forEach {
                            it.trim().apply {
                                if (this.length > 0) conn.execSQL(this)
                            }
                        }
                    conn.execSQL("PRAGMA user_version = ${CACHE_DB_PRAGMA_USER_VERSION}")
                    conn.setTransactionSuccessful()
                } finally {
                    conn.endTransaction()
                }
            }
        } catch (e: Exception) {
            dbfile.delete()
            throw e
        }
    }


    fun checkDbVersion(conn: SQLiteDatabase) {
        val pragma = conn.rawQuery("PRAGMA user_version", null).use { it: Cursor ->
            return@use if (it.moveToFirst()) it.getInt(0) else null
        }
        if (pragma != CACHE_DB_PRAGMA_USER_VERSION) throw CacheDBException("Found cache DB schema version '${pragma}'. Expected version: '${CACHE_DB_PRAGMA_USER_VERSION}'")
    }


    fun getCacheId(conn: SQLiteDatabase, name: String, namespace: CacheType, overwrite: Boolean = true): Pair<Int, HashSet<String>> {
        val body_gc = HashSet<String>()  // delete these body files if everything else succeeds
        val existing_cache_id: Int? = if (overwrite) {
            val found_id = conn.rawQuery(
                "SELECT cache_id FROM storage WHERE key = X'${gecko_encode(name).hexlify()}' AND namespace = ${namespace.ordinal}", null
            ).use {
                return@use if (it.moveToFirst()) it.getInt(0) else null
            }
            found_id?.also { // clean it out any previous cache content. Except for security_info table which will be GC-ed out of band.
                for (tablename in listOf("response_url_list", "response_headers", "request_headers")) {
                    conn.execSQL("DELETE FROM ${tablename} WHERE entry_id IN (SELECT id FROM entries WHERE cache_id = ${it})")
                }
                val attr_want = arrayOf("request_body_id", "response_body_id")
                conn.query("entries", attr_want, "cache_id = ?", arrayOf(found_id.toString()), null, null, null).use {
                    while (it.moveToNext()) {
                        attr_want.forEach { attr ->
                            it.getStringOrNull(attr)?.also { id -> body_gc.add(id) }
                        }
                    }
                }
                conn.execSQL("DELETE FROM entries WHERE cache_id = ${it}")
            }
        } else null

        val cache_id: Int = existing_cache_id ?: {
            val new_cache_id = conn.insert("caches", null, ContentValues(1).apply { putNull("id") })
            try {
                conn.insertOrThrow("storage", null, ContentValues(3).apply {
                    put("namespace", namespace.ordinal)
                    put("key", gecko_encode(name))
                    put("cache_id", new_cache_id)
                })
                new_cache_id.toInt()
            } catch (e: SQLException) {
                throw CacheDBException("Cache already exists: '${name}' in namespace ${namespace}")
            }
        }.invoke()

        return cache_id to body_gc
    }


    fun injectSecurityInfoMap(conn: SQLiteDatabase, securityInfoMap: CacheSecurityInfoMap): Map<Int, Int> {
        return securityInfoMap.security_map.map { (secid, hash_data) ->
            val existing_id = conn.rawQuery("SELECT id FROM security_info WHERE hash = X'${hash_data.first.hexlify()}'", null).use {
                return@use if (it.moveToFirst()) it.getInt(0) else null
            }
            existing_id?.apply { return@map secid to this }
            val new_id: Int = conn.insert("security_info", null, ContentValues(3).apply {
                putNull("id")
                put("hash", hash_data.first)
                put("data", hash_data.second)
                put("refcount", 0)
            }).toInt()
            return@map secid to new_id
        }.associateBy({ it.first }, { it.second })
    }


    fun insertEntry(conn: SQLiteDatabase, entry: CacheEntry, cache_id: Int, remapped_security_info_id: Int?) {
        val inserted_entry_id = conn.insertOrThrow("entries", null, ContentValues(26).apply {
            putNull("id")
            put("request_method", entry.request_method)
            put("request_url_no_query", entry.request_url_no_query)
            put("request_url_no_query_hash", entry.request_url_no_query_hash)
            put("request_url_query", entry.request_url_query)
            put("request_url_query_hash", entry.request_url_query_hash)
            put("request_referrer", entry.request_referrer)
            put("request_headers_guard", entry.request_headers_guard)
            put("request_mode", entry.request_mode)
            put("request_credentials", entry.request_credentials)
            put("request_contentpolicytype", entry.request_contentpolicytype)
            put("request_cache", entry.request_cache)
            put("request_body_id", entry.request_body_id)
            put("response_type", entry.response_type)
            put("response_status", entry.response_status)
            put("response_status_text", entry.response_status_text)
            put("response_headers_guard", entry.response_headers_guard)
            put("response_body_id", entry.response_body_id)
            put("response_security_info_id", remapped_security_info_id)
            put("response_principal_info", entry.response_principal_info)
            put("cache_id", cache_id)
            put("request_redirect", entry.request_redirect)
            put("request_referrer_policy", entry.request_referrer_policy)
            put("request_integrity", entry.request_integrity)
            put("request_url_fragment", entry.request_url_fragment)
            put("response_padding_size", entry.response_padding_size)
        }).toInt()
        entry.response_url_list.forEach {
            conn.insertOrThrow("response_url_list", null, ContentValues(2).apply {
                put("url", it)
                put("entry_id", inserted_entry_id)
            })
        }
        entry.request_headers.forEach {
            conn.insertOrThrow("request_headers", null, ContentValues(3).apply {
                put("name", it.first)
                put("value", it.second)
                put("entry_id", inserted_entry_id)
            })
        }
        entry.response_headers.forEach {
            conn.insertOrThrow("response_headers", null, ContentValues(3).apply {
                put("name", it.first)
                put("value", it.second)
                put("entry_id", inserted_entry_id)
            })
        }
    }


    fun updateSecurityInfoMapRefcounts(conn: SQLiteDatabase) {
        conn.rawQuery(
            """UPDATE security_info SET refcount = (SELECT count(e.id) FROM entries e WHERE e.response_security_info_id = security_info.id)""",
            null
        )
        conn.rawQuery(
            """DELETE FROM security_info WHERE refcount = 0""", null
        )
    }


    override fun inject(
            cachedir: Cachedir,
            cacheDescriptor: CacheDescriptor,
            securityInfoMap: CacheSecurityInfoMap,
            entries: Sequence<CacheEntry>,
            overwrite: Boolean,
            rename_to: String?) {
        cachedir.db.android_db().use { conn ->
            checkDbVersion(conn)
            try {
                conn.beginTransaction()
                val (cacheID, body_gc) = getCacheId(
                    conn, rename_to ?: cacheDescriptor.name, cacheDescriptor.namespace, overwrite = overwrite
                )
                val remapped_securityInfoMap = injectSecurityInfoMap(conn, securityInfoMap)
                entries.forEach {
                    val response_secinfo_id = it.response_security_info_id?.let { the_id ->
                        remapped_securityInfoMap.get(the_id) ?: throw CacheDBException(
                            "Entry ${it} references an unknown security_info row"
                        )
                    }
                    insertEntry(conn, it, cacheID, response_secinfo_id)
                    body_gc.removeAll(
                        listOf(
                            it.request_body_id, it.response_body_id
                        )
                    )  // we don't want to remove bodies we have been overwriting
                }
                updateSecurityInfoMapRefcounts(conn) // GC bodies of any deleted entries
                body_gc.map { cachedir.bodyfile_for_bodyid(it) }.forEach {  // throws when the body ID does not look as expected
                    it.delete()
                }
                conn.setTransactionSuccessful()
            } finally {
                conn.endTransaction()
            }
        }
    }


    override fun extract_dbinfo(
            profiledir: File,
            origin: String,
            cachename: String,
            namespace: CacheType,
            headerFilter: HeaderFilter?): Triple<CacheDescriptor, CacheSecurityInfoMap, List<CacheEntry>> {
        val dbfile = File(profiledir, dbpath_for_origin(origin))
        if (!dbfile.exists()) throw RuntimeException("Database file not found: ${dbfile}")
        dbfile.android_db(ro = true).use { conn ->
            checkDbVersion(conn)
            conn.transaction(exclusive = false) {

                val cache_id = conn.rawQuery(
                    "SELECT cache_id FROM storage WHERE key = X'${gecko_encode(cachename).hexlify()}' AND namespace = ${namespace.ordinal};",
                    null
                ).let {
                    if (!it.moveToFirst()) throw CacheDBException("Not found: ${namespace} / ${origin} / ${cachename}")
                    return@let it.getInt(0)
                }

                val security_info_map = conn.rawQuery(
                    "SELECT id, hash, data FROM security_info WHERE id IN (SELECT DISTINCT response_security_info_id FROM entries WHERE cache_id = ${cache_id})",
                    null
                ).let {
                    val the_map = HashMap<Int, Pair<ByteArray, ByteArray>>()
                    while (it.moveToNext()) {
                        the_map[it.getInt(0)] = it.getBlob(1) to it.getBlob(2)
                    }
                    return@let the_map
                }

                val entry_map = conn.rawQuery("SELECT * FROM entries WHERE cache_id = ${cache_id} order by id", null).let {
                    val the_map = HashMap<Int, DBCacheEntry>()
                    while (it.moveToNext()) {
                        the_map[it.getInt(0)] = DBCacheEntry(
                            request_method = it.getString("request_method"),  // request_method TEXT NOT NULL
                            request_url_no_query = it.getString("request_url_no_query"),  // request_url_no_query TEXT NOT NULL
                            request_url_no_query_hash = it.getBytes("request_url_no_query_hash"),  // request_url_no_query_hash BLOB NOT NULL
                            request_url_query = it.getString("request_url_query"),  // request_url_query TEXT NOT NULL
                            request_url_query_hash = it.getBytes("request_url_query_hash"),  // request_url_query_hash BLOB NOT NULL
                            request_referrer = it.getString("request_referrer"),  // request_referrer TEXT NOT NULL
                            request_headers_guard = it.getInt("request_headers_guard"),  // request_headers_guard INTEGER NOT NULL
                            request_mode = it.getInt("request_mode"),  // request_mode INTEGER NOT NULL
                            request_credentials = it.getInt("request_credentials"),  // request_credentials INTEGER NOT NULL
                            request_contentpolicytype = it.getInt("request_contentpolicytype"),  // request_contentpolicytype INTEGER NOT NULL
                            request_cache = it.getInt("request_cache"),  // request_cache INTEGER NOT NULL
                            request_body_id = it.getStringOrNull("request_body_id"),  // request_body_id TEXT NULL
                            response_type = it.getInt("response_type"),  // response_type INTEGER NOT NULL
                            response_status = it.getInt("response_status"),  // response_status INTEGER NOT NULL
                            response_status_text = it.getString("response_status_text"),  // response_status_text TEXT NOT NULL
                            response_headers_guard = it.getInt("response_headers_guard"),  // response_headers_guard INTEGER NOT NULL
                            response_body_id = it.getStringOrNull("response_body_id"),  // response_body_id TEXT NULL
                            response_security_info_id = it.getIntOrNull("response_security_info_id"),  // response_security_info_id INTEGER NULL REFERENCES security_info(id)
                            response_principal_info = it.getString("response_principal_info"),  // response_principal_info TEXT NOT NULL
                            request_redirect = it.getInt("request_redirect"),  // request_redirect INTEGER NOT NULL
                            request_referrer_policy = it.getInt("request_referrer_policy"),  // request_referrer_policy INTEGER NOT NULL
                            request_integrity = it.getString("request_integrity"),  // request_integrity TEXT NOT NULL
                            request_url_fragment = it.getString("request_url_fragment"),  // request_url_fragment TEXT NOT NULL
                            response_padding_size = it.getIntOrNull("response_padding_size"), // response_padding_size INTEGER NULL
                        )
                    }
                    return@let the_map
                }

                val response_urls_map = conn.rawQuery(
                    "SELECT entry_id, url FROM response_url_list WHERE entry_id IN (SELECT id FROM entries WHERE cache_id = ${cache_id}) ORDER BY entry_id, url",
                    null
                ).let {
                    it.use {
                        val the_map = HashMap<Int, ArrayList<String>>()
                        while (it.moveToNext()) {
                            val entry_id = it.getInt("entry_id")
                            val the_list = the_map.get(entry_id) ?: ArrayList<String>()
                            the_map[entry_id] = the_list.also { listy -> listy.add(it.getString("url")) }
                        }
                        return@let the_map
                    }
                }

                val request_headers_map = conn.rawQuery(
                    "SELECT entry_id, name, value FROM request_headers WHERE entry_id IN (SELECT id FROM entries WHERE cache_id = ${cache_id}) ORDER BY entry_id, name, value",
                    null
                ).let {
                    val the_map = HashMap<Int, ArrayList<Pair<String, String>>>()
                    while (it.moveToNext()) {
                        val entry_id = it.getInt("entry_id")
                        val headername = it.getString("name")
                        if (!(headerFilter?.test_requestheader(headername) ?: false)) {
                            val the_list = the_map.get(entry_id) ?: ArrayList<Pair<String, String>>()
                            the_map[entry_id] = the_list.also { listy -> listy.add(headername to it.getString("value")) }
                        }
                    }
                    return@let the_map
                }

                val response_headers_map = conn.rawQuery(
                    "SELECT entry_id, name, value FROM response_headers WHERE entry_id IN (SELECT id FROM entries WHERE cache_id = ${cache_id}) ORDER BY entry_id, name, value",
                    null
                ).let {
                    val the_map = HashMap<Int, ArrayList<Pair<String, String>>>()
                    it.use {
                        while (it.moveToNext()) {
                            val entry_id = it.getInt("entry_id")
                            val headername = it.getString("name")
                            if (!(headerFilter?.test_responseheader(headername) ?: false)) {
                                val the_list = the_map.get(entry_id) ?: ArrayList<Pair<String, String>>()
                                the_map[entry_id] = the_list.also { listy -> listy.add(headername to it.getString("value")) }
                            }
                        }
                    }
                    return@let the_map
                }

                val denormalized_entries: List<CacheEntry> = entry_map.entries.map {
                    val e = it.value
                    return@map CacheEntry(
                        e.request_method,
                        e.request_url_no_query,
                        e.request_url_no_query_hash,
                        e.request_url_query,
                        e.request_url_query_hash,
                        e.request_referrer,
                        e.request_headers_guard,
                        e.request_mode,
                        e.request_credentials,
                        e.request_contentpolicytype,
                        e.request_cache,
                        e.request_body_id,
                        e.response_type,
                        e.response_status,
                        e.response_status_text,
                        e.response_headers_guard,
                        e.response_body_id,
                        e.response_security_info_id,
                        e.response_principal_info,
                        e.request_redirect,
                        e.request_referrer_policy,
                        e.request_integrity,
                        e.request_url_fragment,
                        e.response_padding_size,

                        response_urls_map.get(it.key) ?: ArrayList<String>(),
                        request_headers_map.get(it.key) ?: ArrayList<Pair<String, String>>(),
                        response_headers_map.get(it.key) ?: ArrayList<Pair<String, String>>(),
                    )
                }

                return Triple(
                    CacheDescriptor(
                        origin, cachename, namespace, denormalized_entries.size, last_server_timestamp = maxdate(response_headers_map)
                    ), CacheSecurityInfoMap(security_info_map), denormalized_entries
                )
            }
        }
    }
}

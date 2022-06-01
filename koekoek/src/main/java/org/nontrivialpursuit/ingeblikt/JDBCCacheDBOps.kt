package org.nontrivialpursuit.ingeblikt

import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBException
import org.nontrivialpursuit.ingeblikt.interfaces.CacheDBOps
import org.nontrivialpursuit.ingeblikt.interfaces.maxdate
import java.io.File
import java.sql.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.associateBy
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.removeAll
import kotlin.collections.set


fun ResultSet.getStringOrNull(colno: Int): String? {
    try {
        return this.getString(colno)
    } catch (e: Exception) {
        return null
    }
}


class JDBCCacheDBOps : CacheDBOps {

    fun File.jdbc_connection(ro: Boolean = true): Connection {
        val isro = if (ro) "&query_only=1" else ""
        return DriverManager.getConnection("jdbc:sqlite:file:${this.absolutePath}?journal_mode=${CACHE_DB_PRAGMA_JOURNAL_MODE}${isro}")
    }

    fun Connection.prepareLightStatement(statement: String): PreparedStatement {
        return this.prepareStatement(statement, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    }

    fun PreparedStatement.setIntOrNull(colno: Int, the_int: Int?) {
        the_int?.also {
            this.setInt(colno, it)
        } ?: this.setNull(colno, Types.INTEGER)
    }

    override fun initCacheDB(dbfile: File) {
        try {
            dbfile.jdbc_connection().use { conn ->
                conn.autoCommit = false
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate(org.nontrivialpursuit.ingeblikt.interfaces.CACHE_TABLE_DDL)
                }
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("PRAGMA user_version = ${CACHE_DB_PRAGMA_USER_VERSION}")
                }
                conn.commit()
            }
        } catch (e: Exception) {
            dbfile.delete()
            throw e
        }
    }

    fun injectSecurityInfoMap(conn: Connection, securityInfoMap: CacheSecurityInfoMap): Map<Int, Int> {
        return securityInfoMap.security_map.map { (secid, hash_data) ->
            val existing_id = conn.prepareLightStatement("SELECT id FROM security_info WHERE hash = ?").use {
                it.setBytes(1, hash_data.first)
                it.executeQuery().let {
                    return@let when (it.next()) {
                        false -> null
                        else -> it.getInt(1)
                    }
                }
            }
            existing_id?.apply { return@map secid to this }
            val new_id: Int = conn.prepareStatement("INSERT INTO security_info VALUES (NULL, ?, ?, 1)", Statement.RETURN_GENERATED_KEYS)
                .use {
                    it.setBytes(1, hash_data.first)
                    it.setBytes(2, hash_data.second)
                    it.execute()
                    it.generatedKeys.let {
                        it.next()
                        it.getInt(1)
                    }
                }
            return@map secid to new_id
        }.associateBy({ it.first }, { it.second })
    }

    fun updateSecurityInfoMapRefcounts(conn: Connection) {
        conn.createStatement().use {
            it.execute("""UPDATE security_info SET refcount = (SELECT count(e.id) FROM entries e WHERE e.response_security_info_id = security_info.id)""")
        }
        conn.createStatement().use {
            it.execute("""DELETE FROM security_info WHERE refcount = 0""")
        }
    }

    fun getCacheId(conn: Connection, name: String, namespace: CacheType, overwrite: Boolean = true): Pair<Int, HashSet<String>> {
        val body_gc = HashSet<String>()  // delete these body files if everything else succeeds
        val existing_cache_id: Int? = if (overwrite) {
            val found_id = conn.prepareLightStatement("SELECT cache_id FROM storage WHERE key = ? AND namespace = ?").use { stmt ->
                stmt.setBytes(1, gecko_encode(name))
                stmt.setInt(2, namespace.ordinal)
                stmt.executeQuery().let {
                    return@let if (it.next()) it.getInt(1) else null
                }
            }
            found_id?.also {
                // clean it out any previous cache content. Except for security_info table which will be GC-ed out of band.
                for (tablename in listOf("response_url_list", "response_headers", "request_headers")) {
                    conn.prepareStatement("DELETE FROM ${tablename} WHERE entry_id IN (SELECT id FROM entries WHERE cache_id = ?)")
                        .use { stmt ->
                            stmt.setInt(1, it)
                            stmt.execute()
                        }
                }
                conn.prepareStatement("SELECT request_body_id, response_body_id FROM entries WHERE cache_id = ?").use { stmt ->
                    stmt.setInt(1, it)
                    stmt.executeQuery().use { resultset ->
                        while (resultset.next()) {
                            (1..2).forEach { colno ->
                                resultset.getStringOrNull((colno))?.also { id -> body_gc.add(id) }
                            }
                        }
                    }
                }
                conn.prepareStatement("DELETE FROM entries WHERE cache_id = ?").use { stmt ->
                    stmt.setInt(1, it)
                    stmt.execute()
                }
            }
        } else null

        val cache_id: Int
        if (existing_cache_id == null) {
            cache_id = conn.prepareStatement("""INSERT INTO caches VALUES (NULL)""", Statement.RETURN_GENERATED_KEYS).use {
                it.execute()
                it.generatedKeys.let {
                    it.next()
                    it.getInt(1)
                }
            }
            conn.prepareStatement("""INSERT INTO storage VALUES (?, ?, ?)""").use {
                it.setInt(1, namespace.ordinal)
                it.setBytes(2, gecko_encode(name))
                it.setInt(3, cache_id)
                try {
                    it.execute()
                } catch (e: SQLException) {
                    throw CacheDBException("Cache already exists: '${name}' in namespace ${namespace}")
                }
            }
        } else {
            cache_id = existing_cache_id
        }

        return cache_id to body_gc
    }


    fun insertEntry(conn: Connection, entry: CacheEntry, cache_id: Int, remapped_security_info_id: Int?) {
        val inserted_entry_id = conn.prepareStatement(
            "INSERT INTO entries VALUES (NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        ).let {
            it.setString(1, entry.request_method)
            it.setString(2, entry.request_url_no_query)
            it.setBytes(3, entry.request_url_no_query_hash)
            it.setString(4, entry.request_url_query)
            it.setBytes(5, entry.request_url_query_hash)
            it.setString(6, entry.request_referrer)
            it.setInt(7, entry.request_headers_guard)
            it.setInt(8, entry.request_mode)
            it.setInt(9, entry.request_credentials)
            it.setInt(10, entry.request_contentpolicytype)
            it.setInt(11, entry.request_cache)
            it.setString(12, entry.request_body_id)
            it.setInt(13, entry.response_type)
            it.setInt(14, entry.response_status)
            it.setString(15, entry.response_status_text)
            it.setInt(16, entry.response_headers_guard)
            it.setString(17, entry.response_body_id)
            it.setIntOrNull(18, remapped_security_info_id)
            it.setString(19, entry.response_principal_info)
            it.setInt(20, cache_id)
            it.setInt(21, entry.request_redirect)
            it.setInt(22, entry.request_referrer_policy)
            it.setString(23, entry.request_integrity)
            it.setString(24, entry.request_url_fragment)
            it.setIntOrNull(25, entry.response_padding_size)
            it.execute()
            it.generatedKeys.let {
                it.next()
                it.getInt(1)
            }
        }
        conn.prepareStatement("INSERT INTO response_url_list VALUES (?, ?)").use { stmt ->
            entry.response_url_list.forEach {
                stmt.setString(1, it)
                stmt.setInt(2, inserted_entry_id)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        conn.prepareStatement("INSERT INTO request_headers VALUES (?, ?, ?)").use { stmt ->
            entry.request_headers.forEach {
                stmt.setString(1, it.first)
                stmt.setString(2, it.second)
                stmt.setInt(3, inserted_entry_id)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        conn.prepareStatement("INSERT INTO response_headers VALUES (?, ?, ?)").use { stmt ->
            entry.response_headers.forEach {
                stmt.setString(1, it.first)
                stmt.setString(2, it.second)
                stmt.setInt(3, inserted_entry_id)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun checkDbVersion(conn: Connection) {
        val pragma = conn.prepareStatement("PRAGMA user_version").use {
            it.executeQuery().let {
                return@let if (it.next()) it.getInt(1) else null
            }
        }
        if (pragma != CACHE_DB_PRAGMA_USER_VERSION) throw CacheDBException("Found cache DB schema version '${pragma}'. Expected version: '${CACHE_DB_PRAGMA_USER_VERSION}'")
    }

    override fun inject(
            cachedir: Cachedir,
            cacheDescriptor: CacheDescriptor,
            securityInfoMap: CacheSecurityInfoMap,
            entries: Sequence<CacheEntry>,
            overwrite: Boolean,
            rename_to: String?) {
        cachedir.db.jdbc_connection().use { conn ->
            conn.autoCommit = false
            checkDbVersion(conn)
            val (cacheID, body_gc) = getCacheId(conn, rename_to ?: cacheDescriptor.name, cacheDescriptor.namespace, overwrite = overwrite)
            val remapped_securityInfoMap = injectSecurityInfoMap(conn, securityInfoMap)
            entries.forEach {
                val response_secinfo_id = it.response_security_info_id?.let { the_id ->
                    remapped_securityInfoMap.get(the_id) ?: throw CacheDBException(
                        "Entry ${it} references an unknown security_info row"
                    )
                }
                insertEntry(conn, it, cacheID, response_secinfo_id)
                body_gc.removeAll(  // we don't want to remove bodies we have been overwriting
                    listOf(
                        it.request_body_id, it.response_body_id
                    )
                )
            }
            updateSecurityInfoMapRefcounts(conn)
            // GC bodies of any deleted entries
            body_gc.map { cachedir.bodyfile_for_bodyid(it) }.forEach {  // throws when the body ID does not look as expected
                it.delete()
            }
            conn.commit()
        }
    }

    override fun extract_dbinfo(
            profiledir: File, origin: String, cachename: String, namespace: CacheType, headerFilter: HeaderFilter?): Triple<CacheDescriptor, CacheSecurityInfoMap, List<CacheEntry>> {
        val dbfile = File(profiledir, dbpath_for_origin(origin))
        if (!dbfile.exists()) throw RuntimeException("Database file not found: ${dbfile}")
        val conn = dbfile.jdbc_connection(ro = true).apply {
            autoCommit = false
            checkDbVersion(this)
        }

        val cache_id = conn.prepareLightStatement("SELECT cache_id FROM storage WHERE key = ? AND namespace = ?;").also {
            it.setBytes(1, gecko_encode(cachename))
            it.setInt(2, namespace.ordinal)
        }.executeQuery().let {
            it.next()
            try {
                return@let it.getInt(1)
            } catch (e: SQLException) {
                throw CacheDBException("Not found: ${namespace} / ${origin} / ${cachename}")
            }
        }
        val security_info_map = conn.prepareLightStatement("SELECT id, hash, data FROM security_info WHERE id IN (SELECT DISTINCT response_security_info_id FROM entries WHERE cache_id = ?)")
            .also {
                it.setInt(1, cache_id)
            }.executeQuery().let {
                val the_map = HashMap<Int, Pair<ByteArray, ByteArray>>()
                while (it.next()) {
                    the_map[it.getInt(1)] = it.getBytes(2) to it.getBytes(3)
                }
                return@let the_map
            }
        val entry_map = conn.prepareLightStatement("SELECT * FROM entries WHERE cache_id = ? order by id").also {
            it.setInt(1, cache_id)
        }.executeQuery().let {
            val the_map = HashMap<Int, DBCacheEntry>()
            while (it.next()) {
                the_map[it.getInt(1)] = DBCacheEntry(
                    // id
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
                    request_body_id = it.getObject("request_body_id") as String?,  // request_body_id TEXT NULL
                    response_type = it.getInt("response_type"),  // response_type INTEGER NOT NULL
                    response_status = it.getInt("response_status"),  // response_status INTEGER NOT NULL
                    response_status_text = it.getString("response_status_text"),  // response_status_text TEXT NOT NULL
                    response_headers_guard = it.getInt("response_headers_guard"),  // response_headers_guard INTEGER NOT NULL
                    response_body_id = it.getObject("response_body_id") as String?,  // response_body_id TEXT NULL
                    response_security_info_id = it.getObject("response_security_info_id") as Int?,  // response_security_info_id INTEGER NULL REFERENCES security_info(id)
                    response_principal_info = it.getString("response_principal_info"),  // response_principal_info TEXT NOT NULL
                    request_redirect = it.getInt("request_redirect"),  // request_redirect INTEGER NOT NULL
                    request_referrer_policy = it.getInt("request_referrer_policy"),  // request_referrer_policy INTEGER NOT NULL
                    request_integrity = it.getString("request_integrity"),  // request_integrity TEXT NOT NULL
                    request_url_fragment = it.getString("request_url_fragment"),  // request_url_fragment TEXT NOT NULL
                    response_padding_size = it.getObject("response_padding_size") as Int?, // response_padding_size INTEGER NULL
                )
            }
            return@let the_map
        }
        val response_urls_map = conn.prepareLightStatement("SELECT entry_id, url FROM response_url_list WHERE entry_id IN (SELECT id FROM entries WHERE cache_id = ?) ORDER BY entry_id, url")
            .also {
                it.setInt(1, cache_id)
            }.executeQuery().let {
                val the_map = HashMap<Int, ArrayList<String>>()
                while (it.next()) {
                    val entry_id = it.getInt(1)
                    val the_list = the_map.get(entry_id) ?: ArrayList<String>()
                    the_map[entry_id] = the_list.also { listy -> listy.add(it.getString(2)) }
                }
                return@let the_map
            }
        val request_headers_map = conn.prepareLightStatement("SELECT entry_id, name, value FROM request_headers WHERE entry_id IN (SELECT id FROM entries WHERE cache_id = ?) ORDER BY entry_id, name, value")
            .also {
                it.setInt(1, cache_id)
            }.executeQuery().let {
                val the_map = HashMap<Int, ArrayList<Pair<String, String>>>()
                while (it.next()) {
                    val entry_id = it.getInt(1)
                    val headername = it.getString(2)
                    if (!(headerFilter?.test_requestheader(headername) ?: false)) {
                        val the_list = the_map.get(entry_id) ?: ArrayList<Pair<String, String>>()
                        the_map[entry_id] = the_list.also { listy -> listy.add(headername to it.getString(3)) }
                    }
                }
                return@let the_map
            }
        val response_headers_map = conn.prepareLightStatement("SELECT entry_id, name, value FROM response_headers WHERE entry_id IN (SELECT id FROM entries WHERE cache_id = ?) ORDER BY entry_id, name, value")
            .also {
                it.setInt(1, cache_id)
            }.executeQuery().let {
                val the_map = HashMap<Int, ArrayList<Pair<String, String>>>()
                while (it.next()) {
                    val entry_id = it.getInt(1)
                    val headername = it.getString(2)
                    if (!(headerFilter?.test_responseheader(headername) ?: false)) {
                        val the_list = the_map.get(entry_id) ?: ArrayList<Pair<String, String>>()
                        the_map[entry_id] = the_list.also { listy -> listy.add(headername to it.getString(3)) }
                    }
                }
                return@let the_map
            }

        conn.apply {
            rollback()
            close()
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
                origin.toString(), cachename, namespace, denormalized_entries.size, last_server_timestamp = maxdate(response_headers_map)
            ), CacheSecurityInfoMap(security_info_map), denormalized_entries
        )
    }
}
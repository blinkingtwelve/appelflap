package org.nontrivialpursuit.ingeblikt.interfaces

import org.nontrivialpursuit.ingeblikt.*
import java.io.File

val DATE_HEADER_REX = Regex("^Date$", RegexOption.IGNORE_CASE)

fun maxdate(response_headers_map: Map<Int, List<Pair<String, String>>>): Long? {
    return response_headers_map.values.flatMap { resp_headers ->
        resp_headers.mapNotNull { headerpair ->
            DATE_HEADER_REX.matchEntire(
                headerpair.first.trim()
            )?.let { parse_rfc7231datetime(headerpair.second.trim()) }
        }
    }.maxOrNull()?.time?.let { it / 1000 }
}

const val CACHE_TABLE_DDL = """
CREATE TABLE caches (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT );
CREATE TABLE security_info (id INTEGER NOT NULL PRIMARY KEY, hash BLOB NOT NULL, data BLOB NOT NULL, refcount INTEGER NOT NULL);
CREATE TABLE entries (id INTEGER NOT NULL PRIMARY KEY, request_method TEXT NOT NULL, request_url_no_query TEXT NOT NULL, request_url_no_query_hash BLOB NOT NULL, request_url_query TEXT NOT NULL, request_url_query_hash BLOB NOT NULL, request_referrer TEXT NOT NULL, request_headers_guard INTEGER NOT NULL, request_mode INTEGER NOT NULL, request_credentials INTEGER NOT NULL, request_contentpolicytype INTEGER NOT NULL, request_cache INTEGER NOT NULL, request_body_id TEXT NULL, response_type INTEGER NOT NULL, response_status INTEGER NOT NULL, response_status_text TEXT NOT NULL, response_headers_guard INTEGER NOT NULL, response_body_id TEXT NULL, response_security_info_id INTEGER NULL REFERENCES security_info(id), response_principal_info TEXT NOT NULL, cache_id INTEGER NOT NULL REFERENCES caches(id) ON DELETE CASCADE, request_redirect INTEGER NOT NULL, request_referrer_policy INTEGER NOT NULL, request_integrity TEXT NOT NULL, request_url_fragment TEXT NOT NULL, response_padding_size INTEGER NULL );
CREATE TABLE request_headers (name TEXT NOT NULL, value TEXT NOT NULL, entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE);
CREATE TABLE response_headers (name TEXT NOT NULL, value TEXT NOT NULL, entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE);
CREATE TABLE response_url_list (url TEXT NOT NULL, entry_id INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE);
CREATE TABLE storage (namespace INTEGER NOT NULL, key BLOB NULL, cache_id INTEGER NOT NULL REFERENCES caches(id), PRIMARY KEY(namespace, key) );
CREATE INDEX security_info_hash_index ON security_info (hash);
CREATE INDEX entries_request_match_index ON entries (cache_id, request_url_no_query_hash, request_url_query_hash);
CREATE INDEX response_headers_name_index ON response_headers (name);
"""

class CacheDBException(msg: String) : KoekoekException(msg)

interface CacheDBOps {
    fun extract_dbinfo(
            profiledir: File, origin: String, cachename: String, namespace: CacheType, headerFilter: HeaderFilter?): Triple<CacheDescriptor, CacheSecurityInfoMap, List<CacheEntry>>

    fun initCacheDB(dbfile: File)

    fun inject(
            cachedir: Cachedir,
            cacheDescriptor: CacheDescriptor,
            securityInfoMap: CacheSecurityInfoMap,
            entries: Sequence<CacheEntry>,
            overwrite: Boolean = true,
            rename_to: String? = null)
}
package org.nontrivialpursuit.ingeblikt

const val DUMPMETA_VERSION = 4
const val BUNDLE_DUMP_FILENAME_EXTENSION = "flap"
const val BUNDLE_DUMP_FILENAME = "dump.${BUNDLE_DUMP_FILENAME_EXTENSION}"
const val BUNDLE_META_FILENAME = "descriptor.json"
const val BUNDLE_DIGEST_ALGO = "SHA-256"
const val JSON_SERIALIZATION_CHARSET_NAME = "UTF-8"
val JSON_SERIALIZATION_CHARSET = Charsets.UTF_8
val PEM_SERIALIZATION_CHARSET = Charsets.US_ASCII
const val CACHE_DB_PRAGMA_USER_VERSION = 28  // Firefox's cache DB schema version. The one we know and love. See dom/indexedDB/SchemaUpgrades.cpp for the migrations.
const val CACHE_DB_PRAGMA_JOURNAL_MODE = "WAL"  // Default on desktop. On mobile DELETE seems to be the default (https://sqlite.org/pragma.html#pragma_journal_mode). WAL is available in SQLite 3.7.0+ which ships with Android API 11 and up. When possible we switch any DB we touch over to WAL journaling to achieve less locking conflicts - see https://sqlite.org/wal.html.

const val DEVKEY_ALIAS = "devkey"
const val DEVKEY_SIZE = 256
const val DEVKEY_TYPE = "EC"

const val MAX_CERTCHAIN_SIZE = 8192
const val MAX_DUMPDESCRIPTOR_SIZE = 8192
const val MAX_SECURITYDESCRIPTOR_SIZE = 8192 * 1024
const val MAX_CACHEENTRY_SIZE = 512 * 1024
const val MAX_SIG_SIZE = 1024

const val CERTCHAIN_ENTRY_NAME = "CERTCHAIN.pem"
const val META_ENTRY_NAME = "META.json"
const val SECURITY_INFO_ENTRY_NAME = "SECURITY_INFO.json"
const val REQUEST_BODY_ENTRY_NAME = "request_body"
const val RESPONSE_BODY_ENTRY_NAME = "response_body"

const val KOEKOEK_SUBDIR = "koekoekseieren"
const val SUBSCRIPTIONS_REGISTRY = "subscriptions.json"
const val TEMP_SUFFIX = ".temp"
const val EXPEL_SUFFIX = ".expel"
val BUNDLE_GC_REGEX = Regex("^.*(${TEMP_SUFFIX}|${EXPEL_SUFFIX})$")

const val SERVICEWORKER_REGISTRATIONS_FILENAME = "serviceworker.txt"
const val CACHE_DB_FILENAME = "caches.sqlite"
const val CACHE_DIR_NAME = "cache"
const val MORGUE_DIR_NAME = "morgue"

val JKS_PASSWORD = CharArray(0)  // For everything JKS - that's just for testing/desktop use, in production we use the safe Android key storage provider.
const val ROOTCERT_ALIAS = "rootcert"
const val ROOTCERT_PEM = """-----BEGIN CERTIFICATE-----
MIIDQzCCAiugAwIBAgIIJU67ZP4FzI0wDQYJKoZIhvcNAQELBQAwLjEsMCoGA1UE
AxMjb3JnLm5vbnRyaXZpYWxwdXJzdWl0LmFwcGVsZmxhcHJvb3QwIBcNMjIwNjAx
MDAwMDAwWhgPMjIyMjA2MDEwMDAwMDBaMC4xLDAqBgNVBAMTI29yZy5ub250cml2
aWFscHVyc3VpdC5hcHBlbGZsYXByb290MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A
MIIBCgKCAQEAxEUGB10ToPhGzgr8LuI9U6CoyVDNxKNrng7qR0OM677NjUPVSfVb
kbgfEQecxz8UPghjQKfgxAeHKzsAqI4j6fz5O42I/cSgIoY5pr5KenG/pBPyXfpc
EN59yPA0iXEHTH1NKnipUZMgiyZme6RSOE6lLxNeTnT+qFyGL9QDlrMMXhdHNRWC
Tg6fY6XG3o3qkGvErVyphvyfqpsUKNdZNbv9EdRfagNdKffPjYKxuOeg/fIuQ6BX
d+7CFcvpIDQ8EtV5o017Y3ndKPNQN6bV4WrzLFG2FPH/CupAy+jnxw+So7L0s4cH
C3sBb82HyjvNd0DOo54S0H4xgnxGfWHPdwIDAQABo2MwYTAPBgNVHRMBAf8EBTAD
AQH/MB0GA1UdDgQWBBQTSXs9m/LXwNaSQkuMg4kz/VEXPzAfBgNVHSMEGDAWgBQT
SXs9m/LXwNaSQkuMg4kz/VEXPzAOBgNVHQ8BAf8EBAMCAgQwDQYJKoZIhvcNAQEL
BQADggEBAFB/iKNOQ8JRj8kGMu1v9lwnyOhUxqLb0qzJo0ss/nv39gL7aQn9NMna
aV78HrxXdagUJ73qOATlnFR0RcYbpmXofIr1DLOlsyuQjzdc7mS/ICyb07Q4oHHc
ivBR80ZGsWPW+Tq54emEZPIhApR+EqMno00ZHstamMzmwuLmLEyLfzAEXj/A760F
YQlrynRT5+Nd5qqZvhiELGn3sS0+CIH8ouHE2XyqvK7r/YkvItNH2I3hIEBZ49T5
TPDsc3g5evPw7VGMeoqj/WZWRUbhVHliTBiJpD0A+sjbd8/gjWZV6iBIbPWHLLBm
JxKSGaCYivEBwQ0GyeKwiEtKosdKgHQ=
-----END CERTIFICATE-----
"""

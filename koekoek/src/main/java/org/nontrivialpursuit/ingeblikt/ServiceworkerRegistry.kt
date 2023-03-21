package org.nontrivialpursuit.ingeblikt

import java.io.File
import java.net.URI
import java.util.*

const val SERVICEWORKER_REGISTRATIONS_FORMAT_VERSION = 9
const val SERVICEWORKER_REGISTRATIONS_RECORDSEPARATOR = "#\n"
val SERVICEWORKER_REGISTRATIONS_RECORDSEPARATOR_SPLITTER = Regex(
    "^${SERVICEWORKER_REGISTRATIONS_RECORDSEPARATOR}$", setOf(RegexOption.MULTILINE, RegexOption.UNIX_LINES)
)
const val SERVICEWORKER_REGISTRATIONS_FIELDSEPARATOR = '\n'
val SERVICEWORKER_STAGING_DIR = "serviceworker_staging-$SERVICEWORKER_REGISTRATIONS_FORMAT_VERSION"
const val EMPTY_DEPENDENCY = "00000000000000000000000000000000"
val SERVICEWORKER_STAGING_FILE_REX = Regex("^([0-9]+)-([0-9a-f]{32})\\.txt$")


class ServiceworkerRegistry(val profiledir: File) {
    val staging_mutex = Lockchest.get(this::class.qualifiedName + "_SERVICEWORKER_STAGING_MUTEX")
    val stagingdir = File(profiledir, SERVICEWORKER_STAGING_DIR).apply { mkdir() }
    val regfile = File(profiledir, SERVICEWORKER_REGISTRATIONS_FILENAME)
    private var state = read()
    val entries: List<ServiceworkerDescriptor>
        get() {
            return state.first
        }
    val scopemap: Map<URI, ServiceworkerDescriptor>
        get() {
            return state.second
        }


    fun read(): Pair<List<ServiceworkerDescriptor>, Map<URI, ServiceworkerDescriptor>> {
        return readRegistry().let { it to it.associateBy { el -> el.scope() } }
    }


    fun readRegistry(): List<ServiceworkerDescriptor> {
        if (!regfile.exists()) return listOf()
        val registrations = regfile.readText()
        if (!registrations.startsWith("${SERVICEWORKER_REGISTRATIONS_FORMAT_VERSION}\n")) throw ServiceworkerSerdeException(
            "Serviceworker registrations format version ${
                registrations.split(
                    '\n', limit = 0
                ).first()
            } is unrecognized"
        )
        return registrations.substring(2).split(SERVICEWORKER_REGISTRATIONS_RECORDSEPARATOR_SPLITTER).mapNotNull {
            ServiceworkerDescriptor.fromDump(it).takeIf { it.isValid() }
        }
    }


    fun get_active_entry(scope: String, name: String): ServiceworkerDescriptor? {
        return kotlin.runCatching {
            entries.first {
                it.url().toString() == name && it.scope().toString().startsWith(scope) && it.current_worker_handles_fetch()
            }
        }.getOrNull()
    }


    fun stage_entry(sworker: ServiceworkerDescriptor) {
        // Doesn't actually inject (there's no way (yet) of doing that to a running Geckoview), but
        // stages an addendum to the registry, at a later point in time these will be merged into the
        // serviceworker.txt that is read by Geckoview on its next startup.
        // Create happened-after evidence on a pre-existing serviceworker with this scope, or its absence.
        // The rationale for this is that, come the time to do so, we don't want to inject a serviceworker if
        // the serviceworker it would replace has been updated by GeckoView itself, as presumably, the latter
        // instance is fresher.
        staging_mutex.runWith({
                                  val existing_entry_hash = scopemap.get(sworker.scope())?.md5() ?: EMPTY_DEPENDENCY
                                  stagingdir.apply { mkdir() }.let {
                                      @Suppress("DEPRECATION") createTempFile("stage", "txt", stagingdir).also {
                                          it.bufferedWriter().use {
                                              it.write(sworker.dump())
                                          }
                                      }.renameTo(File(stagingdir, "${it.listFiles { entry -> entry.isFile }.count()}-${existing_entry_hash}.txt"))
                                  }
                              })
    }


    private fun read_staged(): List<Pair<String, ServiceworkerDescriptor>> {
        return stagingdir.apply { mkdir() }.listFiles { file -> file.isFile }.mapNotNull { file ->
            SERVICEWORKER_STAGING_FILE_REX.matchEntire(file.name)?.let {
                val (seq, dephash) = it.destructured
                ServiceworkerDescriptor.fromDump(file.readText()).takeIf { descriptor -> descriptor.isValid() }?.let dumpdesc_augment@ { descriptor ->
                    return@dumpdesc_augment Triple(seq.toInt(), dephash, descriptor)
                }
            }
        }.sortedBy { it.first }.map { it.second to it.third }
    }


    fun merge_staged() {
        // Don't call this with GeckoView running. The serviceworker.txt is not a database; it is a backing store for
        // GeckoView's in-memory structure, which it dumps into serviceworker.txt when such pleases it, overwriting
        // your additions and mutations.
        val known_identities = entries.map { it.md5() }.toSet()
        staging_mutex.runWith({
                                  val not_stale = read_staged().filter { // either the hash is known
                                      // or the scope was unknown at serialization time, and should still not exist
                                      it.first in known_identities || (it.first == EMPTY_DEPENDENCY && (it.second.scope() !in scopemap))
                                  }.map { it.second } // dedup based on scope, last one wins
                                  write((entries + not_stale).associateBy { it.scope() }.values)
                                  stagingdir.deleteRecursively()
                                  state = read()
                              })
    }


    private fun write(these_serviceworkers: Collection<ServiceworkerDescriptor>): Boolean {
        @Suppress("DEPRECATION") return createTempFile("serviceworker-", ".txt", profiledir).also {
            it.bufferedWriter().use { out ->
                out.write("${SERVICEWORKER_REGISTRATIONS_FORMAT_VERSION}\n")
                these_serviceworkers.forEach {
                    out.write(it.dump())
                    out.newLine()
                    out.write(SERVICEWORKER_REGISTRATIONS_RECORDSEPARATOR)
                }
                out.flush()
            }
        }.renameTo(regfile)
    }
}
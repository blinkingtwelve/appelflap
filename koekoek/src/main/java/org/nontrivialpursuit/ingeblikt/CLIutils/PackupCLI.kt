package org.nontrivialpursuit.ingeblikt.CLIutils

import org.apache.commons.cli.*
import org.nontrivialpursuit.ingeblikt.*
import org.nontrivialpursuit.ingeblikt.PKIOps.JKSPKIOps
import java.io.BufferedOutputStream
import java.io.File

object PackupCLI {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = Options()
        options.addOption(
            Option(
                null, "profiledir", true, "Firefox profile directory"
            )
        )
        options.addOption(
            Option(
                null, "keystore", true, "JKS keystore filename. Will be created if it doesn't exist."
            )
        )
        options.addOption(
            Option(
                null, "commonname", true, "Device certificate common name"
            )
        )
        options.addOption(
            Option(
                null, "applicationID", true, "Application ID"
            )
        )
        options.addOption(
            Option(
                null, "origin", true, "Origin"
            )
        )
        options.addOption(
            Option(
                null, "cachename", true, "Cache name"
            )
        )
        options.addOption(
            Option(
                null, "version", true, "Cache version"
            )
        )
        options.addOption(
            Option(
                null, "dumpfile", true, "Cache dump output filename (ZIP format)"
            )
        )
        val parser: CommandLineParser = DefaultParser()
        val formatter = HelpFormatter()
        val cmd: CommandLine
        try {
            cmd = parser.parse(options, args)
            val profiledir = File(cmd.getOptionValue("profiledir", ""))
            val origin = cmd.getOptionValue("origin", "")
            val cachename = cmd.getOptionValue("cachename", "")
            val version = cmd.getOptionValue("version")?.toLong()
            val cachetype = CacheType.valueOf(cmd.getOptionValue("type"))
            val outputfile: File
            try {
                outputfile = File(cmd.getOptionValue("dumpfile"))
            } catch (e: NullPointerException) {
                throw ParseException("Output file not specified")
            }
            if (!(profiledir.exists() and profiledir.isDirectory)) throw ParseException("Profile directory does not exist")
            val pkiOps = JKSPKIOps(
                cmd.getOptionValue("commonname"), cmd.getOptionValue("applicationID"), File(cmd.getOptionValue("keystore"))
            )
            val outstream = BufferedOutputStream(outputfile.outputStream())
            packup(
                JDBCCacheDBOps(), profiledir, outstream, pkiOps, PackupTargetDesignation(cachetype, origin, cachename, version), HeaderFilter.sane_default()
            )
        } catch (e: ParseException) {
            println(e.message)
            formatter.printHelp(javaClass.name, options)
            System.exit(1)
        } catch (e: InterruptedException) {
            e.printStackTrace()
            System.exit(1)
        }
    }
}
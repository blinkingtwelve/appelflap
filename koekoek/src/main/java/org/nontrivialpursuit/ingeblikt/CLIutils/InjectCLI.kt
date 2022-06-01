package org.nontrivialpursuit.ingeblikt.CLIutils

import org.apache.commons.cli.*
import org.nontrivialpursuit.ingeblikt.DumpfileInjector
import org.nontrivialpursuit.ingeblikt.JDBCCacheDBOps
import org.nontrivialpursuit.ingeblikt.PKIOps.JKSPKIOps
import java.io.File
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
object InjectCLI {
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
                null, "dumpfile", true, "Cache dump input filename"
            )
        )
        val parser: CommandLineParser = DefaultParser()
        val formatter = HelpFormatter()
        val cmd: CommandLine
        try {
            cmd = parser.parse(options, args)
            val profiledir: File = try {
                File(cmd.getOptionValue("profiledir"))
            } catch (e: NullPointerException) {
                throw ParseException("Profile directory not specified")
            }
            val inputfile = try {
                File(cmd.getOptionValue("dumpfile"))
            } catch (e: NullPointerException) {
                throw ParseException("Input file not specified")
            }
            val pkiOps = JKSPKIOps(
                cmd.getOptionValue("commonname"), cmd.getOptionValue("applicationID"), File(cmd.getOptionValue("keystore"))
            )
            profiledir.also {
                if (!(it.exists() and it.isDirectory)) throw ParseException("Profile directory '${profiledir}' does not exist")
            }
            DumpfileInjector(inputfile.inputStream().buffered(), pkiOps, profiledir, JDBCCacheDBOps()).inject()
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
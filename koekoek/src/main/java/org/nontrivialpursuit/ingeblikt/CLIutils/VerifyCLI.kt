package org.nontrivialpursuit.ingeblikt.CLIutils

import org.apache.commons.cli.*
import org.nontrivialpursuit.ingeblikt.DumpfileUnpacker
import org.nontrivialpursuit.ingeblikt.PKIOps.JKSPKIOps
import java.io.File

object VerifyCLI {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = Options()
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
            val inputfile = try {
                File(cmd.getOptionValue("dumpfile"))
            } catch (e: NullPointerException) {
                throw ParseException("Input file not specified")
            }
            val pkiOps = JKSPKIOps(
                cmd.getOptionValue("commonname"), cmd.getOptionValue("applicationID"), File(cmd.getOptionValue("keystore"))
            )
            val (info, amt_verified) = DumpfileUnpacker.verifyDump(inputfile.inputStream().buffered(), pkiOps)
            val (_, _, certinfo) = info
            val (certDN, certserial) = certinfo
            println("Archive created by '${certDN}', certificate serial ${certserial}: ${amt_verified} entries verified")
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
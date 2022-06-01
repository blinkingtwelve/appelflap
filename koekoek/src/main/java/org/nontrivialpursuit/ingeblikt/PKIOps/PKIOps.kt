package org.nontrivialpursuit.ingeblikt.PKIOps

import net.iharder.Base64
import org.nontrivialpursuit.ingeblikt.*
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

val RFC2253_FIELDS = Regex("(?<!\\\\),")
val RFC2253_KEYVALUES = Regex("(?<!\\\\)=")
val RFC2253_UNESCAPE = Regex("\\\\(,|=)")

fun X509Certificate.subject(): Map<String, String> {
    fun unescape(thing: String): String {
        return RFC2253_UNESCAPE.replace(thing, {
            it.value
        })
    }
    return this.subjectX500Principal.getName("RFC2253").split(RFC2253_FIELDS).map { it.split(RFC2253_KEYVALUES, 2) }.filter { it.size == 2 }
        .map { it.map { unescape(it) } }.associate { it[0] to it[1] }
}

fun X509Certificate.subjectDNCN(): String? {
    return this.subject()["CN"]
}

fun X509Certificate.PEMicate(): String {
    return listOf(
        "-----BEGIN CERTIFICATE-----", Base64.encodeBytes(this.encoded, Base64.DO_BREAK_LINES).trim('\n'), "-----END CERTIFICATE-----"
    ).joinToString("\n")
}

enum class KeystoreType(val type: String) {
    onAndroid("AndroidKeyStore"),
    JKS("JKS"),
}

enum class SignatureAlgorithm(val algo: String) {
    SHA256_WITH_ECDSA("SHA256withECDSA");

    companion object {
        private val mapping = values().associateBy(SignatureAlgorithm::algo)
        fun fromValue(value: String) = mapping[value] ?: throw IllegalArgumentException("No value ${value} in ${this::class.java}")
    }
}

class PKIOpsException(msg: String) : KoekoekException(msg)

abstract class PKIOps(
        val devcertCommonName: String,
        val applicationID: String,
        val keystoreType: KeystoreType,
        val rootCertProtectionParameter: KeyStore.ProtectionParameter,
        val keystoreBackingFile: File?) {

    abstract fun createDeviceKey(): KeyStore.PrivateKeyEntry

    val rootcert: Certificate
    val store: KeyStore

    @Synchronized
    fun KeyStore.save() {
        if (keystoreType == KeystoreType.JKS) {
            this.store(keystoreBackingFile!!.outputStream(), JKS_PASSWORD)
        }
    }

    val devkeyPassword = when (keystoreType) {
        KeystoreType.JKS -> JKS_PASSWORD
        else -> null
    }

    val devkeyProtectionParam = when (keystoreType) {
        KeystoreType.JKS -> KeyStore.PasswordProtection(devkeyPassword)
        else -> null
    }

    val devkeysignatureAlgorithm = SignatureAlgorithm.SHA256_WITH_ECDSA.algo

    fun getSigner(): Signature {
        return Signature.getInstance(devkeysignatureAlgorithm).also {
            it.initSign(getOrCreateDeviceKey().privateKey)
        }
    }

    @Synchronized
    fun importRootCert(): X509Certificate {
        val rootcert = CertificateFactory.getInstance("X.509").generateCertificate(
            ROOTCERT_PEM.byteInputStream(PEM_SERIALIZATION_CHARSET)
        )
        store.setEntry(ROOTCERT_ALIAS, KeyStore.TrustedCertificateEntry(rootcert), rootCertProtectionParameter)
        store.save()
        return rootcert as X509Certificate
    }

    fun getRootCert(): X509Certificate {
        return store.getCertificate(ROOTCERT_ALIAS) as X509Certificate? ?: importRootCert()
    }

    fun getOrCreateDeviceKey(): KeyStore.PrivateKeyEntry {
        return (store.getEntry(DEVKEY_ALIAS, devkeyProtectionParam) ?: createDeviceKey()) as KeyStore.PrivateKeyEntry
    }

    fun getPemChainForDevCert(): List<String> {
        val chain = getOrCreateDeviceKey().certificateChain
        return chain.map { (it as X509Certificate).PEMicate() }
    }

    fun readPemChain(input: InputStream): List<X509Certificate> {
        val certs = LinkedList<X509Certificate>()
        val certstream = BufferedInputStream(input)
        val certfactory = CertificateFactory.getInstance("X.509")
        while (certstream.available() > 0) {
            certs.add(certfactory.generateCertificate(certstream) as X509Certificate)
        }
        return certs
    }


    fun verifyAppelflapChain(
            certList: List<X509Certificate>, checkDevkeyRelated: Boolean = false) {
        /* Verifies a chain *for Appelflap purposes*, that is, this is not a general verifier, nor does it need to be
           We want to see 3 certs:
           - last cert being the trust anchor, ignored, we use the Appelflap root CA anchor built into the app
           - intermediate cert signed by trust anchor, and with CN equal to our application ID (= "deployment cert")
           - first cert signed by intermediate cert, optionally checking whether its pubkey matches our local device certificate's
        */
        if (certList.size != 3) throw PKIOpsException("Incorrect chain length (<>3)")
        val (devcert, depcert, _) = certList

        if (depcert.runCatching { verify(rootcert.publicKey) }.isFailure) throw PKIOpsException("Deployment certificate not signed with built-in CA root")
        if (devcert.runCatching { verify(depcert.publicKey) }.isFailure) throw PKIOpsException("Device certificate not signed with deployment certificate")

        if (depcert.subjectDNCN() != applicationID) throw PKIOpsException("Deployment certificate subject not equal to 'CN=${applicationID}'")
        if (devcert.subjectDNCN()?.length == 0) throw PKIOpsException("Can't find a CommonName subject in deployment certificate")
        if (checkDevkeyRelated && !devcert.publicKey.equals(getOrCreateDeviceKey().certificate.publicKey)) throw PKIOpsException("Device certificate does not match generated public key")
    }

    @Synchronized
    fun ingestSignedAppelflapCertchain(input: InputStream): Pair<Boolean, String> {
        val certs = readPemChain(input)
        try {
            verifyAppelflapChain(certs, checkDevkeyRelated = true)
            if (certs.first().subjectDNCN() != devcertCommonName) {
                return false to "Leaf certificate common name (${
                    certs.first().subject()
                }) does not match this device's node ID (${devcertCommonName})"
            }
            store.setKeyEntry(
                DEVKEY_ALIAS, store.getKey(DEVKEY_ALIAS, devkeyPassword), devkeyPassword, certs.toTypedArray()
            )
            store.save()
            return true to "All OK"
        } catch (e: PKIOpsException) {
            return false to e.message!!
        }
    }

    @Synchronized
    fun deleteChain() {
        listOf(
            DEVKEY_ALIAS, ROOTCERT_ALIAS
        ).map { store.runCatching { deleteEntry(it) } }
        store.save()
    }

    init {
        store = KeyStore.getInstance(keystoreType.type).also {
            when (keystoreType) {
                KeystoreType.JKS -> {
                    keystoreBackingFile?.run {
                        when (this.exists()) {
                            true -> it.load(this.inputStream(), null)
                            false -> it.load(null)
                        }
                    }
                }
                KeystoreType.onAndroid -> {
                    it.load(null)
                }
            }
        }
        rootcert = getRootCert()
    }
}
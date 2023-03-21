package org.nontrivialpursuit.ingeblikt.PKIOps

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.nontrivialpursuit.ingeblikt.DEVKEY_ALIAS
import org.nontrivialpursuit.ingeblikt.DEVKEY_SIZE
import org.nontrivialpursuit.ingeblikt.DEVKEY_TYPE
import org.nontrivialpursuit.ingeblikt.JKS_PASSWORD
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.util.*

private const val CRYPTO_PROVIDER = "BC"

class JKSPKIOps(devcertCommonName: String, applicationID: String, keystoreBackingFile: File) : PKIOps(
    devcertCommonName = devcertCommonName,
    applicationID = applicationID,
    keystoreType = KeystoreType.JKS,
    rootCertProtectionParameter = KeyStore.PasswordProtection(null),
    keystoreBackingFile = keystoreBackingFile
) {
    @Synchronized
    override fun createDeviceKey(): KeyStore.PrivateKeyEntry {
        val bcprov = BouncyCastleProvider().also {
            Security.addProvider(it)
        }
        val keypair = KeyPairGenerator.getInstance(DEVKEY_TYPE, bcprov).let {
            it.initialize(DEVKEY_SIZE, SecureRandom())
            it.generateKeyPair()
        }

        val subject_but_also_issuer = X500Name("CN=${devcertCommonName}")
        val somedate = Date(0L)
        val certBuilder = JcaX509v3CertificateBuilder(
            subject_but_also_issuer, BigInteger.ONE, somedate, somedate, subject_but_also_issuer, keypair.public
        )
        val cert = JcaX509CertificateConverter().setProvider(bcprov)
            .getCertificate(certBuilder.build(JcaContentSignerBuilder(devkeysignatureAlgorithm).build(keypair.private)))

        store.setKeyEntry(DEVKEY_ALIAS, keypair.private, JKS_PASSWORD, arrayOf(cert))
        store.save()
        return getOrCreateDeviceKey()
    }
}
package org.nontrivialpursuit.appelflap

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import org.nontrivialpursuit.ingeblikt.DEVKEY_ALIAS
import org.nontrivialpursuit.ingeblikt.DEVKEY_SIZE
import org.nontrivialpursuit.ingeblikt.DEVKEY_TYPE
import org.nontrivialpursuit.ingeblikt.PKIOps.KeystoreType
import org.nontrivialpursuit.ingeblikt.PKIOps.PKIOps
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.security.auth.x500.X500Principal

private const val CRYPTO_PROVIDER = "AndroidKeyStore"

class AndroidPKIOps(val context: Context, devcertCommonName: String) : PKIOps(
    devcertCommonName = devcertCommonName,
    applicationID = context.packageName,
    keystoreType = KeystoreType.onAndroid,
    rootCertProtectionParameter = KeyProtection.Builder(KeyProperties.PURPOSE_VERIFY).build(),
    keystoreBackingFile = null
) {
    @Synchronized
    override fun createDeviceKey(): KeyStore.PrivateKeyEntry {
        val keygenny: KeyPairGenerator = KeyPairGenerator.getInstance(
            DEVKEY_TYPE, CRYPTO_PROVIDER
        )
        val parameterSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
            DEVKEY_ALIAS, KeyProperties.PURPOSE_SIGN
        ).run {
            setUserAuthenticationRequired(false)
            setDigests(KeyProperties.DIGEST_SHA256)
            setCertificateSubject(X500Principal("CN=${devcertCommonName}"))
            setKeySize(DEVKEY_SIZE)
            build()
        }
        with(keygenny) {
            initialize(parameterSpec)
            genKeyPair()
        }
        return getOrCreateDeviceKey()
    }
}
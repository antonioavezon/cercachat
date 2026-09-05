package cl.antonioavezon.cercachat.transport

import cl.antonioavezon.cercachat.protocol.CryptoIds
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class HostIdentity(
    val sslContext: SSLContext,
    val fingerprintSha256: String,
)

object TlsFactory {
    fun createHostIdentity(): HostIdentity {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000L)
        val notAfter = Date(now + 24L * 60L * 60L * 1_000L)
        val name = X500Name("CN=CercaChat-temporal")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(160, SecureRandom()),
            notBefore,
            notAfter,
            name,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        val fingerprint = CryptoIds.sha256Hex(cert.encoded)
        val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        val password = CharArray(0)
        keyStore.setKeyEntry("host", keyPair.private, password, arrayOf(cert))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)
        val context = SSLContext.getInstance("TLS")
        context.init(kmf.keyManagers, arrayOf(RejectAllTrustManager), SecureRandom())
        return HostIdentity(context, fingerprint)
    }

    fun createPinnedClientContext(expectedFingerprintSha256: String): SSLContext {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(FingerprintTrustManager(expectedFingerprintSha256)), SecureRandom())
        return context
    }
}

private object RejectAllTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("El anfitrión no acepta certificados de cliente X.509")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("El anfitrión no valida servidores externos")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

class FingerprintTrustManager(
    private val expectedFingerprintSha256: String,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("No se espera autenticación de cliente por certificado")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("El anfitrión no presentó certificado")
        }
        val presented = CryptoIds.sha256Hex(chain[0].encoded)
        if (!CryptoIds.constantTimeEquals(presented, expectedFingerprintSha256.lowercase())) {
            throw CertificateException("La identidad del anfitrión no coincide con el QR")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

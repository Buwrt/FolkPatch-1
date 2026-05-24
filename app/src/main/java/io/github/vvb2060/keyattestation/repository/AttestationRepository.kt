package io.github.vvb2060.keyattestation.repository

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Simplified Attestation helper for FolkPatch integration
 */
object AttestationRepository {
    private const val TAG = "KeyAttestation"
    private const val ALIAS = "folkpatch_attestation"
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }
    
    private val certFactory: CertificateFactory by lazy {
        CertificateFactory.getInstance("X.509")
    }
    
    /**
     * Generate key attestation
     */
    @JvmStatic
    fun generateAttestation(): AttestationData {
        try {
            // Delete existing key if any
            if (keyStore.containsAlias(ALIAS)) {
                keyStore.deleteEntry(ALIAS)
            }
            
            // Generate key pair with attestation
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setKeySize(256)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge("FolkPatch Attestation".toByteArray())
                .build()
            
            // Get KeyPairGenerator
            val keyPairGenerator = android.security.keystore.KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            
            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
            
            // Get certificate chain
            val certChain = keyStore.getCertificateChain(ALIAS)
                ?: throw Exception("Unable to get certificate chain")
            
            val certificates = ArrayList<X509Certificate>()
            for (cert in certChain) {
                certificates.add(cert as X509Certificate)
            }
            
            return AttestationData.parseCertificateChain(certificates)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate attestation", e)
            throw e
        }
    }
    
    /**
     * Load attestation data from raw bytes
     */
    @JvmStatic
    fun loadAttestationData(data: ByteArray): AttestationData {
        val inputStream = ByteArrayInputStream(data)
        val certificates = ArrayList<X509Certificate>()
        
        // Try to parse as PKCS7 certificate chain
        try {
            val certPath = certFactory.generateCertPath(inputStream, "PKCS7")
            for (cert in certPath.certificates) {
                certificates.add(cert as X509Certificate)
            }
        } catch (e: Exception) {
            // Try to parse as individual certificates
            inputStream.reset()
            val certs = certFactory.generateCertificates(inputStream)
            for (cert in certs) {
                certificates.add(cert as X509Certificate)
            }
        }
        
        if (certificates.isEmpty()) {
            throw Exception("No certificates found in data")
        }
        
        return AttestationData.parseCertificateChain(certificates)
    }
    
    /**
     * Check if device supports key attestation
     */
    fun isSupported(): Boolean {
        return try {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        } catch (e: Exception) {
            false
        }
    }
}

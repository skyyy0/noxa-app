package com.example.data.auth

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.RSAPublicKeySpec
import java.util.concurrent.TimeUnit

/**
 * Keyless Google Sign-In verification.
 *
 * The standard flow hands the ID token to Firebase Auth
 * (`signInWithCredential`), which fails with `operation-not-allowed` whenever
 * the Google provider is disabled in the Firebase console — the picker
 * returns to the app and nothing happens. This verifier removes that
 * dependency: the token Google already issued to Credential Manager is
 * checked directly against Google's public signing keys
 * (`/oauth2/v3/certs`, no signup and no API key), exactly like Firebase
 * would: RS256 signature, issuer, audience and expiry. On success the app
 * builds the local session itself, so Google login works on every install
 * regardless of console configuration.
 */
internal object GoogleIdTokenVerifier {

    /** The claims the app cares about, already proven authentic. */
    data class Identity(
        /** Stable per-Google-account id — used as the NOXA uid. */
        val subject: String,
        val email: String?,
        val name: String?,
        val pictureUrl: String?
    )

    private const val CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs"

    /** Tolerated clock skew when checking `exp`/`iat`. */
    private const val CLOCK_SKEW_SECONDS = 60L

    /** Signing keys are rotated rarely; six hours is safely conservative. */
    private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cacheLock = Any()
    private var cachedKeys: Map<String, java.security.PublicKey> = emptyMap()
    private var cacheFetchedAt = 0L

    /**
     * Verifies [idToken] as a Google-issued identity for [expectedAudience]
     * (the project's web client id). Returns the verified identity, or null
     * when the token is not authentic — never an exception, never a guess.
     */
    suspend fun verify(idToken: String, expectedAudience: String): Identity? =
        withContext(Dispatchers.IO) {
            try {
                val parts = idToken.split(".")
                if (parts.size != 3) return@withContext null

                val header = JSONObject(String(base64UrlDecode(parts[0]), Charsets.UTF_8))
                val payload = JSONObject(String(base64UrlDecode(parts[1]), Charsets.UTF_8))
                if (!"RS256".equals(header.optString("alg"), ignoreCase = true)) {
                    return@withContext null
                }

                val nowSeconds = System.currentTimeMillis() / 1000
                val exp = payload.optLong("exp", 0L)
                if (exp in 1 until (nowSeconds - CLOCK_SKEW_SECONDS)) return@withContext null

                val issuer = payload.optString("iss")
                val issuerOk = issuer == "accounts.google.com" ||
                    issuer == "https://accounts.google.com"
                if (!issuerOk) return@withContext null
                if (payload.optString("aud") != expectedAudience) return@withContext null

                val key = publicKey(header.optString("kid")) ?: return@withContext null
                val signature = Signature.getInstance("SHA256withRSA")
                signature.initVerify(key)
                signature.update(
                    (parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII)
                )
                if (!signature.verify(base64UrlDecode(parts[2]))) return@withContext null

                val subject = payload.optString("sub")
                if (subject.isBlank()) return@withContext null

                Identity(
                    subject = subject,
                    email = payload.optString("email").ifBlank { null },
                    name = payload.optString("name").ifBlank { null },
                    pictureUrl = payload.optString("picture").ifBlank { null }
                )
            } catch (e: Exception) {
                null
            }
        }

    /** The public key for [kid], refetching Google's set once when unknown. */
    private fun publicKey(kid: String): java.security.PublicKey? {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            if (cachedKeys.isNotEmpty() && now - cacheFetchedAt < CACHE_TTL_MS) {
                return cachedKeys[kid] ?: singleKeyOrNull(cachedKeys)
            }
        }
        val fetched = fetchKeys()
        if (fetched.isNotEmpty()) {
            synchronized(cacheLock) {
                cachedKeys = fetched
                cacheFetchedAt = now
            }
        }
        synchronized(cacheLock) {
            val keys = cachedKeys
            if (keys.isEmpty()) return null
            return keys[kid] ?: singleKeyOrNull(keys)
        }
    }

    /** Tokens normally carry a `kid`; fall back to the sole key when absent. */
    private fun singleKeyOrNull(keys: Map<String, java.security.PublicKey>): java.security.PublicKey? =
        if (keys.size == 1) keys.values.first() else null

    /**
     * Downloads Google's current signing certificates (public, keyless).
     *
     * Google's JWKS hands out bare RSA keys (`kty`/`kid`/`n`/`e`) with **no**
     * `x5c` certificate chain, so the modulus/exponent form is the primary
     * parse path and `x5c` remains as a fallback.
     */
    private fun fetchKeys(): Map<String, java.security.PublicKey> = try {
        val request = Request.Builder()
            .url(CERTS_URL)
            .header("Cache-Control", "max-age=3600")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        } ?: return emptyMap()

        val keysJson = JSONObject(body).optJSONArray("keys") ?: return emptyMap()
        val result = HashMap<String, java.security.PublicKey>()
        for (i in 0 until keysJson.length()) {
            val key = keysJson.optJSONObject(i) ?: continue
            if (!"RSA".equals(key.optString("kty"), ignoreCase = true)) continue
            publicKeyFrom(key)?.let { result[key.optString("kid")] = it }
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }

    /** Parses one JWKS entry: `x5c` certificate when present, else `n`+`e`. */
    private fun publicKeyFrom(key: JSONObject): java.security.PublicKey? {
        key.optJSONArray("x5c")?.optString(0)?.takeIf { it.isNotBlank() }?.let { certificate ->
            try {
                val der = Base64.decode(certificate, Base64.DEFAULT)
                return CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(der))
                    .publicKey
            } catch (e: Exception) {
                // Fall through to the raw modulus/exponent form.
            }
        }
        val modulus = key.optString("n").takeIf { it.isNotBlank() } ?: return null
        val exponent = key.optString("e").takeIf { it.isNotBlank() } ?: return null
        return try {
            val modulusInt = BigInteger(1, base64UrlDecode(modulus))
            val exponentInt = BigInteger(1, base64UrlDecode(exponent))
            KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulusInt, exponentInt))
        } catch (e: Exception) {
            null
        }
    }

    private fun base64UrlDecode(text: String): ByteArray =
        Base64.decode(text, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

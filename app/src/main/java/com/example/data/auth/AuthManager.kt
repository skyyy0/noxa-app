package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sign-in for NOXA.
 *
 * Two backends, one interface:
 *
 *  * **Firebase** — used whenever the build actually carries Firebase config
 *    (`google-services.json`), including the unique-username registry in
 *    Firestore. Unchanged behaviour on configured builds.
 *  * **Built-in local accounts** — the always-available fallback. The release
 *    APK on GitHub has no `google-services.json`, so Firebase sign-up used to
 *    fail with "Firebase belum dikonfigurasi". With the local store, email +
 *    password registration and sign-in work on every install, with zero keys
 *    and zero setup; names stay unique per device.
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }

    private val localStore = LocalAccountStore(context)
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    /** Keyless shared registry (accounts, roles, support chat). */
    private val backend = com.example.data.support.NoxaBackend()

    private val remotePrefs =
        context.getSharedPreferences("noxa_remote_session", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<NoxaUser?>(auth?.currentUser?.let { toNoxaUser(it) }
        ?: restoreRemoteSession()
        ?: localStore.restoreSessionUid()?.let { uid ->
            localStore.accountForUid(uid)?.let { account ->
                NoxaUser(
                    uid = account.uid,
                    displayName = account.username,
                    email = account.email,
                    photoUrl = null
                )
            }
        })
    val currentUser: StateFlow<NoxaUser?> = _currentUser.asStateFlow()

    private val _isGuestMode = MutableStateFlow(_currentUser.value == null)
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                _currentUser.value = toNoxaUser(firebaseAuth.currentUser!!)
                _isGuestMode.value = false
            }
        }
    }

    private fun toNoxaUser(user: com.google.firebase.auth.FirebaseUser): NoxaUser = NoxaUser(
        uid = user.uid,
        displayName = user.displayName,
        email = user.email,
        photoUrl = user.photoUrl?.toString()
    )

    // -------------------------------------------------- remote session (keyless)

    /** Rebuilds the session created by a successful remote sign-in. */
    private fun restoreRemoteSession(): NoxaUser? {
        val uid = remotePrefs.getString(KEY_REMOTE_UID, null) ?: return null
        return NoxaUser(
            uid = uid,
            displayName = remotePrefs.getString(KEY_REMOTE_USERNAME, null),
            email = remotePrefs.getString(KEY_REMOTE_EMAIL, null),
            photoUrl = null,
            role = com.example.data.support.AccountRole.from(
                remotePrefs.getString(KEY_REMOTE_ROLE, null)
            )
        )
    }

    private fun storeRemoteSession(user: NoxaUser) {
        remotePrefs.edit()
            .putString(KEY_REMOTE_UID, user.uid)
            .putString(KEY_REMOTE_USERNAME, user.displayName)
            .putString(KEY_REMOTE_EMAIL, user.email)
            .putString(KEY_REMOTE_ROLE, user.role.name)
            .apply()
    }

    private fun clearRemoteSession() {
        remotePrefs.edit().clear().apply()
    }

    /**
     * Mirrors a remote account into the on-device store so a sign-in still
     * works offline afterwards (same uid on both sides).
     */
    private suspend fun mirrorRemoteLocally(
        login: com.example.data.support.BackendLogin,
        password: String
    ) {
        try {
            if (localStore.accountForUid(login.uid) != null) {
                localStore.storeSessionUid(login.uid)
                return
            }
            val result = localStore.createAccount(
                email = login.email,
                username = login.username,
                password = password,
                uid = login.uid
            )
            if (result is LocalAccountStore.CreateResult.Created) {
                localStore.storeSessionUid(login.uid)
            }
        } catch (e: Exception) {
            Log.w("AuthManager", "Local mirror failed: ${e.message}")
        }
    }

    fun continueAsGuest() {
        _isGuestMode.value = true
    }

    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val serverClientId = webClientId()
        if (serverClientId == null || auth == null) {
            Log.e("AuthManager", "default_web_client_id missing from google-services.json")
            onError("Google Sign-In belum dikonfigurasi pada build ini.")
            return
        }

        _isSigningIn.value = true

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        _currentUser.value = toNoxaUser(user)
                        _isGuestMode.value = false
                        onSuccess(_currentUser.value!!)
                    } else {
                        onError("Failed to obtain Firebase user")
                    }
                } else {
                    onError("Unexpected credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthManager", "Google Sign-In cancelled: ${e.message}")
            } catch (e: Exception) {
                Log.e("AuthManager", "Google Sign-In failed", e)
                onError(e.localizedMessage ?: "Sign in failed")
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    /**
     * Email/password sign-in — the Google-free path, so an account works on any
     * device without a Google account on it. Falls back to the built-in local
     * store on builds without Firebase configuration.
     */
    fun signInWithEmail(
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _isSigningIn.value = true
        if (auth == null) {
            scope.launch {
                try {
                    // 1) Shared registry first — that is where the developer
                    //    account (and every account across devices) lives.
                    when (val remote = backend.authenticate(email, password)) {
                        is com.example.data.support.RemoteAuthResult.Ok -> {
                            val login = remote.login
                            mirrorRemoteLocally(login, password)
                            val user = NoxaUser(
                                uid = login.uid,
                                displayName = login.username,
                                email = login.email,
                                photoUrl = null,
                                role = login.role
                            )
                            storeRemoteSession(user)
                            _currentUser.value = user
                            _isGuestMode.value = false
                            onSuccess(user)
                        }

                        // 2) Not in the registry (or registry offline):
                        //    fall back to the built-in local store.
                        com.example.data.support.RemoteAuthResult.Rejected,
                        com.example.data.support.RemoteAuthResult.Unreachable -> {
                            val account = localStore.authenticate(email, password)
                            if (account == null) {
                                onError("Email atau password salah.")
                            } else {
                                val user = NoxaUser(
                                    uid = account.uid,
                                    displayName = account.username,
                                    email = account.email,
                                    photoUrl = null
                                )
                                _currentUser.value = user
                                _isGuestMode.value = false
                                onSuccess(user)
                            }
                        }
                    }
                } catch (e: Exception) {
                    onError("Masuk gagal. Coba lagi.")
                } finally {
                    _isSigningIn.value = false
                }
            }
            return
        }
        scope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = result.user
                if (user != null) {
                    _currentUser.value = toNoxaUser(user)
                    _isGuestMode.value = false
                    onSuccess(_currentUser.value!!)
                } else {
                    onError("Masuk gagal. Coba lagi.")
                }
            } catch (e: Exception) {
                onError(humanizeAuthError(e))
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    /**
     * Email/password registration with a **unique username**. On Firebase
     * builds the name is claimed through [UsernameRegistry] (Firestore); on
     * plain builds it is claimed in the built-in store. Either way the user
     * never sees a configuration error.
     */
    fun registerWithEmail(
        email: String,
        password: String,
        username: String,
        scope: CoroutineScope,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val trimmedName = username.trim()
        UsernameRegistry.validate(trimmedName)?.let { onError(it); return }
        if (password.length < 6) {
            onError("Password minimal 6 karakter.")
            return
        }
        if (!LocalAccountStore.isEmailFormatValid(email)) {
            onError("Format email tidak valid.")
            return
        }

        _isSigningIn.value = true

        if (auth == null) {
            scope.launch {
                try {
                    // One uid for both registries, generated up front.
                    val uid = "noxa-" + System.currentTimeMillis().toString(36) +
                        "-" + (1000..9999).random()
                    val (remoteLogin, remoteFailure) =
                        backend.register(uid, email.trim(), trimmedName, password)

                    when {
                        // Real conflict (email/username already registered
                        // globally) — refuse before creating anything.
                        remoteFailure != null &&
                            remoteFailure != com.example.data.support.NoxaBackend.UNREACHABLE -> {
                            onError(remoteFailure)
                        }

                        remoteLogin != null -> {
                            // Registered in the shared registry + mirrored
                            // locally under the same uid (offline sign-in).
                            localStore.createAccount(email, trimmedName, password, uid)
                            localStore.storeSessionUid(uid)
                            val user = NoxaUser(
                                uid = uid,
                                displayName = trimmedName,
                                email = email.trim().lowercase(java.util.Locale.ROOT),
                                photoUrl = null,
                                role = remoteLogin.role
                            )
                            storeRemoteSession(user)
                            _currentUser.value = user
                            _isGuestMode.value = false
                            onSuccess(user)
                        }

                        // Registry offline: local registration still works.
                        else -> {
                            when (val result =
                                localStore.createAccount(email, trimmedName, password)) {
                                is LocalAccountStore.CreateResult.Failed -> onError(result.message)
                                is LocalAccountStore.CreateResult.Created -> {
                                    val account = result.account
                                    localStore.storeSessionUid(account.uid)
                                    val user = NoxaUser(
                                        uid = account.uid,
                                        displayName = account.username,
                                        email = account.email,
                                        photoUrl = null
                                    )
                                    _currentUser.value = user
                                    _isGuestMode.value = false
                                    onSuccess(user)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    onError("Pendaftaran gagal. Coba lagi.")
                } finally {
                    _isSigningIn.value = false
                }
            }
            return
        }

        scope.launch {
            try {
                // Fast, friendly check before creating anything.
                if (UsernameRegistry.isTaken(trimmedName) == true) {
                    onError("Username \"$trimmedName\" sudah dipakai. Pilih yang lain.")
                    return@launch
                }

                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = result.user
                if (user == null) {
                    onError("Pendaftaran gagal. Coba lagi.")
                    return@launch
                }

                when (val claim = UsernameRegistry.claim(user.uid, trimmedName)) {
                    UsernameRegistry.ClaimResult.TAKEN,
                    UsernameRegistry.ClaimResult.INVALID -> {
                        try { user.delete().await() } catch (ignored: Exception) { Unit }
                        auth.signOut()
                        onError("Username \"$trimmedName\" sudah dipakai. Pilih yang lain.")
                        return@launch
                    }

                    // Unreachable Firestore: the account still works; the name
                    // is stored locally as the display name (documented tradeoff).
                    UsernameRegistry.ClaimResult.AVAILABLE,
                    UsernameRegistry.ClaimResult.UNAVAILABLE -> Unit
                }

                runCatching {
                    user.updateProfile(userProfileChangeRequest { displayName = trimmedName })
                        .await()
                }
                _currentUser.value = toNoxaUser(user)
                _isGuestMode.value = false
                onSuccess(_currentUser.value!!)
            } catch (e: Exception) {
                onError(humanizeAuthError(e))
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    /** Maps provider errors to Indonesian, human messages (never raw codes). */
    private fun humanizeAuthError(e: Exception): String {
        val msg = e.message.orEmpty()
        val lower = msg.lowercase()
        return when {
            "email_exists" in lower || "email already" in lower ->
                "Email sudah terdaftar. Gunakan Masuk."
            "invalid_email" in lower || "malformed" in lower || "badly formatted" in lower ->
                "Format email tidak valid."
            "weak_password" in lower -> "Password minimal 6 karakter."
            "invalid_login_credentials" in lower || "password is invalid" in lower ||
                "no user record" in lower || "user-not-found" in lower ->
                "Email atau password salah."
            "network" in lower -> "Koneksi internet bermasalah. Coba lagi."
            else -> msg.ifBlank { "Autentikasi gagal. Coba lagi." }
        }
    }

    fun attemptSilentAutoSignIn(scope: CoroutineScope) {
        if (auth?.currentUser != null) return
        val serverClientId = webClientId() ?: return

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth?.signInWithCredential(authCredential)?.await()
                }
            } catch (e: Exception) {
                // Silent check failure is expected on non-configured accounts; continue gracefully
            }
        }
    }

    /**
     * The OAuth web client id is generated by the Google Services plugin from
     * `google-services.json`. It is resolved by name so that the app still
     * compiles and runs (in guest mode) when that file is not present.
     */
    private fun webClientId(): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId == 0) return null
        return try {
            context.getString(resId).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------- account settings

    /**
     * Changes the signed-in account's username (Pengaturan akun). Updates the
     * shared registry when reachable; the local mirror always follows, so the
     * rename survives offline. Returns through [onError]/[onSuccess].
     */
    fun changeUsername(
        newUsername: String,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("Belum masuk ke akun.")
            return
        }
        UsernameRegistry.validate(newUsername)?.let { onError(it); return }
        val trimmed = newUsername.trim()
        if (trimmed.equals(user.displayName, ignoreCase = true)) {
            onError("Username sama dengan yang sekarang.")
            return
        }
        scope.launch {
            try {
                val reachable = runCatching { backend.readState() != null }.getOrDefault(false)
                if (reachable) {
                    val updated = backend.updateUsername(user.uid, trimmed)
                    if (!updated) {
                        onError("Username \"$trimmed\" sudah dipakai. Pilih yang lain.")
                        return@launch
                    }
                }
                localStore.updateUsername(user.uid, trimmed)
                val updated = user.copy(displayName = trimmed)
                if (reachable) storeRemoteSession(updated)
                _currentUser.value = updated
                onSuccess(trimmed)
            } catch (e: Exception) {
                onError("Ganti username gagal. Coba lagi.")
            }
        }
    }

    /**
     * Changes the password after re-verifying the current one (remote first,
     * local fallback). Both stores are updated so either sign-in path works.
     */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val user = _currentUser.value
        if (user == null) {
            onError("Belum masuk ke akun.")
            return
        }
        if (newPassword.length < 6) {
            onError("Password baru minimal 6 karakter.")
            return
        }
        if (currentPassword == newPassword) {
            onError("Password baru harus berbeda dari yang lama.")
            return
        }
        scope.launch {
            try {
                val email = user.email
                if (email.isNullOrBlank()) {
                    onError("Akun ini tidak punya email untuk verifikasi.")
                    return@launch
                }
                // Verify the current password wherever the account lives.
                var verified = false
                var remoteReachable = false
                when (val remote = backend.authenticate(email, currentPassword)) {
                    is com.example.data.support.RemoteAuthResult.Ok -> {
                        verified = true
                        remoteReachable = true
                    }

                    com.example.data.support.RemoteAuthResult.Rejected -> {
                        remoteReachable = true
                    }

                    com.example.data.support.RemoteAuthResult.Unreachable -> {
                        val account = localStore.authenticate(email, currentPassword)
                        verified = account != null
                    }
                }
                if (!verified) {
                    onError(
                        if (remoteReachable) "Password lama salah."
                        else "Password lama salah."
                    )
                    return@launch
                }
                var changed = false
                if (remoteReachable) {
                    changed = backend.updatePassword(user.uid, newPassword)
                }
                localStore.updatePassword(user.uid, newPassword)
                if (remoteReachable && !changed) {
                    onError("Ganti password gagal di server. Coba lagi.")
                    return@launch
                }
                onSuccess()
            } catch (e: Exception) {
                onError("Ganti password gagal. Coba lagi.")
            }
        }
    }

    fun signOut(scope: CoroutineScope, onComplete: () -> Unit = {}) {
        auth?.signOut()
        localStore.clearSession()
        clearRemoteSession()
        _currentUser.value = null
        _isGuestMode.value = true
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("AuthManager", "Failed to clear credential state", e)
            } finally {
                onComplete()
            }
        }
    }

    private companion object {
        const val KEY_REMOTE_UID = "uid"
        const val KEY_REMOTE_USERNAME = "username"
        const val KEY_REMOTE_EMAIL = "email"
        const val KEY_REMOTE_ROLE = "role"
    }
}

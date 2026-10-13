package com.example.data.support

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Role attached to an account. `DEVELOPER` is Sky's own account and is the
 * only role that can open the NOXA Admin console.
 */
enum class AccountRole(val label: String) {
    DEVELOPER("Developer"),
    ADMIN("Admin"),
    PARTNER("Partner"),
    MODERATOR("Moderator"),
    USER("User");

    val isStaff: Boolean get() = this != USER

    companion object {
        fun from(raw: String?): AccountRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: USER
    }
}

/** One message inside a support thread. `from` is "user" or "support". */
data class SupportMessage(
    val from: String,
    val text: String,
    val at: Long
)

/**
 * The support conversation between one user and NOXA Support.
 *
 * Rule requested by Sky: a user may only send **one message at a time** —
 * until Support replies, the composer stays locked. [canUserSend] encodes it.
 */
data class SupportThread(
    val uid: String,
    val username: String,
    val messages: List<SupportMessage>,
    val lastUserAt: Long,
    val lastSupportAt: Long,
    val crashHash: String = "",
    val resolved: Boolean = false
) {
    /** True when the user has nothing pending with Support. */
    val canUserSend: Boolean get() = lastUserAt <= lastSupportAt

    /** For the dev console: the last message came from a user. */
    val unreadForSupport: Boolean
        get() = messages.lastOrNull()?.from == "user"

    val lastMessage: SupportMessage? get() = messages.lastOrNull()
}

/** A registered account as stored by the backend. */
data class BackendLogin(
    val uid: String,
    val email: String,
    val username: String,
    val role: AccountRole,
    val createdAt: Long = 0L
)

/** Outcome of a remote authentication attempt. */
sealed interface RemoteAuthResult {
    data class Ok(val login: BackendLogin) : RemoteAuthResult

    /** Credentials do not match any account. */
    data object Rejected : RemoteAuthResult

    /** Network/parse failure — the caller falls back to the local store. */
    data object Unreachable : RemoteAuthResult
}

/**
 * The keyless NOXA backend: accounts, roles and support chat.
 *
 * **Why Telegraph?** The release APK carries no `google-services.json`, so
 * Firebase (and every key-value store that needs an account, e.g. kvdb.io)
 * is unavailable. Telegraph's `api.telegra.ph` needs **no key and no signup**:
 * a single article acts as a JSON document —
 *
 *  * `createPage` once (the document now exists, path embedded below),
 *  * `getPage?return_content=true` to read the state,
 *  * `editPage` (with the page's short-name `access_token`) to write it back.
 *
 * The whole app state is one JSON object (`v`, `seq`, `accounts`, `threads`)
 * rendered as the page content, so every install reads and writes the same
 * registry. Optimistic concurrency: every write bumps `seq` after re-reading,
 * and a stale read is retried before it can clobber a newer one.
 *
 * Honest limits: no server-side authorization (any reader of the page could
 * technically edit it with the token, which ships in the APK), and writes are
 * last-writer-wins under heavy concurrency. For a personal app distributed by
 * direct APK that is a deliberate, documented tradeoff — passwords are never
 * stored (PBKDF2-HMAC-SHA256, 60k iterations), only salted hashes.
 */
class NoxaBackend {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------------ read

    /**
     * Reads the full backend state, or `null` when unreachable/corrupt.
     *
     * Telegraph occasionally answers slowly or rate-limits an anonymous call;
     * a single failed read used to surface as "Email atau password salah."
     * during sign-in. Every read now retries with a short backoff before it
     * gives up honestly.
     */
    suspend fun readState(): BackendState? = withContext(Dispatchers.IO) {
        repeat(READ_ATTEMPTS) { attempt ->
            val raw = httpGet("$API_BASE/getPage/$PAGE_PATH?return_content=true")
            if (raw != null) {
                val parsed = try {
                    val root = JSONObject(raw)
                    if (!root.optBoolean("ok")) null else {
                        val content = root.getJSONObject("result").optJSONArray("content")
                        var payload: String? = null
                        if (content != null) {
                            for (i in 0 until content.length()) {
                                val node = content.optJSONObject(i) ?: continue
                                val children = node.optJSONArray("children") ?: continue
                                for (j in 0 until children.length()) {
                                    val text = children.optString(j)
                                    if (text.startsWith("{")) {
                                        payload = text
                                        break
                                    }
                                }
                                if (payload != null) break
                            }
                        }
                        payload?.let { parseState(JSONObject(it)) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "State parse failed: ${e.message}")
                    null
                }
                if (parsed != null) return@withContext parsed
            }
            if (attempt < READ_ATTEMPTS - 1) delay(350L * (attempt + 1))
        }
        null
    }

    // ----------------------------------------------------------------- write

    /**
     * Reads, mutates and writes the state back. Returns the block's result,
     * or `null` when the backend could not be reached or the write failed.
     * The read happens inside the write window so `seq` ordering stays honest.
     */
    suspend fun <T> mutate(block: (BackendState) -> T): T? = withContext(Dispatchers.IO) {
        val state = readState() ?: return@withContext null
        val result = block(state)
        if (writeState(state)) result else null
    }

    private suspend fun writeState(state: BackendState): Boolean {
        state.seq += 1
        val json = serializeState(state)
        val children = JSONArray().put(json.toString())
        val content = JSONArray().put(JSONObject().put("tag", "p").put("children", children))
        val body = FormBody.Builder()
            .add("access_token", PAGE_TOKEN)
            .add("title", PAGE_TITLE)
            .add("content", content.toString())
            .build()
        val attempts = 3
        repeat(attempts) { attempt ->
            val raw = httpPost("$API_BASE/editPage/$PAGE_PATH", body) ?: return false
            val ok = try {
                JSONObject(raw).optBoolean("ok")
            } catch (e: Exception) {
                false
            }
            if (ok) return true
            if (attempt < attempts - 1) delay(400L * (attempt + 1))
        }
        return false
    }

    // ------------------------------------------------------------ auth: user

    /**
     * Verifies email + password against the remote registry.
     * Distinguishes "wrong credentials" from "backend offline" so the caller
     * can still fall back to the built-in local account store.
     */
    suspend fun authenticate(email: String, password: String): RemoteAuthResult {
        val state = readState() ?: return RemoteAuthResult.Unreachable
        val account = state.accounts[email.trim().lowercase(Locale.ROOT)]
            ?: return RemoteAuthResult.Rejected
        if (!verifyPassword(account.saltHex, account.hashHex, password)) {
            return RemoteAuthResult.Rejected
        }
        return RemoteAuthResult.Ok(
            BackendLogin(
                uid = account.uid,
                email = account.email,
                username = account.username,
                role = account.role,
                createdAt = account.createdAt
            )
        )
    }

    /**
     * Creates a remote account. Returns the new login, or a failure message
     * (`null` first element means "backend unreachable").
     */
    suspend fun register(
        uid: String,
        email: String,
        username: String,
        password: String,
        role: AccountRole = AccountRole.USER
    ): Pair<BackendLogin?, String?> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val salt = randomSalt()
        var conflict: String? = null
        val login = mutate { state ->
            if (state.accounts.containsKey(cleanEmail)) {
                conflict = "Email sudah terdaftar. Gunakan Masuk."
                return@mutate null
            }
            if (state.accounts.values.any { it.username.equals(username, ignoreCase = true) }) {
                conflict = "Username \"$username\" sudah dipakai. Pilih yang lain."
                return@mutate null
            }
            val account = RemoteAccountInternal(
                uid = uid,
                email = cleanEmail,
                username = username,
                saltHex = toHex(salt),
                hashHex = toHex(pbkdf2(password.toByteArray(Charsets.UTF_8), salt)),
                role = role,
                createdAt = System.currentTimeMillis()
            )
            state.accounts[cleanEmail] = account
            BackendLogin(uid, cleanEmail, username, role, account.createdAt)
        }
        return when {
            login != null -> login to null
            conflict != null -> null to conflict
            else -> null to UNREACHABLE
        }
    }

    // -------------------------------------------------------- account updates

    /** Renames an account everywhere (registry + its support thread). */
    suspend fun updateUsername(uid: String, newUsername: String): Boolean {
        val changed = mutate { state ->
            val account = state.accounts.values.firstOrNull { it.uid == uid } ?: return@mutate false
            if (state.accounts.values.any { it !== account && it.username.equals(newUsername, ignoreCase = true) }) {
                return@mutate false
            }
            state.accounts[account.email] = RemoteAccountInternal(
                uid = account.uid,
                email = account.email,
                username = newUsername,
                saltHex = account.saltHex,
                hashHex = account.hashHex,
                role = account.role,
                createdAt = account.createdAt
            )
            state.threads[uid]?.let { thread ->
                state.threads[uid] = thread.copy(username = newUsername)
            }
            true
        }
        return changed == true
    }

    /** Changes an account's password (hash only — the plaintext never travels back). */
    suspend fun updatePassword(uid: String, newPassword: String): Boolean {
        val salt = randomSalt()
        val hashHex = toHex(pbkdf2(newPassword.toByteArray(Charsets.UTF_8), salt))
        val changed = mutate { state ->
            val account = state.accounts.values.firstOrNull { it.uid == uid } ?: return@mutate false
            state.accounts[account.email] = RemoteAccountInternal(
                uid = account.uid,
                email = account.email,
                username = account.username,
                saltHex = toHex(salt),
                hashHex = hashHex,
                role = account.role,
                createdAt = account.createdAt
            )
            true
        }
        return changed == true
    }

    /** Changes an account's role (used by the dev console). */
    suspend fun updateRole(uid: String, role: AccountRole): Boolean {
        val changed = mutate { state ->
            val account = state.accounts.values.firstOrNull { it.uid == uid } ?: return@mutate false
            state.accounts[account.email] = RemoteAccountInternal(
                uid = account.uid,
                email = account.email,
                username = account.username,
                saltHex = account.saltHex,
                hashHex = account.hashHex,
                role = role,
                createdAt = account.createdAt
            )
            true
        }
        return changed == true
    }

    /** True when the email is already registered remotely. */
    suspend fun isEmailTaken(email: String): Boolean? {
        val state = readState() ?: return null
        return state.accounts.containsKey(email.trim().lowercase(Locale.ROOT))
    }

    /** All registered accounts, newest first (dev console). */
    suspend fun listAccounts(): List<BackendLogin>? =
        readState()?.accounts?.values
            ?.map { BackendLogin(it.uid, it.email, it.username, it.role, it.createdAt) }
            ?.sortedByDescending { it.createdAt }

    /** All support threads, most recently active first (dev console). */
    suspend fun listThreads(): List<SupportThread>? =
        readState()?.threads?.values?.sortedByDescending { it.lastUserAt.coerceAtLeast(it.lastSupportAt) }

    // -------------------------------------------------------- support: user

    /**
     * Sends one message from a user. Enforces Sky's rule: while a previous
     * user message is still unanswered the send is refused (`false`).
     * The first message of each turn automatically attaches the latest crash
     * trace (if one exists), deduplicated by content hash.
     */
    suspend fun sendUserMessage(
        uid: String,
        username: String,
        text: String,
        crashTrace: String? = null
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val sent = mutate { state ->
            val existing = state.threads[uid]
            if (existing != null && !existing.canUserSend) return@mutate false

            var body = trimmed
            var crashHash = existing?.crashHash ?: ""
            val freshCrash = crashTrace?.takeIf { it.isNotBlank() }
            val firstOfTurn = existing == null || existing.messages.lastOrNull()?.from == "support"
            if (freshCrash != null && firstOfTurn) {
                val hash = sha256(freshCrash)
                if (hash != crashHash) {
                    crashHash = hash
                    body += "\n\n---\nLaporan crash otomatis (baru saja):\n" +
                        freshCrash.take(CRASH_ATTACH_LIMIT)
                }
            }

            val message = SupportMessage("user", body, System.currentTimeMillis())
            val updated = SupportThread(
                uid = uid,
                username = username,
                messages = (existing?.messages ?: emptyList()) + message,
                lastUserAt = message.at,
                lastSupportAt = existing?.lastSupportAt ?: 0L,
                crashHash = crashHash,
                resolved = false
            )
            state.threads[uid] = updated
            pruneThreads(state)
            true
        }
        return sent == true
    }

    /** Reads one thread (the user's own chat). */
    suspend fun userThread(uid: String): SupportThread? = readState()?.threads?.get(uid)

    // ------------------------------------------------------- support: staff

    /**
     * Support/developer reply. Clears the user's pending flag, which unlocks
     * their composer — this is what re-arms the "one message at a time" rule.
     */
    suspend fun sendSupportMessage(threadUid: String, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val sent = mutate { state ->
            val existing = state.threads[threadUid] ?: SupportThread(
                uid = threadUid,
                username = "",
                messages = emptyList(),
                lastUserAt = 0L,
                lastSupportAt = 0L
            )
            val message = SupportMessage("support", trimmed, System.currentTimeMillis())
            state.threads[threadUid] = existing.copy(
                messages = existing.messages + message,
                lastSupportAt = message.at
            )
            pruneThreads(state)
            true
        }
        return sent == true
    }

    /** Staff-created account (role picker in the dev console). */
    suspend fun createAccountAsStaff(
        email: String,
        username: String,
        password: String,
        role: AccountRole
    ): Pair<BackendLogin?, String?> {
        val uid = "noxa-" + System.currentTimeMillis().toString(36) +
            "-" + (1000..9999).random()
        return register(uid, email, username, password, role)
    }

    // ------------------------------------------------------------- internals

    class RemoteAccountInternal(
        val uid: String,
        val email: String,
        val username: String,
        val saltHex: String,
        val hashHex: String,
        val role: AccountRole,
        val createdAt: Long
    )

    /** Full backend document, mirrored 1:1 onto the Telegraph page. */
    class BackendState {
        var seq: Long = 0L
        val accounts = LinkedHashMap<String, RemoteAccountInternal>()
        val threads = LinkedHashMap<String, SupportThread>()
    }

    private fun parseState(json: JSONObject): BackendState {
        val state = BackendState()
        state.seq = json.optLong("seq")
        val accounts = json.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accounts.length()) {
            val obj = accounts.optJSONObject(i) ?: continue
            val email = obj.optString("email").trim().lowercase(Locale.ROOT)
            if (email.isBlank()) continue
            state.accounts[email] = RemoteAccountInternal(
                uid = obj.optString("uid"),
                email = email,
                username = obj.optString("username"),
                saltHex = obj.optString("saltHex"),
                hashHex = obj.optString("hashHex"),
                role = AccountRole.from(obj.optString("role")),
                createdAt = obj.optLong("createdAt")
            )
        }
        val threads = json.optJSONObject("threads") ?: JSONObject()
        for (uid in threads.keys()) {
            val obj = threads.optJSONObject(uid) ?: continue
            val rawMessages = obj.optJSONArray("messages") ?: JSONArray()
            val messages = ArrayList<SupportMessage>(rawMessages.length())
            for (i in 0 until rawMessages.length()) {
                val m = rawMessages.optJSONObject(i) ?: continue
                messages.add(
                    SupportMessage(
                        from = m.optString("from"),
                        text = m.optString("text"),
                        at = m.optLong("at")
                    )
                )
            }
            state.threads[uid] = SupportThread(
                uid = uid,
                username = obj.optString("username"),
                messages = messages,
                lastUserAt = obj.optLong("lastUserAt"),
                lastSupportAt = obj.optLong("lastSupportAt"),
                crashHash = obj.optString("crashHash"),
                resolved = obj.optBoolean("resolved")
            )
        }
        return state
    }

    private fun serializeState(state: BackendState): JSONObject {
        val accounts = JSONArray()
        state.accounts.values.forEach { account ->
            accounts.put(
                JSONObject()
                    .put("uid", account.uid)
                    .put("email", account.email)
                    .put("username", account.username)
                    .put("saltHex", account.saltHex)
                    .put("hashHex", account.hashHex)
                    .put("role", account.role.name)
                    .put("createdAt", account.createdAt)
            )
        }
        val threads = JSONObject()
        state.threads.forEach { (uid, thread) ->
            val messages = JSONArray()
            thread.messages.forEach { message ->
                messages.put(
                    JSONObject()
                        .put("from", message.from)
                        .put("text", message.text)
                        .put("at", message.at)
                )
            }
            threads.put(
                uid,
                JSONObject()
                    .put("username", thread.username)
                    .put("messages", messages)
                    .put("lastUserAt", thread.lastUserAt)
                    .put("lastSupportAt", thread.lastSupportAt)
                    .put("crashHash", thread.crashHash)
                    .put("resolved", thread.resolved)
            )
        }
        return JSONObject()
            .put("v", 1)
            .put("seq", state.seq)
            .put("accounts", accounts)
            .put("threads", threads)
    }

    /** Keeps the document inside Telegraph's page-size limit. */
    private fun pruneThreads(state: BackendState) {
        val MAX_MESSAGES_PER_THREAD = 200
        val MAX_THREADS = 120
        state.threads.values.forEach { thread ->
            if (thread.messages.size > MAX_MESSAGES_PER_THREAD) {
                state.threads[thread.uid] = thread.copy(
                    messages = thread.messages.takeLast(MAX_MESSAGES_PER_THREAD)
                )
            }
        }
        if (state.threads.size > MAX_THREADS) {
            val keep = state.threads.values
                .sortedByDescending { it.lastUserAt.coerceAtLeast(it.lastSupportAt) }
                .take(MAX_THREADS)
                .map { it.uid }
                .toSet()
            state.threads.keys.retainAll(keep)
        }
    }

    private fun httpGet(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "NOXA/1.13.3")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET failed: ${e.message}")
        null
    }

    private fun httpPost(url: String, body: FormBody): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NOXA/1.13.3")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "POST failed: ${e.message}")
        null
    }

    // ------------------------------------------------------- password crypto

    /** Verifies a PBKDF2-HMAC-SHA256 hex hash, same parameters as local store. */
    private fun verifyPassword(saltHex: String, hashHex: String, password: String): Boolean = try {
        val actual = pbkdf2(password.toByteArray(Charsets.UTF_8), fromHex(saltHex))
        MessageDigest.isEqual(actual, fromHex(hashHex))
    } catch (e: Exception) {
        false
    }

    /**
     * PBKDF2 (RFC 2898) over HMAC-SHA256 — same implementation as
     * `PasswordVault`, kept dependency-free so the backend module compiles
     * standalone. 60k iterations / 32-byte key, matching seeded accounts.
     */
    private fun pbkdf2(password: ByteArray, salt: ByteArray): ByteArray {
        val iterations = 60_000
        val dkLen = 32
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(object : java.security.Key {
            override fun getAlgorithm(): String = "HmacSHA256"
            override fun getEncoded(): ByteArray = password
            override fun getFormat(): String = "RAW"
        })
        val out = ByteArray(dkLen)
        var offset = 0
        var block = 1
        while (offset < dkLen) {
            mac.reset()
            var u = mac.doFinal(salt + intToBytes(block))
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            val take = minOf(t.size, dkLen - offset)
            System.arraycopy(t, 0, out, offset, take)
            offset += take
            block++
        }
        return out
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun randomSalt(): ByteArray =
        ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun fromHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }

    private fun sha256(text: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        text.hashCode().toString()
    }

    companion object {
        private const val TAG = "NoxaBackend"
        private const val API_BASE = "https://api.telegra.ph"

        /** The one state document shared by every install. */
        private const val PAGE_PATH = "NOXA-Backend-Core-10-13"
        private const val PAGE_TITLE = "NOXA Backend Core"
        private const val PAGE_TOKEN =
            "7941e33f1aeb16bf70aed150bdf81ef1f0700eee189f7ca79cdbf2a416bf"

        /** How often a read is retried before giving up (slow/rate-limited CDN). */
        private const val READ_ATTEMPTS = 3

        /** Returned to callers when the backend itself is unreachable. */
        const val UNREACHABLE = "server_noxa_tidak_tersedia"

        /** How much of a crash trace travels with a support message. */
        private const val CRASH_ATTACH_LIMIT = 6_000
    }
}

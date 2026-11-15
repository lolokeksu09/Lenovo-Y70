package app.pinduoduo.overlayru

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * License transport and local server-signed entitlement cache.
 *
 * License requests contain only the activation key or signed entitlement plus a random install ID.
 * Translation text and Accessibility events are never part of this API.
 */
class LicenseManager private constructor(context: Context) {

    companion object {
        private const val PREFS = "overlay_ru_license"
        private const val INSTALL_ID = "install_uuid"
        private const val TOKEN = "signed_entitlement"
        private const val LAST_SEEN = "max_seen_wall_clock_sec"
        private const val LAST_RENEW = "last_renew_attempt_sec"
        private const val DAY = 86_400L

        @Volatile
        private var instance: LicenseManager? = null

        @JvmStatic
        @Synchronized
        fun get(context: Context): LicenseManager =
            instance ?: LicenseManager(context.applicationContext).also { instance = it }

        private fun uuidV4(id: String): Boolean = try {
            val uuid = UUID.fromString(id)
            uuid.version() == 4 && uuid.toString() == id
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val activeRequest = AtomicBoolean(false)

    // Persist once per installation; never regenerate the ID merely because a lease expired.
    private val installId: String = run {
        val stored = prefs.getString(INSTALL_ID, null)
        if (stored != null && uuidV4(stored)) {
            stored
        } else {
            val id = UUID.randomUUID().toString()
            if (!prefs.edit().putString(INSTALL_ID, id).commit()) {
                throw IllegalStateException("Cannot save installation identifier")
            }
            id
        }
    }

    class State(
        val pro: Boolean,
        val code: String,
        val plan: String,
        val leaseExpiresAt: Long
    )

    fun interface Callback {
        fun onResult(success: Boolean, statusCode: String)
    }

    fun configured(): Boolean =
        BuildConfig.LICENSE_API_BASE_URL.startsWith("https://") &&
            BuildConfig.LICENSE_PUBLIC_KEY_B64.isNotEmpty()

    fun busy(): Boolean = activeRequest.get()

    fun supportId(): String = installId.substring(0, 8)

    fun state(): State {
        if (!configured()) return State(false, "not_configured", "", 0L)
        val token = prefs.getString(TOKEN, "")
        if (token.isNullOrEmpty()) return State(false, "free", "", 0L)
        val now = System.currentTimeMillis() / 1000L
        if (now + 300L < prefs.getLong(LAST_SEEN, 0L)) {
            return State(false, "clock_changed", "", 0L)
        }
        return try {
            val signed = LicenseTokenVerifier.verify(
                token, BuildConfig.LICENSE_PUBLIC_KEY_B64, installId, now
            )
            val old = prefs.getLong(LAST_SEEN, 0L)
            if (now - old >= 60L) prefs.edit().putLong(LAST_SEEN, now).apply()
            State(true, "active", signed.plan, signed.expiresAt)
        } catch (_: LicenseTokenVerifier.InvalidLicenseException) {
            State(false, "expired_or_invalid", "", 0L)
        }
    }

    fun activate(text: String?, callback: Callback) {
        if (!configured()) {
            main.post { callback.onResult(false, "not_configured") }
            return
        }
        val key = text?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (!Regex("OVR-(?:[A-Z2-7]{4}-){7}[A-Z2-7]{4}").matches(key)) {
            main.post { callback.onResult(false, "invalid_license") }
            return
        }
        if (!activeRequest.compareAndSet(false, true)) {
            main.post { callback.onResult(false, "request_in_progress") }
            return
        }
        executor.execute {
            var success = false
            var status: String
            try {
                val request = JSONObject().put("key", key).put("install_id", installId)
                val response = post("/v1/activate", request)
                storeValidatedResponse(response)
                success = true
                status = "activated"
            } catch (error: ApiError) {
                status = error.code
            } catch (_: Exception) {
                status = "connection_error"
            }
            activeRequest.set(false)
            main.post { callback.onResult(success, status) }
        }
    }

    fun renew(callback: Callback) {
        if (!configured()) {
            main.post { callback.onResult(false, "not_configured") }
            return
        }
        val token = prefs.getString(TOKEN, "")
        if (token.isNullOrEmpty()) {
            main.post { callback.onResult(false, "no_license") }
            return
        }
        if (!activeRequest.compareAndSet(false, true)) {
            main.post { callback.onResult(false, "request_in_progress") }
            return
        }
        prefs.edit().putLong(LAST_RENEW, System.currentTimeMillis() / 1000L).apply()
        executor.execute {
            var success = false
            var status: String
            try {
                val request = JSONObject().put("token", token).put("install_id", installId)
                val response = post("/v1/renew", request)
                storeValidatedResponse(response)
                success = true
                status = "renewed"
            } catch (error: ApiError) {
                status = error.code
                if (status == "license_revoked" || status == "license_expired" ||
                    status == "activation_not_found" || status == "invalid_token") {
                    // Explicit server denial invalidates only the license; not the install ID.
                    prefs.edit().remove(TOKEN).apply()
                }
            } catch (_: Exception) {
                // Server outage or offline: retain the last independently verified signed lease.
                status = "connection_error"
            }
            activeRequest.set(false)
            main.post { callback.onResult(success, status) }
        }
    }

    fun renewIfNeeded() {
        if (!configured() || busy()) return
        val token = prefs.getString(TOKEN, "")
        if (token.isNullOrEmpty()) return
        val now = System.currentTimeMillis() / 1000L
        val previous = prefs.getLong(LAST_RENEW, 0L)
        if (now < previous || now - previous < DAY) return
        val current = state()
        if (!current.pro || current.leaseExpiresAt - now <= 3L * DAY) {
            renew(Callback { _, _ -> })
        }
    }

    /** Commit only a signed, locally verified lease whose response metadata matches. */
    private fun storeValidatedResponse(response: JSONObject) {
        val token = response.getString("token")
        val signed = LicenseTokenVerifier.verify(
            token, BuildConfig.LICENSE_PUBLIC_KEY_B64, installId,
            System.currentTimeMillis() / 1000L
        )
        if (signed.plan != response.getString("plan") ||
            signed.expiresAt != response.getLong("expires_at")) {
            throw LicenseTokenVerifier.InvalidLicenseException()
        }
        val now = System.currentTimeMillis() / 1000L
        if (!prefs.edit().putString(TOKEN, token).putLong(LAST_SEEN, now).commit()) {
            throw IllegalStateException("Could not persist entitlement")
        }
    }

    /**
     * Explicit HTTPS-only POST, redirects disabled. Server error codes are constrained
     * before displaying them. Never log request bodies, activation keys or JWTs.
     */
    private fun post(path: String, body: JSONObject): JSONObject {
        val base = BuildConfig.LICENSE_API_BASE_URL.replace(Regex("/+$"), "")
        val url = URL(base + path)
        if (!url.protocol.equals("https", ignoreCase = true) || url.userInfo != null) {
            throw ApiError("invalid_configuration")
        }

        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.useCaches = false
        try {
            val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status != 200 && status != 403 && status != 422) {
                throw ApiError("service_unavailable")
            }
            val stream = if (status == 200) connection.inputStream else connection.errorStream
            if (stream == null) throw ApiError("service_unavailable")
            return stream.use {
                val response = JSONObject(readLimited(it))
                if (status != 200) {
                    var code = response.optString("error")
                    if (!Regex("[a-z_]{1,64}").matches(code)) code = "service_unavailable"
                    throw ApiError(code)
                }
                response
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) break
            if (output.size() + count > 16_384) throw ApiError("service_unavailable")
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private class ApiError(val code: String) : Exception(code)
}

package com.factorybill.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.factorybill.app.BuildConfig
import com.factorybill.app.core.OcrSuggestion
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

sealed interface CloudScanResult {
    data class Success(val suggestion: OcrSuggestion, val rawText: String = "") : CloudScanResult
    data class Unavailable(val reason: String) : CloudScanResult
}

object CloudScanConfig {
    private val endpoint = BuildConfig.CLOUD_SCAN_URL.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = endpoint.startsWith("https://") &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    internal fun scanUrl(): String = "$endpoint/v1/scan"
}

/** Sends a bill image to the user's secured Cloud Run service only after explicit consent. */
object CloudBillScanner {
    suspend fun recognize(context: Context, uri: Uri): CloudScanResult = withContext(Dispatchers.IO) {
        if (!CloudScanConfig.isConfigured) {
            return@withContext CloudScanResult.Unavailable(
                "Cloud scanning isn't configured yet. Set up the Cloud Run and Firebase values before building."
            )
        }

        var bitmap: Bitmap? = null
        var connection: HttpURLConnection? = null
        try {
            val firebase = FirebaseApp.getInstance("factorybill-cloud-scan")
            val appCheckToken = Tasks.await(
                FirebaseAppCheck.getInstance(firebase).getAppCheckToken(false),
                APP_CHECK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            ).token

            bitmap = OcrScanner.imageForAi(context, uri)
                ?: return@withContext CloudScanResult.Unavailable("Could not prepare the bill photo for cloud scanning.")
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                return@withContext CloudScanResult.Unavailable("Could not encode the bill photo for cloud scanning.")
            }
            val imageBytes = output.toByteArray()
            if (imageBytes.size > MAX_IMAGE_BYTES) {
                return@withContext CloudScanResult.Unavailable("The prepared photo exceeds the 8 MB cloud-scan limit.")
            }

            val payload = JSONObject()
                .put("mime_type", "image/jpeg")
                .put("image_base64", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                .toString()
                .toByteArray(Charsets.UTF_8)

            connection = URL(CloudScanConfig.scanUrl()).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Firebase-AppCheck", appCheckToken)
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.outputStream.use { it.write(payload) }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                return@withContext CloudScanResult.Unavailable(
                    when (statusCode) {
                        401, 403 -> "Cloud scan authorization failed. Check Firebase App Check setup."
                        413 -> "The bill photo is too large for cloud scanning."
                        429 -> "Cloud scanning is busy. Wait a moment and try again."
                        else -> "Cloud scanning failed (HTTP $statusCode). Check your connection and service setup."
                    }
                )
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val suggestionJson = JSONObject(response).optJSONObject("suggestion")
                ?: return@withContext CloudScanResult.Unavailable("Cloud AI returned an invalid bill response.")
            val suggestion = LocalAiBillScanner.parseSuggestion(suggestionJson.toString())
                ?: return@withContext CloudScanResult.Unavailable("Cloud AI couldn't identify readable bill details.")
            CloudScanResult.Success(suggestion)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CloudScanResult.Unavailable("Cloud scanning couldn't connect or authenticate. Check internet and service setup.")
        } finally {
            bitmap?.recycle()
            connection?.disconnect()
        }
    }

    private const val APP_CHECK_TIMEOUT_SECONDS = 30L
    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 60_000
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val JPEG_QUALITY = 88
}

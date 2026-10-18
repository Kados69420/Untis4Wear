package com.untis4wear

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate

class UntisException(message: String) : Exception(message)

/**
 * Minimal WebUntis JSON-RPC client. Port of the Garmin app's UntisApiClient:
 * authenticate -> getTimetable -> logout, using the JSESSIONID cookie.
 *
 * [fetchTimetable] blocks, call it from a background thread.
 */
class UntisApi(
    private val scheme: String = "https",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000
) {

    fun fetchTimetable(settings: Settings, date: LocalDate): List<LessonSlot> {
        val url = buildUrl(scheme, settings.untisUrl, settings.schoolName)

        val auth = rpc(
            url, "authenticate",
            JSONObject()
                .put("user", settings.username)
                .put("password", settings.password)
                .put("client", "Untis4Wear"),
            sessionId = null
        )
        val authResult = auth.optJSONObject("result")
            ?: throw UntisException("Authentication response has no result")
        val sessionId = authResult.optString("sessionId", "")
        if (sessionId.isEmpty()) throw UntisException("Authentication response has no sessionId")
        val personId = authResult.getInt("personId")
        val personType = authResult.getInt("personType")

        try {
            val yyyymmdd = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
            val fields = { JSONArray().put("id").put("name").put("longname").put("externalkey") }
            val options = JSONObject()
                .put("element", JSONObject().put("id", personId).put("type", personType))
                .put("klasseFields", fields())
                .put("roomFields", fields())
                .put("subjectFields", fields())
                .put("startDate", yyyymmdd)
                .put("endDate", yyyymmdd)
            val response = rpc(
                url, "getTimetable",
                JSONObject().put("options", options),
                sessionId = sessionId
            )
            val result = response.optJSONArray("result")
                ?: throw UntisException("Timetable response has no result list")
            return TimetableParser.parse(result)
        } finally {
            // Best effort: a failing logout must not hide an already fetched timetable.
            try {
                rpc(url, "logout", JSONObject(), sessionId = sessionId)
            } catch (e: Exception) {
            }
        }
    }

    private fun rpc(url: String, method: String, params: JSONObject, sessionId: String?): JSONObject {
        val body = JSONObject()
            .put("id", 1)
            .put("method", method)
            .put("params", params)
            .put("jsonrpc", "2.0")
        val bytes = body.toString().toByteArray(Charsets.UTF_8)

        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            if (sessionId != null) {
                connection.setRequestProperty("Cookie", "JSESSIONID=$sessionId")
            }
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw UntisException("HTTP $code")

            val text = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val json = JSONObject(text)
            if (json.has("error")) {
                val message = json.optJSONObject("error")?.optString("message", "") ?: ""
                throw UntisException("WebUntis error for $method: $message")
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Accepts "mese.webuntis.com", "https://mese.webuntis.com/" etc. */
        fun normalizeHost(raw: String): String {
            var host = raw.trim()
            host = host.replace(Regex("^[A-Za-z][A-Za-z0-9+.-]*://"), "")
            return host.substringBefore('/').trim()
        }

        fun buildUrl(scheme: String, untisUrl: String, schoolName: String): String {
            val school = URLEncoder.encode(schoolName.trim(), "UTF-8").replace("+", "%20")
            return "$scheme://${normalizeHost(untisUrl)}/WebUntis/jsonrpc.do?school=$school"
        }
    }
}

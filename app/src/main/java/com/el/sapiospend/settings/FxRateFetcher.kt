package com.el.sapiospend.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pulls a fresh rate table off the internet, when there is one.
 *
 * Deliberately the *optional* half of the feature. The app converts with
 * [FxRates.BUNDLED] on a phone that has never been online, and everything this class does
 * is replace those figures with newer ones. Every failure path — no network, a feed that
 * has moved, a response that does not parse — ends in [Result.failure] and the app keeps
 * converting with whatever it already had. Nothing here is ever on the path to drawing a
 * screen.
 *
 * Only the pivot currency's rates leave the device, which is to say nothing does: the
 * request is a bare GET for a public price list with no query string, no headers
 * identifying the user and no body. It carries no budget figures, no currency choice and
 * no identifier of any kind, which is what lets an app whose whole pitch is that the
 * numbers stay on the phone ask for this permission at all.
 *
 * Two sources rather than one because a free feed with no API key is free to disappear,
 * and a converter that stops updating the month its provider folds is a support ticket
 * nobody will connect to the cause.
 */
object FxRateFetcher {

    /** ExchangeRate-API's open endpoint: no key, and it quotes the thin currencies. */
    private const val PRIMARY = "https://open.er-api.com/v6/latest/USD"

    /** A CDN-hosted daily dump, in a different shape, from a different operator. */
    private const val FALLBACK =
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"

    /**
     * Short enough that a refresh on a bad connection gives up rather than sitting on a
     * socket. Nothing is waiting on the result, so a slow success is worth no more than
     * a fast failure.
     */
    private const val TIMEOUT_MS = 10_000

    /**
     * A newer table, or a failure that the caller is expected to ignore.
     *
     * Runs on IO — it is a blocking socket read whichever way the call arrives.
     */
    suspend fun fetch(): Result<FxRates> = withContext(Dispatchers.IO) {
        runCatching { parsePrimary(get(PRIMARY)) }
            .recoverCatching { parseFallback(get(FALLBACK)) }
            .mapCatching { rates ->
                // A feed that answered but cannot price the currencies this app offers is
                // a failure, not a table: taking it would drop coverage the bundled table
                // already had.
                require(rates.perUsd.isNotEmpty()) { "no usable rates in response" }
                rates
            }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // Redirects are followed by default and both sources use them; left alone.
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("rate feed returned ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** `{"result":"success","time_last_update_unix":…,"rates":{"NGN":1550.5,…}}` */
    private fun parsePrimary(body: String): FxRates {
        val json = JSONObject(body)
        require(json.optString("result") == "success") { "rate feed reported failure" }
        val rates = json.getJSONObject("rates")
        // Seconds in this feed, millis everywhere in the app.
        val asOf = json.optLong("time_last_update_unix", 0L)
            .takeIf { it > 0L }?.times(1000L)
            ?: System.currentTimeMillis()
        return FxRates(ratesFor(rates) { it.uppercase() }, asOf, FxRates.Source.FETCHED)
    }

    /** `{"date":"2026-05-01","usd":{"ngn":1550.5,…}}` — lowercase codes, date only. */
    private fun parseFallback(body: String): FxRates {
        val json = JSONObject(body)
        val rates = json.getJSONObject("usd")
        // The feed dates to a day, not an instant. Using "now" would claim more precision
        // than it has, but a day's worth of drift on a budgeting app is noise, and a date
        // that cannot be parsed back would show the user a 1970 timestamp.
        val asOf = json.optString("date").let(::parseIsoDate) ?: System.currentTimeMillis()
        return FxRates(ratesFor(rates) { it.uppercase() }, asOf, FxRates.Source.FETCHED)
    }

    /**
     * Only the currencies the picker offers, so a 160-entry response is not carried
     * around in SharedPreferences for the eight of them that can be selected.
     */
    private fun ratesFor(rates: JSONObject, normalise: (String) -> String): Map<String, Double> {
        val wanted = AppCurrency.entries.associateBy { it.code }
        val out = LinkedHashMap<String, Double>()
        rates.keys().forEach { key ->
            val code = normalise(key)
            if (code !in wanted) return@forEach
            val value = rates.optDouble(key, Double.NaN)
            if (value.isFinite() && value > 0.0) out[code] = value
        }
        return out
    }

    /** "2026-05-01" as midnight UTC, or null for anything that is not that. */
    private fun parseIsoDate(value: String): Long? {
        val parts = value.split('-')
        if (parts.size != 3) return null
        val (year, month, day) = parts.map { it.toIntOrNull() ?: return null }
        return runCatching {
            java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(year, month - 1, day)
            }.timeInMillis
        }.getOrNull()
    }
}

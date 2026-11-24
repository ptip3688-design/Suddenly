package com.example.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ResearchSource(
    val title: String,
    val snippet: String,
    val url: String
)

data class WeatherReport(
    val locationName: String,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val humidityPercent: Int,
    val conditionLabel: String
)

class InternetResearchService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun searchWeb(query: String): Result<List<ResearchSource>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<ResearchSource>()
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")

            // 1. DuckDuckGo Instant Answer API
            val ddgReq = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .header("User-Agent", "JarvisAI-Android/1.0")
                .build()
            client.newCall(ddgReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val abstractText = json.optString("AbstractText")
                        val abstractUrl = json.optString("AbstractURL")
                        val heading = json.optString("Heading")
                        if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                            results.add(
                                ResearchSource(
                                    title = heading.ifBlank { query },
                                    snippet = abstractText,
                                    url = abstractUrl
                                )
                            )
                        }
                    }
                }
            }

            // 2. Wikipedia Open Search API
            val wikiReq = Request.Builder()
                .url("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=4&srsearch=$encoded")
                .header("User-Agent", "JarvisAI-Android/1.0")
                .build()
            client.newCall(wikiReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val searchArr = json.optJSONObject("query")?.optJSONArray("search")
                        if (searchArr != null) {
                            for (i in 0 until searchArr.length()) {
                                val item = searchArr.getJSONObject(i)
                                val title = item.optString("title")
                                val rawSnippet = item.optString("snippet")
                                val cleanSnippet = rawSnippet
                                    .replace(Regex("<[^>]*>"), " ")
                                    .replace("&quot;", "\"")
                                    .replace("&amp;", "&")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                val urlTitle = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                                if (title.isNotBlank()) {
                                    results.add(
                                        ResearchSource(
                                            title = title,
                                            snippet = cleanSnippet,
                                            url = "https://en.wikipedia.org/wiki/$urlTitle"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Result.success(results.distinctBy { it.url })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUrlReadableText(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                rawUrl
            } else {
                "https://$rawUrl"
            }
            val request = Request.Builder()
                .url(normalized)
                .header("User-Agent", "Mozilla/5.0 (Android; JarvisAI)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code} fetching $normalized"))
                }
                val html = response.body?.string().orEmpty()
                val stripped = html
                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                Result.success(stripped.take(12000))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWeatherForCity(city: String): Result<WeatherReport> = withContext(Dispatchers.IO) {
        try {
            val encodedCity = URLEncoder.encode(city.trim(), "UTF-8")
            val geoReq = Request.Builder()
                .url("https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json")
                .build()
            val (lat, lon, resolvedName) = client.newCall(geoReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Geocoding failed (${resp.code})"))
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val arr = json.optJSONArray("results")
                    ?: return@withContext Result.failure(IllegalStateException("Location '$city' not found."))
                val first = arr.getJSONObject(0)
                val name = listOfNotNull(
                    first.optString("name").takeIf { it.isNotBlank() },
                    first.optString("country").takeIf { it.isNotBlank() }
                ).joinToString(", ")
                Triple(first.getDouble("latitude"), first.getDouble("longitude"), name)
            }
            fetchWeatherForCoords(lat, lon, resolvedName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWeatherForCoords(
        latitude: Double,
        longitude: Double,
        label: String = "Current Location"
    ): Result<WeatherReport> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Weather fetch failed (${resp.code})"))
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val current = json.getJSONObject("current")
                val code = current.optInt("weather_code", 0)
                Result.success(
                    WeatherReport(
                        locationName = label,
                        temperatureC = current.optDouble("temperature_2m", 0.0),
                        windSpeedKmh = current.optDouble("wind_speed_10m", 0.0),
                        humidityPercent = current.optInt("relative_humidity_2m", 0),
                        conditionLabel = weatherCodeToLabel(code)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun weatherCodeToLabel(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2, 3 -> "Partly Cloudy"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Conditions Code $code"
    }
}

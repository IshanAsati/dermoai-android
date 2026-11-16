package com.dermoai.feature.finder.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A nearby clinic/doctor, as shown on the map and in the list. */
data class Clinic(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val address: String,
    val phone: String?,
    val website: String?,
    val distanceMeters: Double?,
)

/** Search outcome — [isBroadFallback] true means no dermatologist was found and general clinics are shown. */
data class NearbyResult(
    val clinics: List<Clinic>,
    val isBroadFallback: Boolean,
)

@Serializable
private data class OverpassResponse(val elements: List<OverpassElement> = emptyList())

@Serializable
private data class OverpassElement(
    val type: String = "node",
    val id: Long = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap(),
)

@Serializable
private data class OverpassCenter(val lat: Double, val lon: Double)

@Serializable
private data class NominatimItem(
    val osm_type: String = "node",
    val osm_id: Long = 0,
    val lat: String = "",
    val lon: String = "",
    val name: String = "",
    val display_name: String = "",
    val extratags: Map<String, String>? = null,
)

/**
 * Finds dermatologists near a location from OpenStreetMap data, using two
 * independent free services at the same time so that one being overloaded
 * (the public Overpass servers often are) doesn't leave the screen empty:
 *
 *  - **Overpass** — tag-exact queries (healthcare speciality = dermatology, ...).
 *  - **Nominatim** — OpenStreetMap's own search, asked for "dermatologist",
 *    "dermatology" and "skin clinic" inside the area around the user.
 *
 * Usage-policy notes: every request is user-initiated and time-limited, Nominatim
 * calls are spaced over a second apart, and each carries an identifying User-Agent.
 * Falls back to general clinics when no dermatologist is found.
 */
@Singleton
class DermatologistRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findNearby(lat: Double, lon: Double): NearbyResult = withContext(Dispatchers.IO) {
        // Stage 1: dermatology-tagged places from both sources at once.
        val derm = gather(
            overpass = { overpassSearch(dermQuery(lat, lon), lat, lon, dermatology = true) },
            nominatim = { nominatimSearch(DERM_TERMS, lat, lon, dermatology = true) },
        )
        if (derm.found.isNotEmpty()) {
            return@withContext NearbyResult(derm.found.map { it.clinic }, isBroadFallback = false)
        }

        // Stage 2: nothing dermatology-specific nearby — show general clinics instead.
        val broad = gather(
            overpass = { overpassSearch(broadQuery(lat, lon), lat, lon, dermatology = false) },
            nominatim = { nominatimSearch(BROAD_TERMS, lat, lon, dermatology = false) },
        )
        if (derm.allFailed && broad.allFailed) {
            throw IOException("Clinic search failed: ${broad.error ?: derm.error}")
        }
        // A name like "... Skin Clinic" counts as dermatology even if it came from the broad query.
        val named = broad.found.filter { it.isDermatologyByName }
        if (named.isNotEmpty()) {
            NearbyResult(named.map { it.clinic }, isBroadFallback = false)
        } else {
            NearbyResult(broad.found.map { it.clinic }, isBroadFallback = true)
        }
    }

    private class Found(val clinic: Clinic, val isDermatology: Boolean) {
        val isDermatologyByName: Boolean get() = DERM_NAME.containsMatchIn(clinic.name)
    }

    private class Gathered(val found: List<Found>, val allFailed: Boolean, val error: String?)

    /** Runs both sources in parallel; one failing is fine, only both failing counts as a failed search. */
    private suspend fun gather(
        overpass: () -> List<Found>,
        nominatim: suspend () -> List<Found>,
    ): Gathered = coroutineScope {
        val o = async { runCatching { overpass() } }
        val n = async { runCatching { nominatim() } }
        val ro = o.await()
        val rn = n.await()
        ro.exceptionOrNull()?.let { Log.w(TAG, "Overpass failed: ${it.message}") }
        rn.exceptionOrNull()?.let { Log.w(TAG, "Nominatim failed: ${it.message}") }

        val merged = (ro.getOrNull().orEmpty() + rn.getOrNull().orEmpty())
            .groupBy { it.clinic.id }
            .map { (_, dupes) -> dupes.firstOrNull { it.isDermatology } ?: dupes.first() }
            .filter { (it.clinic.distanceMeters ?: 0.0) <= MAX_DISTANCE_M }
            .sortedBy { it.clinic.distanceMeters }
            .take(MAX_RESULTS)
        Gathered(
            found = merged,
            allFailed = ro.isFailure && rn.isFailure,
            error = (rn.exceptionOrNull() ?: ro.exceptionOrNull())?.message,
        )
    }

    // ── Overpass ─────────────────────────────────────────────────────────────

    /** Tries each public Overpass server in turn, quickly; throws only when all of them fail. */
    private fun overpassSearch(query: String, lat: Double, lon: Double, dermatology: Boolean): List<Found> {
        var last: Exception = IOException("No Overpass server reachable")
        for (endpoint in OVERPASS_ENDPOINTS) {
            try {
                return runOverpass(endpoint, query, lat, lon, dermatology)
            } catch (e: Exception) {
                Log.w(TAG, "Overpass $endpoint: ${e.message}")
                last = e
            }
        }
        throw IOException("Overpass unavailable: ${last.message}", last)
    }

    private fun runOverpass(endpoint: String, query: String, lat: Double, lon: Double, dermatology: Boolean): List<Found> {
        // Overpass expects the query as a form field named "data".
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("data", query).build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("empty response")
            return json.decodeFromString<OverpassResponse>(body).elements.mapNotNull { element ->
                val pointLat = element.lat ?: element.center?.lat ?: return@mapNotNull null
                val pointLon = element.lon ?: element.center?.lon ?: return@mapNotNull null
                Found(
                    clinic = Clinic(
                        id = "${element.type}_${element.id}",
                        name = element.tags["name"]?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME,
                        lat = pointLat,
                        lon = pointLon,
                        address = buildAddress(element.tags),
                        phone = element.tags["phone"] ?: element.tags["contact:phone"],
                        website = element.tags["website"] ?: element.tags["contact:website"],
                        distanceMeters = haversineMeters(lat, lon, pointLat, pointLon),
                    ),
                    isDermatology = dermatology,
                )
            }
        }
    }

    // ── Nominatim ────────────────────────────────────────────────────────────

    /** One search per term inside a box around the user, spaced out per Nominatim's 1-request-a-second rule. */
    private suspend fun nominatimSearch(terms: List<String>, lat: Double, lon: Double, dermatology: Boolean): List<Found> {
        val out = mutableListOf<Found>()
        var failures = 0
        var lastError: Exception? = null
        terms.forEachIndexed { index, term ->
            if (index > 0) delay(NOMINATIM_SPACING_MS)
            try {
                out += runNominatim(term, lat, lon, dermatology)
            } catch (e: Exception) {
                failures++
                lastError = e
                Log.w(TAG, "Nominatim '$term': ${e.message}")
            }
        }
        // Individual terms can fail; only report failure if nothing at all answered.
        if (failures == terms.size) throw IOException("Nominatim unavailable: ${lastError?.message}", lastError)
        return out
    }

    private fun runNominatim(term: String, lat: Double, lon: Double, dermatology: Boolean): List<Found> {
        val d = NOMINATIM_BOX_DEGREES
        val url = HttpUrl.Builder()
            .scheme("https").host("nominatim.openstreetmap.org").addPathSegment("search")
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("q", term)
            .addQueryParameter("bounded", "1")
            // left,top,right,bottom
            .addQueryParameter("viewbox", "${lon - d},${lat + d},${lon + d},${lat - d}")
            .addQueryParameter("limit", "20")
            .addQueryParameter("extratags", "1")
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("empty response")
            return json.decodeFromString<List<NominatimItem>>(body).mapNotNull { item ->
                val pointLat = item.lat.toDoubleOrNull() ?: return@mapNotNull null
                val pointLon = item.lon.toDoubleOrNull() ?: return@mapNotNull null
                val name = item.name.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
                Found(
                    clinic = Clinic(
                        // Same "<type>_<id>" form Overpass uses, so a place found by both merges into one.
                        id = "${item.osm_type}_${item.osm_id}",
                        name = name,
                        lat = pointLat,
                        lon = pointLon,
                        address = item.display_name.split(",").drop(1).take(3)
                            .joinToString(", ") { it.trim() }.ifBlank { "Address not listed" },
                        phone = item.extratags?.get("phone") ?: item.extratags?.get("contact:phone"),
                        website = item.extratags?.get("website") ?: item.extratags?.get("contact:website"),
                        distanceMeters = haversineMeters(lat, lon, pointLat, pointLon),
                    ),
                    isDermatology = dermatology,
                )
            }
        }
    }

    private fun buildAddress(tags: Map<String, String>): String {
        val house = tags["addr:housenumber"] ?: ""
        val street = tags["addr:street"] ?: ""
        val city = tags["addr:city"] ?: ""
        val postcode = tags["addr:postcode"] ?: ""
        return listOf("$house $street".trim(), city, postcode)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "Address not listed" }
    }

    companion object {
        private const val TAG = "DermoFinder"

        /** Public Overpass instances, tried in this order with short timeouts. */
        val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )
        const val USER_AGENT = "DermoAI/2.0 (Android; educational app)"

        private const val RADIUS = 15_000
        private const val BROAD_RADIUS = 5_000
        private const val MAX_DISTANCE_M = 20_000.0
        private const val MAX_RESULTS = 40
        private const val DEFAULT_NAME = "Dermatologist"
        private const val NOMINATIM_BOX_DEGREES = 0.15 // about 15 km each way
        private const val NOMINATIM_SPACING_MS = 1_100L
        private val DERM_TERMS = listOf("dermatologist", "dermatology", "skin clinic")
        private val BROAD_TERMS = listOf("doctors", "clinic")
        private val DERM_NAME = Regex("derma|skin", RegexOption.IGNORE_CASE)

        // Exact tag values only (Overpass answers those from its index); regex filters on
        // common keys force a planet-wide scan. OSM spells it "speciality" or "specialty".
        private fun dermQuery(lat: Double, lon: Double) = buildString {
            val around = "(around:$RADIUS,$lat,$lon)"
            append("[out:json][timeout:8];(")
            append("nwr[\"healthcare:speciality\"=\"dermatology\"]$around;")
            append("nwr[\"healthcare:specialty\"=\"dermatology\"]$around;")
            append("nwr[\"healthcare\"=\"dermatologist\"]$around;")
            append(");out center tags;")
        }

        // Small radius and a result cap: in a dense city this set is otherwise huge.
        private fun broadQuery(lat: Double, lon: Double) = buildString {
            val around = "(around:$BROAD_RADIUS,$lat,$lon)"
            append("[out:json][timeout:8];(")
            append("nwr[\"amenity\"=\"doctors\"]$around;")
            append("nwr[\"amenity\"=\"clinic\"]$around;")
            append(");out center tags 60;")
        }

        /** Haversine distance in meters between two coordinates. */
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}

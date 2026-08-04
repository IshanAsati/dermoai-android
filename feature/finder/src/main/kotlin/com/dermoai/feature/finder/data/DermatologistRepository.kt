package com.dermoai.feature.finder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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

/**
 * Finds dermatologists near a location via the free Overpass (OpenStreetMap)
 * API. Respects usage policies: one request per search, 25s timeout, no
 * polling, proper User-Agent. Falls back to general clinics when no
 * dermatologist exists nearby.
 */
@Singleton
class DermatologistRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findNearby(lat: Double, lon: Double): NearbyResult = withContext(Dispatchers.IO) {
        val dermatologists = queryOverpass(
            query = dermQuery(lat, lon),
            lat = lat,
            lon = lon,
        )
        if (dermatologists.isNotEmpty()) {
            NearbyResult(clinics = dermatologists, isBroadFallback = false)
        } else {
            NearbyResult(
                clinics = queryOverpass(
                    query = broadQuery(lat, lon),
                    lat = lat,
                    lon = lon,
                ),
                isBroadFallback = true,
            )
        }
    }

    private fun queryOverpass(query: String, lat: Double, lon: Double): List<Clinic> {
        val request = Request.Builder()
            .url(OVERPASS_ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .post(okhttp3.RequestBody.create(null, query))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val parsed = runCatching { json.decodeFromString<OverpassResponse>(body) }.getOrNull()
                ?: return emptyList()
            return parsed.elements.mapNotNull { element ->
                val pointLat = element.lat ?: element.center?.lat ?: return@mapNotNull null
                val pointLon = element.lon ?: element.center?.lon ?: return@mapNotNull null
                val name = element.tags["name"]?.takeIf { it.isNotBlank() }
                Clinic(
                    id = "${element.type}_${element.id}",
                    name = name ?: DEFAULT_NAME,
                    lat = pointLat,
                    lon = pointLon,
                    address = buildAddress(element.tags),
                    phone = element.tags["phone"] ?: element.tags["contact:phone"],
                    website = element.tags["website"] ?: element.tags["contact:website"],
                    distanceMeters = haversineMeters(lat, lon, pointLat, pointLon),
                )
            }.sortedBy { it.distanceMeters }
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
        const val OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val USER_AGENT = "DermoAI/1.0 (Android; educational app)"
        private const val RADIUS_DERM = 10_000
        private const val RADIUS_BROAD = 15_000
        private const val DEFAULT_NAME = "Dermatologist"

        private fun dermQuery(lat: Double, lon: Double) = buildString {
            append("[out:json][timeout:25];")
            append("(node[\"healthcare:specialty\"=\"dermatology\"](around:$RADIUS_DERM,$lat,$lon);")
            append("node[\"healthcare\"=\"dermatologist\"](around:$RADIUS_DERM,$lat,$lon);")
            append("way[\"healthcare:specialty\"=\"dermatology\"](around:$RADIUS_DERM,$lat,$lon);")
            append("way[\"healthcare\"=\"dermatologist\"](around:$RADIUS_DERM,$lat,$lon););")
            append("out center tags;")
        }

        private fun broadQuery(lat: Double, lon: Double) = buildString {
            append("[out:json][timeout:25];")
            append("(node[\"amenity\"=\"doctors\"](around:$RADIUS_BROAD,$lat,$lon);")
            append("way[\"amenity\"=\"doctors\"](around:$RADIUS_BROAD,$lat,$lon););")
            append("out center tags;")
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

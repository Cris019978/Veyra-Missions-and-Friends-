package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SafePublicSpot(
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val placeType: String,
  val isFromOpenStreetMap: Boolean = true,
)

/**
 * Configures osmdroid for public OpenStreetMap tile rendering without requiring any API keys
 * or external storage permissions, and queries OpenStreetMap's Overpass API to anchor missions
 * onto verified open public places (parks, plazas, museums, libraries, gardens, pedestrian paths)
 * while excluding private property and dangerous/restricted zones.
 */
object OsmSafePlaceFinder {

  @Volatile
  private var osmdroidConfigured = false

  fun configureOsmdroid(context: Context) {
    if (osmdroidConfigured) return
    synchronized(this) {
      if (osmdroidConfigured) return
      val appCtx = context.applicationContext
      val config = Configuration.getInstance()
      val baseDir = File(appCtx.cacheDir, "osmdroid").apply { mkdirs() }
      val tileDir = File(baseDir, "tiles").apply { mkdirs() }
      config.osmdroidBasePath = baseDir
      config.osmdroidTileCache = tileDir
      config.userAgentValue = appCtx.packageName.ifBlank { "com.aistudio.radarquest.vxkmqp" }
      config.load(
        appCtx,
        appCtx.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE),
      )
      osmdroidConfigured = true
    }
  }

  val EXCLUDED_PRIVATE_OR_DANGEROUS_TYPES = setOf(
    "hospital",
    "clinic",
    "doctors",
    "doctor",
    "dentist",
    "medical_lab",
    "pharmacy",
    "police",
    "fire_station",
    "prison",
    "courthouse",
    "school",
    "primary_school",
    "secondary_school",
    "preschool",
    "kindergarten",
    "industrial",
    "industrial_estate",
    "military",
    "gas_station",
    "fuel",
    "car_repair",
    "car_wash",
    "cemetery",
    "grave_yard",
    "funeral_home",
    "bar",
    "night_club",
    "nightclub",
    "pub",
    "casino",
    "liquor_store",
    "parking",
    "airport",
    "aerodrome",
    "train_station",
    "subway_station",
    "bus_station",
    "transit_depot",
    "storage",
    "moving_company",
    "charging_station",
    "electric_vehicle_charging_station",
    "real_estate_agency",
    "motorway",
    "trunk",
    "private",
    "residential",
  )

  val BLOCKED_KEYWORDS = setOf(
    "privad",
    "private",
    "residenc",
    "condominio",
    "casa ",
    "hogar",
    "hospital",
    "clínica",
    "clinica",
    "urgencias",
    "policía",
    "policia",
    "comisaría",
    "comisaria",
    "cárcel",
    "carcel",
    "prisión",
    "prison",
    "militar",
    "cuartel",
    "fábrica",
    "fabrica",
    "industrial",
    "bodega",
    "almacén",
    "gasolinera",
    "combustible",
    "autopista",
    "carretera",
    "periférico",
    "viaducto",
    "aeropuerto",
    "estación de tren",
    "cementerio",
    "panteón",
    "panteon",
    "casino",
    "cantina",
    "nightclub",
    "bar ",
    "estacionamiento",
    "taller mecánico",
    "taller mecanico",
    "subestación",
    "subestacion",
    "peligro",
    "restringid",
  )

  /**
   * Pure deterministic safety filter evaluated both on OpenStreetMap/Overpass responses and unit tests.
   */
  fun isSafePublicSpot(
    name: String,
    types: List<String>,
    businessStatus: String? = "OPERATIONAL",
  ): Boolean {
    if (name.isBlank()) return false
    if (!businessStatus.isNullOrBlank() && businessStatus != "OPERATIONAL") return false

    val normalizedTypes = types.map { it.lowercase() }
    if (normalizedTypes.any { it in EXCLUDED_PRIVATE_OR_DANGEROUS_TYPES }) {
      return false
    }

    val lowerName = name.lowercase()
    if (BLOCKED_KEYWORDS.any { keyword -> lowerName.contains(keyword) }) {
      return false
    }

    return true
  }

  suspend fun fetchSafePublicAnchors(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
  ): List<SafePublicSpot> = withContext(Dispatchers.IO) {
    if (latitude == 0.0 && longitude == 0.0) return@withContext emptyList()

    val clampedRadius = radiusMeters.coerceIn(300, 6000)
    val places = queryOverpassPublicPlaces(latitude, longitude, clampedRadius)
    if (places.size >= 8) {
      return@withContext places
    }

    val walkable = queryOverpassWalkablePaths(latitude, longitude, clampedRadius, needed = 10 - places.size)
    (places + walkable).distinctBy { "${"%.4f".format(it.latitude)},${"%.4f".format(it.longitude)}" }
  }

  private fun queryOverpassPublicPlaces(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
  ): List<SafePublicSpot> {
    return runCatching {
      val query = """
        [out:json][timeout:6];
        (
          node(around:$radiusMeters,$latitude,$longitude)["leisure"~"^(park|garden|playground|recreation_ground)$"]["access"!~"^(private|no)$"];
          way(around:$radiusMeters,$latitude,$longitude)["leisure"~"^(park|garden|recreation_ground)$"]["access"!~"^(private|no)$"];
          node(around:$radiusMeters,$latitude,$longitude)["tourism"~"^(museum|attraction|artwork|viewpoint|gallery)$"]["access"!~"^(private|no)$"];
          node(around:$radiusMeters,$latitude,$longitude)["amenity"~"^(library|community_centre|arts_centre|fountain|marketplace|cafe)$"]["access"!~"^(private|no)$"];
          node(around:$radiusMeters,$latitude,$longitude)["place"~"^(square|plaza)$"];
          way(around:$radiusMeters,$latitude,$longitude)["place"~"^(square|plaza)$"];
        );
        out center 25;
      """.trimIndent()

      val body = "data=" + URLEncoder.encode(query, "UTF-8")
      val url = URL("https://overpass-api.de/api/interpreter")
      val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 5000
        readTimeout = 6000
        doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        setRequestProperty("User-Agent", "VeyraAndroid/1.0 (OpenStreetMap Mission Radar)")
      }

      conn.outputStream.use { os ->
        os.write(body.toByteArray(Charsets.UTF_8))
      }

      if (conn.responseCode !in 200..299) {
        conn.disconnect()
        return@runCatching emptyList()
      }

      val raw = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
      conn.disconnect()
      parseOverpassResponse(raw)
    }.getOrDefault(emptyList())
  }

  internal fun parseOverpassResponse(rawJson: String): List<SafePublicSpot> {
    val root = JSONObject(rawJson)
    val elements = root.optJSONArray("elements") ?: return emptyList()
    val results = mutableListOf<SafePublicSpot>()

    for (i in 0 until elements.length()) {
      val obj = elements.optJSONObject(i) ?: continue
      val tags = obj.optJSONObject("tags") ?: continue
      val access = tags.optString("access", "").lowercase()
      if (access == "private" || access == "no" || access == "customers") continue

      val centerObj = obj.optJSONObject("center")
      val lat = if (obj.has("lat")) obj.optDouble("lat", Double.NaN) else centerObj?.optDouble("lat", Double.NaN) ?: Double.NaN
      val lon = if (obj.has("lon")) obj.optDouble("lon", Double.NaN) else centerObj?.optDouble("lon", Double.NaN) ?: Double.NaN
      if (lat.isNaN() || lon.isNaN()) continue

      val rawName = tags.optString("name", "").trim()
      val leisure = tags.optString("leisure", "")
      val tourism = tags.optString("tourism", "")
      val amenity = tags.optString("amenity", "")
      val place = tags.optString("place", "")
      val landuse = tags.optString("landuse", "")
      val highway = tags.optString("highway", "")

      val primaryType = listOf(leisure, tourism, amenity, place, highway)
        .firstOrNull { it.isNotBlank() } ?: "public_spot"

      val displayName = rawName.ifBlank {
        when (primaryType) {
          "park", "garden", "recreation_ground" -> "Parque público abierto"
          "square", "plaza" -> "Plaza pública"
          "museum", "gallery", "artwork" -> "Espacio cultural público"
          "library" -> "Biblioteca pública"
          "viewpoint" -> "Mirador público"
          "fountain" -> "Fuente urbana"
          "pedestrian", "footway" -> "Andador peatonal OSM"
          else -> "Punto público OSM"
        }
      }

      val types = listOf(leisure, tourism, amenity, place, landuse, highway, access)
        .filter { it.isNotBlank() }

      val status = if (tags.optString("disused", "") == "yes") "CLOSED_TEMPORARILY" else "OPERATIONAL"
      if (isSafePublicSpot(displayName, types, status)) {
        results.add(
          SafePublicSpot(
            name = displayName.take(64),
            latitude = lat,
            longitude = lon,
            placeType = primaryType,
            isFromOpenStreetMap = true,
          )
        )
      }
    }
    return results
  }

  private fun queryOverpassWalkablePaths(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int,
    needed: Int,
  ): List<SafePublicSpot> {
    if (needed <= 0) return emptyList()
    return runCatching {
      val searchR = radiusMeters.coerceIn(300, 1200)
      val query = """
        [out:json][timeout:5];
        way(around:$searchR,$latitude,$longitude)["highway"~"^(pedestrian|footway|path|living_street)$"]["access"!~"^(private|no)$"];
        out center ${needed.coerceIn(1, 10)};
      """.trimIndent()
      val body = "data=" + URLEncoder.encode(query, "UTF-8")
      val url = URL("https://overpass-api.de/api/interpreter")
      val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 4500
        readTimeout = 4500
        doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        setRequestProperty("User-Agent", "VeyraAndroid/1.0 (OpenStreetMap Mission Radar)")
      }
      conn.outputStream.use { os ->
        os.write(body.toByteArray(Charsets.UTF_8))
      }
      if (conn.responseCode !in 200..299) {
        conn.disconnect()
        return@runCatching emptyList()
      }
      val raw = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
      conn.disconnect()
      parseOverpassResponse(raw)
    }.getOrDefault(emptyList())
  }
}

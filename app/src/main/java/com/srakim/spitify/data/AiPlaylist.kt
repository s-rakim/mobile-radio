package com.srakim.spitify.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/** How to get into a song from the one before, as the DJ marked it. */
@Serializable
data class MixIn(val technique: String = "blend", val bars: Int = 16, val fadeSec: Double = 6.0)

@Serializable
data class DjPick(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val genre: String? = null,
    val bpm: Int? = null,
    val key: String? = null,
    val energy: Double = 0.5,
    val note: String? = null,
    val mixIn: MixIn? = null,
)

/** What the server's DJ sends back. "agent" when a model picked the songs, "rules" otherwise. */
@Serializable
data class DjReply(
    val engine: String = "rules",
    val title: String? = null,
    val description: String? = null,
    val intro: String? = null,
    val arc: String? = null,
    val tracks: List<DjPick> = emptyList(),
)

/** Fade lengths per song for DJ sets, read by the playback service when it mixes. */
object MixPlan {
    private val fades = ConcurrentHashMap<String, Long>()

    fun remember(picks: List<DjPick>) = picks.forEach { p ->
        p.mixIn?.let { fades[p.id] = (it.fadeSec * 1000).toLong().coerceIn(300L, 12_000L) }
    }

    fun fadeMs(id: String?): Long? = id?.let { fades[it] }
}

internal val djJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

/**
 * AI Playlist. With a Spitify account the prompt goes to the server's DJ agent, which digs
 * through Audius with a language model and orders the songs for tempo, key and energy like a
 * real set. Without one (or if the server can't be reached) the prompt is read on the phone:
 * words that name a genre, a mood, an activity, a time of day or an artist ("songs like Burna
 * Boy") turn into Audius searches and genre charts, then get mixed and shaped into a set.
 */
object AiPlaylist {

    data class Result(
        val title: String,
        val description: String,
        val understood: List<String>,
        val tracks: List<Track>,
        val intro: String? = null,
        val byAgent: Boolean = false,
    )

    /** Asks the server's DJ first and falls back to the phone. */
    suspend fun generate(prompt: String, catalog: Catalog, account: AccountSync, skip: Set<String>, size: Int = 40): Result {
        if (account.state.value.signedIn) {
            val fromServer = runCatching { fromServer(prompt, catalog, account, skip, size) }.getOrNull()
            if (fromServer != null && fromServer.tracks.size >= minOf(5, size)) return fromServer
        }
        return generate(prompt, catalog, skip, size)
    }

    private suspend fun fromServer(prompt: String, catalog: Catalog, account: AccountSync, skip: Set<String>, size: Int): Result {
        val reply = withContext(Dispatchers.IO) {
            account.request(
                "api/ai/playlist",
                "POST",
                buildJsonObject {
                    put("prompt", prompt)
                    put("size", size)
                    put("skip", JsonArray(skip.take(300).map { JsonPrimitive(it) }))
                },
                slow = true,
            )
        }.let { djJson.decodeFromJsonElement<DjReply>(it) }
        MixPlan.remember(reply.tracks)
        val tracks = catalog.audius.tracksByIds(reply.tracks.map { it.id })
        val title = reply.title?.takeIf { it.isNotBlank() } ?: prompt.trim().take(40).replaceFirstChar { it.uppercase() }
        return Result(
            title = title,
            description = reply.description ?: "Made from \"${prompt.trim()}\"",
            understood = listOfNotNull(reply.arc?.let { "$it arc" }, if (reply.engine == "agent") "picked by the DJ" else null),
            tracks = tracks,
            intro = reply.intro,
            byAgent = reply.engine == "agent",
        )
    }

    private data class Hint(val genres: List<String> = emptyList(), val searches: List<String> = emptyList(), val label: String)

    // Everyday words mapped onto Audius genres and search terms.
    private val vocabulary: Map<String, Hint> = buildMap<String, Hint> {
        fun add(words: String, hint: Hint) { words.split(',').forEach { w -> this[w.trim()] = hint } }
        add("hip hop,hiphop,rap,rapper,bars", Hint(listOf("Hip-Hop/Rap"), label = "hip-hop"))
        add("trap,drill", Hint(listOf("Trap", "Hip-Hop/Rap"), listOf("drill"), "trap"))
        add("edm,electronic,electro,synth,synthwave", Hint(listOf("Electronic"), label = "electronic"))
        add("pop,catchy,radio", Hint(listOf("Pop"), label = "pop"))
        add("rnb,r&b,soul,neo-soul,slow jams", Hint(listOf("R&B/Soul"), label = "R&B"))
        add("lofi,lo-fi,study,studying,homework,focus,concentrate,coding,reading", Hint(listOf("Lo-Fi", "Ambient"), listOf("lofi beats"), "focus"))
        add("house,deep house,afro house,amapiano", Hint(listOf("House"), listOf("amapiano"), "house"))
        add("rock,guitar,punk,grunge,metal", Hint(listOf("Rock", "Alternative"), label = "rock"))
        add("indie,alternative,alt", Hint(listOf("Alternative"), label = "indie"))
        add("ambient,calm,relax,relaxing,chill,chilled,meditation,meditate,spa,peaceful", Hint(listOf("Ambient", "Lo-Fi"), listOf("chill"), "chill"))
        add("sleep,sleepy,bedtime,night,late night,3am", Hint(listOf("Ambient", "Lo-Fi"), listOf("sleep"), "late night"))
        add("jazz,jazzy,saxophone,sax,coffee,cafe", Hint(listOf("Jazz"), listOf("jazz"), "jazz"))
        add("latin,reggaeton,salsa,bachata", Hint(listOf("Latin"), listOf("reggaeton"), "latin"))
        add("techno,rave,warehouse", Hint(listOf("Techno"), label = "techno"))
        add("dubstep,bass,riddim", Hint(listOf("Dubstep"), label = "bass"))
        add("acoustic,unplugged,folk,singer-songwriter", Hint(listOf("Acoustic"), label = "acoustic"))
        add("reggae,dancehall,island", Hint(listOf("Reggae"), listOf("dancehall"), "reggae"))
        add("movie,film,cinematic,epic,soundtrack,score", Hint(listOf("Soundtrack"), listOf("cinematic"), "cinematic"))
        add("funk,funky,disco,groove", Hint(listOf("Funk"), listOf("disco"), "funk"))
        add("classical,piano,orchestra,violin", Hint(listOf("Classical"), listOf("piano"), "classical"))
        add("afrobeats,afrobeat,afro,naija,afropop", Hint(listOf("Hip-Hop/Rap", "Pop"), listOf("afrobeats"), "afrobeats"))
        add("gym,workout,run,running,cardio,lift,lifting,hype,pump", Hint(listOf("Hip-Hop/Rap", "Electronic", "Trap"), listOf("workout"), "workout"))
        add("party,club,dance,dancing,turn up,pregame,pre game", Hint(listOf("House", "Electronic", "Hip-Hop/Rap"), listOf("party"), "party"))
        add("sad,heartbreak,breakup,cry,crying,lonely,melancholy", Hint(listOf("R&B/Soul", "Alternative", "Acoustic"), listOf("sad"), "sad"))
        add("happy,sunny,summer,feel good,good vibes,upbeat", Hint(listOf("Pop", "Funk"), listOf("summer"), "feel-good"))
        add("love,romantic,date,valentine", Hint(listOf("R&B/Soul", "Pop"), listOf("love"), "romantic"))
        add("road trip,drive,driving,car", Hint(listOf("Pop", "Rock", "Hip-Hop/Rap"), listOf("road trip"), "road trip"))
        add("morning,wake up,breakfast", Hint(listOf("Acoustic", "Pop"), listOf("morning"), "morning"))
        add("rainy,rain,cozy,autumn,fall", Hint(listOf("Lo-Fi", "Jazz", "Acoustic"), listOf("rainy day"), "cozy"))
        add("gaming,game,videogame", Hint(listOf("Electronic", "Dubstep"), listOf("gaming"), "gaming"))
        add("gospel,worship,church", Hint(emptyList(), listOf("gospel"), "gospel"))
    }

    private val filler = setOf(
        "a", "an", "the", "some", "songs", "song", "music", "playlist", "for", "to", "with", "and", "of", "me", "my",
        "make", "give", "i", "want", "need", "mix", "tracks", "vibes", "vibe", "that", "like", "please", "in", "on", "while",
    )

    suspend fun generate(prompt: String, catalog: Catalog, skip: Set<String>, size: Int = 40): Result = coroutineScope {
        val text = prompt.lowercase().replace(Regex("[^a-z0-9&' -]"), " ").replace(Regex("\\s+"), " ").trim()
        val hints = vocabulary.entries
            .filter { (word, _) -> Regex("(^| )${Regex.escape(word)}( |$)").containsMatchIn(text) }
            .map { it.value }
        val genres = hints.flatMap { it.genres }.distinct().take(4)
        val searches = hints.flatMap { it.searches }.distinct().take(3)

        // "like Burna Boy", "by Tems", "similar to SZA": everything after those words is an artist.
        val artist = Regex("(?:like|by|similar to|sounds like|inspired by) ([a-z0-9&' .-]{2,40})")
            .find(text)?.groupValues?.get(1)
            ?.split(" and ", " but ", " for ", " with ")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() && vocabulary[it] == null }

        // Leftover words that weren't understood still get a search, they may be a name or a scene.
        val known = vocabulary.keys.flatMap { it.split(' ') }.toSet()
        val leftovers = text.split(' ').filter { it.length > 2 && it !in filler && it !in known && artist?.contains(it) != true }
            .take(3).joinToString(" ")

        val jobs = buildList {
            genres.forEach { g -> add(async { runCatching { catalog.audius.trendingTracks(genre = g, time = "month", limit = 50) }.getOrDefault(emptyList()) }) }
            searches.forEach { q -> add(async { runCatching { catalog.audius.searchTracks(q) }.getOrDefault(emptyList()) }) }
            if (artist != null) add(async { runCatching { catalog.audius.searchTracks(artist) }.getOrDefault(emptyList()) })
            if (leftovers.isNotBlank()) add(async { runCatching { catalog.audius.searchTracks(leftovers) }.getOrDefault(emptyList()) })
            if (isEmpty()) add(async { runCatching { catalog.audius.trendingTracks(limit = 60) }.getOrDefault(emptyList()) })
        }
        val pools = jobs.awaitAll().map { list -> list.filter { it.id !in skip }.shuffled().toMutableList() }

        // Deal one from each pool in turn so no single genre or search takes over.
        val out = LinkedHashMap<String, Track>()
        val perArtist = HashMap<String, Int>()
        while (out.size < size && pools.any { it.isNotEmpty() }) {
            for (pool in pools) {
                val next = pool.removeFirstOrNull() ?: continue
                val count = perArtist[next.artist] ?: 0
                // Two songs per artist at most, unless the prompt asked for that artist.
                if (count >= 2 && (artist == null || !next.artist.lowercase().contains(artist))) continue
                if (out.putIfAbsent(next.id, next) == null) perArtist[next.artist] = count + 1
                if (out.size >= size) break
            }
        }

        val understood = buildList {
            hints.map { it.label }.distinct().forEach { add(it) }
            artist?.let { add("like ${it.split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }}") }
        }
        val title = prompt.trim().take(40).replaceFirstChar { it.uppercase() }.ifBlank { "AI Playlist" }
        val description = if (understood.isEmpty()) "Made from \"${prompt.trim()}\"" else "Made from \"${prompt.trim()}\": ${understood.joinToString(", ")}"
        Result(title, description, understood, SetShaper.shape(out.values.toList()))
    }
}

/**
 * The phone's own sequencing when the server's DJ isn't around: a rough energy per song from
 * its genre, then a warm-up, a climb to a peak about two thirds in and a landing, with the
 * same artist kept apart. Much simpler than the server's, which also has tempo and key.
 */
object SetShaper {
    private val energy = mapOf(
        "Ambient" to 0.2, "Lo-Fi" to 0.3, "Classical" to 0.3, "Acoustic" to 0.35, "Folk" to 0.35, "Downtempo" to 0.38,
        "Jazz" to 0.4, "Soundtrack" to 0.45, "Blues" to 0.45, "Country" to 0.5, "R&B/Soul" to 0.5, "Reggae" to 0.5,
        "World" to 0.5, "Alternative" to 0.55, "Pop" to 0.62, "Funk" to 0.65, "Hip-Hop/Rap" to 0.66, "Latin" to 0.68,
        "Deep House" to 0.68, "Disco" to 0.68, "Dancehall" to 0.7, "Rock" to 0.7, "Electronic" to 0.74, "House" to 0.76,
        "Tech House" to 0.8, "Trap" to 0.8, "Trance" to 0.82, "Techno" to 0.84, "Punk" to 0.85, "Metal" to 0.88,
        "Drum & Bass" to 0.9, "Dubstep" to 0.9, "Hardstyle" to 0.95,
    )

    fun energyOf(t: Track): Double = energy[t.genre] ?: 0.55

    private fun target(i: Int, n: Int): Double {
        val t = if (n <= 1) 0.0 else i.toDouble() / (n - 1)
        val pts = listOf(0.0 to 0.45, 0.35 to 0.64, 0.68 to 0.88, 0.85 to 0.8, 1.0 to 0.6)
        for (k in 1 until pts.size) {
            val (x0, y0) = pts[k - 1]
            val (x1, y1) = pts[k]
            if (t <= x1) return y0 + (y1 - y0) * (t - x0) / (x1 - x0)
        }
        return 0.6
    }

    fun shape(tracks: List<Track>): List<Track> {
        val left = tracks.toMutableList()
        val out = ArrayList<Track>(tracks.size)
        for (i in tracks.indices) {
            val goal = target(i, tracks.size)
            val prev = out.lastOrNull()
            val recent = out.takeLast(3).map { it.artist }
            val pick = left.minBy { t ->
                var cost = kotlin.math.abs(energyOf(t) - goal) * 3
                if (prev != null && t.genre != prev.genre) cost += 0.4
                if (t.artist in recent) cost += if (t.artist == prev?.artist) 3.0 else 1.0
                cost
            }
            left.remove(pick)
            out += pick
        }
        return out
    }
}

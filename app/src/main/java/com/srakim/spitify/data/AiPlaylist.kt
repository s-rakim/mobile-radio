package com.srakim.spitify.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * AI Playlist without a paid AI service. The prompt is read on the phone: words that name a
 * genre, a mood, an activity, a time of day or an artist ("songs like Burna Boy") are turned
 * into Audius searches and genre charts, then mixed and trimmed into a playlist. It runs
 * offline from any model and costs nothing, and it explains what it picked up so you can
 * tweak the prompt.
 */
object AiPlaylist {

    data class Result(val title: String, val description: String, val understood: List<String>, val tracks: List<Track>)

    private data class Hint(val genres: List<String> = emptyList(), val searches: List<String> = emptyList(), val label: String)

    // Everyday words mapped onto Audius genres and search terms.
    private val vocabulary: Map<String, Hint> = buildMap {
        fun put(words: String, hint: Hint) = words.split(',').forEach { put(it.trim(), hint) }
        put("hip hop,hiphop,rap,rapper,bars", Hint(listOf("Hip-Hop/Rap"), label = "hip-hop"))
        put("trap,drill", Hint(listOf("Trap", "Hip-Hop/Rap"), listOf("drill"), "trap"))
        put("edm,electronic,electro,synth,synthwave", Hint(listOf("Electronic"), label = "electronic"))
        put("pop,catchy,radio", Hint(listOf("Pop"), label = "pop"))
        put("rnb,r&b,soul,neo-soul,slow jams", Hint(listOf("R&B/Soul"), label = "R&B"))
        put("lofi,lo-fi,study,studying,homework,focus,concentrate,coding,reading", Hint(listOf("Lo-Fi", "Ambient"), listOf("lofi beats"), "focus"))
        put("house,deep house,afro house,amapiano", Hint(listOf("House"), listOf("amapiano"), "house"))
        put("rock,guitar,punk,grunge,metal", Hint(listOf("Rock", "Alternative"), label = "rock"))
        put("indie,alternative,alt", Hint(listOf("Alternative"), label = "indie"))
        put("ambient,calm,relax,relaxing,chill,chilled,meditation,meditate,spa,peaceful", Hint(listOf("Ambient", "Lo-Fi"), listOf("chill"), "chill"))
        put("sleep,sleepy,bedtime,night,late night,3am", Hint(listOf("Ambient", "Lo-Fi"), listOf("sleep"), "late night"))
        put("jazz,jazzy,saxophone,sax,coffee,cafe", Hint(listOf("Jazz"), listOf("jazz"), "jazz"))
        put("latin,reggaeton,salsa,bachata", Hint(listOf("Latin"), listOf("reggaeton"), "latin"))
        put("techno,rave,warehouse", Hint(listOf("Techno"), label = "techno"))
        put("dubstep,bass,riddim", Hint(listOf("Dubstep"), label = "bass"))
        put("acoustic,unplugged,folk,singer-songwriter", Hint(listOf("Acoustic"), label = "acoustic"))
        put("reggae,dancehall,island", Hint(listOf("Reggae"), listOf("dancehall"), "reggae"))
        put("movie,film,cinematic,epic,soundtrack,score", Hint(listOf("Soundtrack"), listOf("cinematic"), "cinematic"))
        put("funk,funky,disco,groove", Hint(listOf("Funk"), listOf("disco"), "funk"))
        put("classical,piano,orchestra,violin", Hint(listOf("Classical"), listOf("piano"), "classical"))
        put("afrobeats,afrobeat,afro,naija,afropop", Hint(listOf("Hip-Hop/Rap", "Pop"), listOf("afrobeats"), "afrobeats"))
        put("gym,workout,run,running,cardio,lift,lifting,hype,pump", Hint(listOf("Hip-Hop/Rap", "Electronic", "Trap"), listOf("workout"), "workout"))
        put("party,club,dance,dancing,turn up,pregame,pre game", Hint(listOf("House", "Electronic", "Hip-Hop/Rap"), listOf("party"), "party"))
        put("sad,heartbreak,breakup,cry,crying,lonely,melancholy", Hint(listOf("R&B/Soul", "Alternative", "Acoustic"), listOf("sad"), "sad"))
        put("happy,sunny,summer,feel good,good vibes,upbeat", Hint(listOf("Pop", "Funk"), listOf("summer"), "feel-good"))
        put("love,romantic,date,valentine", Hint(listOf("R&B/Soul", "Pop"), listOf("love"), "romantic"))
        put("road trip,drive,driving,car", Hint(listOf("Pop", "Rock", "Hip-Hop/Rap"), listOf("road trip"), "road trip"))
        put("morning,wake up,breakfast", Hint(listOf("Acoustic", "Pop"), listOf("morning"), "morning"))
        put("rainy,rain,cozy,autumn,fall", Hint(listOf("Lo-Fi", "Jazz", "Acoustic"), listOf("rainy day"), "cozy"))
        put("gaming,game,videogame", Hint(listOf("Electronic", "Dubstep"), listOf("gaming"), "gaming"))
        put("gospel,worship,church", Hint(emptyList(), listOf("gospel"), "gospel"))
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
        Result(title, description, understood, out.values.toList())
    }
}

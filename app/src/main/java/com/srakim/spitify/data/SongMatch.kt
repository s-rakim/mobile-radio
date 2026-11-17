package com.srakim.spitify.data

import java.text.Normalizer

/** Tells whether two songs from different places are the same recording. */
object SongMatch {

    fun clean(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace("&", " and ")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex(" - .*(remaster|version|edit|live|mix).*"), " ")
        .replace(Regex("\\b(feat|ft|featuring)\\b.*"), " ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val ta = a.split(' ').toSet()
        val tb = b.split(' ').toSet()
        val jaccard = ta.intersect(tb).size.toDouble() / ta.union(tb).size
        val contains = if (a.contains(b) || b.contains(a)) 0.85 else 0.0
        return maxOf(jaccard, contains)
    }

    fun firstArtist(artist: String): String = artist.split(",", "&", " feat", " ft.", " x ", " and ").first().trim()

    /** Words that mark a remix, edit or cover rather than the song itself. */
    private val VERSION_WORDS = listOf(
        "remix", "flip", "sped", "speed", "slowed", "reverb", "nightcore", "8d", "cover", "live",
        "acoustic", "instrumental", "karaoke", "bootleg", "edit", "mashup", "chopped", "screwed", "bass boosted",
    ).map { Regex("\\b$it\\b") }

    /** One title is a remix, sped-up, slowed, live or cover version and the other isn't. */
    fun isOtherVersion(wanted: String, candidate: String): Boolean {
        val w = wanted.lowercase()
        val c = candidate.lowercase()
        return VERSION_WORDS.any { it.containsMatchIn(c) != it.containsMatchIn(w) }
    }

    /** True when [title] (cleaned) mentions [artist] (cleaned), as in "Rihanna - Needed Me (Lyrics)". */
    fun titleNames(title: String, artist: String): Boolean =
        artist.length >= 3 && Regex("\\b${Regex.escape(artist)}\\b").containsMatchIn(title)

    /** [title] with [artist]'s name taken out. */
    fun withoutArtist(title: String, artist: String): String =
        title.replace(artist, " ").replace(Regex("\\s+"), " ").trim()

    /**
     * True only when [candidate] is the very same recording as [wanted]: the same ISRC, or failing
     * that the same title (ignoring "(feat. …)", "- 2011 Remaster" and the like), the same main
     * artist and the same length give or take a few seconds. A remix, sped-up, live or cover
     * version never counts. Fan uploads that put the artist in the title ("Rihanna - Needed Me")
     * still count when everything else agrees.
     */
    fun exact(wanted: Track, candidate: Track): Boolean {
        val a = wanted.isrc?.trim()?.uppercase()
        val b = candidate.isrc?.trim()?.uppercase()
        if (!a.isNullOrEmpty() && a == b) return true
        if (isOtherVersion(wanted.title, candidate.title)) return false
        if (wanted.durationSec > 0 && candidate.durationSec > 0 &&
            kotlin.math.abs(wanted.durationSec - candidate.durationSec) > MAX_LENGTH_DIFF_SEC
        ) return false

        val wantedArtist = clean(firstArtist(wanted.artist))
        val candArtist = clean(firstArtist(candidate.artist))
        var wantedTitle = clean(wanted.title)
        var candTitle = clean(candidate.title)
        val named = when {
            titleNames(wantedTitle, candArtist) -> { wantedTitle = withoutArtist(wantedTitle, candArtist); true }
            titleNames(candTitle, wantedArtist) -> { candTitle = withoutArtist(candTitle, wantedArtist); true }
            else -> false
        }
        if (wantedTitle.isEmpty() || wantedTitle != candTitle) return false
        if (wanted.artist.isBlank()) return true
        return named ||
            similarity(wantedArtist, candArtist) >= 0.85 ||
            similarity(clean(wanted.artist), clean(candidate.artist)) >= 0.85
    }

    private const val MAX_LENGTH_DIFF_SEC = 7
}

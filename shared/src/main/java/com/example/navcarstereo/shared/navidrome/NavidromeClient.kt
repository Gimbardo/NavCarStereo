package com.example.navcarstereo.shared.navidrome

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Subsonic API (v1.16.1) client, just the calls NavCarStereo needs:
 * ping, recently played albums, an album's tracks, search, and stream/cover-art URLs.
 */
class NavidromeClient(private val config: NavidromeConfig) {

    private val salt = randomSalt()
    private val token = md5(config.password + salt)

    suspend fun ping() {
        withContext(Dispatchers.IO) { get("ping") }
    }

    suspend fun recentAlbums(size: Int = 20): List<NavAlbum> = albumList("recent", size)

    suspend fun newAlbums(size: Int = 20): List<NavAlbum> = albumList("newest", size)

    suspend fun topAlbums(size: Int = 20): List<NavAlbum> = albumList("frequent", size)

    suspend fun randomAlbums(size: Int = 20): List<NavAlbum> = albumList("random", size)

    suspend fun allAlbums(offset: Int, size: Int): List<NavAlbum> = albumList("alphabeticalByName", size, offset)

    private suspend fun albumList(type: String, size: Int, offset: Int = 0): List<NavAlbum> = withContext(Dispatchers.IO) {
        get(
            "getAlbumList2",
            "type" to type,
            "size" to size.toString(),
            "offset" to offset.toString(),
        )
            .getJSONObject("albumList2")
            .optJSONArray("album")
            .orEmpty()
            .map { it.toAlbum() }
    }

    suspend fun album(albumId: String): NavAlbum = withContext(Dispatchers.IO) {
        get("getAlbum", "id" to albumId).getJSONObject("album").toAlbum()
    }

    /**
     * Le tracce Subsonic hanno un `coverArt` proprio (spesso l'id del file, non dell'album): usarlo
     * causerebbe un secondo fetch/URL diverso dalla cover album già mostrata e già in cache. Si
     * sovrascrive con la cover art dell'album, identica a quella vista un attimo prima nella lista.
     */
    suspend fun albumSongs(albumId: String): List<NavSong> = withContext(Dispatchers.IO) {
        val album = get("getAlbum", "id" to albumId).getJSONObject("album")
        val albumCoverArtId = album.optStringOrNull("coverArt")
        album.optJSONArray("song")
            .orEmpty()
            .map { it.toSong() }
            .map { song -> if (albumCoverArtId != null) song.copy(coverArtId = albumCoverArtId) else song }
    }

    suspend fun song(songId: String): NavSong = withContext(Dispatchers.IO) {
        get("getSong", "id" to songId).getJSONObject("song").toSong()
    }

    suspend fun playlists(): List<NavPlaylist> = withContext(Dispatchers.IO) {
        get("getPlaylists")
            .getJSONObject("playlists")
            .optJSONArray("playlist")
            .orEmpty()
            .map { it.toPlaylist() }
    }

    suspend fun playlistSongs(playlistId: String): List<NavSong> = withContext(Dispatchers.IO) {
        get("getPlaylist", "id" to playlistId)
            .getJSONObject("playlist")
            .optJSONArray("entry")
            .orEmpty()
            .map { it.toSong() }
    }

    suspend fun search(query: String, songCount: Int = 25): List<NavSong> = withContext(Dispatchers.IO) {
        get(
            "search3",
            "query" to query,
            "songCount" to songCount.toString(),
            "albumCount" to "0",
            "artistCount" to "0",
        )
            .optJSONObject("searchResult3")
            ?.optJSONArray("song")
            .orEmpty()
            .map { it.toSong() }
    }

    suspend fun getLyricsBySongId(songId: String): NavLyrics? = withContext(Dispatchers.IO) {
        get("getLyricsBySongId", "id" to songId)
            .optJSONObject("lyricsList")
            ?.optJSONArray("structuredLyrics")
            .orEmpty()
            .firstOrNull()
            ?.toLyrics()
    }

    fun streamUrl(songId: String): String = buildUrl("stream", "id" to songId).toString()

    fun coverArtUrl(coverArtId: String): String = buildUrl("getCoverArt", "id" to coverArtId).toString()

    private fun get(endpoint: String, vararg params: Pair<String, String>): JSONObject {
        val connection = buildUrl(endpoint, *params).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            val body = connection.inputStream.bufferedReader().readText()
            val response = JSONObject(body).getJSONObject("subsonic-response")
            check(response.getString("status") == "ok") {
                response.optJSONObject("error")?.optString("message") ?: "Errore Navidrome"
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun buildUrl(endpoint: String, vararg params: Pair<String, String>): URL {
        val query = buildString {
            append("u=").append(encode(config.username))
            append("&t=").append(token)
            append("&s=").append(salt)
            append("&v=1.16.1&c=NavCarStereo&f=json")
            for ((key, value) in params) {
                append("&").append(key).append("=").append(encode(value))
            }
        }
        return URL("${config.serverUrl.trimEnd('/')}/rest/$endpoint?$query")
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        fun randomSalt(): String {
            val bytes = ByteArray(8)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun md5(input: String): String =
            MessageDigest.getInstance("MD5")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun JSONObject.optStringOrNull(name: String): String? = if (has(name)) getString(name) else null

        fun JSONObject.toAlbum() = NavAlbum(
            id = getString("id"),
            name = optString("name", getString("id")),
            artist = optString("artist", ""),
            coverArtId = optStringOrNull("coverArt"),
            songCount = optInt("songCount", 0),
        )

        fun JSONObject.toPlaylist() = NavPlaylist(
            id = getString("id"),
            name = optString("name", getString("id")),
            songCount = optInt("songCount", 0),
            coverArtId = optStringOrNull("coverArt"),
        )

        fun JSONObject.toSong() = NavSong(
            id = getString("id"),
            title = optString("title", getString("id")),
            artist = optString("artist", ""),
            album = optString("album", ""),
            albumId = optStringOrNull("albumId"),
            track = if (has("track")) optInt("track") else null,
            coverArtId = optStringOrNull("coverArt"),
            durationSeconds = if (has("duration")) optInt("duration") else null,
        )

        fun JSONArray?.orEmpty(): List<JSONObject> {
            if (this == null) return emptyList()
            return (0 until length()).map { getJSONObject(it) }
        }
    }
}

/** Non sincronizzate ("synced": false) → ogni riga ha startMs null, la vista scorre senza auto-highlight. */
internal fun JSONObject.toLyrics(): NavLyrics = NavLyrics(
    lines = optJSONArray("line").orEmptyLines().map {
        NavLyricLine(
            startMs = if (it.has("start")) it.optLong("start") else null,
            text = it.optString("value", ""),
        )
    },
)

private fun JSONArray?.orEmptyLines(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).map { getJSONObject(it) }
}

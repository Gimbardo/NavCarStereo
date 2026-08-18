package com.example.navcarstereo.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.MediaItem
import com.example.navcarstereo.shared.navidrome.NavidromeClient
import com.example.navcarstereo.shared.navidrome.NavidromeConfig
import com.example.navcarstereo.shared.parseSongId

private fun sanitize(name: String) = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")

/**
 * DownloadManager gestisce da solo coda, notifica e retry: niente WorkManager/foreground service.
 * Destinazione nella cartella privata dell'app (Android/data/.../Music) così non serve il permesso
 * di storage nemmeno su Android 9-10 (ponytail: se un domani serve visibile in Musica di sistema,
 * passare a setDestinationInExternalPublicDir + permesso runtime pre-API29).
 */
fun downloadTrack(context: Context, config: NavidromeConfig, track: MediaItem) {
    val songId = parseSongId(track.mediaId)
    val title = track.mediaMetadata.title?.toString() ?: songId
    val artist = track.mediaMetadata.artist?.toString()
    val filename = sanitize(if (artist != null) "$artist - $title" else title) + ".mp3"

    val request = DownloadManager.Request(Uri.parse(NavidromeClient(config).streamUrl(songId)))
        .setTitle(title)
        .setDescription(artist ?: "NavCarStereo")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, filename)

    context.getSystemService(DownloadManager::class.java).enqueue(request)
}

fun downloadAlbum(context: Context, config: NavidromeConfig, tracks: List<MediaItem>) {
    tracks.forEach { downloadTrack(context, config, it) }
}

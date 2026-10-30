package com.example.navcarstereo.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.navcarstereo.shared.navidrome.CredentialsStore
import com.example.navcarstereo.shared.navidrome.NavidromeClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * Collage 2×2 di copertine per le cartelle lettera della tab Album in Android Auto.
 * Servito come content:// URI e non come byte inline: 27 immagini dentro la lista supererebbero
 * i 256KB che Media3 concede verso Android Auto. Gli id delle copertine stanno nell'URI, quindi se
 * cambiano gli album di una lettera cambia l'URI e il collage si rigenera (cache su disco per URI).
 */
class CoverCollageProvider : ContentProvider() {

    private val client by lazy {
        NavidromeClient(requireNotNull(CredentialsStore(requireContext()).load()) { "Navidrome non configurato" })
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        // Provider esportato (Android Auto deve leggerlo): gli id arrivano da fuori, si accettano
        // solo caratteri da id Navidrome, così non possono diventare un path fuori dalla cache.
        val coverIds = uri.getQueryParameter(PARAM_COVERS).orEmpty().split(",").take(MAX_COVERS)
        if (coverIds.isEmpty() || coverIds.any { !it.matches(SAFE_ID) }) throw FileNotFoundException("covers non validi: $uri")
        val dir = File(requireContext().cacheDir, "cover_collages").apply { mkdirs() }
        val file = File(dir, coverIds.joinToString(".") + ".jpg")
        if (!file.exists()) {
            val bytes = runCatching { collage(coverIds) }.getOrElse { throw FileNotFoundException("collage fallito: ${it.message}") }
            val tmp = File.createTempFile("collage", ".tmp", dir)
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** 1 copertina → piena; 2–3 → le 4 celle a rotazione; 4 → griglia 2×2. */
    private fun collage(coverIds: List<String>): ByteArray = runBlocking {
        val covers = coverIds
            .map { id -> async(Dispatchers.IO) { runCatching { client.coverArt(id, size = CELL) }.getOrNull() } }
            .awaitAll()
            .mapNotNull { bytes -> bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
        check(covers.isNotEmpty()) { "nessuna copertina scaricata" }
        val bitmap = Bitmap.createBitmap(CELL * 2, CELL * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (covers.size == 1) {
            canvas.drawBitmap(covers[0], null, Rect(0, 0, CELL * 2, CELL * 2), null)
        } else {
            for (i in 0 until 4) {
                val left = (i % 2) * CELL
                val top = (i / 2) * CELL
                canvas.drawBitmap(covers[i % covers.size], null, Rect(left, top, left + CELL, top + CELL), null)
            }
        }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val MAX_COVERS = 4
        private const val CELL = 160
        private const val PARAM_COVERS = "covers"
        private val SAFE_ID = Regex("[A-Za-z0-9_-]+")

        fun uri(context: Context, coverIds: List<String>): Uri = Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.covercollage")
            .appendPath("collage")
            .appendQueryParameter(PARAM_COVERS, coverIds.take(MAX_COVERS).joinToString(","))
            .build()
    }
}

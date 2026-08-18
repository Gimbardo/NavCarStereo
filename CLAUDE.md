# NavCarStereo

Riproduttore musicale Android/Android Auto che si connette a un server **Navidrome** (API Subsonic) per sfogliare e riprodurre la libreria musicale dell'utente. Focus primario: usabilità su **Android Auto**.

## Contesto utente

Sviluppatore esperto in Ruby/Rails, esperienza pregressa in Java, primo progetto Kotlin/Android. Preferire analogie Ruby/Rails quando si introducono costrutti idiomatici Kotlin (data class ~ Struct/attr_accessor, extension functions ~ monkey-patching type-safe, null-safety `?`/`!!` ~ `&.`/`.nil?`, scope functions `let`/`also`/`apply` ~ `tap`/blocchi, sealed class ~ classi + `case` su tipo, coroutines ~ Fiber/async strutturato, companion object ~ `self.`/metodi di classe). Java come riferimento secondario solo dove l'analogia Ruby non rende (tipizzazione statica, generics).

## Problema che si vuole risolvere

Le app Android Auto per Navidrome esistenti sono disorganizzate. Requisiti chiave, in ordine di priorità:

1. **Home** con gli ultimi album ascoltati (recenti/in progress) — schermata di partenza, non una lista di menu.
2. **Ricerca** per album, artista, brano (per nome).
3. **Riproduzione in ordine sensato**: sequenziale o casuale (shuffle). Niente ordinamenti alfabetici o altri criteri strani imposti di default.

Qualsiasi nuova schermata o funzione va valutata contro questi tre punti: se non aiuta "trovare la musica in 2 tap e ascoltarla senza sorprese", probabilmente non serve.

## Architettura moduli

- `shared` — client Subsonic/Navidrome (`navidrome/NavidromeClient.kt`), modelli dominio (`navidrome/NavidromeModels.kt`), credenziali cifrate (`navidrome/CredentialsStore.kt`), e il `PlaybackService` (Media3 `MediaLibraryService`) che espone la libreria ad Android Auto.
- `mobile` — app companion per telefono: unica schermata di setup (URL server, utente, password), salva le credenziali e testa la connessione. Android Auto proietta l'UI dal servizio dichiarato in `shared`, non serve altro qui.

## Stack

- Kotlin, Jetpack Compose (vedi plugin in `build.gradle.kts`).
- Per la riproduzione media + integrazione Android Auto: **Media3** (`androidx.media3`) — `MediaSessionService`/`MediaLibraryService` sono lo standard attuale, non il vecchio `MediaBrowserServiceCompat`.
- Per l'API Navidrome: protocollo Subsonic (REST, auth token+salt o Basic). Non serve un SDK esterno, è poche chiamate HTTP.

## Vincoli Android Auto da tenere a mente

- La UI in auto è quasi tutta dettata dal framework (`MediaItem` browsable/playable, `MediaBrowser` tree) — non si disegnano schermate custom come su mobile.
- La struttura dell'albero `MediaBrowser` **è** l'organizzazione delle schermate: la home (ultimi ascoltati) deve essere la root del browser tree, non annidata sotto un menu "Libreria".
- Ricerca si implementa via `onSearch`/`onGetSearchResult` del `MediaLibraryService`.
- Ordine di riproduzione (sequenziale/shuffle) si gestisce con `Player.setShuffleModeEnabled` di Media3, non riordinando manualmente le playlist.

## Note di stile

- Niente librerie/dipendenze aggiuntive se stdlib o Media3 (già necessario) coprono il caso.
- Preferire funzioni pure e data class immutabili ai POJO mutabili stile Java.
- JSON: `org.json` (incluso nell'SDK Android), niente kotlinx.serialization/Moshi per poche chiamate REST.
- Credenziali: cifrate con AndroidKeyStore direttamente (vedi `CredentialsStore`), non `androidx.security-crypto` (deprecata dalla 1.1.0).

## Stato attuale

Browse tree a due tab (root → figli browsable "Home"/"Album", standard Android Auto per i tab): **Home** = righe di album raggruppate (ultimi ascoltati, nuove uscite, più ascoltati, random) via extra `CONTENT_STYLE_GROUP_TITLE_HINT`/`CONTENT_STYLE_BROWSABLE_HINT` del protocollo MediaBrowser; **Album** = elenco alfabetico completo (fino a 500, limite Subsonic per chiamata, nessuna paginazione reale ancora). Tap su album → tracce → play in ordine. Ricerca (`search3`, solo brani) apre l'album del brano trovato invece di riprodurlo subito. Build verificata con `./gradlew :mobile:assembleDebug` (compila, non ancora testata su device/Android Auto reale — tab e righe raggruppate da confermare su DHU).

Non ancora fatto (fast-follow, non bloccante):
- Test su Desktop Head Unit / device reale.
- Notifica di errore chiara se le credenziali non sono configurate quando Android Auto avvia il `PlaybackService` (oggi lancia eccezione in `onCreate`).
- Ricerca per artista/album (oggi solo ricerca brani via `search3`).
- Paginazione reale della tab Album se una libreria supera 500 album (oggi tronca al limite Subsonic).
- "Più ascoltati" è "tutti i tempi" (`getAlbumList2 type=frequent`), non c'è un filtro "ultimo mese" nell'API Subsonic/Navidrome.

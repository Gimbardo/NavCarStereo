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

Browse tree a quattro tab (root → figli browsable "Home"/"Album"/"Random"/"Playlist", 4 = massimo Android Auto): **Home** = righe di album raggruppate (ultimi ascoltati 8, nuove uscite 10, più ascoltati 10) via extra `CONTENT_STYLE_GROUP_TITLE_HINT`/`CONTENT_STYLE_BROWSABLE_HINT` del protocollo MediaBrowser, ricaricata via `notifyChildrenChanged` quando cambia l'album in riproduzione; **Album** = libreria completa (`getAlbumList2` a blocchi da 500 finché finiscono); per Android Auto (controller legacy) divisa in cartelle A–Z/# mostrate come griglia di riquadri (PNG della lettera generati a runtime, `setArtworkData`; lo stile `CATEGORY_GRID` provato non rimpicciolisce i riquadri), perché Media3 tronca a 256KB (~500 album) le liste verso i controller legacy (`MediaUtils.truncateListBySize`), l'app mobile riceve la lista intera; **Random** = 50 album da `getAlbumList2 type=random` (casuale lato server su tutta la libreria). Ogni brano riprodotto viene scrobblato (`scrobble submission=true`): è ciò che aggiorna "ultimi ascoltati" in Navidrome. Shuffle/repeat esposti ad Android Auto come pulsanti custom della sessione (`setMediaButtonPreferences` + `onCustomCommand`). Tap su album → tracce → play in ordine. Ricerca (`search3`, solo brani) apre l'album del brano trovato invece di riprodurlo subito. Verificato su DHU (Pixel 7a via USB): tab, righe Home, cartelle A–Z, pulsanti shuffle/repeat. DHU in `~/Android/Sdk/extras/google/auto/`; via adb wireless è troppo lento (ping timeout), serve il cavo.

`versionCode`/`versionName` in `mobile/build.gradle.kts` sono derivati da git (tag corrente e conteggio commit), non hardcoded.

## CI/CD

- `.github/workflows/test.yml` — esegue `./gradlew test` su ogni push/PR, a prescindere dal branch (gate per il merge).
- `.github/workflows/release.yml` — al push di un tag `vX.Y.Z` (o via `workflow_dispatch` per un run manuale, senza pubblicare release): test, build di `mobile-release.apk` firmato, pubblicazione come GitHub Release con l'APK allegato.
- Firma release: keystore in `~/keystores/navcarstereo-release.jks` (fuori dal repo, non in git), password in `~/keystores/navcarstereo-release.password.txt`. Caricata su GitHub come secret (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`); se persa, impossibile aggiornare l'app con la stessa identità di firma — fare backup.
- R8/minify disattivato in release (`optimization.enable = false`), nessuna ottimizzazione/offuscazione.

Non ancora fatto (fast-follow, non bloccante):
- Test in auto reale (finora solo DHU). Aggiornamento Home dopo l'ascolto e tab Random non verificati a schermo.
- Notifica di errore chiara se le credenziali non sono configurate quando Android Auto avvia il `PlaybackService` (oggi lancia eccezione in `onCreate`).
- Ricerca per artista/album (oggi solo ricerca brani via `search3`).
- Una singola lettera con più di ~500 album verrebbe ancora troncata da Media3 per Android Auto (oggi lontani: 613 album totali).
- App mobile: niente tab Random (la riga Random è sparita anche dalla sua Home, che condivide il browse tree).
- "Più ascoltati" è "tutti i tempi" (`getAlbumList2 type=frequent`), non c'è un filtro "ultimo mese" nell'API Subsonic/Navidrome.

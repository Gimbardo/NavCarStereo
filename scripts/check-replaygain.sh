#!/usr/bin/env bash
# Uso: ND_USER=utente ND_PASS=password ./scripts/check-replaygain.sh [url] [n_brani]
# Conta quanti brani (random, da getRandomSongs) hanno replayGain nel JSON Subsonic.
set -euo pipefail
: "${ND_USER:?manca ND_USER}" "${ND_PASS:?manca ND_PASS}"
URL=${1:-https://music.gimbaro.dev}
N=${2:-100}

S=$(openssl rand -hex 6)
T=$(printf '%s%s' "$ND_PASS" "$S" | md5sum | cut -d' ' -f1)

curl -fsS "$URL/rest/getRandomSongs?u=$ND_USER&t=$T&s=$S&v=1.16.1&c=check&f=json&size=$N" |
  jq '.["subsonic-response"] as $r
      | if $r.status != "ok" then $r.error
        else {
          totale: ($r.randomSongs.song | length),
          con_replayGain: ($r.randomSongs.song | map(select(has("replayGain"))) | length),
          esempio: ($r.randomSongs.song | map(select(has("replayGain"))) | .[0] | {title, replayGain})
        } end'

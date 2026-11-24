# jalgpall-stats

Builds season statistics for a football team from [jalgpall.ee](https://jalgpall.ee/voistlused/): every player's games, minutes, goals, cards, results and goalkeeping, for one season. The output is JSON, or a CSV that matches the season tabs of the stats sheet column for column.

## Requirements

- Java 25. The Gradle toolchain looks for it; set `JAVA_HOME` if it isn't the default JDK.
- No local Gradle needed: use the wrapper (`./gradlew`).

## Running

```bash
./gradlew bootRun          # starts on http://localhost:8080
./gradlew test             # unit and web tests; they never call jalgpall.ee
./gradlew bootJar          # build/libs/app-0.0.1-SNAPSHOT.jar
```

## API

All endpoints live under `/api/teams/{teamId}/seasons/{year}`. `teamId` is the number in the team's jalgpall.ee URL (`/voistlused/team/4240` gives `4240`), and `year` is the season.

| Endpoint | Returns |
|---|---|
| `GET /players` | Every player who played for the team that season, most games first (JSON) |
| `GET /players.csv` | The same as a CSV download, ready to import into the sheet |
| `GET /players/{playerId}` | One player's season (JSON) |

Every endpoint takes an optional `leagues` parameter (jalgpall.ee league ids, e.g. `?leagues=537,62`). It limits the stats to those leagues, and then no leagues are excluded automatically (see below).

```bash
curl localhost:8080/api/teams/4240/seasons/2025/players
curl -OJ localhost:8080/api/teams/4240/seasons/2025/players.csv
curl localhost:8080/api/teams/4240/seasons/2025/players/24734
```

```json
{
  "playerId": 24734,
  "name": "Matheas Madik",
  "position": "VV",
  "games": 20,
  "minutes": 1800,
  "goals": 0,
  "assists": 0,
  "yellowCards": 0,
  "redCards": 0,
  "wins": 13,
  "draws": 2,
  "losses": 5,
  "goalkeeping": { "games": 20, "cleanSheets": 8, "goalsConceded": 28, "goalsConcededPerGame": 1.4 },
  "averageMinutesPerGame": 90.0,
  "minutesPerGoal": null,
  "minutesPerYellowCard": null,
  "winRate": 0.65,
  "drawRate": 0.1,
  "lossRate": 0.25
}
```

Ratios are rounded to 3 decimals and are `null` when undefined (e.g. minutes per goal with no goals). `goalkeeping` only appears for players who kept goal in at least one match.

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details:

| Status | When |
|---|---|
| 400 | Invalid id, year outside 2000–2100, or a non-numeric league |
| 404 | No players for that team and season, or the player didn't play for the team |
| 502 | jalgpall.ee unreachable or its pages changed |

### CSV columns

`Nimi, Mänge, Minuteid, Väravaid, Kollaseid, Punaseid, Min per goal, Min per yellow, Avg min per game, Clean sheets, Sisse lastud väravaid, Avg väravaid per game (gk), Võite, Viike, Kaotusi, Win %, Draw %, Lose %, Assiste`

- Undefined ratios are written as `Pole`.
- Percentages are written like `67.86%`.
- Players who never kept goal get `0` in the three goalkeeping columns.

## How the numbers are worked out

For each season the app reads:

1. **The team page** (`ajax.php?type=teamplayertable`) for the players: the registered roster plus anyone in the statistics tables (players who left mid-season).
2. **Each player's season totals** (`playertotal`): goals, assists and cards.
3. **Each player's match list** (`playerstat1`): minutes and results.
4. **Each match protocol** (`/protocol/{id}`), fetched once per match and shared by all players: who was in goal, substitution times and goal minutes.

Rules:

- **Games** count when the player got minutes and the match has a result.
- **Win/draw/loss** uses the final result, including extra time (`LA`) and penalty shoot-outs (`PEN`).
- **Forfeit results:**
  - `+ : -` / `- : +` (awarded) count only if the protocol shows the game was actually played (substitutions or goals). A walkover doesn't count.
  - `- : -` (annulled, e.g. the other team withdrew from the league) never counts.
  - Scores like `- : 3` (one side's result annulled) count as a loss for that side.
- **Goalkeeping:**
  - The keeper is whoever the protocol marks `(VV)` in that match, so a field player who goes in goal counts and a keeper playing on the field doesn't.
  - Goals conceded are only the goals scored while that player was on the pitch. Shoot-out penalties don't count, extra-time goals do.
  - A clean sheet is a match with no goals conceded while in goal.
  - Some older protocols have no `(VV)` markers; then the registered position (`VV` on the season roster) decides.
- **Excluded leagues:** leagues whose name contains an entry of `jalgpall.excluded-leagues` (default: the winter tournament, `Taliturniir`) are subtracted from the season totals.

## Configuration

`src/main/resources/application.properties`, or any Spring Boot property source (e.g. `JALGPALL_MAX_CONCURRENT_REQUESTS=2`):

| Property | Default | Meaning |
|---|---|---|
| `jalgpall.base-url` | `https://jalgpall.ee/voistlused/` | Competition site |
| `jalgpall.max-concurrent-requests` | `4` | Parallel requests to jalgpall.ee |
| `jalgpall.timeout` | `15s` | Connect and read timeout per request |
| `jalgpall.excluded-leagues` | `Taliturniir` | Comma-separated league name fragments to leave out |
| `jalgpall.user-agent` | a browser user agent | Sent with every request |

## Known limitations

- **Assists:** jalgpall.ee doesn't record assists for the lower leagues, so `assists` is usually 0.
- **Unmarked keepers:** a player who goes in goal without being marked `(VV)` (e.g. after a red card) can't be detected; the goals conceded while they were in goal aren't credited to anyone.
- **Inconsistent minutes on jalgpall.ee:** for a few older games the site returns one of two different minute values from request to request. If a total looks off by a few dozen minutes, run the export again.

## Project layout

```
src/main/java/ee/jalgpall/scraper/app/
├── client/    JalgpallClient: HTTP calls to jalgpall.ee, with a concurrency limit
├── parser/    JalgpallParser (team page, totals, match lists), ProtocolParser (match protocols)
├── service/   SeasonStatsService: combines everything into season stats
├── export/    SeasonCsvWriter: the sheet-ready CSV
├── model/     records for players, matches, protocols and stats
├── web/       SeasonController and error handling
└── config/    JalgpallProperties, RestClient setup
```

Built with Spring Boot 4.2, Java 25 (virtual threads), jsoup and Jackson 3.

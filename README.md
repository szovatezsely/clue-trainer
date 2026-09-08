# Clue Trainer

An endless practice game for GeoGuessr country metas.

You get one real identification clue — a road sign, a bollard, a utility pole, a licence plate,
a landscape — and three countries to choose from. Pick the country the clue belongs to. Whether
you are right or wrong, the guide text that explains the clue is revealed, so every answer
teaches you the meta instead of just scoring you.

The clues are extracted from the community-written GeoGuessr guides on
[plonkit.net](https://www.plonkit.net/guide): **5,107 clues across 136 covered countries and
territories**, of which 1,711 are country-level "how to identify this country" clues.

- **Backend:** Kotlin + Ktor (scraper, dataset, game rules, image proxy)
- **Frontend:** Vue 3 + TypeScript + Vite, served by nginx
- **Runtime:** Docker Compose, one command to start
- **Interface:** dark, keyboard-first, and sized so the clue, the answers and the
  score always share one screen

## Quick start

```bash
docker compose up --build
```

Then open **http://localhost:8080**.

The backend API is also exposed directly on **http://localhost:8081** (e.g.
http://localhost:8081/api/health) if you want to poke at it.

To stop it: `docker compose down` — add `-v` to also drop the cached dataset and images.

> The game needs outbound internet access for the clue images: plonkit.net rejects image
> requests that do not carry a matching `Referer` header, which a browser cannot send
> cross-origin, so the backend fetches them and caches each one on disk. Everything else
> (clue texts, countries, game logic) works fully offline from the bundled dataset.
>
> plonkit.net also rate-limits image requests per client. Normal play stays well inside the
> limit, but hammering it (a script, a fast click-through) earns a 429 with a `Retry-After` of
> up to ~20 minutes. The backend then pauses all image fetches for exactly that long instead of
> making it worse, clues already cached keep working, and the affected clue offers a
> **Skip this clue** button that says how long is left. Nothing is broken permanently.

## How to play

| Action | Mouse | Keyboard |
| --- | --- | --- |
| Start / stop the endless run | **Start** / **Stop** | <kbd>S</kbd> |
| Answer | click a country | <kbd>1</kbd> <kbd>2</kbd> <kbd>3</kbd> |
| Next clue | **Next clue** | <kbd>Enter</kbd> |
| Clear the score | **Reset score** | — |

The score strip counts how many clues you identified **right** and **wrong**, plus your accuracy,
current streak and best streak. Stopping the run pauses it and hides the current clue (so it stays
a fair question); starting again continues where you left off. Your score survives a page reload
and is only cleared by **Reset score**.

The intro text steps aside once a run starts, and the clue image is sized against the viewport, so
the clue, the three answers and the score stay visible together without scrolling.

Two filters shape the training set:

- **Region** — restrict the clues, and the answer options, to one continent. Practising
  "Estonia vs Latvia vs Lithuania" is a very different exercise from "Estonia vs Peru vs Laos".
  The number next to each region is how many clues it currently offers.
- **Include regional clues** — off by default. The guides also contain regional and spotlight
  clues (*"this pole type is specific to northern Ghana"*). They make good hard practice, but
  many of those images have a small map of the country pinned into the corner, which gives the
  answer away. Turn this on to add them anyway (+3,396 clues).

## How it works

```
plonkit.net/guide ──scrape──> clues.json ──> Ktor API ──> Vue SPA
                                 │              │
                     bundled seed + volume      └── /api/image proxy + disk cache
```

**1. Scraping.** Every PlonkIt page embeds its own content as a JSON island
(`<script id="__PRELOADED_DATA__">`) that the site's SPA hydrates from, so
[`PlonkItScraper`](backend/src/main/kotlin/net/geoclue/trainer/data/PlonkItScraper.kt) reads that
instead of parsing rendered markup. The guide map page yields the country index (name, slug, ISO
code, continent); each country page yields its chapters and, inside them, the individual tips:
image, markdown explanation, tags and a Street View link. Requests are sequential, delayed and
back off on `429`.

Reference material is filtered out — the "Maps and resources" chapters, summary sheets, and
images whose file name marks them as a map, chart, infographic or locator map — because none of
it is something a player could spot in Street View, and a locator map would hand over the answer.

**2. Storage.** The scrape result is one `clues.json`. A full snapshot is **bundled in the
backend image**, so the game is playable the moment the container is up, with no scraping and no
network. A live re-scrape is written to `${DATA_DIR}/clues.json` in the Docker volume and takes
precedence from then on.

**3. The game.** A question is one clue plus three countries: the right one, and two distractors
drawn from the same continent. A territory is never offered next to its parent country (Alaska vs
United States), since the clue would be true for both.

Scores, the clues you have already seen, and the correct answer all live in a server-side session —
`/next` returns the image and the three options and nothing else, so the answer cannot be read out
of the network tab. When every clue in the current pool has been shown, the pool starts over: the
run really is endless.

**4. Images.** `/api/image?path=…` fetches the clue image from the origin with the required
headers, caches it under `${DATA_DIR}/images`, and serves it with a one-year cache header. Only
paths that appear in the dataset are proxied, so the endpoint cannot be used as an open relay.

At most three fetches are in flight at a time. A 429 from the origin trips a shared pause for as
long as its `Retry-After` asks (asking again inside that window only extends it), and
`/api/image/status` reports how much of the pause is left so the UI can explain itself. A clue
whose image cannot be fetched can be skipped, which drops it without scoring it — otherwise the
unanswered question would simply be served again.

**5. Explanations.** The guide texts are markdown, and 679 of the 7,657 paragraphs have their bold
markers padded on the wrong side (`has their own** unique plate **design`), which markdown cannot
pair up — the asterisks would show in the text. `frontend/src/markdown.ts` moves the padding out
of the run before rendering (`has their own **unique plate** design`), leaves correct markdown
alone, and links open in a new tab.

## Configuration

Environment variables on the `backend` service (all optional):

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `DATA_DIR` | `/data` | Directory for the dataset cache and the image cache |
| `SCRAPE_ON_START` | `false` | Re-scrape plonkit.net on boot if the cached dataset is stale |
| `DATASET_MAX_AGE_DAYS` | `30` | Age at which the cached dataset counts as stale |
| `ALLOW_REFRESH` | `true` | Whether `POST /api/dataset/refresh` is allowed |
| `SCRAPE_DELAY_MS` | `1200` | Politeness delay between two guide page requests |
| `SESSION_TTL_HOURS` | `12` | How long an idle game session is kept in memory |

### Refreshing the clues

The bundled dataset is a snapshot; the guides keep being improved. To pull the current state of
every country guide (a few minutes of rate-limited requests, the old dataset keeps serving until
it finishes):

```bash
curl -X POST http://localhost:8081/api/dataset/refresh
```

Watch it with `docker compose logs -f backend`, and check `GET /api/dataset/status`. Setting
`SCRAPE_ON_START=true` does the same on every boot where the cache is older than
`DATASET_MAX_AGE_DAYS`.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Liveness plus dataset size |
| `GET` | `/api/meta` | Countries, continents, clue counts, dataset timestamp |
| `POST` | `/api/game/sessions` | Start a run, returns a `sessionId` |
| `GET` | `/api/game/sessions/{id}` | Current score of a run |
| `GET` | `/api/game/sessions/{id}/next` | Next question — `?continent=Europe&scope=core\|all` |
| `POST` | `/api/game/sessions/{id}/answer` | `{"clueId":"…","countryCode":"…"}` → verdict, explanation, score |
| `POST` | `/api/game/sessions/{id}/skip` | Drop the current clue without scoring it |
| `POST` | `/api/game/sessions/{id}/reset` | Clear the score of a run |
| `GET` | `/api/image?path=/images/…` | Clue image, proxied and cached |
| `GET` | `/api/image/status` | Whether the origin is rate-limiting images, and for how long |
| `GET` | `/api/dataset/status` | Dataset timestamp and refresh state |
| `POST` | `/api/dataset/refresh` | Trigger a background re-scrape |

Country options carry a `flag` emoji as well as the ISO code; the UI shows the code, because
flag emoji do not render as flags on every platform (notably Windows).

## Local development

Backend (needs a JDK 17+; the Gradle wrapper handles the rest):

```bash
cd backend && ./gradlew run
```

Frontend (needs Node 20+; Vite proxies `/api` to `localhost:8080`):

```bash
cd frontend && npm install && npm run dev
```

Tests — game rules, scraper extraction against a fixture page, and a consistency check over the
bundled dataset:

```bash
cd backend && ./gradlew test
```

Type-check the frontend with `cd frontend && npm run typecheck`.

## Project layout

```
backend/
  src/main/kotlin/net/geoclue/trainer/
    Application.kt              Ktor server, plugins, wiring
    Config.kt                   environment-driven configuration
    model/                      dataset models and API DTOs
    data/PlonkItScraper.kt      guide extraction
    data/ClueRepository.kt      dataset loading, caching, refresh
    game/GameService.kt         question building and grading
    game/GameSession.kt         per-run state and the session store
    routes/                     API routes and the image proxy
  src/main/resources/seed/clues.json   bundled scrape (5,107 clues)
  src/test/kotlin/                     game and scraper tests
frontend/
  src/App.vue                   page layout and keyboard shortcuts
  src/composables/useGame.ts    the game loop
  src/markdown.ts               guide-markdown repair and rendering
  src/styles/base.css           design tokens (dark theme, green accent)
  src/components/               header, controls, score strip, clue stage,
                                answer reveal, region menu
  nginx.conf                    static hosting + /api proxy
docker-compose.yml
```

## Credits

All clue images and explanations are the work of the GeoGuessr community and belong to the
authors of the guides on [plonkit.net](https://www.plonkit.net/guide) — this trainer only quizzes
you on them, and every answer links back to the full country guide. If you find it useful, use
the original guides too.

Not affiliated with, endorsed by, or connected to GeoGuessr or PlonkIt.

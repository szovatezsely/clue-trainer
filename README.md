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

The backend API is also reachable directly on **http://localhost:8081** (e.g.
http://localhost:8081/api/health) if you want to poke at it. It is bound to loopback, so it is
never exposed to the network.

To stop it: `docker compose down` — add `-v` to also drop the cached dataset and images.

For a public, always-on deployment see [Deploy it for free, always on](#deploy-it-for-free-always-on).

> The game needs outbound internet access for the clue images: plonkit.net rejects image
> requests that do not carry a matching `Referer` header, which a browser cannot send
> cross-origin, so the backend fetches them and caches each one on disk. Everything else
> (clue texts, countries, game logic) works fully offline from the bundled dataset.
>
> plonkit.net also rate-limits image requests per client, harshly and progressively. The game
> works around it by preferring clues whose image it has already cached, pacing the fetches it
> does make, and pausing entirely when the origin asks it to. A clue that still cannot load
> offers a **Skip this clue** button saying how long is left. See
> [The image cache](#the-image-cache) for how to fill the cache once and stop depending on the
> origin altogether.

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

Fetches are paced: one at a time, at most one every `IMAGE_MIN_INTERVAL_MS`. A 429 from the
origin trips a shared pause for as long as its `Retry-After` asks (asking again inside that
window only extends it), and `/api/image/status` reports how much of the pause is left so the UI
can explain itself. The game avoids the situation in the first place by preferring clues it has
already cached — see [The image cache](#the-image-cache). A clue whose image cannot be fetched can
still be skipped, which drops it without scoring it.

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
| `IMAGE_MIN_INTERVAL_MS` | `3000` | Smallest gap between two image fetches from the origin |
| `WARM_CACHE` | `false` | Fill the image cache in the background after boot |
| `WARM_CACHE_ALL` | `false` | Warm every clue image, not only the country-level ones |
| `WARM_START_DELAY_MS` | `20000` | Grace period before warming starts |
| `WARM_INTERVAL_MS` | `30000` | Gap between two images while warming; doubles on each rate limit |
| `WARM_QUIET_MS` | `60000` | Warming stands aside this long after a player's request |

`docker-compose.prod.yml` sets `ALLOW_REFRESH=false` and reads `SITE_ADDRESS` and `JAVA_OPTS`
from `.env` (see [`.env.example`](.env.example)).

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

### The image cache

plonkit.net rate-limits image requests, punishes repeat offenders progressively, and treats
datacenter addresses far more harshly than residential ones. Measured with the same code at the
same 3-second pace:

| Fetching from | Effective rate | Rate limiting |
| --- | --- | --- |
| An Oracle Cloud VM | ~47 images/hour | constant, escalating to 30-minute blocks |
| A home connection | ~1,080 images/hour | none observed |

Three mechanisms keep that from reaching the player.

**The game plays from what it has.** Clues whose image is already on disk load instantly and
cannot fail, so those are what it offers. Roughly one pick in ten deliberately reaches for an
uncached clue, which grows the cache while people play; while the origin is actively throttling,
only cached clues are offered. Until 40 images are cached the game draws from everything, so a
fresh deployment still works — it simply fills up as you play.

**Pacing.** Fetches happen one at a time, at most one every `IMAGE_MIN_INTERVAL_MS` (3 s). A
player reads for far longer than that between clues, so it costs nothing while making a burst
impossible.

**Warming** (`WARM_CACHE=true`) walks the clue list in the background, fetching what is missing
one image every `WARM_INTERVAL_MS`. Each rate limit doubles that interval and a clean run eases
it back, so it converges on a pace the origin tolerates. It pauses while anyone is playing, and
skips images already on disk — so it is safe to leave enabled, and a no-op on a full cache.

```bash
docker compose -f docker-compose.prod.yml logs -f backend | grep -i warm
```

### Filling the cache from your own machine

Because of the table above, the fast way to a complete cache is to fetch it from a home
connection once and copy the result to the server, which then never contacts plonkit again.

**1. Fetch locally.** In a clone on your own machine, with the local stack:

```bash
WARM_CACHE=true WARM_CACHE_ALL=true WARM_INTERVAL_MS=5000 docker compose up -d --build
```

Watch it with `docker compose logs -f backend | grep -i warm`. Measured on a home connection
this runs at about **10 images a minute**, so roughly **8 hours** for all 5,107 (~2.1 GB) or
**2.5 hours** for the 1,711 country-level ones — leave it overnight. It is resumable: stop and
start it whenever you like, and already-cached images are skipped without a request. Drop
`WARM_CACHE_ALL` to fetch only the country-level set.

**2. Export the volume** into a tarball (PowerShell; use `$PWD` in bash):

```powershell
docker run --rm -v clue-trainer_clue-data:/data -v "${PWD}:/backup" alpine tar czf /backup/clue-images.tgz -C /data images
```

**3. Copy it to the server:**

```bash
scp clue-images.tgz ubuntu@your-host:~
```

**4. Import it there:**

```bash
docker run --rm -v clue-trainer_clue-data:/data -v ~:/backup alpine tar xzf /backup/clue-images.tgz -C /data
```

```bash
docker compose -f docker-compose.prod.yml restart backend
```

The restart is what matters: the backend indexes the cache on boot, and the log line
`Image cache holds N of 5243 clue images` confirms what arrived (5,107 clue images plus the 136
country hero images). From then on every clue is served from local disk.

These are someone else's images — keep the archive between your machine and your server rather
than publishing it.

## Deploy it for free, always on

The whole app needs **134 MB of memory** and boots in **2 seconds**, so a very small VM is
plenty. The free tier that gives you one that never sleeps is
[Oracle Cloud Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm):
an ARM instance of 2 OCPU / 12 GB, 200 GB of block storage and 10 TB of egress a month, for as
long as you keep it. Render and Koyeb free tiers also fit the memory budget, but they sleep after
15 minutes of inactivity and have no persistent disk — which for this app means losing the image
cache and re-fetching from plonkit.net on every wake-up.

On a VM the app runs exactly as it does locally: same two containers, same volume, same
behaviour. `docker-compose.prod.yml` only changes what a public URL demands:

| | Local (`docker-compose.yml`) | Server (`docker-compose.prod.yml`) |
| --- | --- | --- |
| Site | `localhost:8080` | `:80` / `:443` through Caddy |
| API port | `127.0.0.1:8081` for debugging | not published; only nginx reaches it |
| TLS | — | Let's Encrypt, automatic, renewed by Caddy |
| `ALLOW_REFRESH` | `true` | `false` — nobody should be able to trigger a scrape |
| Restart on reboot | yes | yes |

### 1. Create the account

Sign up at [cloud.oracle.com/free](https://cloud.oracle.com/free). Two things about this step:

- **The home region is permanent** and Always Free resources exist only in it, so pick one near
  you (Frankfurt, Amsterdam, Zurich, Marseille) — this dropdown decides whether you can get an
  ARM instance at all.
- **A card is required for identity verification, not billing.** As long as you only ever create
  resources marked "Always Free eligible", nothing is charged. Decline the upgrade prompts.

### 2. Create the instance

Compute → Instances → Create instance:

- **Shape:** `VM.Standard.A1.Flex`, 2 OCPU, 12 GB (marked Always Free eligible)
- **Image:** Canonical Ubuntu 24.04
- **Networking:** assign a public IPv4 address
- Download the SSH private key before you leave the page

If it answers "Out of host capacity", try another availability domain or retry over the next few
hours — ARM capacity frees up. Alternatively use `VM.Standard.E2.1.Micro` (1/8 OCPU, 1 GB), which
is always available; see [the micro shape](#the-1-gb-micro-shape) below, because you cannot build
the images on it.

### 3. Open ports 80 and 443 in the security list

A fresh VCN allows SSH and ICMP only, so the site stays unreachable until you add a rule. The
symptom is a browser tab that spins forever — the packets are dropped, not refused — while SSH
keeps working perfectly.

**Networking → Virtual cloud networks → your VCN → Subnets → your subnet → Security Lists → the
list attached to it → Add Ingress Rules:**

| Field | Value |
| --- | --- |
| Stateless | unchecked |
| Source Type / CIDR | CIDR, `0.0.0.0/0` |
| IP Protocol | TCP |
| Destination Port Range | `80,443` |

It applies immediately, no reboot. Reach the list by clicking through the **subnet**: a VCN can
hold several security lists, and adding the rule to one that is not attached to this subnet looks
exactly like doing nothing.

That is the only firewall in the way. Oracle's Ubuntu images do carry restrictive local
`iptables` rules, but **Docker's published ports bypass the `INPUT` chain** — it DNATs in
`nat/PREROUTING` and filters in its own chains — so container traffic never meets them. Local
rules only matter for ports served by a process running directly on the host.

### 4. Install Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
```

```bash
sudo usermod -aG docker $USER && sudo systemctl enable --now docker
```

The script brings the Compose v2 plugin with it, so `docker compose` (with a space) works
straight away. **Log out and back in** afterwards — the `docker` group only applies to a new
login session, and until then every command fails with
`permission denied ... /var/run/docker.sock`.

The image ships `git`; if yours does not, `sudo apt-get update && sudo apt-get install -y git`.
Check the tooling before going further:

```bash
git --version && docker compose version && docker run --rm hello-world
```

### 5. Start the stack

```bash
git clone https://github.com/<you>/<repo>.git clue-trainer && cd clue-trainer
```

Use the **HTTPS** URL, not `git@github.com:…` — the VM has no SSH key for GitHub. A private repo
needs a personal access token instead of a password, or a deploy key on the machine.

```bash
cp .env.example .env
```

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

The first build takes a few minutes on the ARM shape — Gradle and npm both start from scratch —
and later ones reuse the cached layers. Then open **http://&lt;your-public-ip&gt;**: the game is
playable immediately from the bundled dataset, with no scraping needed.

### 6. Add HTTPS

A certificate authority will not issue a certificate for a bare IP address, so this needs a
hostname. A free one from [duckdns.org](https://www.duckdns.org) works: sign in, claim
`something.duckdns.org`, point it at the instance's public IP, then

```bash
sed -i 's|^SITE_ADDRESS=.*|SITE_ADDRESS=something.duckdns.org|' .env
```

```bash
docker compose -f docker-compose.prod.yml up -d
```

Caddy obtains the certificate at startup, renews it on its own, serves HTTP/2 and HTTP/3, and
redirects `http://` to `https://`. Certificates live in the `caddy-data` volume, so restarts do
not ask Let's Encrypt for new ones. To watch or confirm:

```bash
docker compose -f docker-compose.prod.yml logs -f caddy
```

```bash
docker compose -f docker-compose.prod.yml exec caddy ls /data/caddy/certificates/*/*
```

If the certificate is missing, port 80 was unreachable when Caddy last tried; fix the ingress
rule and `up -d --force-recreate caddy` to make it retry immediately.

### Day-to-day

Deploy the latest commit:

```bash
git pull && docker compose -f docker-compose.prod.yml up -d --build
```

Follow the logs:

```bash
docker compose -f docker-compose.prod.yml logs -f
```

Check what is running:

```bash
docker compose -f docker-compose.prod.yml ps
```

Stop everything (the volumes, and with them the clue cache and the certificate, stay):

```bash
docker compose -f docker-compose.prod.yml down
```

The production stack refuses `POST /api/dataset/refresh` on purpose. To re-scrape, set
`ALLOW_REFRESH: "true"` in `docker-compose.prod.yml`, `up -d` the backend, run the refresh
against `localhost:8080` from inside the container, and put it back:

```bash
docker compose -f docker-compose.prod.yml exec backend curl -X POST localhost:8080/api/dataset/refresh
```

### The 1 GB micro shape

If ARM capacity never appears, `VM.Standard.E2.1.Micro` runs the app fine — 134 MB against 1 GB —
but it cannot **build** it: Gradle alone asks for a 1 GB heap and the Kotlin compile will thrash
or be killed. Build the images somewhere else and let the VM only pull them:

```bash
docker buildx build --platform linux/amd64 -t ghcr.io/<you>/clue-trainer-backend:latest --push ./backend
```

Do the same for `./frontend`, replace the two `build:` blocks in `docker-compose.prod.yml` with
`image:` lines, and add `JAVA_OPTS=-Xmx256m` to `.env` so the JVM does not size its heap for a
machine that small.

Give that shape a swap file too — 1 GB with none is fragile even when only running:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
```

```bash
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### If something is wrong

| Symptom | Cause | Fix |
| --- | --- | --- |
| Browser spins forever, SSH fine | No ingress rule for 80/443 | Step 3 — and check the rule is on the list attached to *this* subnet |
| `ERR_SSL_PROTOCOL_ERROR` | Caddy has no certificate yet, usually because port 80 was closed when it last tried | `up -d --force-recreate caddy`, then read its log |
| Kotlin build fails with `Unresolved reference 'data'` | The clone is missing files: a `.gitignore` pattern without a leading slash matches at every level | `git ls-files --others --ignored --exclude-standard -- backend/src` |
| "Out of host capacity" | ARM demand in your region | Another availability domain, retry later, or the micro shape |
| Certificate never issued | Hostname does not resolve to the VM, or :80 is closed | `dig +short <host>`, and check the ingress rule for 80 (Let's Encrypt validates over HTTP) |
| Clue images broken | plonkit.net is rate-limiting, and the cache is nearly empty | Fill the cache from your own machine (see above); the game plays from cached clues once it has 40 |
| Backend killed on the micro shape | JVM heap sized for the container, not the box | `JAVA_OPTS=-Xmx256m` in `.env` |

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
    data/ImageWarmer.kt         background, self-slowing image prefetch
    game/GameService.kt         question building and grading
    game/GameSession.kt         per-run state and the session store
    game/ImageAvailability.kt   what the game asks the cache before picking a clue
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
docker-compose.yml              local stack: backend + frontend
docker-compose.prod.yml         server stack: the same two, plus Caddy for TLS
Caddyfile                       front door, HTTPS when SITE_ADDRESS is a hostname
.env.example                    deployment settings
```

## Credits

All clue images and explanations are the work of the GeoGuessr community and belong to the
authors of the guides on [plonkit.net](https://www.plonkit.net/guide) — this trainer only quizzes
you on them, and every answer links back to the full country guide. If you find it useful, use
the original guides too.

Not affiliated with, endorsed by, or connected to GeoGuessr or PlonkIt.

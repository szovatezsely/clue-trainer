<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import AnswerReveal from './components/AnswerReveal.vue'
import ClueStage from './components/ClueStage.vue'
import GameControls from './components/GameControls.vue'
import ScoreBoard from './components/ScoreBoard.vue'
import SiteHeader from './components/SiteHeader.vue'
import { useGame } from './composables/useGame'

const {
  meta,
  stats,
  question,
  result,
  phase,
  running,
  errorMessage,
  continent,
  includeRegional,
  imageReady,
  imageFailed,
  imageRetryIn,
  busy,
  canAnswer,
  loadMeta,
  restoreScore,
  start,
  stop,
  next,
  noteImageFailure,
  skipClue,
  submit,
  resetScore,
} = useGame()

/** Text entry must keep every key; a checkbox or select only keeps its own. */
function isTextEntry(target: HTMLElement | null): boolean {
  if (!target) return false
  if (target.isContentEditable) return true
  const tag = target.tagName
  if (tag === 'TEXTAREA') return true
  if (tag !== 'INPUT') return false
  return !['checkbox', 'radio', 'button', 'submit', 'range'].includes(
    (target as HTMLInputElement).type,
  )
}

function onKeydown(event: KeyboardEvent) {
  if (event.metaKey || event.ctrlKey || event.altKey) return
  const target = event.target as HTMLElement | null
  if (isTextEntry(target)) return

  const key = event.key.toLowerCase()
  const tag = target?.tagName ?? ''
  // Space and Enter belong to whatever control the player has focused, so the
  // shortcut only fires when focus is somewhere neutral. Digits and S always work,
  // even right after clicking a filter, which leaves the checkbox focused.
  const focusOwnsActivation = ['BUTTON', 'INPUT', 'SELECT', 'A'].includes(tag)

  if (key === 's') {
    event.preventDefault()
    running.value ? stop() : start()
    return
  }

  if (['1', '2', '3'].includes(key) && canAnswer.value) {
    const option = question.value?.options[Number(key) - 1]
    if (option) {
      event.preventDefault()
      submit(option.code)
    }
    return
  }

  if ((key === 'enter' || key === ' ') && !focusOwnsActivation) {
    if (phase.value === 'answered') {
      event.preventDefault()
      next()
    } else if (!running.value) {
      event.preventDefault()
      start()
    }
  }
}

onMounted(() => {
  loadMeta()
  restoreScore()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <SiteHeader :meta="meta" />

  <main class="shell page">
    <!-- The intro gives way to the game itself once a run is on, so the clue,
         the options and the score all fit on one screen. -->
    <section v-if="!running" class="hero">
      <p class="eyebrow">Endless meta practice</p>
      <h1>
        Spot the clue,<br />
        name the <span class="hero__accent">country</span>.
      </h1>
      <p class="hero__lead">
        Every clue is a real identification detail taken from a country guide: a road sign, a
        bollard, a utility pole, a licence plate, a landscape. Pick the country it belongs to and
        read why it works.
      </p>
    </section>

    <GameControls
      v-model:continent="continent"
      v-model:includeRegional="includeRegional"
      :meta="meta"
      :running="running"
      :busy="busy"
      @start="start"
      @stop="stop"
      @reset="resetScore"
    />

    <ScoreBoard :stats="stats" />

    <div class="board">
      <!-- Something went wrong: the API is unreachable or the filters are empty. -->
      <div v-if="phase === 'error'" class="panel panel--error">
        <p class="eyebrow">Problem</p>
        <h2>{{ errorMessage }}</h2>
        <button class="btn" type="button" @click="start()">Try again</button>
      </div>

      <!-- Not started yet. -->
      <div v-else-if="!running && !question" class="panel">
        <p class="eyebrow">Ready when you are</p>
        <h2>Press start for an endless run of clues.</h2>
        <ul class="keys">
          <li>
            <span class="keys__combo"><kbd>1</kbd><kbd>2</kbd><kbd>3</kbd></span>
            <span class="keys__what">pick a country</span>
          </li>
          <li>
            <span class="keys__combo"><kbd>S</kbd></span>
            <span class="keys__what">start and stop the run</span>
          </li>
          <li>
            <span class="keys__combo"><kbd>Enter</kbd></span>
            <span class="keys__what">next clue</span>
          </li>
        </ul>
        <p class="panel__text">Your score is kept until you reset it.</p>
        <button class="btn" type="button" @click="start()">
          Start
          <span class="btn__key">S</span>
        </button>
      </div>

      <!-- Stopped mid-run: the clue is hidden so it stays a fair question. -->
      <div v-else-if="!running" class="panel">
        <p class="eyebrow">Paused</p>
        <h2>
          {{ stats.correct }} right, {{ stats.wrong }} wrong
          <template v-if="stats.answered"> — {{ stats.accuracy }}% accuracy</template>
        </h2>
        <p class="panel__text">The current clue is hidden while the game is stopped.</p>
        <button class="btn" type="button" @click="start()">
          Continue
          <span class="btn__key">S</span>
        </button>
      </div>

      <template v-else>
        <ClueStage
          :question="question"
          :result="result"
          :can-answer="canAnswer"
          :busy="busy"
          :image-ready="imageReady"
          :image-failed="imageFailed"
          :image-retry-in="imageRetryIn"
          @pick="submit"
          @image-loaded="imageReady = true"
          @image-failed="noteImageFailure"
          @skip="skipClue"
        />

        <div v-if="!question" class="panel panel--quiet">
          <p class="eyebrow">Loading</p>
          <h2>Picking a clue…</h2>
        </div>

        <p v-if="errorMessage" class="board__error">{{ errorMessage }}</p>

        <AnswerReveal v-if="result" :result="result" :busy="busy" @next="next" />
      </template>
    </div>
  </main>

  <footer class="footer">
    <div class="shell footer__inner">
      <p>
        Clue texts and images come from the community-written GeoGuessr guides on
        <a
          :href="meta ? meta.source : 'https://www.plonkit.net/guide'"
          target="_blank"
          rel="noopener noreferrer"
          >plonkit.net</a
        >. This trainer only quizzes you on them — please support the original guides.
      </p>
      <p v-if="meta" class="footer__meta">
        {{ meta.clueCount.toLocaleString('en-GB') }} clues · {{ meta.countryCount }} countries ·
        {{ meta.continents.length }} regions
      </p>
    </div>
  </footer>
</template>

<style scoped>
.page {
  padding-bottom: 64px;
}

.hero {
  padding: 48px 0 28px;
  max-width: 760px;
}

.hero h1 {
  margin: 12px 0 18px;
}

.hero__accent {
  color: var(--green);
}

.hero__lead {
  color: var(--text-dim);
  font-size: 1rem;
  max-width: 62ch;
  margin: 0;
}

.board {
  display: grid;
  gap: 18px;
  padding-top: 18px;
}

.panel {
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  padding: 34px 32px;
  display: grid;
  gap: 16px;
  justify-items: start;
  background: var(--surface);
}

.panel--error {
  background: var(--red-veil);
  border-color: rgba(255, 107, 107, 0.35);
}

.panel__text {
  color: var(--text-dim);
  margin: 0;
  max-width: 60ch;
}

/* One shortcut per line, keys and meaning in their own columns. */
.keys {
  display: grid;
  gap: 8px;
  margin: 2px 0 0;
  padding: 0;
  list-style: none;
}

.keys li {
  display: grid;
  grid-template-columns: 118px auto;
  align-items: center;
  gap: 14px;
}

.keys__combo {
  display: inline-flex;
  gap: 4px;
}

.keys__what {
  color: var(--text-dim);
  font-size: 0.94rem;
}

.board__error {
  margin: 0;
  color: var(--red);
  font-size: 0.9rem;
}

.footer {
  border-top: 1px solid var(--line);
  background: var(--bg);
  padding: 30px 0 44px;
}

.footer__inner {
  display: grid;
  gap: 8px;
  color: var(--text-dim);
  font-size: 0.86rem;
}

.footer p {
  margin: 0;
  max-width: 78ch;
}

.footer__meta {
  color: var(--text-faint);
  font-size: 0.78rem;
}

@media (max-width: 640px) {
  .hero {
    padding: 32px 0 22px;
  }

  .panel {
    padding: 24px 18px;
  }

  .keys li {
    grid-template-columns: 104px auto;
  }
}
</style>

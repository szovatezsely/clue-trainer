<script setup lang="ts">
import { computed } from 'vue'
import { clueImageUrl } from '../api'
import type { AnswerResult, CountryOption, Question } from '../types'

const props = defineProps<{
  question: Question | null
  result: AnswerResult | null
  canAnswer: boolean
  busy: boolean
  imageReady: boolean
  imageFailed: boolean
  /** Seconds of the guide site's rate-limit window left, 0 if it is not that. */
  imageRetryIn: number
}>()

const emit = defineEmits<{
  pick: [code: string]
  imageLoaded: []
  imageFailed: []
  skip: []
}>()

const answered = computed(() => props.result !== null)

/** How long the guide site wants us to wait, in words. */
const retryWait = computed(() => {
  const seconds = props.imageRetryIn
  if (seconds <= 0) return ''
  if (seconds < 90) return seconds + ' seconds'
  return 'about ' + Math.round(seconds / 60) + ' minutes'
})

/** Colour state for one option once the answer is in. */
function stateOf(option: CountryOption): string {
  if (!props.result) return ''
  if (option.code === props.result.correctCountry.code) return 'option--correct'
  if (option.code === props.result.chosenCountry.code) return 'option--wrong'
  return 'option--dimmed'
}
</script>

<template>
  <section v-if="question" class="stage">
    <figure class="clue">
      <!-- The image is fetched from the guide site through the backend; when
           that fails there is nothing to guess from, so offer a way out. -->
      <div v-if="imageFailed" class="clue__frame clue__frame--failed">
        <div class="failed">
          <p class="eyebrow">
            {{ imageRetryIn > 0 ? 'Images are rate-limited' : 'Clue image unavailable' }}
          </p>
          <p v-if="imageRetryIn > 0" class="failed__text">
            The guide site is limiting how many images it hands out and asked us to wait
            <strong>{{ retryWait }}</strong>. Clues whose image is already cached keep working;
            after that window new ones load again.
          </p>
          <p v-else class="failed__text">
            The guide site would not hand this image over. The other clues are unaffected.
          </p>
          <button class="btn btn--ghost" type="button" :disabled="busy" @click="emit('skip')">
            Skip this clue
          </button>
        </div>
      </div>
      <div v-else class="clue__frame" :class="{ 'clue__frame--loading': !imageReady }">
        <img
          :key="question.clueId"
          :src="clueImageUrl(question.imageUrl)"
          :alt="'Clue ' + question.questionNumber + ' — a photo that identifies one country'"
          decoding="async"
          @load="emit('imageLoaded')"
          @error="emit('imageFailed')"
        />
      </div>
    </figure>

    <div class="prompt">
      <div class="prompt__head">
        <h2 class="prompt__title">Which country is this clue from?</h2>
        <span class="clue__number">Clue #{{ question.questionNumber }}</span>
        <span v-for="tag in question.tags" :key="tag" class="tag">{{ tag }}</span>
      </div>

      <div class="options">
        <button
          v-for="(option, index) in question.options"
          :key="option.code"
          type="button"
          class="option"
          :class="stateOf(option)"
          :aria-label="option.name"
          :disabled="!canAnswer || busy"
          @click="emit('pick', option.code)"
        >
          <span class="option__index">{{ index + 1 }}</span>
          <span class="option__name">
            {{ option.name }}
            <small class="option__code">{{ option.code }}</small>
          </span>
          <span v-if="answered && stateOf(option) === 'option--correct'" class="option__mark">
            correct
          </span>
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.stage {
  display: grid;
  gap: 16px;
}

.clue {
  margin: 0;
}

/* The frame hugs the image instead of leaving dead bars beside a tall clue. */
.clue__frame {
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
  overflow: hidden;
  width: fit-content;
  max-width: 100%;
  min-height: 180px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.clue__frame--failed {
  width: 100%;
  min-height: 220px;
  border-style: dashed;
}

.failed {
  display: grid;
  gap: 12px;
  justify-items: center;
  text-align: center;
  padding: 24px;
}

.failed__text {
  margin: 0;
  color: var(--text-dim);
  max-width: 46ch;
}

.clue__frame--loading {
  width: 100%;
  background-image: linear-gradient(100deg, #131316 30%, #1e1e23 50%, #131316 70%);
  background-size: 200% 100%;
  animation: sweep 1.1s linear infinite;
}

.clue__frame img {
  /* As tall as it can be while the score strip, the question and the options
     still share the screen with it: 360px is what that chrome measures. */
  max-height: min(calc(100vh - 360px), 560px);
  max-width: 100%;
  width: auto;
  object-fit: contain;
}

@keyframes sweep {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: -100% 0;
  }
}

.prompt__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.prompt__title {
  margin: 0;
}

.clue__number {
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.option {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--text);
  text-align: left;
  transition:
    border-color var(--transition),
    background var(--transition),
    transform var(--transition);
}

.option:not(:disabled):hover {
  border-color: var(--line-strong);
  background: var(--surface-hover);
  transform: translateY(-1px);
}

.option:focus-visible {
  outline: 2px solid var(--green);
  outline-offset: 3px;
}

.option:disabled {
  cursor: default;
}

.option__index {
  width: 22px;
  height: 22px;
  flex: none;
  border-radius: 50%;
  border: 1px solid var(--line-strong);
  display: grid;
  place-items: center;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--text-dim);
}

.option__name {
  font-weight: 500;
  font-size: 0.95rem;
  line-height: 1.2;
}

.option__code {
  display: block;
  font-size: 0.65rem;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--text-faint);
}

.option__mark {
  margin-left: auto;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--green-bright);
}

.option--correct {
  border-color: var(--green);
  background: var(--green-veil);
}

.option--wrong {
  border-color: var(--red);
  background: var(--red-veil);
}

.option--dimmed {
  opacity: 0.4;
}

@media (max-width: 780px) {
  .options {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .option {
    padding: 12px 14px;
  }

  .clue__frame img {
    max-height: 42vh;
  }
}
</style>

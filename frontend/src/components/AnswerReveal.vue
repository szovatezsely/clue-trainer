<script setup lang="ts">
import { computed } from 'vue'
import { renderGuideMarkdown } from '../markdown'
import type { AnswerResult } from '../types'

const props = defineProps<{ result: AnswerResult; busy: boolean }>()
const emit = defineEmits<{ next: [] }>()

/**
 * The guide text is markdown (bold, bullet lists, links). The backend has
 * already neutralised angle brackets, so the only tags here are the ones
 * `marked` itself produces.
 */
const explanationHtml = computed(() => renderGuideMarkdown(props.result.explanation.paragraphs))

const heading = computed(() =>
  props.result.correct
    ? 'Correct — ' + props.result.correctCountry.name
    : 'Not quite — it is ' + props.result.correctCountry.name,
)
</script>

<template>
  <section class="reveal" :class="result.correct ? 'reveal--right' : 'reveal--wrong'">
    <header class="reveal__head">
      <h2 class="reveal__title">
        {{ heading }}
        <span class="reveal__code">{{ result.correctCountry.code }}</span>
      </h2>
      <button class="btn btn--accent reveal__next" type="button" :disabled="busy" @click="emit('next')">
        Next clue
        <span class="btn__key">Enter</span>
      </button>
    </header>

    <p v-if="!result.correct" class="reveal__chosen">You picked {{ result.chosenCountry.name }}.</p>

    <div class="reveal__body">
      <p class="reveal__source">
        {{ result.explanation.section
        }}<template v-if="result.explanation.subsection">
          — {{ result.explanation.subsection }}</template
        >
      </p>
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="prose" v-html="explanationHtml" />

      <div class="reveal__links">
        <a :href="result.explanation.sourceUrl" target="_blank" rel="noopener noreferrer">
          Read the full {{ result.correctCountry.name }} guide
        </a>
        <a
          v-if="result.explanation.streetViewUrl"
          :href="result.explanation.streetViewUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          Open this spot in Street View
        </a>
      </div>
    </div>
  </section>
</template>

<style scoped>
.reveal {
  border: 1px solid var(--line);
  border-left: 3px solid var(--green);
  border-radius: var(--radius-lg);
  padding: 22px 26px;
  background: var(--surface);
  display: grid;
  gap: 14px;
}

.reveal--wrong {
  border-left-color: var(--red);
}

.reveal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
}

.reveal__title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.reveal--right .reveal__title {
  color: var(--green-bright);
}

.reveal__code {
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: var(--text-faint);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 3px 10px;
}

.reveal__next {
  flex: none;
}

.reveal__chosen {
  margin: -4px 0 0;
  color: var(--text-dim);
  font-size: 0.9rem;
}

.reveal__source {
  margin: 0 0 10px;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.reveal__links {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  margin-top: 14px;
  font-size: 0.9rem;
}

.prose {
  max-width: 78ch;
  color: var(--text-dim);
}

.prose :deep(p) {
  margin: 0 0 0.8em;
}

.prose :deep(p:last-child) {
  margin-bottom: 0;
}

.prose :deep(ul),
.prose :deep(ol) {
  margin: 0 0 0.8em;
  padding-left: 1.2em;
}

.prose :deep(li) {
  margin-bottom: 0.2em;
}

.prose :deep(strong) {
  font-weight: 600;
  color: var(--text);
}

.prose :deep(em) {
  color: var(--text-faint);
}

.prose :deep(a) {
  color: var(--text);
}

@media (max-width: 640px) {
  .reveal {
    padding: 18px 16px;
  }

  .reveal__next {
    width: 100%;
    justify-content: center;
  }
}
</style>

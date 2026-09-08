<script setup lang="ts">
import type { Meta } from '../types'

const props = defineProps<{ meta: Meta | null }>()

const scrapedOn = () => {
  if (!props.meta) return ''
  const date = new Date(props.meta.scrapedAt)
  return Number.isNaN(date.getTime())
    ? props.meta.scrapedAt
    : date.toLocaleDateString('en-GB', { year: 'numeric', month: 'short', day: 'numeric' })
}
</script>

<template>
  <header class="masthead">
    <div class="shell masthead__inner">
      <div class="wordmark">
        <span class="wordmark__text">clue trainer</span><span class="wordmark__dot">.</span>
      </div>
      <dl v-if="meta" class="dataset">
        <div class="dataset__item">
          <dt>Clues</dt>
          <dd>{{ meta.clueCount.toLocaleString('en-GB') }}</dd>
        </div>
        <div class="dataset__item">
          <dt>Countries</dt>
          <dd>{{ meta.countryCount }}</dd>
        </div>
        <div class="dataset__item">
          <dt>Updated</dt>
          <dd>{{ scrapedOn() }}</dd>
        </div>
      </dl>
    </div>
  </header>
</template>

<style scoped>
.masthead {
  border-bottom: 1px solid var(--line);
  background: var(--bg);
  position: sticky;
  top: 0;
  z-index: 20;
}

.masthead__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 72px;
}

.wordmark {
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-weight: 400;
  letter-spacing: -0.02em;
}

.wordmark__dot {
  color: var(--green);
}

.dataset {
  display: flex;
  gap: 30px;
  margin: 0;
}

/* Label and value are centred on each other, so the trio reads as a block. */
.dataset__item {
  text-align: center;
}

.dataset dt {
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.dataset dd {
  margin: 2px 0 0;
  font-size: 0.92rem;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 720px) {
  .dataset__item:nth-child(3) {
    display: none;
  }

  .dataset {
    gap: 20px;
  }
}
</style>

<script setup lang="ts">
import LanguageSwitch from './LanguageSwitch.vue'
import { formatDate, formatNumber, t } from '../i18n'
import type { Meta } from '../types'

defineProps<{ meta: Meta | null }>()
</script>

<template>
  <header class="masthead">
    <div class="shell masthead__inner">
      <div class="wordmark">
        <span class="wordmark__text">clue trainer</span><span class="wordmark__dot">.</span>
      </div>
      <div class="masthead__end">
        <LanguageSwitch />
        <dl v-if="meta" class="dataset">
          <div class="dataset__item">
            <dt>{{ t.header.clues }}</dt>
            <dd>{{ formatNumber(meta.clueCount) }}</dd>
          </div>
          <div class="dataset__item">
            <dt>{{ t.header.countries }}</dt>
            <dd>{{ meta.countryCount }}</dd>
          </div>
          <div class="dataset__item">
            <dt>{{ t.header.updated }}</dt>
            <dd>{{ formatDate(meta.scrapedAt) }}</dd>
          </div>
        </dl>
      </div>
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

/* The language switch leads the counts, and the group travels together at
   the right edge. */
.masthead__end {
  display: flex;
  align-items: center;
  gap: 26px;
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

@media (max-width: 860px) {
  /* The date is the first thing to go; the language switch never is. */
  .dataset__item:nth-child(3) {
    display: none;
  }

  .masthead__end {
    gap: 18px;
  }

  .dataset {
    gap: 20px;
  }
}

@media (max-width: 560px) {
  .dataset__item:nth-child(2) {
    display: none;
  }
}
</style>

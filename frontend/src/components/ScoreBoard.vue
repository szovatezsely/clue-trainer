<script setup lang="ts">
import type { Stats } from '../types'

defineProps<{ stats: Stats }>()
</script>

<template>
  <!-- Deliberately a thin strip: the vertical space belongs to the clue. -->
  <section class="scores" aria-label="Score">
    <div class="score score--right">
      <span class="score__value">{{ stats.correct }}</span>
      <span class="score__label">Right</span>
    </div>
    <div class="score score--wrong">
      <span class="score__value">{{ stats.wrong }}</span>
      <span class="score__label">Wrong</span>
    </div>
    <div class="score">
      <span class="score__value">{{ stats.accuracy }}<small>%</small></span>
      <span class="score__label">Accuracy</span>
    </div>
    <div class="score">
      <span class="score__value">{{ stats.streak }}</span>
      <span class="score__label">Streak</span>
    </div>
    <div class="score score--muted">
      <span class="score__value">{{ stats.bestStreak }}</span>
      <span class="score__label">Best streak</span>
    </div>
  </section>
</template>

<style scoped>
.scores {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border: 1px solid var(--line);
  border-radius: 999px;
  overflow: hidden;
  background: var(--surface);
}

.score {
  padding: 7px 18px;
  border-right: 1px solid var(--line);
  display: flex;
  align-items: baseline;
  gap: 7px;
  min-width: 0;
}

.score:first-child {
  padding-left: 24px;
}

.score:last-child {
  border-right: 0;
  padding-right: 24px;
}

.score__value {
  font-family: var(--font-display);
  font-size: 1.05rem;
  font-weight: 400;
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
}

.score__value small {
  font-size: 0.62em;
  color: var(--text-faint);
  margin-left: 1px;
}

.score__label {
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: var(--text-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.score--right .score__value {
  color: var(--green-bright);
}

.score--wrong .score__value {
  color: var(--red);
}

.score--muted .score__value {
  color: var(--text-dim);
}

@media (max-width: 760px) {
  .scores {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    border-radius: var(--radius-lg);
  }

  .score,
  .score:first-child,
  .score:last-child {
    padding: 7px 14px;
  }

  .score:nth-child(odd) {
    border-right: 1px solid var(--line);
  }

  .score:nth-child(even) {
    border-right: 0;
  }

  .score:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }

  .score--muted {
    display: none;
  }
}
</style>

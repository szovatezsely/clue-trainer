<script setup lang="ts">
import { computed } from 'vue'
import SelectMenu from './SelectMenu.vue'
import type { SelectOption } from './SelectMenu.vue'
import { formatNumber, t } from '../i18n'
import type { GameMode, Meta } from '../types'

const props = defineProps<{
  meta: Meta | null
  running: boolean
  busy: boolean
}>()

const mode = defineModel<GameMode>('mode', { required: true })
const continent = defineModel<string>('continent', { required: true })
const wholeGuide = defineModel<boolean>('wholeGuide', { required: true })

/** What the country game gains beyond the "identifying X" chapter. */
const beyondCoreCount = computed(() =>
  props.meta ? props.meta.clueCount - props.meta.coreClueCount : 0,
)

/** How many clues the current mode and scope actually serve, per continent. */
function servedBy(counts: { clueCount: number; coreClueCount: number; regionClueCount: number }) {
  if (mode.value === 'region') return counts.regionClueCount
  return wholeGuide.value ? counts.clueCount : counts.coreClueCount
}

const continentOptions = computed<SelectOption[]>(() => [
  {
    value: 'all',
    label: t.value.controls.everywhere,
    hint: props.meta
      ? String(
          servedBy({
            clueCount: props.meta.clueCount,
            coreClueCount: props.meta.coreClueCount,
            regionClueCount: props.meta.regionClueCount,
          }),
        )
      : undefined,
  },
  // The value stays the English name the API filters on; only the label
  // follows the language.
  ...(props.meta?.continents ?? []).map((item) => ({
    value: item.name,
    label: item.label,
    hint: String(servedBy(item)),
  })),
])

const emit = defineEmits<{
  start: []
  stop: []
  reset: []
}>()

const modes = computed<{ value: GameMode; label: string }[]>(() => [
  { value: 'country', label: t.value.controls.modeCountry },
  { value: 'region', label: t.value.controls.modeRegion },
])
</script>

<template>
  <section class="controls">
    <div class="controls__actions">
      <button v-if="!running" class="btn" type="button" @click="emit('start')">
        {{ t.controls.start }}
        <span class="btn__key">S</span>
      </button>
      <button v-else class="btn btn--ghost" type="button" @click="emit('stop')">
        {{ t.controls.stop }}
        <span class="btn__key">S</span>
      </button>
      <button class="btn btn--ghost" type="button" :disabled="busy" @click="emit('reset')">
        {{ t.controls.resetScore }}
      </button>
    </div>

    <div class="controls__filters">
      <!-- Which question the clue is asked as: name the country, or, for a clue
           that only holds in one part of one country, name that part. -->
      <div class="modes" role="group" :aria-label="t.controls.guessLabel">
        <span class="modes__label">{{ t.controls.guess }}</span>
        <div class="modes__switch">
          <button
            v-for="item in modes"
            :key="item.value"
            type="button"
            class="modes__option"
            :class="{ 'modes__option--on': mode === item.value }"
            :aria-pressed="mode === item.value"
            @click="mode = item.value"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <SelectMenu v-model="continent" :label="t.controls.continent" :options="continentOptions" />

      <!-- Scope, not a second region switch: how much of each guide the
           country game draws its questions from. -->
      <label v-if="mode === 'country'" class="toggle">
        <input v-model="wholeGuide" type="checkbox" />
        <span class="toggle__track"><span class="toggle__thumb" /></span>
        <span class="toggle__text">
          {{ t.controls.wholeGuide }}
          <small v-if="meta">
            {{ t.controls.wholeGuideHint(formatNumber(beyondCoreCount)) }}
          </small>
        </span>
      </label>
      <p v-else-if="meta" class="hint">
        {{ t.controls.regionHint(formatNumber(meta.regionClueCount), meta.regionCountryCount) }}
      </p>
    </div>
  </section>
</template>

<style scoped>
.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px 24px;
  padding: 4px 0 22px;
}

.controls__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 4px;
}

.controls__filters {
  display: flex;
  align-items: flex-end;
  gap: 22px;
  flex-wrap: wrap;
}

.modes {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.modes__label {
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-faint);
}

/* One track, two halves: the chosen one is filled in, exactly like the toggle. */
.modes__switch {
  display: inline-flex;
  padding: 3px;
  gap: 3px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.modes__option {
  padding: 7px 16px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-dim);
  font-size: 0.88rem;
  font-weight: 500;
  transition:
    background var(--transition),
    color var(--transition);
}

.modes__option:hover:not(.modes__option--on) {
  background: var(--surface-hover);
  color: var(--text);
}

.modes__option--on {
  background: var(--green);
  color: var(--green-ink);
}

.modes__option:focus-visible {
  outline: 2px solid var(--green);
  outline-offset: 3px;
}

.hint {
  margin: 0;
  padding-bottom: 8px;
  max-width: 34ch;
  font-size: 0.78rem;
  line-height: 1.35;
  color: var(--text-faint);
}

.toggle {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  user-select: none;
  padding-bottom: 6px;
}

.toggle input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle__track {
  width: 40px;
  height: 22px;
  border-radius: 999px;
  background: var(--surface-alt);
  border: 1px solid var(--line);
  position: relative;
  transition:
    background var(--transition),
    border-color var(--transition);
  flex: none;
}

.toggle__thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--text-dim);
  transition:
    transform var(--transition),
    background var(--transition);
}

.toggle input:checked + .toggle__track {
  background: var(--green);
  border-color: var(--green);
}

.toggle input:checked + .toggle__track .toggle__thumb {
  transform: translateX(18px);
  background: var(--green-ink);
}

.toggle input:focus-visible + .toggle__track {
  outline: 2px solid var(--green);
  outline-offset: 3px;
}

.toggle__text {
  font-size: 0.9rem;
  line-height: 1.25;
}

.toggle__text small {
  display: block;
  /* Wrapped on purpose, so the explanation cannot stretch the controls row. */
  max-width: 42ch;
  font-size: 0.72rem;
  color: var(--text-faint);
}

@media (max-width: 720px) {
  .controls {
    align-items: stretch;
  }

  .controls__filters {
    gap: 18px;
    width: 100%;
    align-items: stretch;
    flex-direction: column;
  }

  .modes__switch {
    width: 100%;
  }

  .modes__option {
    flex: 1;
  }
}
</style>

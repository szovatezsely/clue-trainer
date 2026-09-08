<script setup lang="ts">
import { computed } from 'vue'
import SelectMenu from './SelectMenu.vue'
import type { SelectOption } from './SelectMenu.vue'
import type { Meta } from '../types'

const props = defineProps<{
  meta: Meta | null
  running: boolean
  busy: boolean
}>()

const continent = defineModel<string>('continent', { required: true })
const includeRegional = defineModel<boolean>('includeRegional', { required: true })

/** Clues from the regional and spotlight chapters of each guide. */
const regionalCount = computed(() =>
  props.meta ? props.meta.clueCount - props.meta.coreClueCount : 0,
)

/** The region menu counts only what the current scope actually serves. */
const regionOptions = computed<SelectOption[]>(() => [
  {
    value: 'all',
    label: 'All regions',
    hint: props.meta
      ? String(includeRegional.value ? props.meta.clueCount : props.meta.coreClueCount)
      : undefined,
  },
  ...(props.meta?.continents ?? []).map((item) => ({
    value: item.name,
    label: item.name,
    hint: String(includeRegional.value ? item.clueCount : item.coreClueCount),
  })),
])

const emit = defineEmits<{
  start: []
  stop: []
  reset: []
}>()
</script>

<template>
  <section class="controls">
    <div class="controls__actions">
      <button v-if="!running" class="btn" type="button" @click="emit('start')">
        Start
        <span class="btn__key">S</span>
      </button>
      <button v-else class="btn btn--ghost" type="button" @click="emit('stop')">
        Stop
        <span class="btn__key">S</span>
      </button>
      <button class="btn btn--ghost" type="button" :disabled="busy" @click="emit('reset')">
        Reset score
      </button>
    </div>

    <div class="controls__filters">
      <SelectMenu v-model="continent" label="Region" :options="regionOptions" />

      <label class="toggle">
        <input v-model="includeRegional" type="checkbox" />
        <span class="toggle__track"><span class="toggle__thumb" /></span>
        <span class="toggle__text">
          Include regional clues
          <small v-if="meta">
            +{{ regionalCount.toLocaleString() }} harder ones, often with a location map
          </small>
        </span>
      </label>
    </div>
  </section>
</template>

<style scoped>
.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px 32px;
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
  gap: 28px;
  flex-wrap: wrap;
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
}
</style>

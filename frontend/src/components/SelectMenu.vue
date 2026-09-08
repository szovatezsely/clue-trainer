<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

export interface SelectOption {
  value: string
  label: string
  hint?: string
}

const props = defineProps<{ label: string; options: SelectOption[] }>()
const model = defineModel<string>({ required: true })

const open = ref(false)
const activeIndex = ref(0)
const root = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)

const selected = computed(() => props.options.find((option) => option.value === model.value))
const selectedIndex = computed(() =>
  Math.max(
    0,
    props.options.findIndex((option) => option.value === model.value),
  ),
)

async function toggle() {
  open.value = !open.value
  if (!open.value) return
  activeIndex.value = selectedIndex.value
  // Move focus into the popup so the arrow keys drive it and the game's own
  // shortcuts stay out of the way while it is open.
  await nextTick()
  list.value?.focus()
}

function close(returnFocus = true) {
  if (!open.value) return
  open.value = false
  if (returnFocus) (root.value?.querySelector('.select__trigger') as HTMLElement | null)?.focus()
}

function choose(index: number) {
  const option = props.options[index]
  if (!option) return
  model.value = option.value
  close()
}

function move(delta: number) {
  const count = props.options.length
  if (!count) return
  activeIndex.value = (activeIndex.value + delta + count) % count
}

function onListKeydown(event: KeyboardEvent) {
  // Both the modern and the legacy key names, since some embedded browsers
  // still report "Down" / "Esc" / "Spacebar".
  switch (event.key) {
    case 'ArrowDown':
    case 'Down':
      move(1)
      break
    case 'ArrowUp':
    case 'Up':
      move(-1)
      break
    case 'Home':
      activeIndex.value = 0
      break
    case 'End':
      activeIndex.value = props.options.length - 1
      break
    case 'Enter':
    case ' ':
    case 'Spacebar':
      choose(activeIndex.value)
      break
    case 'Escape':
    case 'Esc':
    case 'Tab':
      close()
      break
    default:
      return
  }
  event.preventDefault()
}

function onDocumentPointerDown(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) close(false)
}

watch(open, (isOpen) => {
  if (isOpen) document.addEventListener('pointerdown', onDocumentPointerDown)
  else document.removeEventListener('pointerdown', onDocumentPointerDown)
})

onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))
</script>

<template>
  <div ref="root" class="select">
    <span :id="'select-label-' + label" class="select__label">{{ label }}</span>
    <button
      type="button"
      class="select__trigger"
      :class="{ 'select__trigger--open': open }"
      :aria-expanded="open"
      :aria-labelledby="'select-label-' + label"
      aria-haspopup="listbox"
      @click="toggle"
    >
      <span class="select__value">{{ selected?.label ?? '—' }}</span>
      <span v-if="selected?.hint" class="select__hint">{{ selected.hint }}</span>
      <svg class="select__caret" viewBox="0 0 10 6" aria-hidden="true">
        <path d="M1 1l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.4" />
      </svg>
    </button>

    <div
      v-show="open"
      ref="list"
      class="select__menu"
      role="listbox"
      tabindex="-1"
      :aria-activedescendant="'select-option-' + activeIndex"
      @keydown.stop="onListKeydown"
    >
      <button
        v-for="(option, index) in options"
        :id="'select-option-' + index"
        :key="option.value"
        type="button"
        role="option"
        class="select__option"
        :class="{
          'select__option--active': index === activeIndex,
          'select__option--selected': option.value === model,
        }"
        :aria-selected="option.value === model"
        @click="choose(index)"
        @mousemove="activeIndex = index"
      >
        <span class="select__dot" aria-hidden="true" />
        <span class="select__option-label">{{ option.label }}</span>
        <span v-if="option.hint" class="select__option-hint">{{ option.hint }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.select {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 232px;
}

.select__label {
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.select__trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--text);
  font-size: 0.92rem;
  text-align: left;
  transition:
    border-color var(--transition),
    background var(--transition);
}

.select__trigger:hover,
.select__trigger--open {
  border-color: var(--line-strong);
  background: var(--surface-hover);
}

.select__trigger:focus-visible {
  outline: 2px solid var(--green);
  outline-offset: 2px;
}

.select__value {
  font-weight: 500;
}

.select__hint {
  color: var(--text-faint);
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
}

.select__caret {
  width: 10px;
  height: 6px;
  margin-left: auto;
  color: var(--text-dim);
  transition: transform var(--transition);
}

.select__trigger--open .select__caret {
  transform: rotate(180deg);
}

.select__menu {
  position: absolute;
  z-index: 30;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  max-height: 360px;
  overflow-y: auto;
  padding: 6px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-lg);
  background: var(--surface-alt);
  box-shadow: var(--shadow);
  outline: none;
}

.select__option {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border-radius: 7px;
  color: var(--text-dim);
  font-size: 0.9rem;
  text-align: left;
}

.select__option--active {
  background: var(--surface-hover);
  color: var(--text);
}

.select__option--selected {
  color: var(--text);
}

.select__dot {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
  background: transparent;
  border: 1px solid var(--line-strong);
}

.select__option--selected .select__dot {
  background: var(--green);
  border-color: var(--green);
}

.select__option-label {
  font-weight: 500;
}

.select__option-hint {
  margin-left: auto;
  font-size: 0.78rem;
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 640px) {
  .select {
    min-width: 0;
    width: 100%;
  }
}
</style>

import { computed, ref, watch } from 'vue'
import { en } from './en'
import { hu } from './hu'
import type { Messages } from './en'

export type Locale = 'en' | 'hu'

/** The order the switcher shows them in; LanguageSwitch draws each one's flag. */
export const locales: { value: Locale; name: string }[] = [
  { value: 'en', name: en.languageName },
  { value: 'hu', name: hu.languageName },
]

const bundles: Record<Locale, Messages> = { en, hu }

const STORAGE_KEY = 'clue-trainer.locale'

function isLocale(value: unknown): value is Locale {
  return value === 'en' || value === 'hu'
}

/**
 * The language to open in: whatever was chosen last, otherwise Hungarian for a
 * Hungarian browser, otherwise English.
 */
function initial(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (isLocale(stored)) return stored
  } catch {
    /* private browsing: fall through to the browser's own preference */
  }
  const preferred = navigator.languages ?? [navigator.language]
  for (const tag of preferred) {
    const code = tag?.split('-')[0]?.toLowerCase()
    if (isLocale(code)) return code
  }
  return 'en'
}

/**
 * The chosen language, shared by every component.
 *
 * Module scope on purpose: the switcher in the header and the board below it
 * are not in a parent-child relationship, and a single ref is a great deal less
 * ceremony than threading a prop through both.
 */
export const locale = ref<Locale>(initial())

/** The current language's copy. Read it as `t.hero.eyebrow` in a template. */
export const t = computed<Messages>(() => bundles[locale.value])

export function setLocale(next: Locale): void {
  if (next === locale.value) return
  locale.value = next
}

/** Formats a count the way the current language writes numbers. */
export function formatNumber(value: number): string {
  return value.toLocaleString(t.value.numberLocale)
}

/** Formats an ISO timestamp as a short date, or returns it unchanged if unparseable. */
export function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(t.value.numberLocale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

/** The message for an API error code, falling back to the generic one. */
export function errorMessage(code: string): string {
  const messages = t.value.errors as Record<string, string | undefined>
  return messages[code] ?? t.value.errors.unknown
}

watch(
  locale,
  (value) => {
    document.documentElement.lang = bundles[value].htmlLang
    try {
      localStorage.setItem(STORAGE_KEY, value)
    } catch {
      /* private browsing: the choice simply does not survive a reload */
    }
  },
  { immediate: true },
)

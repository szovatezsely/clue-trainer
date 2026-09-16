<script setup lang="ts">
import { locale, locales, setLocale, t } from '../i18n'
</script>

<template>
  <!-- Two flags rather than a dropdown: with only two languages, the one you
       are not in is always one click away and needs no opening.

       The flags are drawn rather than written as 🇬🇧 / 🇭🇺, because Windows
       ships no glyphs for the regional-indicator pairs and renders them as the
       bare letters "GB" and "HU". -->
  <div class="langs" role="group" :aria-label="t.header.language">
    <button
      v-for="item in locales"
      :key="item.value"
      type="button"
      class="langs__option"
      :class="{ 'langs__option--on': locale === item.value }"
      :aria-pressed="locale === item.value"
      :title="item.name"
      :lang="item.value"
      @click="setLocale(item.value)"
    >
      <svg
        v-if="item.value === 'en'"
        class="langs__flag"
        viewBox="0 0 60 30"
        aria-hidden="true"
        focusable="false"
      >
        <clipPath id="flag-gb-quarters">
          <path d="M30,15 h30 v15 z v15 h-30 z h-30 v-15 z v-15 h30 z" />
        </clipPath>
        <path d="M0,0 h60 v30 h-60 z" fill="#012169" />
        <path d="M0,0 L60,30 M60,0 L0,30" stroke="#fff" stroke-width="6" />
        <path
          d="M0,0 L60,30 M60,0 L0,30"
          clip-path="url(#flag-gb-quarters)"
          stroke="#c8102e"
          stroke-width="4"
        />
        <path d="M30,0 v30 M0,15 h60" stroke="#fff" stroke-width="10" />
        <path d="M30,0 v30 M0,15 h60" stroke="#c8102e" stroke-width="6" />
      </svg>
      <svg v-else class="langs__flag" viewBox="0 0 60 30" aria-hidden="true" focusable="false">
        <path d="M0,0 h60 v10 h-60 z" fill="#ce2939" />
        <path d="M0,10 h60 v10 h-60 z" fill="#fff" />
        <path d="M0,20 h60 v10 h-60 z" fill="#477050" />
      </svg>
      <span class="visually-hidden">{{ item.name }}</span>
    </button>
  </div>
</template>

<style scoped>
.langs {
  display: inline-flex;
  padding: 3px;
  gap: 3px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.langs__option {
  display: grid;
  place-items: center;
  width: 36px;
  height: 28px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  /* The flags carry their own colours, so dimming has to work on the image. */
  opacity: 0.4;
  transition:
    background var(--transition),
    opacity var(--transition);
}

.langs__option:hover {
  background: var(--surface-hover);
  opacity: 0.75;
}

.langs__option--on,
.langs__option--on:hover {
  background: var(--surface-hover);
  box-shadow: inset 0 0 0 1px var(--green);
  opacity: 1;
}

.langs__option:focus-visible {
  outline: 2px solid var(--green);
  outline-offset: 2px;
}

.langs__flag {
  width: 22px;
  height: 11px;
  /* A hairline keeps the white band of the Hungarian flag off the background. */
  box-shadow: 0 0 0 1px var(--line-strong);
  border-radius: 1px;
  display: block;
}
</style>

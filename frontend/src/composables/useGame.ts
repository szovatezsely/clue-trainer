import { computed, ref, watch } from 'vue'
import { ApiError, api } from '../api'
import { emptyStats } from '../types'
import type { AnswerResult, Meta, Question, Stats } from '../types'

type Phase = 'idle' | 'loading' | 'question' | 'answered' | 'error'

const SESSION_KEY = 'clue-trainer.session'
const FILTER_KEY = 'clue-trainer.filters'

function readStored<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

function store(key: string, value: unknown): void {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* private browsing: the game simply forgets its session on reload */
  }
}

/**
 * The whole game loop: one endless stream of clues, scored on the server.
 *
 * The session id lives in localStorage so a page reload continues the same run
 * with the same score - and, because the backend re-serves a question that has
 * not been answered yet, with the same clue on screen.
 */
export function useGame() {
  const storedFilters = readStored(FILTER_KEY, { continent: 'all', includeRegional: false })

  const meta = ref<Meta | null>(null)
  const stats = ref<Stats>({ ...emptyStats })
  const question = ref<Question | null>(null)
  const result = ref<AnswerResult | null>(null)
  const phase = ref<Phase>('idle')
  const running = ref(false)
  const errorMessage = ref<string | null>(null)
  const continent = ref<string>(storedFilters.continent)
  const includeRegional = ref<boolean>(storedFilters.includeRegional)
  const imageReady = ref(false)
  const imageFailed = ref(false)
  const imageRetryIn = ref(0)

  let sessionId: string | null = readStored<string | null>(SESSION_KEY, null)

  const busy = computed(() => phase.value === 'loading')
  const canAnswer = computed(() => running.value && phase.value === 'question')

  async function loadMeta(): Promise<void> {
    try {
      meta.value = await api.meta()
    } catch (error) {
      errorMessage.value = describe(error)
      phase.value = 'error'
    }
  }

  /**
   * A reload keeps the session id, so pull the score that belongs to it back
   * in rather than showing a misleading 0 - 0 until the next answer.
   */
  async function restoreScore(): Promise<void> {
    if (!sessionId) return
    try {
      stats.value = (await api.session(sessionId)).stats
    } catch {
      sessionId = null
      store(SESSION_KEY, null)
    }
  }

  async function ensureSession(forceNew = false): Promise<string> {
    if (sessionId && !forceNew) return sessionId
    const session = await api.createSession()
    sessionId = session.sessionId
    stats.value = session.stats
    store(SESSION_KEY, sessionId)
    return sessionId
  }

  /** Runs an API call, transparently recovering from an expired session. */
  async function withSession<T>(call: (id: string) => Promise<T>): Promise<T> {
    const id = await ensureSession()
    try {
      return await call(id)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'unknown_session') {
        return await call(await ensureSession(true))
      }
      throw error
    }
  }

  async function loadNext(): Promise<void> {
    phase.value = 'loading'
    errorMessage.value = null
    result.value = null
    imageReady.value = false
    imageFailed.value = false
    imageRetryIn.value = 0
    try {
      question.value = await withSession((id) =>
        api.nextQuestion(id, continent.value, includeRegional.value),
      )
      phase.value = 'question'
    } catch (error) {
      errorMessage.value = describe(error)
      phase.value = 'error'
    }
  }

  async function submit(countryCode: string): Promise<void> {
    const current = question.value
    if (!current || !canAnswer.value) return
    const previous = phase.value
    phase.value = 'loading'
    try {
      const answer = await withSession((id) => api.answer(id, current.clueId, countryCode))
      result.value = answer
      stats.value = answer.stats
      phase.value = 'answered'
    } catch (error) {
      if (error instanceof ApiError && (error.code === 'stale_answer' || error.code === 'no_pending_question')) {
        await loadNext()
        return
      }
      errorMessage.value = describe(error)
      phase.value = previous === 'question' ? 'question' : 'error'
    }
  }

  async function start(): Promise<void> {
    running.value = true
    if (phase.value === 'question' || phase.value === 'answered') return
    await loadNext()
  }

  function stop(): void {
    running.value = false
  }

  async function next(): Promise<void> {
    if (!running.value) return
    await loadNext()
  }

  /**
   * The clue image did not load. Ask the backend why, so the panel can say
   * whether this is the guide site rate-limiting us (and for how long) or just
   * one missing image.
   */
  async function noteImageFailure(): Promise<void> {
    imageReady.value = true
    imageFailed.value = true
    try {
      const status = await api.imageStatus()
      imageRetryIn.value = status.throttled ? status.retryInSeconds : 0
    } catch {
      imageRetryIn.value = 0
    }
  }

  /**
   * The clue image could not be fetched (the guide site rate-limits bursts, and
   * a handful of images have gone missing upstream). Drop the question server
   * side - otherwise it would be re-served as the pending one - and move on.
   */
  async function skipClue(): Promise<void> {
    try {
      const session = await withSession((id) => api.skip(id))
      stats.value = session.stats
    } catch (error) {
      errorMessage.value = describe(error)
    }
    await loadNext()
  }

  async function resetScore(): Promise<void> {
    try {
      const session = await withSession((id) => api.resetScore(id))
      stats.value = session.stats
      result.value = null
      question.value = null
      if (running.value) await loadNext()
      else phase.value = 'idle'
    } catch (error) {
      errorMessage.value = describe(error)
    }
  }

  watch([continent, includeRegional], async () => {
    store(FILTER_KEY, { continent: continent.value, includeRegional: includeRegional.value })
    // Switching the training set mid-run pulls a fresh clue from the new pool.
    if (running.value) await loadNext()
    else if (phase.value !== 'idle') {
      question.value = null
      result.value = null
      phase.value = 'idle'
    }
  })

  return {
    meta,
    stats,
    question,
    result,
    phase,
    running,
    errorMessage,
    continent,
    includeRegional,
    imageReady,
    imageFailed,
    imageRetryIn,
    busy,
    canAnswer,
    loadMeta,
    restoreScore,
    start,
    stop,
    next,
    noteImageFailure,
    skipClue,
    submit,
    resetScore,
  }
}

function describe(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return 'Unexpected error. Please try again.'
}

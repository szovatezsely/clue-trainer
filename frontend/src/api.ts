import { locale } from './i18n'
import type { AnswerResult, GameMode, ImageStatus, Meta, Question, SessionInfo } from './types'

/**
 * Adds the language to a request.
 *
 * Country names, chapter headings and the clue explanations are all served in
 * it, so every call that returns any of them carries it - the alternative
 * would be shipping the guide's megabyte of prose to the browser.
 */
function withLang(params = new URLSearchParams()): string {
  params.set('lang', locale.value)
  return '?' + params.toString()
}

/** An error carrying the machine-readable code the backend sent along. */
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    })
  } catch {
    throw new ApiError('network_error', 'The server is not reachable.', 0)
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(
      body?.error ?? 'http_' + response.status,
      body?.message ?? 'Request failed (' + response.status + ').',
      response.status,
    )
  }
  return (await response.json()) as T
}

export const api = {
  meta: () => request<Meta>('/api/meta' + withLang()),

  /** Why an image did not arrive - an `<img>` tag cannot read the error body. */
  imageStatus: () => request<ImageStatus>('/api/image/status'),

  createSession: () => request<SessionInfo>('/api/game/sessions', { method: 'POST' }),

  session: (sessionId: string) =>
    request<SessionInfo>('/api/game/sessions/' + encodeURIComponent(sessionId)),

  nextQuestion: (sessionId: string, mode: GameMode, continent: string, wholeGuide: boolean) => {
    const params = new URLSearchParams()
    params.set('mode', mode)
    if (continent && continent !== 'all') params.set('continent', continent)
    // The scope only means something to the country game - every clue the
    // region game plays comes from the regional chapters anyway, so the
    // backend ignores this there.
    params.set('scope', wholeGuide ? 'all' : 'core')
    return request<Question>(
      '/api/game/sessions/' + encodeURIComponent(sessionId) + '/next' + withLang(params),
    )
  },

  answer: (sessionId: string, clueId: string, answer: string) =>
    request<AnswerResult>(
      '/api/game/sessions/' + encodeURIComponent(sessionId) + '/answer' + withLang(),
      { method: 'POST', body: JSON.stringify({ clueId, answer }) },
    ),

  /**
   * The verdict already on screen, re-worded in the current language. Grading
   * clears the question server side, so the reveal cannot simply be re-asked.
   */
  lastAnswer: (sessionId: string) =>
    request<AnswerResult>(
      '/api/game/sessions/' + encodeURIComponent(sessionId) + '/answer' + withLang(),
    ),

  /** Gives up on the current clue without scoring it. */
  skip: (sessionId: string) =>
    request<SessionInfo>('/api/game/sessions/' + encodeURIComponent(sessionId) + '/skip', {
      method: 'POST',
    }),

  resetScore: (sessionId: string) =>
    request<SessionInfo>('/api/game/sessions/' + encodeURIComponent(sessionId) + '/reset', {
      method: 'POST',
    }),
}

/** Clue images are proxied by the backend, which is the only party allowed to fetch them. */
export function clueImageUrl(imagePath: string): string {
  return '/api/image?path=' + encodeURIComponent(imagePath)
}

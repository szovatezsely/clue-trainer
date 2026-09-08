import type { AnswerResult, ImageStatus, Meta, Question, SessionInfo } from './types'

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
  meta: () => request<Meta>('/api/meta'),

  /** Why an image did not arrive - an `<img>` tag cannot read the error body. */
  imageStatus: () => request<ImageStatus>('/api/image/status'),

  createSession: () => request<SessionInfo>('/api/game/sessions', { method: 'POST' }),

  session: (sessionId: string) =>
    request<SessionInfo>('/api/game/sessions/' + encodeURIComponent(sessionId)),

  nextQuestion: (sessionId: string, continent: string, includeRegional: boolean) => {
    const params = new URLSearchParams()
    if (continent && continent !== 'all') params.set('continent', continent)
    params.set('scope', includeRegional ? 'all' : 'core')
    return request<Question>(
      '/api/game/sessions/' + encodeURIComponent(sessionId) + '/next?' + params.toString(),
    )
  },

  answer: (sessionId: string, clueId: string, countryCode: string) =>
    request<AnswerResult>('/api/game/sessions/' + encodeURIComponent(sessionId) + '/answer', {
      method: 'POST',
      body: JSON.stringify({ clueId, countryCode }),
    }),

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

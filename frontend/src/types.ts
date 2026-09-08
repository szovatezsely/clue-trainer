export interface CountryOption {
  code: string
  name: string
  flag: string
  continent: string
}

export interface Stats {
  correct: number
  wrong: number
  answered: number
  streak: number
  bestStreak: number
  accuracy: number
}

export interface Question {
  clueId: string
  imageUrl: string
  imageWidth: number
  tags: string[]
  options: CountryOption[]
  questionNumber: number
  remainingClues: number
}

export interface Explanation {
  paragraphs: string[]
  section: string
  subsection: string
  tags: string[]
  sourceUrl: string
  streetViewUrl: string | null
}

export interface AnswerResult {
  correct: boolean
  correctCountry: CountryOption
  chosenCountry: CountryOption
  explanation: Explanation
  stats: Stats
}

export interface Continent {
  name: string
  countryCount: number
  clueCount: number
  coreClueCount: number
}

export interface Meta {
  source: string
  scrapedAt: string
  clueCount: number
  coreClueCount: number
  countryCount: number
  continents: Continent[]
  countries: CountryOption[]
  tags: string[]
  refreshAllowed: boolean
  refreshState: string
}

export interface ImageStatus {
  throttled: boolean
  retryInSeconds: number
}

export interface SessionInfo {
  sessionId: string
  stats: Stats
}

export const emptyStats: Stats = {
  correct: 0,
  wrong: 0,
  answered: 0,
  streak: 0,
  bestStreak: 0,
  accuracy: 0,
}

export type GameMode = 'country' | 'region'

export interface CountryOption {
  code: string
  name: string
  flag: string
  continent: string
}

/** One button on the board: a country in the country game, a region in the region game. */
export interface AnswerOption {
  value: string
  label: string
  note: string
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
  mode: GameMode
  /** Given away in the region game, where it is the premise, withheld otherwise. */
  country: CountryOption | null
  imageUrl: string
  imageWidth: number
  tags: string[]
  options: AnswerOption[]
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
  /** False when the paragraphs fell back to the guide's own English. */
  translated: boolean
}

export interface AnswerResult {
  correct: boolean
  mode: GameMode
  correctAnswer: AnswerOption
  chosenAnswer: AnswerOption
  /** The whole board as it was shown, so its labels can follow the language. */
  options: AnswerOption[]
  country: CountryOption
  explanation: Explanation
  stats: Stats
}

export interface Continent {
  /** The filter key sent back to the API; stays English in every language. */
  name: string
  /** The same continent as the current language writes it. */
  label: string
  countryCount: number
  clueCount: number
  coreClueCount: number
  regionClueCount: number
}

export interface Meta {
  source: string
  scrapedAt: string
  clueCount: number
  coreClueCount: number
  regionClueCount: number
  countryCount: number
  regionCountryCount: number
  continents: Continent[]
  countries: CountryOption[]
  tags: string[]
  refreshAllowed: boolean
  refreshState: string
  /** The language this payload was rendered in. */
  lang: string
  /** How many clues have their explanation in [lang]; equals clueCount for English. */
  translatedClueCount: number
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

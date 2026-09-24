/**
 * The English copy, and the shape every other language has to match.
 *
 * [Messages] is derived from this object, so adding a string here without
 * translating it is a type error in `npm run typecheck` rather than an English
 * sentence that quietly shows up mid-game.
 */
export const en = {
  /** Goes on `<html lang>`, and drives hyphenation and screen readers. */
  htmlLang: 'en',
  /** Number and date formatting - the game shows a lot of counts. */
  numberLocale: 'en-GB',
  /** The label on this language's own button in the switcher. */
  languageName: 'English',

  header: {
    clues: 'Clues',
    /** The same count in the region game, where only placed clues are in play. */
    regionClues: 'Region clues',
    countries: 'Countries',
    /** When the clues were scraped from the guides. */
    scraped: 'Scraped',
    /** When the app itself last changed. */
    updated: 'Updated',
    language: 'Language',
  },

  hero: {
    eyebrow: 'Endless meta practice',
    titleLead: 'Spot the clue,',
    titleTail: 'name the',
    country: 'country',
    region: 'region',
    countryLead:
      'Every clue is a real identification detail taken from a country guide: a road sign, a ' +
      'bollard, a utility pole, a licence plate, a landscape. Pick the country it belongs to and ' +
      'read why it works.',
    regionLead:
      'The harder half of every guide: clues that only hold in one part of one country. You are ' +
      'told which country the clue is from — your job is to place it inside it, from three regions ' +
      'of that same country.',
  },

  controls: {
    start: 'Start',
    stop: 'Stop',
    resetScore: 'Reset score',
    guess: 'Guess',
    guessLabel: 'What to guess',
    modeCountry: 'Countries',
    modeRegion: 'Regions',
    continent: 'Continent',
    everywhere: 'Everywhere',
    wholeGuide: 'Play the whole guide',
    wholeGuideHint: (extra: string) =>
      `+${extra} clues from the regional and spotlight chapters. Harder, and some carry a locator map.`,
    regionHint: (clues: string, countries: number) =>
      `${clues} clues across ${countries} countries. Name which region they belong to.`,
  },

  scores: {
    label: 'Score',
    right: 'Right',
    wrong: 'Wrong',
    accuracy: 'Accuracy',
    streak: 'Streak',
    bestStreak: 'Best streak',
  },

  idle: {
    eyebrow: 'Ready when you are',
    title: 'Press start for an endless run of clues.',
    keyAnswer: 'pick an answer',
    keyStartStop: 'start and stop the run',
    keyNext: 'next clue',
    scoreKept: 'Your score is kept until you reset it.',
  },

  paused: {
    eyebrow: 'Paused',
    score: (correct: number, wrong: number) => `${correct} right, ${wrong} wrong`,
    accuracy: (accuracy: number) => ` — ${accuracy}% accuracy`,
    hidden: 'The current clue is hidden while the game is stopped.',
    resume: 'Continue',
  },

  loading: {
    eyebrow: 'Loading',
    title: 'Picking a clue…',
  },

  problem: {
    eyebrow: 'Problem',
    retry: 'Try again',
  },

  clue: {
    askCountry: 'Which country is this clue from?',
    askRegion: (country: string) => `Which part of ${country} is this clue from?`,
    number: (n: number) => `Clue #${n}`,
    correctMark: 'correct',
    altCountry: (n: number) => `Clue ${n} — a photo that identifies one country`,
    altRegion: (n: number, country: string) => `Clue ${n} — a photo from one part of ${country}`,
    throttledEyebrow: 'Images are rate-limited',
    unavailableEyebrow: 'Clue image unavailable',
    throttledLead: 'The guide site is limiting how many images it hands out and asked us to wait',
    throttledTail:
      '. Clues whose image is already cached keep working; after that window new ones load again.',
    unavailable: 'The guide site would not hand this image over. The other clues are unaffected.',
    skip: 'Skip this clue',
    waitSeconds: (seconds: number) => `${seconds} seconds`,
    waitMinutes: (minutes: number) => `about ${minutes} minutes`,
  },

  reveal: {
    correct: (answer: string) => `Correct — ${answer}`,
    wrong: (answer: string) => `Not quite — it is ${answer}`,
    youPicked: (answer: string) => `You picked ${answer}.`,
    next: 'Next clue',
    guideLink: (country: string) => `Read the full ${country} guide`,
    streetViewLink: 'Open this spot in Street View',
    /** Shown when this clue's explanation has no translation yet. */
    untranslated: null as string | null,
  },

  footer: {
    creditLead: 'Clue texts and images come from the community-written GeoGuessr guides on',
    creditTail: '. This trainer only quizzes you on them — please support the original guides.',
    stats: (clues: string, countries: number, continents: number, regional: string) =>
      `${clues} clues · ${countries} countries · ${continents} continents · ${regional} placed in a region`,
  },

  /**
   * Keyed by the `error` code the backend sends, so a message stays in the
   * player's language instead of arriving pre-worded from the server.
   */
  errors: {
    network_error: 'The server is not reachable.',
    no_clues: 'No clues match the selected filters.',
    unknown_continent: 'No such continent.',
    unknown_session: 'This game session expired. Start a new one.',
    no_pending_question: 'Ask for a clue first.',
    no_answer_yet: 'Nothing has been answered in this session yet.',
    stale_answer: 'That answer belongs to an older clue.',
    invalid_option: 'That was not one of the offered answers.',
    refresh_disabled: 'Refreshing is disabled.',
    internal_error: 'Something went wrong on the server.',
    unknown: 'Unexpected error. Please try again.',
  },
}

export type Messages = typeof en

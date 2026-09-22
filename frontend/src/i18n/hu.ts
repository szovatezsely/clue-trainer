import type { Messages } from './en'

/**
 * The Hungarian copy.
 *
 * Country names, chapter headings and the clue explanations themselves are not
 * here - they belong to the dataset and are translated server side, so that the
 * megabyte of guide text never has to be shipped to the browser.
 */
export const hu: Messages = {
  htmlLang: 'hu',
  numberLocale: 'hu-HU',
  languageName: 'Magyar',

  header: {
    clues: 'Nyomok',
    countries: 'Országok',
    scraped: 'Begyűjtve',
    updated: 'Frissítve',
    language: 'Nyelv',
  },

  hero: {
    eyebrow: 'Végtelen metagyakorlás',
    titleLead: 'Vedd észre a nyomot,',
    titleTail: 'és nevezd meg',
    // The accent word follows "nevezd meg", so it carries the accusative
    // ending Hungarian needs there: "nevezd meg az országot".
    country: 'az országot',
    region: 'a régiót',
    countryLead:
      'Minden nyom egy valódi azonosítási részlet az országkalauzokból: egy útjelző tábla, egy ' +
      'terelőoszlop, egy villanyoszlop, egy rendszámtábla, egy tájkép. Válaszd ki, melyik országhoz ' +
      'tartozik, és olvasd el, miért árulkodó.',
    regionLead:
      'Minden kalauz nehezebbik fele: olyan nyomok, amelyek az adott országnak csak egy részére ' +
      'igazak. Az országot megkapod — a te dolgod, hogy ezen belül helyezd el, ugyanannak az ' +
      'országnak a három régiója közül választva.',
  },

  controls: {
    start: 'Indítás',
    stop: 'Leállítás',
    // Short on purpose: the long form pushed the controls onto a second row.
    resetScore: 'Nullázás',
    guess: 'Mit tippelsz',
    guessLabel: 'Mit kell kitalálni',
    modeCountry: 'Országok',
    modeRegion: 'Régiók',
    continent: 'Kontinens',
    everywhere: 'Mindenhol',
    wholeGuide: 'A teljes kalauz',
    wholeGuideHint: (extra: string) =>
      `+${extra} nyom a regionális és a kiemelt fejezetekből. Nehezebb, és néhányhoz térkép is tartozik.`,
    regionHint: (clues: string, countries: number) =>
      `${clues} nyom ${countries} országból. Nevezd meg, melyik régióhoz tartoznak.`,
  },

  scores: {
    label: 'Pontszám',
    right: 'Jó',
    wrong: 'Rossz',
    accuracy: 'Pontosság',
    streak: 'Sorozat',
    bestStreak: 'Legjobb sorozat',
  },

  idle: {
    eyebrow: 'Kezdheted, amikor akarod',
    title: 'Nyomd meg az indítást a nyomok végtelen sorához.',
    keyAnswer: 'válasz kiválasztása',
    keyStartStop: 'a játék indítása és leállítása',
    keyNext: 'következő nyom',
    scoreKept: 'A pontszámod megmarad, amíg le nem nullázod.',
  },

  paused: {
    eyebrow: 'Szüneteltetve',
    score: (correct: number, wrong: number) => `${correct} jó, ${wrong} rossz`,
    accuracy: (accuracy: number) => ` — ${accuracy}% pontosság`,
    hidden: 'A jelenlegi nyom rejtve marad, amíg a játék áll.',
    resume: 'Folytatás',
  },

  loading: {
    eyebrow: 'Betöltés',
    title: 'Nyom kiválasztása…',
  },

  problem: {
    eyebrow: 'Hiba',
    retry: 'Próbáld újra',
  },

  clue: {
    askCountry: 'Melyik országból való ez a nyom?',
    askRegion: (country: string) => `${country} melyik részéből való ez a nyom?`,
    number: (n: number) => `${n}. nyom`,
    correctMark: 'helyes',
    altCountry: (n: number) => `${n}. nyom — fénykép, amely egy országot azonosít`,
    altRegion: (n: number, country: string) => `${n}. nyom — fénykép ${country} egyik részéből`,
    throttledEyebrow: 'A képek letöltése korlátozva van',
    unavailableEyebrow: 'A nyom képe nem érhető el',
    throttledLead: 'A kalauz oldala korlátozza, hány képet ad ki, és arra kért, hogy várjunk',
    throttledTail:
      '. Azok a nyomok, amelyek képe már a gyorsítótárban van, továbbra is működnek; a várakozás ' +
      'után az újak is betöltődnek.',
    unavailable: 'A kalauz oldala nem adta ki ezt a képet. A többi nyomot ez nem érinti.',
    skip: 'Nyom kihagyása',
    waitSeconds: (seconds: number) => `${seconds} másodpercet`,
    waitMinutes: (minutes: number) => `körülbelül ${minutes} percet`,
  },

  reveal: {
    correct: (answer: string) => `Helyes — ${answer}`,
    wrong: (answer: string) => `Nem talált — a válasz: ${answer}`,
    youPicked: (answer: string) => `Te ezt választottad: ${answer}.`,
    next: 'Következő nyom',
    guideLink: (country: string) => `A teljes kalauz elolvasása: ${country}`,
    streetViewLink: 'A helyszín megnyitása a Street View-ban',
    untranslated: 'Ehhez a nyomhoz még nincs magyar magyarázat — az eredeti szöveg következik.',
  },

  footer: {
    creditLead:
      'A nyomok szövege és képei a közösség által írt GeoGuessr-kalauzokból származnak:',
    creditTail: '. Ez a gyakorló csak kérdez belőlük — kérjük, támogasd az eredeti kalauzokat.',
    stats: (clues: string, countries: number, continents: number, regional: string) =>
      `${clues} nyom · ${countries} ország · ${continents} kontinens · ebből ${regional} tartozik megnevezett régióhoz`,
  },

  errors: {
    network_error: 'A szerver nem érhető el.',
    no_clues: 'A kiválasztott szűrőkhöz nincs nyom.',
    unknown_continent: 'Nincs ilyen kontinens.',
    unknown_session: 'Ez a játékmenet lejárt. Indíts egy újat.',
    no_pending_question: 'Előbb kérj egy nyomot.',
    no_answer_yet: 'Ebben a menetben még nem válaszoltál semmire.',
    stale_answer: 'Ez a válasz egy korábbi nyomhoz tartozik.',
    invalid_option: 'Ez nem szerepelt a felkínált válaszok között.',
    refresh_disabled: 'A frissítés ki van kapcsolva.',
    internal_error: 'Valami elromlott a szerveren.',
    unknown: 'Váratlan hiba. Kérlek, próbáld újra.',
  },
}

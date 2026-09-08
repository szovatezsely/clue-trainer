import { marked } from 'marked'

marked.setOptions({ gfm: true, breaks: false })

/**
 * Repairs the bold markers in a guide paragraph.
 *
 * The guide editor frequently leaves the spaces on the wrong side of a bold
 * run — `has their own** unique plate **design`, `signs** **have 8 stripes` —
 * and markdown cannot pair those delimiters up, so the asterisks end up
 * visible in the text. Splitting on the delimiters and moving the padding out
 * of the run turns them back into what the author meant:
 *
 *     has their own** unique plate **design  ->  has their own **unique plate** design
 *     signs** **have 8 stripes               ->  signs have 8 stripes
 *
 * Correctly written markdown is returned untouched, and a paragraph with an
 * odd number of delimiters is left alone rather than guessed at.
 */
export function normalizeEmphasis(text: string): string {
  const parts = text.split('**')
  // An even number of parts means an odd number of delimiters: not our case.
  if (parts.length < 3 || parts.length % 2 === 0) return text

  let out = parts[0]
  for (let i = 1; i < parts.length; i += 2) {
    const inner = parts[i]
    const after = parts[i + 1]
    const trimmed = inner.trim()
    if (trimmed === '') {
      // An empty run is just stray punctuation; keep the words apart.
      out += ' ' + after
      continue
    }
    const lead = inner.slice(0, inner.length - inner.trimStart().length)
    const trail = inner.slice(inner.trimEnd().length)
    out += lead + '**' + trimmed + '**' + trail + after
  }
  // Collapse the double spaces the repair can leave behind, but never touch
  // the indentation of a line, which markdown uses for nested lists.
  return out.replace(/(\S)[ \t]{2,}/g, '$1 ')
}

/** Renders the guide explanation of a clue as HTML, links opening in a new tab. */
export function renderGuideMarkdown(paragraphs: string[]): string {
  const markdown = paragraphs.map(normalizeEmphasis).join('\n\n')
  const html = marked.parse(markdown, { async: false }) as string
  return html.replace(/<a /g, '<a target="_blank" rel="noopener noreferrer" ')
}

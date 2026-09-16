"""Assembles the Hungarian clue explanations the backend serves.

The translations are kept as plain text under `tools/hu-clues/`, one file per
batch, in a format that is deliberately not JSON:

    @ united-states-a9Az
    Az USA a **Speed Limit** feliratot használja a sebességkorlátozó tábláin.
    Az amerikai táblákon a számok általában alacsonyabbak, mint a kanadaiakon.

    @ norway-Qb12
    ...

One clue per `@` line, one paragraph per line, blank lines ignored. Nothing has
to be escaped - no quotes, no backslashes, no `\\u` sequences - which is what
makes a megabyte of guide prose editable by hand and readable in a diff. No
source paragraph starts with `@`, so the delimiter cannot collide with content.

Commands:

    python tools/hu-clues.py todo [--limit N] [--country CODE]
        Prints the next untranslated clues, in the same format, ready to be
        translated into a new batch file. Core "Identifying <country>" clues
        come first: they are what the default game plays.

    python tools/hu-clues.py build
        Validates every batch against the English source and writes
        backend/src/main/resources/seed/hu/clues.json.

    python tools/hu-clues.py status
        Coverage, overall and per country.
"""

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SEED = ROOT / "backend" / "src" / "main" / "resources" / "seed" / "clues.json"
BATCHES = ROOT / "tools" / "hu-clues"
OUT = ROOT / "backend" / "src" / "main" / "resources" / "seed" / "hu" / "clues.json"

MARKER = "@ "
# `todo` prints the country and chapter above each clue, and the batch files
# keep them - they are the only context a translator has. No guide paragraph
# starts with either marker, so neither can swallow real text.
COMMENT = "#"

# A markdown link's target is a URL, and a URL is not translatable text. The
# build checks that every one of them survived the translation unchanged.
LINK_TARGET = re.compile(r"\]\(([^)]+)\)")


def load_source():
    dataset = json.loads(SEED.read_text(encoding="utf-8"))
    countries = {c["code"]: c for c in dataset["countries"]}
    return dataset["clues"], countries


def parse_batches():
    """Reads every batch file into {clue id: [paragraphs]}, newest file wins."""
    texts: dict[str, list[str]] = {}
    duplicates: list[str] = []
    if not BATCHES.exists():
        return texts, duplicates
    for path in sorted(BATCHES.glob("*.txt")):
        current = None
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith(COMMENT):
                continue
            if line.startswith(MARKER):
                current = line[len(MARKER) :].strip()
                if current in texts:
                    duplicates.append(f"{current} (again in {path.name})")
                texts[current] = []
            elif line.strip():
                if current is None:
                    raise SystemExit(f"{path.name}: text before the first '{MARKER}' line")
                texts[current].append(line.rstrip())
    return texts, duplicates


def sort_key(clue):
    """Core clues first - the country game's default scope - then by country."""
    core = 0 if clue.get("section", "").lower().startswith("identifying") else 1
    return (core, clue["countryCode"], clue["id"])


def cmd_todo(args):
    clues, countries = load_source()
    done, _ = parse_batches()
    pending = [c for c in sorted(clues, key=sort_key) if c["id"] not in done]
    if args.country:
        pending = [c for c in pending if c["countryCode"] == args.country]

    out = sys.stdout
    written = chars = 0
    for clue in pending:
        size = sum(len(p) for p in clue["text"])
        # Clue lengths range from a line to a paragraph, so a batch is capped by
        # how much text it holds, not by how many clues - that keeps every batch
        # about the same amount of work.
        if written >= args.limit or (written and chars + size > args.chars):
            break
        country = countries.get(clue["countryCode"], {}).get("name", clue["countryCode"])
        out.write(f"# {country} / {clue.get('section', '')}\n")
        out.write(f"{MARKER}{clue['id']}\n")
        for paragraph in clue["text"]:
            out.write(paragraph + "\n")
        out.write("\n")
        written += 1
        chars += size
    left = len(pending) - written
    print(f"# --- {written} clues ({chars} chars) above, {left} still pending ---", file=sys.stderr)


def cmd_build(args):
    clues, _ = load_source()
    source = {c["id"]: c for c in clues}
    texts, duplicates = parse_batches()

    problems = []
    for clue_id in duplicates:
        problems.append(f"duplicate clue {clue_id}")

    translated = {}
    for clue_id, paragraphs in texts.items():
        clue = source.get(clue_id)
        if clue is None:
            problems.append(f"{clue_id}: no such clue in the dataset")
            continue
        english = clue["text"]
        if len(paragraphs) != len(english):
            problems.append(
                f"{clue_id}: {len(paragraphs)} paragraphs, the guide has {len(english)}"
            )
            continue
        for index, (hu, en) in enumerate(zip(paragraphs, english), start=1):
            want = LINK_TARGET.findall(en)
            got = LINK_TARGET.findall(hu)
            if want != got:
                problems.append(f"{clue_id} paragraph {index}: link targets changed")
        translated[clue_id] = paragraphs

    if problems:
        for problem in problems[:40]:
            print("  !!", problem, file=sys.stderr)
        if len(problems) > 40:
            print(f"  !! ... and {len(problems) - 40} more", file=sys.stderr)
        if not args.force:
            raise SystemExit(f"{len(problems)} problem(s); nothing written (use --force to ignore)")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps(dict(sorted(translated.items())), ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
    )
    size = OUT.stat().st_size
    print(f"wrote {OUT.relative_to(ROOT)}")
    print(f"  {len(translated)} of {len(clues)} clues ({100 * len(translated) // len(clues)}%), {size // 1024} KiB")


def cmd_status(args):
    clues, countries = load_source()
    done, _ = parse_batches()
    by_country: dict[str, list[int]] = {}
    core_total = core_done = 0
    for clue in clues:
        stats = by_country.setdefault(clue["countryCode"], [0, 0])
        stats[0] += 1
        is_core = clue.get("section", "").lower().startswith("identifying")
        core_total += is_core
        if clue["id"] in done:
            stats[1] += 1
            core_done += is_core

    total = len(clues)
    print(f"clues       {len(done)}/{total} ({100 * len(done) // total}%)")
    print(f"core clues  {core_done}/{core_total} ({100 * core_done // max(core_total, 1)}%)")
    if args.verbose:
        for code, (count, hit) in sorted(by_country.items(), key=lambda kv: kv[1][1] - kv[1][0]):
            if hit == count:
                continue
            name = countries.get(code, {}).get("name", code)
            print(f"  {name:<34} {hit:>4}/{count}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    todo = sub.add_parser("todo", help="print the next untranslated clues")
    todo.add_argument("--limit", type=int, default=200, help="most clues to print")
    todo.add_argument("--chars", type=int, default=14000, help="most source characters to print")
    todo.add_argument("--country", help="restrict to one ISO country code")
    todo.set_defaults(func=cmd_todo)

    build = sub.add_parser("build", help="validate and write the backend resource")
    build.add_argument("--force", action="store_true", help="write despite problems")
    build.set_defaults(func=cmd_build)

    status = sub.add_parser("status", help="how much is translated")
    status.add_argument("-v", "--verbose", action="store_true")
    status.set_defaults(func=cmd_status)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()

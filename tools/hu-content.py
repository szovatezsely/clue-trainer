"""Builds the Hungarian bundle of finite vocabularies for the backend.

Everything the game says that is *not* clue prose lives here: country names,
continents, the tag chips and the guide's chapter headings. It is generated
rather than hand-edited so that the "Identifying <country>" headings - 132 of
them, one per guide - stay in step with the country names above them.

Run it after changing anything below:

    python tools/hu-content.py

Output: backend/src/main/resources/seed/hu/content.json
"""

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SEED = ROOT / "backend" / "src" / "main" / "resources" / "seed" / "clues.json"
OUT = ROOT / "backend" / "src" / "main" / "resources" / "seed" / "hu" / "content.json"

# ISO code -> Hungarian name. Exonyms where Hungarian has one (Németország),
# the local name where it does not (Costa Rica).
COUNTRIES = {
    "AD": "Andorra",
    "AE": "Egyesült Arab Emírségek",
    "AL": "Albánia",
    "AQ": "Antarktisz",
    "AR": "Argentína",
    "AS": "Amerikai Szamoa",
    "AT": "Ausztria",
    "AU": "Ausztrália",
    "BD": "Banglades",
    "BE": "Belgium",
    "BG": "Bulgária",
    "BM": "Bermuda",
    "BO": "Bolívia",
    "BR": "Brazília",
    "BT": "Bhután",
    "BW": "Botswana",
    "BY": "Fehéroroszország",
    "CA": "Kanada",
    "CC": "Kókusz-szigetek",
    "CH": "Svájc",
    "CL": "Chile",
    "CN": "Kína",
    "CO": "Kolumbia",
    "CR": "Costa Rica",
    "CW": "Curaçao",
    "CX": "Karácsony-sziget",
    "CY": "Ciprus",
    "CZ": "Csehország",
    "DE": "Németország",
    "DK": "Dánia",
    "DO": "Dominikai Köztársaság",
    "EC": "Ecuador",
    "EE": "Észtország",
    "EG": "Egyiptom",
    "ES": "Spanyolország",
    "FI": "Finnország",
    "FK": "Falkland-szigetek",
    "FO": "Feröer szigetek",
    "FR": "Franciaország",
    "GB": "Egyesült Királyság",
    "GH": "Ghána",
    "GI": "Gibraltár",
    "GL": "Grönland",
    "GR": "Görögország",
    "GS": "Déli-Georgia és Déli-Sandwich-szigetek",
    "GT": "Guatemala",
    "GU": "Guam",
    "HK": "Hongkong",
    "HR": "Horvátország",
    "HU": "Magyarország",
    "ID": "Indonézia",
    "IE": "Írország",
    "IL": "Izrael és Ciszjordánia",
    "IM": "Man-sziget",
    "IN": "India",
    "IO": "Brit Indiai-óceáni Terület",
    "IQ": "Irak",
    "IS": "Izland",
    "IT": "Olaszország",
    "JE": "Jersey",
    "JO": "Jordánia",
    "JP": "Japán",
    "KE": "Kenya",
    "KG": "Kirgizisztán",
    "KH": "Kambodzsa",
    "KR": "Dél-Korea",
    "KZ": "Kazahsztán",
    "LA": "Laosz",
    "LB": "Libanon",
    "LI": "Liechtenstein",
    "LK": "Srí Lanka",
    "LS": "Lesotho",
    "LT": "Litvánia",
    "LU": "Luxemburg",
    "LV": "Lettország",
    "MC": "Monaco",
    "ME": "Montenegró",
    "MG": "Madagaszkár",
    "MK": "Észak-Macedónia",
    "ML": "Mali",
    "MN": "Mongólia",
    "MO": "Makaó",
    "MP": "Északi-Mariana-szigetek",
    "MQ": "Martinique",
    "MT": "Málta",
    "MX": "Mexikó",
    "MY": "Malajzia",
    "NA": "Namíbia",
    "NG": "Nigéria",
    "NL": "Hollandia",
    "NO": "Norvégia",
    "NP": "Nepál",
    "NZ": "Új-Zéland",
    "OM": "Omán",
    "PA": "Panama",
    "PE": "Peru",
    "PH": "Fülöp-szigetek",
    "PK": "Pakisztán",
    "PL": "Lengyelország",
    "PM": "Saint-Pierre és Miquelon",
    "PN": "Pitcairn-szigetek",
    "PR": "Puerto Rico",
    "PT": "Portugália",
    "PT-AZ": "Azori-szigetek",
    "PT-MA": "Madeira",
    "QA": "Katar",
    "RE": "Réunion",
    "RO": "Románia",
    "RS": "Szerbia",
    "RU": "Oroszország",
    "RW": "Ruanda",
    "SE": "Svédország",
    "SG": "Szingapúr",
    "SI": "Szlovénia",
    "SJ": "Svalbard",
    "SK": "Szlovákia",
    "SM": "San Marino",
    "SN": "Szenegál",
    "ST": "São Tomé és Príncipe",
    "SZ": "Eswatini",
    "TH": "Thaiföld",
    "TN": "Tunézia",
    "TR": "Törökország",
    "TW": "Tajvan",
    "TZ": "Tanzánia",
    "UA": "Ukrajna",
    "UG": "Uganda",
    "UM-MQ": "Az USA lakatlan külbirtokai",
    "US": "Amerikai Egyesült Államok",
    "US-AK": "Alaszka",
    "US-HI": "Hawaii",
    "UY": "Uruguay",
    "VI": "Amerikai Virgin-szigetek",
    "VN": "Vietnám",
    "VU": "Vanuatu",
    "ZA": "Dél-Afrika",
}

CONTINENTS = {
    "Africa": "Afrika",
    "Antarctica": "Antarktisz",
    "Asia": "Ázsia",
    "Europe": "Európa",
    "North America": "Észak-Amerika",
    "Oceania": "Óceánia",
    "South America": "Dél-Amerika",
}

TAGS = {
    "architecture": "építészet",
    "bollard": "terelőoszlop",
    "chevron/sign": "tábla/terelőjel",
    "coverage": "lefedettség",
    "guardrail": "szalagkorlát",
    "important": "fontos",
    "landscape": "tájkép",
    "language": "nyelv",
    "license plates": "rendszámtábla",
    "moving info": "haladási infó",
    "pole": "oszlop",
    "roadline": "útburkolati jel",
    "vegetation": "növényzet",
}

# "Identifying <X>" is the core chapter of every guide. The heading is built
# from the country name, so a new guide needs nothing but its entry above; the
# few the guide titles differently are spelled out here.
IDENTIFYING_OVERRIDES = {
    "Identifying the UAE": "Egyesült Arab Emírségek",
    "Identifying the United States": "Amerikai Egyesült Államok",
    "Identifying Cocos (Keeling) Islands": "Kókusz-szigetek",
    "Identifying São Tomé & Príncipe": "São Tomé és Príncipe",
    "Identifying United States Virgin Islands": "Amerikai Virgin-szigetek",
    "Identifying Israel & the West Bank": "Izrael és Ciszjordánia",
    "Identifying the islands": "a szigetek",
    "Identifying Réunion": "Réunion",
    "Identifying Curaçao": "Curaçao",
}

SECTIONS = {
    "Boat coverage": "Hajós lefedettség",
    "City-specific clues": "Városspecifikus nyomok",
    "City-specific obituaries": "Városspecifikus gyászjelentések",
    "Gen 3 Trekkers": "3. generációs trekkerek",
    "Island specific clues": "Szigetspecifikus nyomok",
    "Land coverage": "Szárazföldi lefedettség",
    "National Parks": "Nemzeti parkok",
    "Pinpointable coverage": "Behatárolható lefedettség",
    "Region-specific clues": "Régióspecifikus nyomok",
    "Regional  clues": "Regionális nyomok",
    "Regional and arrondissement-specific clues": "Regionális és kerületspecifikus nyomok",
    "Regional and canton-specific clues": "Regionális és kantonspecifikus nyomok",
    "Regional and county-specific clues": "Regionális és megyespecifikus nyomok",
    "Regional and department-specific clues": "Regionális és département-specifikus nyomok",
    "Regional and district-specific clues": "Regionális és körzetspecifikus nyomok",
    "Regional and division-specific clues": "Regionális és divízióspecifikus nyomok",
    "Regional and emirate-specific clues": "Regionális és emírségspecifikus nyomok",
    "Regional and governorate-specific clues": "Regionális és kormányzóságspecifikus nyomok",
    "Regional and municipality-specific clues": "Regionális és községspecifikus nyomok",
    "Regional and parish-specific clues": "Regionális és parókiaspecifikus nyomok",
    "Regional and prefecture-specific clues": "Regionális és prefektúraspecifikus nyomok",
    "Regional and province-specific clues": "Regionális és tartományspecifikus nyomok",
    "Regional and state-specific clues": "Regionális és államspecifikus nyomok",
    "Regional and voivodeship-specific clues": "Regionális és vajdaságspecifikus nyomok",
    "Regional clues": "Regionális nyomok",
    "Regional/city-specific clues": "Regionális/városspecifikus nyomok",
    "Regional/county-specific clues": "Regionális/megyespecifikus nyomok",
    "Regional/district-specific clues": "Regionális/körzetspecifikus nyomok",
    "Regional/province-specific clues": "Regionális/tartományspecifikus nyomok",
    "Spotlight": "Kiemelt helyek",
    "Step 2.1 – Cairo": "2.1. lépés – Kairó",
    "Step 2.1 – Pinpointable tripods": "2.1. lépés – Behatárolható állványok",
    "Step 2.2 – Alexandria": "2.2. lépés – Alexandria",
    "Step 2.2 – Non-pinpointable tripods": "2.2. lépés – Nem behatárolható állványok",
    "Town specific clues": "Településspecifikus nyomok",
    "Trekker and tripod tips": "Trekker- és állványtippek",
    "Trekker tips": "Trekkertippek",
    # A city used as a chapter of its own; the name itself needs no translating,
    # but listing it keeps the coverage check below honest.
    "Kampala": "Kampala",
}

# Only the generic subsection headings are translated. The rest are proper
# nouns - road numbers (M-072), towns (Vang Vieng), trails (Namche Bazar -
# Gokyo) - and are served exactly as the guide wrote them.
SUBSECTIONS = {
    "Agriculture": "Mezőgazdaság",
    "Agriculture and vegetation": "Mezőgazdaság és növényzet",
    "Architecture": "Építészet",
    "Balearic Islands": "Baleár-szigetek",
    "Bandiagara Escarpment": "Bandiagarai sziklafal",
    "Bishkek and surroundings": "Biskek és környéke",
    "Bollards": "Terelőoszlopok",
    "Canary Islands": "Kanári-szigetek",
    "Car Meta": "Autómeta",
    "Car and Coverage Metas": "Autó- és lefedettségi meták",
    "Car meta": "Autómeta",
    "Car metas": "Autómeták",
    "Car mirror metas": "Visszapillantó-meták",
    "Ceuta and Melilla": "Ceuta és Melilla",
    "Cities": "Városok",
    "Coverage Metas": "Lefedettségi meták",
    "Distinct Roads": "Jellegzetes utak",
    "Divided highways": "Osztott pályás utak",
    "Djenné Mosque": "Djennéi mecset",
    "Djingareyber Mosque (Timbuktu)": "Djingareyber-mecset (Timbuktu)",
    "Flags": "Zászlók",
    "Gao Mosque": "Gaói mecset",
    "Generation 3 Coverage": "3. generációs lefedettség",
    "Gombe National Park": "Gombe Nemzeti Park",
    "Infrastructure": "Infrastruktúra",
    "Infrastructure spotlight": "Infrastruktúra – kiemelve",
    "Kibale National Park": "Kibale Nemzeti Park",
    "Kidepo National Park": "Kidepo Nemzeti Park",
    "Lake Mburo National Park": "Mburo-tavi Nemzeti Park",
    "Lamps": "Lámpák",
    "Landscape": "Tájkép",
    "Landscape and vegetation": "Tájkép és növényzet",
    "Landscape spotlight": "Tájkép – kiemelve",
    "Landscapes": "Tájak",
    "Landscapes & Vegetation": "Tájak és növényzet",
    "Landscapes and vegetation": "Tájak és növényzet",
    "Languages": "Nyelvek",
    "Mainland Spain - Architecture": "Spanyol anyaország – Építészet",
    "Mainland Spain - Infrastructure": "Spanyol anyaország – Infrastruktúra",
    "Mainland Spain - Landscape and vegetation": "Spanyol anyaország – Tájkép és növényzet",
    "Mainland Spain - Miscellaneous": "Spanyol anyaország – Vegyes",
    "Major regionguessing tips": "Fontosabb régiótippek",
    "Minor regionguessing tips": "Kisebb régiótippek",
    "Miscellaneous": "Vegyes",
    "Miscellaneous misplaced tripods": "Vegyes, rossz helyre került állványok",
    "Mopti Mosque": "Moptii mecset",
    "Mount Elgon National Park": "Elgon-hegyi Nemzeti Park",
    "Mount Kilimanjaro": "Kilimandzsáró",
    "Murchison Falls National Park": "Murchison-vízesés Nemzeti Park",
    "National Parks": "Nemzeti parkok",
    "Nature parks": "Természetvédelmi parkok",
    "Niono Mosque": "Nionói mecset",
    "Non-contiguous States and Territories": "Nem összefüggő államok és területek",
    "Non-contiguous regions and territories": "Nem összefüggő régiók és területek",
    "Other Infrastructure": "Egyéb infrastruktúra",
    "Other basic region-guessing tips": "Egyéb alapvető régiótippek",
    "Other recognizable towns": "Egyéb felismerhető települések",
    "Other smaller roads": "Egyéb kisebb utak",
    "Poles": "Oszlopok",
    "Queen Elizabeth National Park": "Queen Elizabeth Nemzeti Park",
    "Rare Google cars": "Ritka Google-autók",
    "Recognisable roads": "Felismerhető utak",
    "Regional languages": "Regionális nyelvek",
    "Remote towns and trekkers": "Távoli települések és trekkerek",
    "Road Features": "Útjellemzők",
    "Roads": "Utak",
    "Sankoré Mosque (Timbuktu)": "Sankoré-mecset (Timbuktu)",
    "Semuliki National Park": "Semuliki Nemzeti Park",
    "Settlements": "Települések",
    "Sidi Yahiya Mosque (Timbuktu)": "Sidi Yahiya-mecset (Timbuktu)",
    "Signage": "Táblák",
    "Special Google cars": "Különleges Google-autók",
    "Step 1.1 - Canada specific": "1.1. lépés – Kanadára jellemző",
    "Step 1.1 - Country Specific": "1.1. lépés – Országra jellemző",
    "Step 1.1 - Different from the UK": "1.1. lépés – Eltér az Egyesült Királyságtól",
    "Step 1.1 - Similar to Czechia": "1.1. lépés – Hasonló Csehországhoz",
    "Step 1.1 - Similar to Slovakia": "1.1. lépés – Hasonló Szlovákiához",
    "Step 1.1 - US-specific": "1.1. lépés – Az USA-ra jellemző",
    "Step 1.2 - Different than Czechia": "1.2. lépés – Eltér Csehországtól",
    "Step 1.2 - Different than Slovakia": "1.2. lépés – Eltér Szlovákiától",
    "Step 1.2 - Similar to Russia": "1.2. lépés – Hasonló Oroszországhoz",
    "Step 1.2 - Similar to the UK": "1.2. lépés – Hasonló az Egyesült Királysághoz",
    "Step 1.2 - US & Canada": "1.2. lépés – USA és Kanada",
    "Step 1.3 - “Vibes“": "1.3. lépés – „Hangulat”",
    "Step 2.1 - Major land coverage": "2.1. lépés – Fő szárazföldi lefedettség",
    "Step 2.2 - Major boat coverage": "2.2. lépés – Fő hajós lefedettség",
    "Step 3.1 - Minor land coverage": "3.1. lépés – Kisebb szárazföldi lefedettség",
    "Step 3.2 - Minor boat coverage": "3.2. lépés – Kisebb hajós lefedettség",
    "Store Chains": "Üzletláncok",
    "Towns": "Települések",
    "Towns & Cities": "Települések és városok",
    "Towns and cities with the southern mirror": "Települések és városok a déli tükörrel",
    "Trail to Everest": "Az Everest-ösvény",
    "Trekker Coverage": "Trekkeres lefedettség",
    "Trekkers": "Trekkerek",
    "Trekkers and unique cars": "Trekkerek és egyedi autók",
    "Vegetation": "Növényzet",
    "Vegetation & Landscape": "Növényzet és tájkép",
    "Åland Islands": "Åland-szigetek",
    "”Bamako” Mosque": "„Bamakói” mecset",
}


def main() -> None:
    dataset = json.loads(SEED.read_text(encoding="utf-8"))
    by_name = {c["name"]: c["code"] for c in dataset["countries"]}

    sections = dict(SECTIONS)
    missing_guides = []
    for section in {c.get("section", "") for c in dataset["clues"]}:
        if not section.startswith("Identifying"):
            continue
        if section in IDENTIFYING_OVERRIDES:
            name = IDENTIFYING_OVERRIDES[section]
        else:
            english = section[len("Identifying ") :].removeprefix("the ")
            code = by_name.get(english)
            name = COUNTRIES.get(code) if code else None
            if name is None:
                missing_guides.append(section)
                continue
        sections[section] = f"{name} azonosítása"

    bundle = {
        "countries": dict(sorted(COUNTRIES.items())),
        "continents": dict(sorted(CONTINENTS.items())),
        "tags": dict(sorted(TAGS.items())),
        "sections": dict(sorted(sections.items())),
        "subsections": dict(sorted(SUBSECTIONS.items())),
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps(bundle, ensure_ascii=False, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )

    # Anything unclaimed is served in English, so say so rather than hide it.
    untranslated_countries = [c["code"] for c in dataset["countries"] if c["code"] not in COUNTRIES]
    used_sections = {c.get("section", "") for c in dataset["clues"]} - {""}
    used_subsections = {c.get("subsection", "") for c in dataset["clues"]} - {""}
    print(f"wrote {OUT.relative_to(ROOT)}")
    print(f"  countries    {len(COUNTRIES)}/{len(dataset['countries'])}")
    print(f"  continents   {len(CONTINENTS)}")
    print(f"  tags         {len(TAGS)}")
    print(f"  sections     {len(sections.keys() & used_sections)} of {len(used_sections)} used")
    print(f"  subsections  {len(SUBSECTIONS.keys() & used_subsections)} of {len(used_subsections)} used")
    if untranslated_countries:
        print(f"  !! no Hungarian name for: {', '.join(untranslated_countries)}")
    if missing_guides:
        print(f"  !! no Hungarian heading for: {', '.join(missing_guides)}")


if __name__ == "__main__":
    main()

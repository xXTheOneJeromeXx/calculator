#!/usr/bin/env python3
"""Builds the encrypted content packs in app/src/main/assets/r/ from content/sources/.

Each translation book becomes zlib-compressed JSON, sealed with AES-256-GCM under a
build-time key (obfuscation only: the key ships in the app) and stored under an opaque
file name. Book names, reader UI words, the font and the license texts also live
in packs, so none of them are readable in the APK.

Usage: python3 tools/build_packs.py          (needs the `cryptography` package)
"""
import hashlib
import hmac
import json
import os
import re
import secrets
import sys
import zipfile
import zlib

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "content", "sources")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "r")
KEY_FILE = os.path.join(ROOT, "content", "pack.key")
KOTLIN_KEY = os.path.join(ROOT, "app", "src", "main", "java", "com", "tbce", "calc", "reader", "K.kt")

# id, abbreviation shown in the app, eBible.org file id, name shown in the app (66 books only)
TRANSLATIONS = [
    ("bsb", "BSB", "engbsb", "Berean Standard Bible"),
    ("web", "WEB", "eng-web", "World English Bible"),
    ("kjv", "KJV", "eng-kjv", "King James Version"),
]

BOOKS = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO "
         "ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL "
         "MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB "
         "JAS 1PE 2PE 1JN 2JN 3JN JUD REV").split()

# Extra abbreviations accepted by the reference jump, on top of the names in the sources.
ALIASES = {
    "GEN": "gen ge gn", "EXO": "exod ex exo", "LEV": "lev le lv", "NUM": "num nu nm",
    "DEU": "deut de dt", "JOS": "josh jos", "JDG": "judg jdg jg", "RUT": "ru rth",
    "1SA": "1sam 1sa 1 sam 1 samuel", "2SA": "2sam 2sa 2 sam 2 samuel",
    "1KI": "1kgs 1ki 1 kings 1 kgs", "2KI": "2kgs 2ki 2 kings 2 kgs",
    "1CH": "1chr 1ch 1 chron 1 chronicles", "2CH": "2chr 2ch 2 chron 2 chronicles",
    "EZR": "ezr", "NEH": "neh ne", "EST": "esth es", "JOB": "jb", "PSA": "ps psa psalm pss",
    "PRO": "prov pr prv", "ECC": "eccl ecc qoh", "SNG": "song sos canticles",
    "ISA": "isa is", "JER": "jer je", "LAM": "lam la", "EZK": "ezek eze ezk", "DAN": "dan da dn",
    "HOS": "hos ho", "JOL": "joel jl", "AMO": "amos am", "OBA": "obad ob", "JON": "jon jnh",
    "MIC": "mic mi", "NAM": "nah na", "HAB": "hab hb", "ZEP": "zeph zep zp", "HAG": "hag hg",
    "ZEC": "zech zec zc", "MAL": "mal ml", "MAT": "matt mt", "MRK": "mark mk mrk mr",
    "LUK": "luke lk luk", "JHN": "john jn jhn", "ACT": "acts ac", "ROM": "rom ro rm",
    "1CO": "1cor 1co 1 cor", "2CO": "2cor 2co 2 cor", "GAL": "gal ga", "EPH": "eph",
    "PHP": "phil php pp", "COL": "col", "1TH": "1thess 1th 1 thess", "2TH": "2thess 2th 2 thess",
    "1TI": "1tim 1ti 1 tim", "2TI": "2tim 2ti 2 tim", "TIT": "tit ti", "PHM": "philem phm pm",
    "HEB": "heb", "JAS": "jas jm", "1PE": "1pet 1pe 1pt 1 pet", "2PE": "2pet 2pe 2pt 2 pet",
    "1JN": "1john 1jn 1jo 1 jn", "2JN": "2john 2jn 2jo 2 jn", "3JN": "3john 3jn 3jo 3 jn",
    "JUD": "jude jud jd", "REV": "rev re rv revelations",
}

# Reader words that would give the app away if left in plain resources.
UI = {
    "book": "Book", "chapter": "Chapter", "ot": "Old Testament", "nt": "New Testament",
    "translation": "Translation", "about": "About these texts", "guide": "How this app works",
    "search_hint": "Search, or type a reference like John 3:16",
    "go_to": "Go to", "no_results": "No matches", "results": "matches",
    # Setup and code screens: kept out of the APK so they don't describe the hidden gesture.
    "code_new": "Choose a code", "code_again": "Enter the code again", "code_change": "Choose a new code",
    "code_min": "Use at least {min} digits. {good} or more is better.",
    "code_how": "To open later: hold = until the display shows 0, type your code, then hold = again.",
    "code_lost": "A forgotten code can't be recovered, and nothing inside can be opened without it.",
    "code_watch": "Anyone watching can see the digits on the calculator display as you type.",
    "code_mismatch": "Those didn't match. Start again.",
    "code_weak": "Easy to guess", "code_short": "Too short", "code_ok": "OK. Longer is better.", "code_good": "Good length",
    # Disguise reminders: name the hidden gesture, so they live in the pack, not the APK.
    "entry_calc": "The app now looks like a calculator. To open the reader: hold =, type your code, then hold = again.",
    "entry_notes": "The app now looks like Notes. To open the reader: tap + to start a note, hold Done, type your code, then hold Done again. A normal tap of Done just saves a note.",
    "entry_clock": "The app now looks like a Clock. To open the reader: hold Start until the timer clears, type your code on the pad, then hold Start again. A normal tap of Start just runs a countdown.",
    "entry_sudoku": "The app now looks like Sudoku. To open the reader: hold Notes, then type your code on the number pad (Erase is 0, Undo removes a digit), then hold Notes again. The board goes back to how it was afterwards. A normal tap of Notes just turns pencil marks on or off.",
}

PARA = {
    "p": "p", "pmo": "p", "pm": "p", "nb": "m", "m": "m", "pmc": "m", "pmr": "m", "cls": "m",
    "pi": "pi", "pi1": "pi", "pi2": "pi", "mi": "pi",
    "q": "q1", "q1": "q1", "q2": "q2", "q3": "q3", "qm": "q1", "qm1": "q1", "qd": "q1",
    "qr": "qr", "qc": "pc", "pc": "pc", "li": "l1", "li1": "l1", "li2": "l2",
}
HEADING = {"ms": 0, "ms1": 0, "ms2": 0, "s": 1, "s1": 1, "s2": 2, "s3": 2, "sp": 2, "qa": 2}
SKIP = {"id", "ide", "h", "toc1", "toc2", "toc3", "mt", "mt1", "mt2", "mt3", "cl", "rem", "usfm",
        "imt", "imt1", "is", "is1", "ip", "ili", "ie", "cp", "ca", "va", "sts", "r", "mr", "sr"}


def clean(s):
    s = re.sub(r"\\(f|fe|x)\s.*?\\\1\*", "", s, flags=re.S)
    s = re.sub(r"\\\+?w\s+([^\\|]*?)(\|[^\\]*?)?\\\+?w\*", r"\1", s)
    s = re.sub(r"\\\+?[a-z0-9]+\*", "", s)
    s = re.sub(r"\\\+?[a-z0-9]+\s?", "", s)
    s = s.replace("\u00b6", "")  # KJV paragraph signs
    return re.sub(r"\s+", " ", s).strip()


def parse_book(text):
    names = {}
    chapters = []
    blocks = None
    para = None
    verse = 0

    def finish():
        nonlocal para
        if para is not None and para[2]:
            blocks.append(para)
        para = None

    def content(t):
        nonlocal para, verse
        if blocks is None:
            return
        if para is None:
            para = ["p", "p", []]
        parts = re.split(r"\\v\s+(\S+)\s?", t)
        pre = clean(parts[0])
        if pre:
            para[2].append([-verse, pre])
        for i in range(1, len(parts), 2):
            verse = int(re.match(r"\d+", parts[i]).group())
            para[2].append([verse, clean(parts[i + 1])])

    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        m = re.match(r"\\(\S+)\s?(.*)", line)
        if not m:
            content(line)
            continue
        marker, rest = m.group(1), m.group(2)
        if marker in ("h", "toc1", "toc2", "toc3"):
            names[marker] = rest.strip()
        if marker == "c":
            if blocks is not None:
                finish()
            blocks = []
            chapters.append(blocks)
            verse = 0
        elif marker in SKIP:
            continue
        elif marker in HEADING:
            finish()
            t = clean(rest)
            if t and blocks is not None:
                blocks.append(["h", HEADING[marker], t])
        elif marker == "d":
            finish()
            if "\\v" in rest:
                para = ["p", "d", []]
                content(rest)
            elif blocks is not None and clean(rest):
                blocks.append(["d", clean(rest)])
        elif marker == "b":
            finish()
            if blocks is not None:
                blocks.append(["b"])
        elif marker in PARA:
            finish()
            para = ["p", PARA[marker], []]
            content(rest)
        else:
            content(line)
    if blocks is not None:
        finish()
    return names, chapters


def load_key():
    if os.path.exists(KEY_FILE):
        return bytes.fromhex(open(KEY_FILE).read().strip())
    key = secrets.token_bytes(32)
    with open(KEY_FILE, "w") as f:
        f.write(key.hex() + "\n")
    return key


def write_kotlin_key(key):
    mask = hashlib.sha256(b"m" + key).digest()
    a = ", ".join(str(b - 256 if b > 127 else b) for b in bytes(x ^ y for x, y in zip(key, mask)))
    b = ", ".join(str(x - 256 if x > 127 else x) for x in mask)
    os.makedirs(os.path.dirname(KOTLIN_KEY), exist_ok=True)
    with open(KOTLIN_KEY, "w") as f:
        f.write(f"""package com.tbce.calc.reader

// Generated by tools/build_packs.py. Do not edit.
internal object K {{
    private val a = byteArrayOf({a})
    private val b = byteArrayOf({b})
    fun get(): ByteArray = ByteArray(32) {{ (a[it].toInt() xor b[it].toInt()).toByte() }}
}}
""")


def pack_name(key, logical):
    return hmac.new(key, logical.encode(), hashlib.sha256).hexdigest()[:16]


def seal(key, logical, data):
    name = pack_name(key, logical)
    # Deterministic nonce (hash of name and content), so rebuilding the same sources gives the same APK.
    nonce = hmac.new(key, b"n" + name.encode() + data, hashlib.sha256).digest()[:12]
    blob = nonce + AESGCM(key).encrypt(nonce, zlib.compress(data, 9), name.encode())
    with open(os.path.join(OUT, name), "wb") as f:
        f.write(blob)
    return len(blob)


def jdump(o):
    return json.dumps(o, ensure_ascii=False, separators=(",", ":")).encode()


def copr_text(zf):
    html = zf.read("copr.htm").decode("utf-8", "replace")
    body = re.sub(r"(?s)<(script|style).*?</\1>", "", html)
    body = re.sub(r"<br\s*/?>|</p>|</h\d>|</div>", "\n", body)
    body = re.sub(r"<[^>]+>", "", body)
    body = body.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&nbsp;", " ")
    lines = [l.strip() for l in body.splitlines()]
    return "\n".join(l for l in lines if l and l not in ("^", "<", ">"))


def main():
    key = load_key()
    write_kotlin_key(key)
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        os.remove(os.path.join(OUT, f))

    total = 0
    aliases = {b: set() for b in BOOKS}
    about = []
    translations = []
    lic = []
    os.makedirs(os.path.join(ROOT, "licenses"), exist_ok=True)
    for tid, abbr, src, long_name in TRANSLATIONS:
        zf = zipfile.ZipFile(os.path.join(SRC, f"{src}_usfm.zip"))
        files = {re.match(r"\d+-(\w{3})", n).group(1): n for n in zf.namelist() if n.endswith(".usfm")}
        books_meta = []
        verses = 0
        for code in BOOKS:
            names, chapters = parse_book(zf.read(files[code]).decode("utf-8-sig"))
            name = names.get("toc2") or names.get("h")
            for n in (names.get("toc1"), names.get("toc2"), names.get("h"), names.get("toc3")):
                if n:
                    aliases[code].add(n.lower())
            books_meta.append([code, name, len(chapters)])
            verses += sum(1 for ch in chapters for bl in ch if bl[0] == "p" for s in bl[2] if s[0] > 0)
            total += seal(key, f"{tid}/{code}", jdump({"n": name, "c": chapters}))
        translations.append({"id": tid, "abbr": abbr, "name": long_name, "books": books_meta})
        about.append(f"{abbr}: {long_name}\nSource: https://ebible.org/Scriptures/{src}_usfm.zip\n\n{copr_text(zf)}")
        lic.append({"name": long_name, "abbr": abbr, "license": "Public Domain", "source": f"https://ebible.org/Scriptures/{src}_usfm.zip",
                    "sha256": hashlib.sha256(open(os.path.join(SRC, f"{src}_usfm.zip"), "rb").read()).hexdigest(), "notice": f"licenses/{abbr}.txt"})
        with open(os.path.join(ROOT, "licenses", f"{abbr}.txt"), "w") as f:
            f.write(copr_text(zf) + "\n")
        print(f"{abbr}: {verses} verses, {sum(b[2] for b in books_meta)} chapters")

    for code, extra in ALIASES.items():
        # Aliases are space separated, except multi-word ones that start with a digit (e.g. "1 sam").
        tokens = extra.split(" ")
        i = 0
        while i < len(tokens):
            if tokens[i].isdigit() and i + 1 < len(tokens):
                aliases[code].add(tokens[i] + " " + tokens[i + 1]); i += 2
            else:
                aliases[code].add(tokens[i]); i += 1
    for code in ("SNG",):
        aliases[code].update({"song of songs", "song of solomon"})
    aliases = {c: sorted(a for a in v if a) for c, v in aliases.items()}

    ofl = open(os.path.join(SRC, "font", "OFL.txt")).read()
    about.append("Typeface: Literata, from https://github.com/google/fonts (ofl/literata)\n\n" + ofl)

    lic.append({"name": "Literata", "license": "SIL Open Font License 1.1", "source": "https://github.com/google/fonts/tree/main/ofl/literata",
                "sha256": hashlib.sha256(open(os.path.join(SRC, "font", "Literata.ttf"), "rb").read()).hexdigest(), "notice": "licenses/Literata-OFL.txt"})
    with open(os.path.join(ROOT, "licenses", "Literata-OFL.txt"), "w") as f:
        f.write(ofl)
    with open(os.path.join(ROOT, "licenses", "licenses.json"), "w") as f:
        json.dump(lic, f, indent=2)
        f.write("\n")

    total += seal(key, "meta", jdump({"translations": translations, "aliases": aliases, "ui": UI}))
    total += seal(key, "about", "\n\n----------\n\n".join(about).encode())
    total += seal(key, "guide", open(os.path.join(ROOT, "docs", "USER_GUIDE.md"), "rb").read())
    total += seal(key, "font", open(os.path.join(SRC, "font", "Literata.ttf"), "rb").read())
    print(f"{len(os.listdir(OUT))} packs, {total / 1e6:.2f} MB")


if __name__ == "__main__":
    sys.exit(main())

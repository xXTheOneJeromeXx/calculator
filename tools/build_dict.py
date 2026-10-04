#!/usr/bin/env python3
"""Builds the Dictionary disguise's word data in app/src/main/assets/w/ from WordNet 3.0.

Source: content/sources/wordnet/WordNet-3.0.tar.gz (Princeton; licence notice kept in
licenses/WordNet.txt and shown in the dictionary's About screen). This is plain decoy
content, not encrypted: an ordinary English dictionary.

Output, one file per first letter (a..z, and 0 for anything else), sorted by lowercase key:
    key \\t display \\t sense \\x1e sense ...
    sense = pos \\x1f gloss \\x1f synonyms \\x1f antonyms \\x1f related   (each list "a, b")
where pos is n, v, a or r, the gloss is WordNet's (definition; "examples"), and related are
words from WordNet's "similar to" and "see also" links, for the thesaurus. Plus the
irregular forms (file 1): inflected \\t pos \\t base per line, for looking up words like "ran",
and the WordNet licence (file 2) for the About screen.
"""
import os
import tarfile
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "content", "sources", "wordnet", "WordNet-3.0.tar.gz")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "w")
POS = [("noun", "n"), ("verb", "v"), ("adj", "a"), ("adv", "r")]
PTR_POS = {"n": "n", "v": "v", "a": "a", "s": "a", "r": "r"}
MAX_SYN = 6
MAX_REL = 12


def word(w):
    # Adjective words may carry a syntactic marker like "(a)" or "(p)".
    if w.endswith(")") and "(" in w:
        w = w[: w.index("(")]
    return w.replace("_", " ")


def main():
    tar = tarfile.open(SRC)

    def read(name):
        return tar.extractfile("WordNet-3.0/dict/" + name).read().decode("latin-1").splitlines()

    synsets = {}  # (p, offset) -> (gloss, [words])
    antonyms = defaultdict(list)  # (p, offset) -> [(source word no., (p, offset), target word no.)]
    related = defaultdict(list)  # (p, offset) -> [(p, offset)] via "similar to" (&) and "see also" (^)
    for long, p in POS:
        for line in read("data." + long):
            if line.startswith("  "):
                continue
            head, _, gloss = line.partition(" | ")
            f = head.split()
            n = int(f[3], 16)
            words = [word(f[4 + 2 * i]) for i in range(n)]
            at = 4 + 2 * n
            for i in range(int(f[at])):
                sym, off, pos, st = f[at + 1 + 4 * i : at + 5 + 4 * i]
                if sym == "!":
                    antonyms[(p, f[0])].append((int(st[:2], 16), (PTR_POS[pos], off), int(st[2:], 16)))
                elif sym in ("&", "^"):
                    related[(p, f[0])].append((PTR_POS[pos], off))
            synsets[(p, f[0])] = (gloss.strip(), words)

    entries = defaultdict(list)  # key -> [(p, offset)] in WordNet sense order, nouns first
    for long, p in POS:
        for line in read("index." + long):
            if line.startswith("  "):
                continue
            f = line.split()
            key = f[0].replace("_", " ")
            p_cnt = int(f[3])
            offsets = f[6 + p_cnt :]
            entries[key] += [(p, o) for o in offsets]

    files = defaultdict(list)
    for key in sorted(entries):
        senses, display = [], None
        for p, o in entries[key]:
            gloss, words = synsets[(p, o)]
            same = [w for w in words if w.lower() == key]
            # Spelling of the first (most common) sense: "tree", not the later "Tree" (a name).
            if same and display is None:
                display = same[0]
            syn = []
            for w in words:
                if w.lower() != key and w not in syn:
                    syn.append(w)
            ant = []
            for src, target, tgt in antonyms[(p, o)]:
                if src and words[src - 1].lower() == key:
                    w = synsets[target][1][tgt - 1]
                    if w not in ant:
                        ant.append(w)
            rel = []
            for target in related[(p, o)]:
                w = synsets[target][1][0]
                if w.lower() != key and w not in rel and w not in syn and w not in ant:
                    rel.append(w)
            senses.append(f"{p}\x1f{gloss}\x1f{', '.join(syn[:MAX_SYN])}\x1f{', '.join(ant)}\x1f{', '.join(rel[:MAX_REL])}")
        first = key[0] if "a" <= key[0] <= "z" else "0"
        files[first].append(f"{key}\t{display or key}\t" + "\x1e".join(senses))

    os.makedirs(OUT, exist_ok=True)
    total = 0
    for name, lines in sorted(files.items()):
        data = ("\n".join(lines) + "\n").encode("utf-8")
        with open(os.path.join(OUT, name), "wb") as fh:
            fh.write(data)
        total += len(data)

    exc = []
    for long, p in POS:
        for line in read(long + ".exc"):
            f = line.split()
            for base in f[1:]:
                exc.append(f"{f[0].replace('_', ' ')}\t{p}\t{base.replace('_', ' ')}")
    data = ("\n".join(sorted(exc)) + "\n").encode("utf-8")
    with open(os.path.join(OUT, "1"), "wb") as fh:
        fh.write(data)
    total += len(data)

    os.makedirs(os.path.join(ROOT, "licenses"), exist_ok=True)
    lic = tar.extractfile("WordNet-3.0/LICENSE").read()
    for path in (os.path.join(ROOT, "licenses", "WordNet.txt"), os.path.join(OUT, "2")):
        with open(path, "wb") as fh:
            fh.write(lic)
    print(f"{len(entries)} words, {len(files)} files, {total} bytes")


if __name__ == "__main__":
    main()

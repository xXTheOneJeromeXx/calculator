# Kanaiic Reader

A private, offline Bible reader for Android that can look like a dictionary. It opens with
your code into the Berean Standard Bible, World English Bible and King James Version, with
Strong's numbers (search the Hebrew and Greek lexicon, or see the words behind any verse),
highlights, notes, a Saved list and passphrase-locked backups. It has no internet permission
and stores everything encrypted, tied to your code and to the phone.

It comes in two versions built from this code, with the same features: each can look like
**Kanaiic Reader** (a plain code screen) or like a working offline dictionary and thesaurus
with notes and PDF tabs (to open the reader from it: hold **Search**, type the code, hold
**Search** again), switchable in Settings.

- **Direct** (`com.tbce.calc`, `dictionary.apk` from Releases) starts already disguised as the
  dictionary, with a message explaining setup until a code is set, and shows as "Dictionary"
  in the phone's app settings. It is not on any store, so its package id leads nowhere.
- **Google Play** (`com.kanaiic.reader`) starts as Kanaiic Reader, offers the disguise after
  setup, and shows as "Kanaiic Reader" in the phone's app settings. Its store page describes
  the disguise, so the disguise only hides it from a glance.

Up to 1.4 there were also calculator, notepad, clock and sudoku disguises; those were removed
because every extra disguise is one more pattern a searcher can learn to recognise.

It raises the cost of casual and fairly thorough phone inspections. It does not stop
someone who watches you type the code, forces you to open it, or takes the app apart with
time and skill. Read [what it does not protect against](docs/USER_GUIDE.md#what-it-does-not-protect-against)
before relying on it.

- **Install the direct version:** download `dictionary.apk` from [Releases](../../releases/latest)
  and follow [docs/INSTALL.md](docs/INSTALL.md).
- **Use:** [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (also inside the app, under Settings).
- **Design and known weaknesses:** [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).
- **What changed in each version:** [CHANGELOG.md](CHANGELOG.md).

## What's where

| Path | What it is |
| --- | --- |
| `app/src/main/java/com/tbce/calc/` | The app (the package name is historical; it can't change without breaking updates). |
| &nbsp;&nbsp;`vault/` | Encryption: the code, keys, the encrypted store, backups. |
| &nbsp;&nbsp;`reader/` | The reader's data: packs, books, annotations, search, Strong's. |
| &nbsp;&nbsp;`dictionary/`, `notes/` | The dictionary disguise's word lookup and its notepad. |
| &nbsp;&nbsp;`ui/` | Every screen: reader, Strong's, lock and setup, dictionary, notes, PDF. |
| &nbsp;&nbsp;`AppController.kt`, `Disguise.kt` | Locking and unlocking; the launcher faces. |
| `app/src/test/` | Unit tests (they read the real packs). |
| `app/src/main/assets/r/` | Encrypted packs: scripture, Strong's, book names, reader wording, the guide. |
| `app/src/main/assets/w/` | Dictionary word data (WordNet 3.0, plain). |
| `content/sources/` | Sources the packs and dictionary are built from. |
| `tools/` | `build_packs.py`, `build_dict.py`, and `scan_apk.sh` (release check). |
| `docs/` | Install guide, user guide (also in the app), threat model, test script. |
| `licenses/` | Notices for the texts, font, WordNet and Strong's. |

## Building

JDK 21 and the Android SDK (platform 37.2). Then:

```sh
./gradlew testDirectDebugUnitTest testPlayDebugUnitTest   # unit tests
./gradlew assembleRelease     # both versions, app/build/outputs/apk/{direct,play}/release/
./gradlew bundlePlayRelease   # Play upload bundle
tools/scan_apk.sh app/build/outputs/apk/direct/release/app-direct-release.apk
```

`scan_apk.sh` checks for telltale words, permissions, backups and exported components. The two
versions (build flavors `direct` and `play`) behave identically; only the application id
differs. Manual checks before a release are in [docs/TEST_SCRIPT.md](docs/TEST_SCRIPT.md).

Scripture, book names and some wording ship as encrypted packs in `app/src/main/assets/r/`,
built from `content/sources/` by `python3 tools/build_packs.py` (needs `cryptography`). The
pack key is in the app, so this hides the text from a casual look only. Running the script
without `content/pack.key` makes a new key. The dictionary's word data in
`app/src/main/assets/w/` is built from WordNet 3.0 by `python3 tools/build_dict.py`.

Official releases are signed with a key whose certificate SHA-256 is
`23:29:49:D5:53:EB:C5:7B:9F:73:11:13:0F:B8:2F:BF:86:EE:BB:38:FE:B9:E0:B4:53:8E:77:B9:16:54:FA:77`.
Builds signed with any other key cannot update an installed copy.

## License

Code: GPLv3 ([LICENSE](LICENSE)). Texts: BSB, WEB and KJV are public domain, and so are
Strong's Hebrew and Greek dictionaries (1890); Literata is under the SIL Open Font License;
WordNet 3.0 is © Princeton University under the WordNet licence. Notices in [licenses/](licenses/).

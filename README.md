# Kanaiic Reader

A private, offline Bible reader for Android that can look like a dictionary. It opens with
your code into the Berean Standard Bible, World English Bible and King James Version, with
Strong's numbers (search the Hebrew and Greek lexicon, or see the words behind any verse),
highlights, notes, a Saved list and passphrase-locked backups. It has no internet permission
and stores everything encrypted, tied to your code and to the phone.

It comes in two versions built from this code, which work the same way: each opens as
**Kanaiic Reader** with a plain code screen, offers right after setup to disguise itself, and
in Settings can be made to look like a working offline dictionary and thesaurus with notes
and PDF tabs (to open the reader from it: hold **Search**, type the code, hold **Search**
again). They differ only in where they come from:

- **Google Play** (`com.kanaiic.reader`). Its store page describes the disguise, so the
  disguise only hides the app from a glance.
- **Direct** (`com.tbce.calc`, `dictionary.apk` from Releases). It is not on any store, so its
  package id leads nowhere.

On both, the phone's app settings list it as "Kanaiic Reader" even while it looks like a
dictionary. Up to 1.5 the direct version was always the dictionary, and up to 1.4 there were
also calculator, notepad, clock and sudoku disguises; those were removed because every extra
disguise is one more pattern a searcher can learn to recognise.

It raises the cost of casual and fairly thorough phone inspections. It does not stop
someone who watches you type the code, forces you to open it, or takes the app apart with
time and skill. Read [what it does not protect against](docs/USER_GUIDE.md#what-it-does-not-protect-against)
before relying on it.

- **Install the direct version:** download `dictionary.apk` from [Releases](../../releases/latest)
  and follow [docs/INSTALL.md](docs/INSTALL.md).
- **Use:** [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (also inside the app, under Settings).
- **Design and known weaknesses:** [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).

## Building

JDK 21 and the Android SDK (platform 37.2). Then:

```sh
./gradlew testDirectDebugUnitTest testPlayDebugUnitTest   # unit tests
./gradlew assembleRelease     # both versions, app/build/outputs/apk/{direct,play}/release/
./gradlew bundlePlayRelease   # Play upload bundle
tools/scan_apk.sh app/build/outputs/apk/direct/release/app-direct-release.apk
```

`scan_apk.sh` checks for telltale words, permissions, backups and exported components; the
direct version must not contain the Play version's name. The play-only face, name and icon
live in `app/src/play/`.

Scripture, book names and some wording ship as encrypted packs in `app/src/main/assets/r/`,
built from `content/sources/` by `python3 tools/build_packs.py` (needs `cryptography`). The
pack key is in the app, so this hides the text from a casual look only. Running the script
without `content/pack.key` makes a new key. The dictionary's word data in
`app/src/main/assets/w/` is built from WordNet 3.0 by `python3 tools/build_dict.py`.

Official releases are signed with a key whose certificate SHA-256 is
`23:29:49:D5:53:EB:C5:7B:9F:73:11:13:0F:B8:2F:BF:86:EE:BB:38:FE:B9:E0:B4:53:8E:77:B9:16:54:FA:77`.
Builds signed with any other key cannot update an installed copy.

## License

Code: GPLv3 ([LICENSE](LICENSE)). Texts: BSB, WEB and KJV are public domain; Literata is
under the SIL Open Font License; WordNet 3.0 is © Princeton University under the WordNet
licence. Notices in [licenses/](licenses/).

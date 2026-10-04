# Calculator

An Android calculator that is also a private, offline Bible reader. To anyone who opens it,
it is a working calculator. With your code (hold `=`, type the code, hold `=` again) it
opens a reader with the Berean Standard Bible, World English Bible and King James Version,
plus highlights, notes, a Saved list and passphrase-locked backups. It has no internet
permission and stores everything encrypted, tied to your code and to the phone.

It raises the cost of casual and fairly thorough phone inspections. It does not stop
someone who watches you type the code, forces you to open it, or takes the app apart with
time and skill. Read [what it does not protect against](docs/USER_GUIDE.md#what-it-does-not-protect-against)
before relying on it.

- **Install:** download `calculator.apk` from [Releases](../../releases/latest) and follow
  [docs/INSTALL.md](docs/INSTALL.md).
- **Use:** [docs/USER_GUIDE.md](docs/USER_GUIDE.md) (also inside the app, under Settings).
- **Design and known weaknesses:** [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).

## Building

JDK 21 and the Android SDK (platform 37.2). Then:

```sh
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # app/build/outputs/apk/release/
tools/scan_apk.sh                # no telltale words, no permissions, backups off
```

Scripture, book names and some wording ship as encrypted packs in `app/src/main/assets/r/`,
built from `content/sources/` by `python3 tools/build_packs.py` (needs `cryptography`). The
pack key is in the app, so this hides the text from a casual look only. Running the script
without `content/pack.key` makes a new key.

Official releases are signed with a key whose certificate SHA-256 is
`23:29:49:D5:53:EB:C5:7B:9F:73:11:13:0F:B8:2F:BF:86:EE:BB:38:FE:B9:E0:B4:53:8E:77:B9:16:54:FA:77`.
Builds signed with any other key cannot update an installed copy.

## License

Code: GPLv3 ([LICENSE](LICENSE)). Texts: BSB, WEB and KJV are public domain; Literata is
under the SIL Open Font License. Notices in [licenses/](licenses/).

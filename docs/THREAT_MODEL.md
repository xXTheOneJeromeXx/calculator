# Threat model and security design

For the independent reviewer and for anyone changing security-relevant code. Describes
version 1.3.0. The user-facing summary is `USER_GUIDE.md`; the original requirements are in
the original design brief (sections 2, 4, 5, 6 and 9).

## What is protected

1. The fact that the app contains a scripture reader (the disguise).
2. The user's notes, highlights, Saved items, settings and reading position.
3. The scripture text (weakly; see "Bundled text").

## Adversaries

- **In scope:** a family member, neighbour, border or police officer who inspects the phone
  by hand, locked or unlocked, possibly with a full storage image taken offline.
- **Out of scope:** a well-resourced lab with hardware exploits against the secure element;
  spyware already on the phone; coercion of the user; observers watching the code entry.

## Design

### Disguise

- Two editions (build flavors). **direct** (`com.tbce.calc`, GitHub / kanaiic.com) starts as
  a working reference app (WordNet 3.0 dictionary and thesaurus, plus a notepad tab) and has
  no undisguised face; **play** (`com.kanaiic.reader`) starts as "Kanaiic Reader" with a plain
  code pad, and offers the Dictionary as its one disguise. The play-only face, label and icon live in
  `src/play/`, so the direct APK never contains the name; `scan_apk.sh` checks that.
- One disguise: the Dictionary. Calculator, Notes, Clock and Sudoku (1.0–1.4) were removed in
  1.5: each extra face was another known pattern a searcher could
  recognise, and its leftovers (a saved Sudoku game, the face preference) were evidence of
  switching. An install on a removed face moves to the default face on update
  (`UpdateReceiver` on MY_PACKAGE_REPLACED, plus the same check on launch), which re-enables
  the default alias and deletes the Sudoku save.
- Hidden gesture (brief section 4, adapted from the calculator's `=` to the Dictionary's
  Search button): hold Search to arm (the box clears), type the code in the box, hold Search
  again to submit. Typing a non-digit, 15 s, or backgrounding silently disarms; the box clears
  on arm and disarm, and lookups store only headwords that were opened. No haptics, sound or
  visual difference beyond the cleared box.
- No permissions (no INTERNET), `allowBackup=false`, empty data-extraction rules, only the
  launcher activity exported (the androidx profile-installer receiver and the
  DYNAMIC_RECEIVER permission are removed in the manifest).
- Scripture, book names, reader words that name the content, setup/code-screen wording, the
  font, the licenses and the user guide ship inside AES-256-GCM packs under opaque names.
  R8 renames and repackages classes. `tools/scan_apk.sh` (run in CI on both editions) fails
  the build on religious terms, "vault", "hold =", "kanaiic" (direct only), any permission,
  backups, or extra exported components. It skips the dictionary's word data (`assets/w`),
  an ordinary English dictionary that naturally defines words like "church".

### Keys (brief section 5)

```
KEK_code   = Argon2id(code digits, salt16, m=64 MiB, t=3, p=1)       (Bouncy Castle)
KEK_device = AES-256-GCM key in Android Keystore, StrongBox if present, else TEE
c0         = version | salt | m | t | p | GCM_KEK_device( GCM_KEK_code( DEK ) )
```

- The code is never stored. A right code is detected only because the inner GCM tag
  verifies. Right and wrong codes run the same derivation and the same Keystore call.
- `DEK` (32 random bytes) never leaves memory unwrapped. Content keys are
  `HMAC(DEK,"enc")` and file names `HMAC(HMAC(DEK,"name"), item)`, truncated to 16 hex.
- Each stored item is `nonce12 || AES-256-GCM(content)` with the file name as AAD.
- Change code re-wraps the DEK with a fresh salt; no bulk re-encryption.
- Crypto-erase deletes `c0`, `c1` and the Keystore key, then the data directory.
- Lock (background, screen off, idle 60 s, Back, panic button, corner double-tap) saves,
  zeros the DEK and derived keys, drops decrypted text, the search index and the font file.
  `VaultSession.wipe` + `KeyVaultTest.wipeZerosKeys`.

### Failed attempts (Q15)

- `c1` = `GCM_KEK_device(enabled byte | consecutive failures)`. Rewritten after every
  submitted attempt, right or wrong, so a failure makes no extra disk write. Opt-in; at 10 in
  a row the vault crypto-erases. Only armed submissions count.

### Backups (Q29)

- `salt16 || nonce12 || GCM(Argon2id(passphrase), deflate(JSON))`, no magic bytes, written
  and read through the Storage Access Framework. Import merges by id and never overwrites.
- While the system picker is open the app does not lock on `onStop`; it locks after
  `PICKER_GRACE_MS` (120 s) if the user does not come back.

### Leaks (brief section 9)

| Vector | Status |
| --- | --- |
| Backups / device transfer | Disabled (manifest + extraction rules). |
| Network | No INTERNET permission; checked in CI. |
| Screenshots, recording, recents | `FLAG_SECURE` and `setRecentsScreenshotEnabled(false)` whenever not on a front screen. Debug builds skip it for testing. |
| Keyboard | Note, search and passphrase fields: `IME_FLAG_NO_PERSONALIZED_LEARNING`, no suggestions, no autofill. Third-party keyboards may ignore this (stated in the guide). |
| Clipboard | Copy/cut/share menus blocked on note fields; scripture text is not selectable. |
| Logs | `Log` calls stripped by R8; release crash handler exits without logging. |
| Intents | No intent filters except the launcher; no share targets, deep links or file types. |
| Notifications, widgets, shortcuts | None. |
| Process death | Cold start always opens the chosen front screen (locked); saved state is not restored. |
| Residual files | Font file in cache only while open, deleted on lock and at start. No export staging file. |
| Timing | Same derivation for right and wrong codes; guard file written on both. |

## Known weaknesses (for the review)

1. **Bundled text is obfuscation.** The pack key is compiled into the app; anyone with the
   APK can decrypt the scripture and see what the app hides (Q30). Content-pack import was
   deferred.
2. **Remaining UI words.** Generic reader words ("Saved", "Notes", "Highlight", "Hide",
   "Search") are still plain strings in the dex. They do not name the content,
   but they show a second app exists.
3. **Argon2 not calibrated.** About 1.5 s on the x86 emulator with the pure-Java Bouncy
   Castle implementation. A slow, 2 GB phone may take several seconds. Needs a device test
   and possibly a native implementation or lower parameters with the Keystore binding
   carrying more of the weight.
4. **StrongBox fallback untested** on a phone without StrongBox (emulator only).
5. **Strings in memory.** Note text, search queries and passphrases pass through Java
   `String`s (EditText) that cannot be zeroed; they live until garbage collection. So does
   the code itself when it is typed in the Dictionary's search box.
6. **Online guessing.** With the device in hand, codes can be tried through the app at
   about 1–2 s each. A 6-digit code falls in days; 8+ digits and the opt-in wipe help.
7. **Picker grace window.** The vault stays unlocked up to 120 s behind the system picker.
8. **Updater tell.** An updater such as Obtainium shows where the app came from.
9. **Single annotation document.** All notes are one encrypted item rewritten on every
   change; fine now, slow for very large note collections.
10. **Disguise.** The launcher face is an activity-alias (direct: Dictionary only; play: its
    own face and the Dictionary). The enabled alias, the face preference and the notepad's
    own (non-secret) notes are visible to the system and in a storage image. On direct the
    manifest has one launcher alias pointing at a non-launcher activity, which an examiner
    may still find unusual. Switching faces (play only) briefly sends the app to the home
    screen. The application label in system settings is fixed per edition ("Dictionary" on
    direct, "Kanaiic Reader" on play) whichever face is chosen.
11. **Forensic self-test not done.** Brief section 12 asks for a full-storage image after use
    to confirm nothing readable remains (including keyboard dictionaries).
12. **Play edition is traceable.** Its package id leads to a public listing that describes
    the disguise, and Play keeps an install record in the user's Google account. Its
    disguise only hides the app from a glance. The user guide says so and points people at
    risk to the direct edition, whose id appears on no store.

## Suggested review scope

- `vault/` (KeyVault, VaultSession, Crypto, DeviceKey, Backup) and `unlock/UnlockController`.
- `AppController.lock`/`onBackground`, `MainActivity` (secure flag, picker grace, crash handler).
- Manifest and `tools/scan_apk.sh`; R8 output for leftover strings.
- The forensic self-test and the low-end device run above.

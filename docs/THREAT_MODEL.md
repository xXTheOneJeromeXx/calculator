# Threat model and security design

For the independent reviewer and for anyone changing security-relevant code. Describes
version 1.1.0. The user-facing summary is `USER_GUIDE.md`; the original requirements are in
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

- Launcher label "Calculator", package `com.tbce.calc`, original icon, a complete calculator
  (BigDecimal, precedence, percent, repeated `=`, scientific landscape).
- Hidden gesture (brief section 4): hold `=` 1.5 s to arm (display resets to 0), digits feed
  both the calculator and a hidden buffer, hold `=` again to submit. Any operator, `.`, short
  `=`, 15 s, or backgrounding silently disarms. No haptics, sound or visual difference.
  `UnlockController` + `UnlockControllerTest` cover every transition.
- No permissions (no INTERNET), `allowBackup=false`, empty data-extraction rules, only the
  launcher activity exported (the androidx profile-installer receiver and the
  DYNAMIC_RECEIVER permission are removed in the manifest).
- Scripture, book names, reader words that name the content, setup/code-screen wording, the
  font, the licenses and the user guide ship inside AES-256-GCM packs under opaque names.
  R8 renames and repackages classes. `tools/scan_apk.sh` (run in CI) fails the build on
  religious terms, "vault", "hold =", any permission, backups, or extra exported components.

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
2. **Remaining UI words.** Generic reader words ("Saved", "Notes", "Highlight", "Back to
   calculator", "Search") are still plain strings in the dex. They do not name the content,
   but they show a second app exists.
3. **Argon2 not calibrated.** About 1.5 s on the x86 emulator with the pure-Java Bouncy
   Castle implementation. A slow, 2 GB phone may take several seconds. Needs a device test
   and possibly a native implementation or lower parameters with the Keystore binding
   carrying more of the weight.
4. **StrongBox fallback untested** on a phone without StrongBox (emulator only).
5. **Strings in memory.** Note text, search queries and passphrases pass through Java
   `String`s (EditText) that cannot be zeroed; they live until garbage collection.
6. **Online guessing.** With the device in hand, codes can be tried through the app at
   about 1–2 s each. A 6-digit code falls in days; 8+ digits and the opt-in wipe help.
7. **Picker grace window.** The vault stays unlocked up to 120 s behind the system picker.
8. **Updater tell.** An updater such as Obtainium shows where the app came from.
9. **Single annotation document.** All notes are one encrypted item rewritten on every
   change; fine now, slow for very large note collections.
10. **Disguises.** The app ships several launcher faces (calculator, notes) as
    activity-aliases; the enabled one, the chosen-disguise preference, and the notepad's own
    (non-secret) notes are all visible to the system and in a storage image. The set of
    faces is also listed in the APK manifest. This reveals that the app can change its
    appearance, though not what it hides. Switching faces briefly sends the app to the home
    screen.
11. **Forensic self-test not done.** Brief section 12 asks for a full-storage image after use
    to confirm nothing readable remains (including keyboard dictionaries).

## Suggested review scope

- `vault/` (KeyVault, VaultSession, Crypto, DeviceKey, Backup) and `unlock/UnlockController`.
- `AppController.lock`/`onBackground`, `MainActivity` (secure flag, picker grace, crash handler).
- Manifest and `tools/scan_apk.sh`; R8 output for leftover strings.
- The forensic self-test and the low-end device run above.

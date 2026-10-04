# Manual test script (phase gate 1)

Automated: `./gradlew testDirectDebugUnitTest testPlayDebugUnitTest` runs 60+ calculator expressions
(`CalculatorTest`), every unlock-gesture transition (`UnlockControllerTest`), and the
crypto tests: RFC 9106 Argon2id vector, right/wrong code, tamper, lost device key,
wipe zeroes keys, no code/key/plaintext in stored files (`KeyVaultTest`).

On the phone or emulator:

1. Calculator: 2+3×4 = 14; = again gives 26; 200+10% = 220; 5÷0 shows "Can't divide by 0";
   sideways, sin 30 = 0.5.
2. Hold `=` on a fresh install: setup opens. Cancel returns to the calculator.
3. Set a code, enter it twice: the placeholder opens. Type in the scratch pad.
4. "Back to calculator": calculator shows; nothing else visible.
5. Hold `=`, type a wrong code, hold `=`: display shows 0, nothing else happens.
6. Hold `=`, type the right code, hold `=`: opens, scratch pad text is still there.
7. Each of these locks back to the calculator: Home, screen off, Back, double-tap top-left
   corner, 60 s without touching.
8. Recents while open shows a blank card; a screenshot while open is black.
9. Hold `=`, type digits, press `+`: normal calculator behaviour, no unlock on next hold.
10. Hold `=`, type the code, wait 15 s: display clears; holding `=` now arms again.

## Phase gate 2 (reader)

Automated: `ReaderTest` decrypts every book of all three translations, checks verse counts,
clean text, reference parsing and search. `tools/scan_apk.sh` on the release APK.

1. Open: Genesis 1 in BSB. Tap the title, pick Psalms, 23: poetry lines indent.
2. Swipe left and right: next and previous chapter.
3. Switch to KJV and WEB from the chip at top right: same passage.
4. Search "jn 3:16", tap "Go to John 3:16": opens with verse 16 at the top.
5. Search "still waters": matches listed with the words highlighted; tap one to open it.
6. Settings: each theme, text size and spacing change the text; About lists the sources.
7. Lock (Home) and open again: same translation, place, theme and size.
8. The calculator button at top left locks with one tap.

## Phase gates 3 and 4 (annotations, Saved)

Automated: `AnnotationsTest` (highlights by verse, notes, footnotes, Saved with notes,
recent passages, all round-tripped through storage).

1. Tap John 3:16, then 18: both are underlined, 17 is not, and the bar shows John 3:16, 18.
   Tap 18 again to take it out. Long-press 21: 16–21 are selected.
2. Pick a color: the range is highlighted. Switch translation: still highlighted.
3. Select a verse, Note, write, Done: a dot appears after it. Tap the dot: the note opens.
4. End of chapter: "+ Note on this chapter", set a verse tag, Done: it shows there.
5. Select a verse, Save. Saved tab: it is listed. Open it, add a label and a note.
6. Save a chapter from its end. Filter and sort in Saved.
7. Lock and open again: everything above is still there. Picker shows Recent.

## Phase gate 5 (hardening)

Automated: `KeyVaultTest` (wipe after 10 only when on, count reset, change code, backup
round trip, wrong passphrase, tamper), `AnnotationsTest.mergeAddsWithoutOverwriting`,
CI (`.github/workflows/ci.yml`) with `tools/scan_apk.sh`.

1. Settings > Export: passphrase twice, save the file. It opens as random bytes.
2. Import that file: "Nothing new". Wrong passphrase: error, nothing changes.
3. Change code: the old code no longer opens, the new one does.
4. Turn on "Erase after 10 wrong codes", enter 10 wrong codes: holding = now starts setup.
5. Set up again and import the backup: notes, highlights and saved items come back.

## 1.3 (editions, reference disguise)

Automated: `DictionaryTest` (lookups, base forms, capitalisation, prefix suggestions, digits
find nothing), `DisguiseTest` (default faces first). `tools/scan_apk.sh` on both release
APKs (`app/build/outputs/apk/{direct,play}/release/`): the direct one must not contain
"kanaiic".

1. Update from 1.2: install the 1.2.0 release, set a code, then install the 1.3.0 direct
   release over it. Before opening, `adb shell cmd package query-activities -a
   android.intent.action.MAIN -c android.intent.category.LAUNCHER --components` lists only
   `.FaceCalculator`; hold `=` with the old code opens the reader.
2. Direct, fresh install: opens as Dictionary. Hold Search: setup, with the Search hint.
3. Dictionary: "ran" opens run ("a form of run"); suggestions while typing; synonyms open
   their own entries; Thesaurus tab shows synonyms, Related and Opposite; Notes tab works and
   holding Done there does nothing; About shows the WordNet notice; recent lookups listed.
4. Hold Search (box clears), type a wrong code, hold Search: an empty search, nothing else.
   Same with the right code: the reader opens. In a debug build, `shared_prefs/w.xml` holds
   only opened headwords.
5. Settings > Disguise on direct lists Dictionary, Calculator, Notes, Clock, Sudoku.
6. Play, fresh install: "Kanaiic Reader" icon and lock screen; Choose a code; wrong code says
   so; right code opens. Disguise list adds "Kanaiic Reader"; switching to Calculator and
   back works.

# Manual test script (phase gate 1)

Automated: `./gradlew testDirectDebugUnitTest testPlayDebugUnitTest` runs the crypto tests:
RFC 9106 Argon2id vector, right/wrong code, tamper, lost device key, wipe zeroes keys, no
code/key/plaintext in stored files (`KeyVaultTest`). (The calculator and its `=` gesture,
with `CalculatorTest` and `UnlockControllerTest`, were removed in 1.5.)

On the phone or emulator (direct edition, Dictionary face):

1. Hold Search on a fresh install: setup opens. Cancel returns to the dictionary.
2. Set a code, enter it twice: the reader opens.
3. Hold Search, type a wrong code, hold Search: an empty search, nothing else happens.
4. Hold Search, type the right code, hold Search: the reader opens.
5. Each of these locks back to the dictionary: Home, screen off, Back, double-tap top-left
   corner, 60 s without touching.
6. Recents while open shows a blank card; a screenshot while open is black (release build).
7. Hold Search, type digits, then a letter: disarmed; the next hold arms again.
8. Hold Search, type the code, wait 15 s: disarmed; holding Search now arms again.

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
8. **Hide** at top left locks with one tap.

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
4. Turn on "Erase after 10 wrong codes", enter 10 wrong codes: holding Search now starts setup.
5. Set up again and import the backup: notes, highlights and saved items come back.

## 1.3 (editions, reference disguise)

Automated: `DictionaryTest` (lookups, base forms, capitalisation, prefix suggestions, digits
find nothing), `DisguiseTest` (default faces first). `tools/scan_apk.sh` on both release
APKs (`app/build/outputs/apk/{direct,play}/release/`): the direct one must not contain
"kanaiic".

1. Direct, fresh install (up to 1.5): opens as Dictionary. Hold Search: setup, with the Search hint.
2. Dictionary: "ran" opens run ("a form of run"); suggestions while typing; synonyms open
   their own entries; Thesaurus tab shows synonyms, Related and Opposite; Notes tab works;
   About shows the WordNet notice; recent lookups listed.
3. Hold Search (box clears), type a wrong code, hold Search: an empty search, nothing else.
   Same with the right code: the reader opens. In a debug build, `shared_prefs/w.xml` holds
   only opened headwords.
4. Play, fresh install: "Kanaiic Reader" icon and lock screen; Choose a code; wrong code says
   so; right code opens. Settings > Disguise lists Kanaiic Reader and Dictionary; switching to
   Dictionary and back works.

## 1.4 (Strong's numbers)

Automated: `StrongsTest` (tags, lexicon, concordance, number parsing).

1. Select Genesis 1:1, tap Original: beginning H7225, God H430, created H1254, each with the
   Hebrew word, transliteration and KJV renderings.
2. Tap a word: Strong's entry; numbers in the text link to their entries; verse list opens
   the verse in the current translation.
3. Search "H3068": the entry row shows; its entry lists 5519 verses and scrolls smoothly.

## 1.5 (Dictionary only)

Automated: `DisguiseTest` (only READER and DICTIONARY remain).

1. Update from 1.4 on a removed face: install the 1.4.0 build, set a code, switch to Sudoku
   and open it (so `shared_prefs/s.xml` exists), then install 1.5 over it. Before opening,
   `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c
   android.intent.category.LAUNCHER` lists `.FaceDictionary`; `s.xml` is gone; `d.xml` says
   DICTIONARY. Hold Search with the old code: the reader opens with everything there.
2. Direct (1.5 only): Settings has no Disguise section.
3. Dictionary Notes tab: write a note; holding Done just saves it.

## 1.6 (same editions)

Automated: `DisguiseTest`. `tools/scan_apk.sh` on both release APKs (no "kanaiic" ban now).

1. Direct, fresh install: launcher shows only `.FaceReader` ("Kanaiic Reader"); it opens to
   "Choose a code"; Settings > Disguise lists Kanaiic Reader and Dictionary; switching to
   Dictionary leaves only `.FaceDictionary`, and Search opens the reader. Same on play.
2. Update a disguised direct install: install the 1.5.0 build, set a code (it is on the
   Dictionary), then install 1.6 over it. Within a second, before opening, the launcher lists
   only `.FaceDictionary` (not also `.FaceReader`); the old code opens the reader via Search.

## 1.7 (PDF tab, disguise offer, Strong's tab)

Make test files with Ghostscript (a 60-page PDF, a copy with `-sUserPassword=…`, and a text
file renamed `.pdf`) and `adb push` them to `/sdcard/Download/`.

1. Dictionary face: tab bar shows Dictionary, Thesaurus, Notes, PDF. PDF tab: "Open a PDF".
2. Open the 60-page PDF: name in the title, page counter, pages sharp. Fling to the end and
   back several times: `dumpsys meminfo` native heap stays bounded (about 50–95 MB).
3. Pinch out: zooms up to 4x and re-renders sharper; one finger pans sideways and scrolls.
   (On the emulator: `adb root`, then `sendevent` on `/dev/input/event2` with ABS_MT_PRESSURE
   set, or touches are ignored. Release every slot afterwards, or reboot if input sticks.)
4. Switch to Dictionary and back: the PDF is still open at the same page.
5. Password-protected file: "This PDF is password-protected, which isn't supported." Text
   file: "That file couldn't be opened as a PDF." With a PDF open, the same messages show in
   a dialog and the open PDF stays.
6. Hold Search with the code while a PDF is open: the reader opens. Nothing in
   `shared_prefs` or `files` names the PDF.
7. Fresh install, set a code: "Disguise the app now?" Later: stays Kanaiic Reader; Settings
   starts with "How to open the app" for the current face, and each Disguise option shows its
   way in. Disguise now: the steps dialog, then Switch to Dictionary: launcher shows only
   `.FaceDictionary`; hold Search with the code opens the reader; no second offer.
8. Bottom bar: Read, Saved, Search, Strong's, Settings. Strong's tab: intro text; "love" lists
   G26, H157, G25 near the top; "agape" and "G26" find G26; tap opens the entry; Back returns
   to the results with the query kept. Verse selection bar button reads "Strong's".


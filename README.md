# Qurafiz

Android app (Kotlin + Jetpack Compose) that helps you memorize the Quran by listening to your recitation in real time.

## How it works
1. Pick a surah (or start a spaced-repetition **Review**), tap the mic and recite.
2. Words turn green as you get them right. A **skipped** or **wrong** word stops you immediately.
3. Hint ladder on the same word: 1st miss → first letter, 2nd miss → the word, 3rd miss → the ayah is played for you.
4. Fix it, tap **Try again**, and continue from that exact word.
5. Perfect ayat are scheduled for review at growing gaps (1, 3, 7, 14, 30, 60 days); mistakes bring them back sooner.

Modes: **Hide text** (words appear only as you recite them: true recall practice), **Strict / gentle** matching, optional English meaning.

## Run it
Open this folder in **Android Studio** (Koala or newer), let Gradle sync, run on a real device (the emulator mic is unreliable for speech).
- Needs internet for ayah audio (everyayah.com, Alafasy) and, unless the Arabic offline pack is installed, for speech recognition.
- Unit tests for the matching logic: `./gradlew test`

## Code map
- `ArabicNormalizer.kt`, `RecitationMatcher.kt`: pure-Kotlin matching (tested in `app/src/test`)
- `SpeechEngine.kt`: continuous Arabic recognition; swap this class to plug in a Quran-tuned model later
- `ReciteViewModel.kt`: session flow, mistakes, hint ladder
- `ProgressStore.kt`: spaced repetition, streak, daily goal
- `ReciteScreen.kt`, `MainActivity.kt`: UI

## Known limits (v0.1)
- Android's built-in recognizer is built for everyday Arabic, so it can mishear Quranic words; "gentle" mode tolerates this. Tajweed/letter-level (makharij) checking is not included yet.
- Not yet built or run on a device (written without an Android SDK); expect small compile fixes on first sync.

## Data
Quran text and English translation from [risan/quran-json](https://github.com/risan/quran-json) (CC BY 4.0), see `app/src/main/assets/QURAN_DATA_LICENSE_CC-BY-4.0.txt`.

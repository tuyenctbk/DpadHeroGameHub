# Implementation Plan - Hide Retro Racer & Update Translations

We will hide **Retro Racer** from the main menu and update the project's translation resources to include missing translations for all supported languages. We will also fix a compilation error in `MonkeyView.kt`.

## User Review Required

> [!IMPORTANT]
> - **Retro Racer** will be hidden from the `MainActivity` UI but the code for the game will remain in the project.
> - **Translations** will be automatically generated for all 14 supported languages in `translate.py`.

## Proposed Changes

### 1. Fix Compilation Error

#### [MODIFY] [MonkeyView.kt](file:///Users/user/AndroidStudioProjects/Games/Snake/app/src/main/java/com/tdpham/games/monkey/MonkeyView.kt)
- Fix the `Long` vs `Int` comparison error at line 602.
- `val blink = (System.currentTimeMillis() / 250) % 2 == 0` -> `val blink = (System.currentTimeMillis() / 250) % 2 == 0L`

### 2. Hide Retro Racer

#### [MODIFY] [MainActivity.kt](file:///Users/user/AndroidStudioProjects/Games/Snake/app/src/main/java/com/tdpham/games/hub/MainActivity.kt)
- Remove `R.id.btn_retrodriver` from the `games` map in `setupGameButtons`.
- Remove `retrodriver` case from the `when` block in `focusLastPlayed`.

#### [MODIFY] [activity_main.xml](file:///Users/user/AndroidStudioProjects/Games/Snake/app/src/main/res/layout/activity_main.xml)
- Set `android:visibility="gone"` for the `Button` with ID `btn_retrodriver`.

### 3. Update Language Translation Resources

#### [MODIFY] [translate.py](file:///Users/user/AndroidStudioProjects/Games/Snake/translate.py)
- Expand the script to include explicit translation dictionaries for all supported languages (ar, de, pt, ko, hi, es, fr, th, tr, ja, in, zh, ru, it).
- Update the loop to use these language-specific dictionaries.

#### [EXECUTE] Run Translation Script
- Execute `python3 translate.py` to synchronize all `strings.xml` files.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the compilation error is fixed and the project builds successfully.

### Manual Verification
- Deploy the app to the emulator.
- Verify that **Retro Racer** is no longer visible in the main menu.
- Check various `strings.xml` files (e.g., `values-es/strings.xml`, `values-ja/strings.xml`) to ensure they contain the new strings.

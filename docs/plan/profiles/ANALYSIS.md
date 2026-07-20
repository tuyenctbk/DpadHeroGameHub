# Analysis: Multi-Profile System for Shared TV Device

## Problem Statement
Android TV is a shared device. Current implementation stores high scores globally, which doesn't work for multiple players. Users need individual profiles to track their own progress and high scores.

## Proposed Solution
Introduce a Profile system where users select or create a profile upon app launch. High scores will be scoped to the active profile.

## Components

### 1. Data Model (`UserProfile`)
- `id`: String (UUID)
- `name`: String (User chosen name)
- `avatarColor`: Int (Color assigned to the profile)
- `createdAt`: Long

### 2. Profile Management (`ProfileManager`)
- Handles storage of profile list in `SharedPreferences` (JSON list).
- Tracks `activeProfileId`.
- Provides methods to create, delete, and switch profiles.

### 3. Score Scoping (`ScoreManager` update)
- Modify `getHighScore` and `updateHighScore` to prepend `activeProfileId` to the storage keys.
- This ensures scores are isolated per player.

### 4. UI Flow
- **SplashActivity**: Transition to `ProfileSelectionActivity` instead of `MainActivity`.
- **ProfileSelectionActivity**:
    - Display list of existing profiles as large focusable cards.
    - Option to "Add Profile".
    - Auto-focus last used profile.
- **ProfileCreationActivity**:
    - Simple UI to enter name (using TV keyboard).
    - Random color assignment.

## ASCII UI Mockups

### Profile Selection
```
+-------------------------------------------------+
|                                                 |
|                CHOOSE YOUR HERO                 |
|                                                 |
|    +---------+     +---------+     +---------+  |
|    |  ( ^ )  |     |  (^_^)  |     |    +    |  |
|    |  ALICE  |     |   BOB   |     |   ADD   |  |
|    +---------+     +---------+     +---------+  |
|                                                 |
|          USE < > TO BROWSE • [OK] TO SELECT     |
+-------------------------------------------------+
```

### Profile Creation
```
+-------------------------------------------------+
|                                                 |
|                NEW HERO PROFILE                 |
|                                                 |
|             [ ENTER NAME: ________ ]            |
|                                                 |
|                [   CREATE   ]                   |
|                                                 |
+-------------------------------------------------+
```

## Risks
- Data migration: Existing "global" high scores might need to be assigned to a "Default" profile or the first profile created.
- TV Keyboard: Entering text on TV can be tedious; names should be short.

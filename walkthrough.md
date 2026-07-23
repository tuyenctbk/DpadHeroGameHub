# Walkthrough - Hub Upgrades, Gibbon Ascent, and Frenzy Feeding

We have implemented all requested upgrades and features across **MainActivity**, **Gibbon Ascent**, **Frenzy Feeding**, and **Retro Racer**.

---

## 1. Hub Upgrades & Game Promotion

* **Removed Games**: Completely removed `road_racer` and `fruit` buttons from [activity_main.xml](file:///Users/user/AndroidStudioProjects/Games/Snake/app/src/main/res/layout/activity_main.xml) and [MainActivity.kt](file:///Users/user/AndroidStudioProjects/Games/Snake/app/src/main/java/com/tdpham/games/hub/MainActivity.kt).
* **Promoted Games**: Moved **Gibbon Ascent** (`btn_monkey`) and **Frenzy Feeding** (`btn_frenzy`) to positions 4 and 5, and moved **Retro Racer** (`btn_retrodriver`) to the 12th position in the carousel, putting them in premium layout positions.

---

## 2. Gibbon Ascent (`MonkeyView.kt` / `strings.xml`)

* **New Title**: Renamed "MONKEY CLIMB" to **GIBBON ASCENT** in [strings.xml](file:///Users/user/AndroidStudioProjects/Games/Snake/app/src/main/res/values/strings.xml) and updated how-to instructions.
* **Character Selector**:
  * Added 4 selectable climbing characters, switchable via D-Pad Left/Right on the paused start overlay:
    1. **Golden Gibbon**: A svelte orange-muzzled gibbon with long limbs and a curly tail.
    2. **Tarzan Climber**: Human skin-toned climber with long flowing brown wild locks and a green leopard-print loincloth.
    3. **Cyber Gorilla (Gorilliza)**: A bulkier dark grey gorilla with neon blue chest core, purple armor plating, one glowing red cyber-eye, and no tail.
    4. **Punk Chimp**: A chimp with a bright neon-red mohawk and glowing neon-green sunglasses.
* **Improved Anatomy & Design**:
  * Slimmed the body down to a svelte, proportional build.
  * Connected long arms and long legs using thick strokes.
* **Half-Heart Stun & Swirling Stars**:
  * Set total health/lives to 6 (representing 3 full hearts).
  * Draw premium vector hearts on the HUD (supporting full hearts, half-hearts, and empty outlines).
  * Hitting minor hazards (coconuts, spiders, snakes, birds) deals 1 damage (half-heart) and stuns/dizzies the gibbon for 1.5 seconds.
  * While dizzy, we render swirling yellow stars in a horizontal ellipse above the gibbon's head.
* **Vine Cutter Beetle**:
  * Spawns a Leafcutter Beetle (`type = 8`) wiggling down the vines.
  * If it touches the gibbon, it snaps the vine! The lower section of the vine falls away dynamically, forcing the gibbon into a rapid gravity-fall off-screen.
  * When hitting the bottom of the screen, the player takes damage, gets dizzy, and respawns on a random vine.
* **Harpy Eagle cinematic death**:
  * If caught by the Harpy Eagle, it is an instant death.
  * Eagle grabs the gibbon in its talons and flies up off-screen before triggering game over.

---

## 3. Frenzy Feeding (`FrenzyView.kt`)

* **Blowfish / Pufferfish (Index 33)**:
  * Spawned naturally as a small fish.
  * If the player gets close (< 220f), the pufferfish inflates into a massive circular spiny balloon and drifts slowly.
  * If the player backs away, it deflates and resumes normal swimming.
  * If the player bites it while deflated, it is eaten normally.
  * If the player bites it while inflated, it deals damage/hurts the player's mouth, decrementing a life and showing on-screen explanation `"MOUTH HURT BY INFLATED PUFFERFISH!"`.
* **Stun & Warning Notices**:
  * Added warning notifications (throttled to avoid spam) with short durations to explain damage or action blocks:
    * `"POISONED BY LIONFISH (STUNNED)!"` (from touching/biting Lionfish when small)
    * `"SHOCKED BY ELECTRIC EEL (STUNNED)!"` (from touching/biting Electric Eel)
    * `"CANNOT EAT: [NAME] IS TOO LARGE!"` (when player tries to bite/eat a fish larger than them)
    * `"CANNOT EAT: CLAM IS CLOSED!"` (when player tries to bite/eat a closed Clam)
* **Custom Species Visuals**:
  * **Dolphin (Index 18)**: Sleek streamlined dolphin body, snout/rostrum beak, tall curved dorsal fin, pectoral flippers, and horizontal tail flukes that flip **UP and DOWN** vertically (instead of horizontal tail wiggle) for organic mammalian swimming.
  * **Sea Turtle (Index 19)**: Added a shiny black eye with highlight to its head.
  * **Great White Shark (Index 22)**: Predatory pointed snout, dark grey top with light grey underbelly, vertical gill slits, sharp heterocercal tail (upper lobe is longer), pectoral fin, black eye, and sharp white teeth inside open mouth.
  * **Orca / Killer Whale (Index 23)**: Sleek jet-black body, white eye patches, white underbelly, massive tall dorsal fin, and horizontal tail flukes that flip **UP and DOWN** vertically.

---

## 4. Retro Racer (`RetroDriverView.kt`)

* **Only Racing Cars**:
  * Removed all non-car road hazards (boost pads, oil slicks, barriers). The road is now populated solely by competitor racing cars (traffic).
* **Centrifugal Pull on Turns**:
  * Curve segments now apply a physical centrifugal force that pulls the motorcycle outward (left or right, proportional to vehicle speed and turn sharpness). Players must steer into the turns to stay on the road, creating real turn points.
* **Participant Rank Labels**:
  * Real-time participant positions (1st, 2nd, 3rd, etc.) are computed dynamically based on Z positions.
  * A yellow bold rank indicator is drawn directly above each competitor vehicle (scaling down with distance).
  * A green bold rank indicator is drawn directly above the player's motorcycle.
* **No Lives / Continuous Racing**:
  * Removed the lives system (`LIVES: X` removed from HUD). The player car cannot "die" or game-over from crashing; it is simply slowed down and spun out before recovering, keeping the race going until the 3rd lap is finished.
* **Game Over/Victory Restart Fix**:
  * Reordered the key event dispatch in `onKeyDown` so that restart checks for `gameWon`/`gameOver` take precedence over active gameplay pausing. This fixes the issue where players could not restart the game with Enter/Center.
* **Vehicle Switch Fix & Gameplay Pausing**:
  * Pressing **D-Pad Left/Right** on the paused start overlay swaps the vehicle choice (Suzuki Red, Yamaha Yellow, Cyber Cyan, Kawasaki Neon).
  * Added **Active Gameplay Pausing**: Pressing **D-Pad Center** or **Enter** during active race gameplay now pauses the game and displays the selection/resume overlay.
* **Speed-dependent Crash Penalty**:
  * If speed > 80 MPH at collision, the vehicle spins out of control (loss of steering) for a duration proportional to speed.
  * Otherwise, it decelerates normally.
* **Off-Road Deceleration**:
  * Going off-road limits target speed to 25 MPH and decelerates the vehicle rapidly by 5 MPH per frame.
* **3-Lap Race Championship**:
  * The tournament is now a 3-lap race.
  * Passing segment 240 wraps player and traffic Z-coordinates cleanly and increments the lap counter.
  * Triggers a flashy gold starting banner: `"LAP 2 / 3"` or `"FINAL LAP!"`.
  * Completing Lap 3 triggers victory.

---

## 5. Dungeon Escape (`DungeonEscapeView.kt`)

* **Opposite-Corner Start & Exit Placement**:
  * Instead of a static hardcoded start at `(1,1)` and exit at `(rows-2, cols-2)`, the start corner is randomly selected from one of the four corners (Top-Left, Top-Right, Bottom-Left, Bottom-Right).
  * The exit door is automatically placed in the opposite diagonal corner (Top-Left ↔ Bottom-Right, Top-Right ↔ Bottom-Left), ensuring the maximum possible distance between start and finish.
  * The key placement and path accessibility checking are updated to support this dynamic routing, guaranteeing that all generated levels remain fully solvable.

---

## 6. Gibbon Ascent Updates (`MonkeyView.kt`)

* **Vine Cutter Blinking Warning**:
  * Active Leafcutter Beetles now trigger a flashing warning indicator at the top of their vine column: a red warning icon `(!)` and text reading `⚠️ BEETLE`. This gives players a visual warning to jump to another vine before the beetle arrives.
* **Vine Severing Ahead of Player**:
  * The Leafcutter Beetle now cuts the vine *before* making contact with the player. If the beetle is on the same vine as the player and gets within 140 pixels above them, the vine is severed and the player falls immediately.
* **Multi-Layered Seasonal Backgrounds**:
  * The plain flat-colored background has been replaced with a rich, layered jungle scene: a vertical linear color gradient, distant tree canopy silhouettes, and hanging background vines with color-shifting seasonal leaves (providing visual depth/parallax).
  * Seasons now automatically cycle every **25 seconds** (with a smooth 4-second transition) to show off the visual designs.

---

## 7. Cat Meowio (`SyobonView.kt`)

* **General Game Speed Slow Down**:
  * Increased the main game loop thread scheduling delay from **16ms (60 FPS)** to **26ms (38.5 FPS)**.
  * This dynamically slows down the general game speed (including player physics, gravity acceleration, walking enemies, floating hazards, and trap triggers) by approximately 38%. This provides a much more manageable and reactable gameplay experience, allowing players to navigate hidden traps with precision.
* **Vertical Pipe Ground Connection Fix**:
  * Updated `buildPipe()` to dynamically verify and force solid ground surface and body tiles underneath the pipe's coordinates.
  * Aligned the bottom row of all vertical pipes with the ground level of the current stage (row 12 for Sky levels, row 13 for other themes). This ensures that pipes are never generated floating in the air or disconnected from the floor.

# Implementation Plan - Feeding Frenzy & Retro Racer Gameplay Upgrades

We will implement significant gameplay upgrades to two games: **Feeding Frenzy** (`FrenzyView.kt`) and **Retro Racer** (`RetroDriverView.kt`).

---

## Proposed Changes

### 1. Feeding Frenzy (`FrenzyView.kt`)

* **Blowfish / Pufferfish (Species index 33)**:
  * Size: 1 (Small).
  * Behavior: 17 (Pufferfish defensive inflating behavior).
  * Color: `#D4E157` (Beige/Yellow-Green).
  * Speed: `2.0f`.
  * Spawning: Add species index `33` to `smallIndices` list in `spawnSingleFish()` to enable natural spawning.

* **Defensive Inflating Behavior**:
  * In the AI update loop, track distance from the pufferfish to the player.
  * If distance < `220f`, trigger inflation: set `isStartled = true` and reduce speed to zero/drift.
  * If distance > `300f`, trigger deflation: set `isStartled = false` and resume normal swimming.

* **Spiny Collision & Hurt Alerts**:
  * In collision checks, scale the pufferfish collision radius by `1.7f` when inflated.
  * If player collides with pufferfish:
    * If deflated: player eats it normally.
    * If inflated: player gets hurt (loses a life, plays error sound, sets invulnerability shield for 4s, and gets bounced back).
    * Show damage explanation banner: `deathReason = "MOUTH HURT BY INFLATED PUFFERFISH!"` and display for 1.8 seconds.
  * Explain other stuns:
    * Lionfish poison: set `deathReason = "POISONED BY LIONFISH (STUNNED)!"`.
    * Electric Eel shock: set `deathReason = "SHOCKED BY ELECTRIC EEL (STUNNED)!"`.

* **Drawing**:
  * In `drawSpecies()` case 33:
    * If deflated: draw a cute standard oval blowfish.
    * If inflated: draw a large circular body and loops of radial spikes extending outwards.

---

### 2. Retro Racer (`RetroDriverView.kt`)

* **Speed-dependent Crash Penalty**:
  * When player collides with a competitor or barrier:
    * If speed > 80 MPH: Player **loses control (spins out)** for a duration based on speed: `oilSpinUntil = now + (1000 + (speed / maxSpeed) * 1500).toLong()`. Set speed to 10 MPH.
    * If speed <= 80 MPH: Player just decelerates to 15 MPH without losing control.
    * Decrement lives, play error sound, and set invinvibility frame as normal.

* **Off-Road Slowdown**:
  * If player goes off-road (`Math.abs(playerX) > 1.0f`), set target max speed to a low `25f` (was 45f) and apply a rapid speed reduction: `speed -= 5f` per tick (was 3f).

* **3-Lap Race Tournament**:
  * Track lap parameters: `currentLap = 1`, `totalLaps = 3`.
  * When reaching segment 240:
    * If `currentLap < totalLaps`:
      * Increment `currentLap`.
      * Wrap player position: `position -= 240 * segmentLength`.
      * Wrap competitor Z coordinates: `car.z -= 240 * segmentLength` for all competitor traffic.
      * Show flash notification overlay: `"LAP $currentLap / 3"` or `"FINAL LAP!"`.
    * If `currentLap == totalLaps`:
      * Race won! Trigger celebration and victory overlay.
  * Render `LAP: $currentLap / 3` in HUD.

---

## Verification Plan

### Compilation
* Compile project on host with `./run_tv.sh`.

### Manual Testing (Feeding Frenzy)
* Approach pufferfish: verify it inflates, stops running, and hurts/bounces player with explanation if eaten spiny.
* Hit Eel or Lionfish: verify the specific stun overlay message appears.

### Manual Testing (Retro Racer)
* Drive off-road: confirm rapid speed penalty.
* Crash at high speed vs. low speed: confirm spin-out stun is applied proportionally to speed.
* Complete 3 laps: verify lap counter increments and the race finishes on Lap 3.

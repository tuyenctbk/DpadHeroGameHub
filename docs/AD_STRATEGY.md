# AdMob Monetization Strategy - Dpad Arcade

## Overview
A user-centric ad strategy optimized for retro gaming on Android TV. This framework balances sustainable revenue with high user retention by implementing long-term grace periods and frequency capping.

---

## Ad Types

### 1. Interstitial Ads (Primary Revenue)
- **Trigger**: Shown when users explicitly exit games back to the hub.
- **Grace Period**: 100 days (requested for long-term user acquisition).
- **Throttling**: Controlled by eligibility gates and frequency caps.

### 2. Banner Ads (Passive Revenue)
- **Placement**: Bottom of the Hub/Game Center.
- **Frequency**: Constant display while in the hub.
- **Impact**: Minimal UX disruption, standard for free-to-play hubs.

---

## Multi-Layer Eligibility System

### Phase 1: Qualification Gates (All must pass)
1. **Days Since Install >= 100 days**
   - Extreme grace period to ensure absolute user loyalty before monetization begins.
2. **App Open Count >= 10 opens**
   - Ensures only recurring, engaged users see ads.
3. **Session Duration >= 10 seconds**
   - Avoids ads on accidental launches or quick checks.

### Phase 2: Frequency Throttling
1. **Cooldown**: Minimum 2 minutes (120s) between consecutive interstitial ads.
2. **Session Cap**: Maximum 3 ads per app session to prevent ad fatigue.
3. **Pre-loading**: Ads are pre-loaded in the background to ensure high-quality delivery.

---

## Remote Config Parameters
Managed via Firebase to allow real-time adjustments without app updates.

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `is_ads_enabled` | `true` | Global master switch for ads |
| `ads_min_days` | `100` | Minimum days after install |
| `ads_min_opens` | `10` | Minimum app opens required |
| `ads_min_session_seconds` | `10` | Minimum seconds in current session |

---

## Implementation Details
- **AdsManager**: Singleton handling initialization, session tracking, and eligibility logic.
- **ConfigManager**: Fetches thresholds from Firebase Remote Config.
- **BaseGameActivity**: Standardized exit point to trigger ads.

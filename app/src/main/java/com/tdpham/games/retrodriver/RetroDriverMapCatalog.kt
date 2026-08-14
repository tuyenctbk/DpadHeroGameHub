package com.tdpham.games.retrodriver

data class TrackChunk(val length: Int, val curve: Float)
data class ObstacleSpawn(val segIndex: Int, val offset: Float, val type: Int) // 0: Boost Pad, 1: Oil Slick, 2: Road Barrier

data class RacingMap(
    val id: Int,
    val name: String,
    val description: String,
    val themeIndex: Int, // 0: Neon Sunset, 1: Desert Sunset, 2: Cyber Night, 3: Snowy Peak
    val chunks: List<TrackChunk>,
    val obstacles: List<ObstacleSpawn> = emptyList()
)

object RetroDriverMapCatalog {
    val maps: List<RacingMap> = listOf(
        // Map 1: Neon Sunset Boulevard
        RacingMap(
            id = 1,
            name = "1. Neon Sunset Blvd",
            description = "High-speed beginner circuit with gentle sweeping turns along the coastline.",
            themeIndex = 0,
            chunks = listOf(
                TrackChunk(40, 0f),
                TrackChunk(35, -6f),
                TrackChunk(35, 6f),
                TrackChunk(30, 0f),
                TrackChunk(40, -10f),
                TrackChunk(40, 10f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(30, -0.4f, 0), // Boost pad
                ObstacleSpawn(65, 0.4f, 1),  // Oil slick
                ObstacleSpawn(110, 0.0f, 0), // Boost pad
                ObstacleSpawn(160, -0.5f, 2),// Barrier
                ObstacleSpawn(200, 0.3f, 0)  // Boost pad
            )
        ),
        // Map 2: Desert Canyon S-Curves
        RacingMap(
            id = 2,
            name = "2. Desert Canyon S-Curves",
            description = "Twin canyon S-bends through sandstone arches and desert plateaus.",
            themeIndex = 1,
            chunks = listOf(
                TrackChunk(30, 0f),
                TrackChunk(30, -14f),
                TrackChunk(30, 14f),
                TrackChunk(30, 0f),
                TrackChunk(35, -16f),
                TrackChunk(35, 16f),
                TrackChunk(50, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(25, 0.0f, 0),
                ObstacleSpawn(55, -0.4f, 1),
                ObstacleSpawn(85, 0.5f, 2),
                ObstacleSpawn(120, 0.0f, 0),
                ObstacleSpawn(170, -0.4f, 1),
                ObstacleSpawn(210, 0.0f, 0)
            )
        ),
        // Map 3: Cyberpunk Expressway
        RacingMap(
            id = 3,
            name = "3. Cyberpunk Expressway",
            description = "Electrified multi-level highway under glowing neon skyscrapers.",
            themeIndex = 2,
            chunks = listOf(
                TrackChunk(50, 0f),
                TrackChunk(20, -12f),
                TrackChunk(20, 12f),
                TrackChunk(40, 0f),
                TrackChunk(45, -15f),
                TrackChunk(45, 15f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(40, 0.0f, 0),
                ObstacleSpawn(65, -0.5f, 2),
                ObstacleSpawn(85, 0.4f, 1),
                ObstacleSpawn(130, 0.0f, 0),
                ObstacleSpawn(180, -0.4f, 1),
                ObstacleSpawn(220, 0.0f, 0)
            )
        ),
        // Map 4: Alpine Glacier Run
        RacingMap(
            id = 4,
            name = "4. Alpine Glacier Run",
            description = "Challenging snowy hairpins through icy mountain passes.",
            themeIndex = 3,
            chunks = listOf(
                TrackChunk(25, 0f),
                TrackChunk(35, -18f),
                TrackChunk(20, 0f),
                TrackChunk(35, 18f),
                TrackChunk(30, -10f),
                TrackChunk(30, 10f),
                TrackChunk(45, 0f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(20, 0.0f, 0),
                ObstacleSpawn(50, -0.5f, 1),
                ObstacleSpawn(100, 0.5f, 1),
                ObstacleSpawn(140, 0.0f, 0),
                ObstacleSpawn(180, -0.4f, 2),
                ObstacleSpawn(210, 0.0f, 0)
            )
        ),
        // Map 5: Pacific Coast Highway
        RacingMap(
            id = 5,
            name = "5. Pacific Coast Highway",
            description = "Ocean breeze and flowing coastal curves with scenic sea vistas.",
            themeIndex = 0,
            chunks = listOf(
                TrackChunk(40, 0f),
                TrackChunk(40, -8f),
                TrackChunk(40, 8f),
                TrackChunk(30, -14f),
                TrackChunk(30, 14f),
                TrackChunk(40, 0f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(35, 0.0f, 0),
                ObstacleSpawn(70, -0.4f, 1),
                ObstacleSpawn(110, 0.4f, 2),
                ObstacleSpawn(150, 0.0f, 0),
                ObstacleSpawn(190, -0.3f, 1),
                ObstacleSpawn(220, 0.0f, 0)
            )
        ),
        // Map 6: Tokyo Megapolis Loop
        RacingMap(
            id = 6,
            name = "6. Tokyo Megapolis Loop",
            description = "Fast urban corners, high-G overpasses, and tunnel sprints.",
            themeIndex = 2,
            chunks = listOf(
                TrackChunk(30, 0f),
                TrackChunk(35, -16f),
                TrackChunk(30, 0f),
                TrackChunk(35, 16f),
                TrackChunk(40, -12f),
                TrackChunk(40, 12f),
                TrackChunk(30, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(25, 0.0f, 0),
                ObstacleSpawn(60, -0.5f, 2),
                ObstacleSpawn(95, 0.0f, 0),
                ObstacleSpawn(130, 0.4f, 1),
                ObstacleSpawn(170, -0.4f, 1),
                ObstacleSpawn(210, 0.0f, 0)
            )
        ),
        // Map 7: Outrun Ridge Speedway
        RacingMap(
            id = 7,
            name = "7. Outrun Ridge Speedway",
            description = "High-speed ridge run through fiery red desert plateaus.",
            themeIndex = 1,
            chunks = listOf(
                TrackChunk(50, 0f),
                TrackChunk(45, -10f),
                TrackChunk(45, 10f),
                TrackChunk(35, -14f),
                TrackChunk(45, 0f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(40, 0.0f, 0),
                ObstacleSpawn(85, -0.4f, 1),
                ObstacleSpawn(130, 0.5f, 2),
                ObstacleSpawn(175, 0.0f, 0),
                ObstacleSpawn(215, 0.0f, 0)
            )
        ),
        // Map 8: Emerald Valley GP
        RacingMap(
            id = 8,
            name = "8. Emerald Valley GP",
            description = "Technical winding chicanes surrounded by lush synthwave greenery.",
            themeIndex = 0,
            chunks = listOf(
                TrackChunk(30, 0f),
                TrackChunk(25, -12f),
                TrackChunk(25, 12f),
                TrackChunk(25, -12f),
                TrackChunk(25, 12f),
                TrackChunk(45, -15f),
                TrackChunk(45, 0f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(25, 0.0f, 0),
                ObstacleSpawn(50, -0.4f, 1),
                ObstacleSpawn(75, 0.4f, 2),
                ObstacleSpawn(100, -0.3f, 1),
                ObstacleSpawn(130, 0.0f, 0),
                ObstacleSpawn(180, 0.5f, 2),
                ObstacleSpawn(220, 0.0f, 0)
            )
        ),
        // Map 9: Polar Fjord Drift
        RacingMap(
            id = 9,
            name = "9. Polar Fjord Drift",
            description = "High-speed icy drifts along sheer cliffs and frozen waters.",
            themeIndex = 3,
            chunks = listOf(
                TrackChunk(30, 0f),
                TrackChunk(45, -14f),
                TrackChunk(25, 0f),
                TrackChunk(45, 14f),
                TrackChunk(30, -16f),
                TrackChunk(30, 16f),
                TrackChunk(35, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(25, 0.0f, 0),
                ObstacleSpawn(65, -0.4f, 1),
                ObstacleSpawn(95, 0.0f, 0),
                ObstacleSpawn(140, 0.4f, 1),
                ObstacleSpawn(180, -0.5f, 2),
                ObstacleSpawn(215, 0.0f, 0)
            )
        ),
        // Map 10: Thunder Dome Raceway
        RacingMap(
            id = 10,
            name = "10. Thunder Dome Raceway",
            description = "Sudden hairpin turns and sharp chicanes under dark cyber storm skies.",
            themeIndex = 2,
            chunks = listOf(
                TrackChunk(35, 0f),
                TrackChunk(30, -17f),
                TrackChunk(25, 0f),
                TrackChunk(30, 17f),
                TrackChunk(25, -14f),
                TrackChunk(25, 14f),
                TrackChunk(45, 0f),
                TrackChunk(25, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(30, 0.0f, 0),
                ObstacleSpawn(60, -0.5f, 2),
                ObstacleSpawn(90, 0.4f, 1),
                ObstacleSpawn(120, -0.3f, 1),
                ObstacleSpawn(150, 0.0f, 0),
                ObstacleSpawn(190, 0.5f, 2),
                ObstacleSpawn(220, 0.0f, 0)
            )
        ),
        // Map 11: Sahara Sandstorm Pass
        RacingMap(
            id = 11,
            name = "11. Sahara Sandstorm Pass",
            description = "Long sweeping curves across shifting desert sands and rocky dunes.",
            themeIndex = 1,
            chunks = listOf(
                TrackChunk(40, 0f),
                TrackChunk(40, -11f),
                TrackChunk(30, 0f),
                TrackChunk(35, -15f),
                TrackChunk(35, 15f),
                TrackChunk(40, 0f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(35, 0.0f, 0),
                ObstacleSpawn(70, -0.4f, 1),
                ObstacleSpawn(110, 0.0f, 0),
                ObstacleSpawn(145, 0.4f, 2),
                ObstacleSpawn(180, -0.3f, 1),
                ObstacleSpawn(220, 0.0f, 0)
            )
        ),
        // Map 12: Hyper Neon Circuit
        RacingMap(
            id = 12,
            name = "12. Hyper Neon Circuit",
            description = "Alternating triple apex turns with blinding neon track borders.",
            themeIndex = 0,
            chunks = listOf(
                TrackChunk(30, 0f),
                TrackChunk(30, -8f),
                TrackChunk(30, -14f),
                TrackChunk(45, 12f),
                TrackChunk(25, -15f),
                TrackChunk(25, 15f),
                TrackChunk(35, 0f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(25, 0.0f, 0),
                ObstacleSpawn(55, -0.4f, 1),
                ObstacleSpawn(85, 0.5f, 2),
                ObstacleSpawn(125, 0.0f, 0),
                ObstacleSpawn(160, -0.4f, 1),
                ObstacleSpawn(195, 0.0f, 0)
            )
        ),
        // Map 13: Blizzard Peak Slalom
        RacingMap(
            id = 13,
            name = "13. Blizzard Peak Slalom",
            description = "Rapid left-right downhill slalom on packed frozen snow.",
            themeIndex = 3,
            chunks = listOf(
                TrackChunk(25, 0f),
                TrackChunk(25, -15f),
                TrackChunk(25, 15f),
                TrackChunk(25, -15f),
                TrackChunk(25, 15f),
                TrackChunk(35, -18f),
                TrackChunk(65, 0f),
                TrackChunk(25, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(20, 0.0f, 0),
                ObstacleSpawn(45, -0.4f, 1),
                ObstacleSpawn(70, 0.4f, 1),
                ObstacleSpawn(95, -0.4f, 1),
                ObstacleSpawn(125, 0.5f, 2),
                ObstacleSpawn(160, 0.0f, 0),
                ObstacleSpawn(210, 0.0f, 0)
            )
        ),
        // Map 14: Matrix Cyber Ring
        RacingMap(
            id = 14,
            name = "14. Matrix Cyber Ring",
            description = "Continuous high-G curved sections inside a digital super-grid.",
            themeIndex = 2,
            chunks = listOf(
                TrackChunk(40, 0f),
                TrackChunk(60, -13f),
                TrackChunk(30, 0f),
                TrackChunk(60, 13f),
                TrackChunk(50, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(35, 0.0f, 0),
                ObstacleSpawn(80, -0.4f, 1),
                ObstacleSpawn(125, 0.0f, 0),
                ObstacleSpawn(165, 0.4f, 2),
                ObstacleSpawn(205, 0.0f, 0)
            )
        ),
        // Map 15: Sunset Strip Freeway
        RacingMap(
            id = 15,
            name = "15. Sunset Strip Freeway",
            description = "Wide multi-lane freeway with gentle high-speed curving stretches.",
            themeIndex = 0,
            chunks = listOf(
                TrackChunk(55, 0f),
                TrackChunk(45, -9f),
                TrackChunk(35, 0f),
                TrackChunk(45, 9f),
                TrackChunk(40, 0f),
                TrackChunk(20, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(45, 0.0f, 0),
                ObstacleSpawn(90, -0.4f, 1),
                ObstacleSpawn(130, 0.5f, 2),
                ObstacleSpawn(170, 0.0f, 0),
                ObstacleSpawn(210, -0.3f, 1)
            )
        ),
        // Map 16: Red Rock Canyon
        RacingMap(
            id = 16,
            name = "16. Red Rock Canyon",
            description = "Narrow red cliff canyons with sudden technical bends.",
            themeIndex = 1,
            chunks = listOf(
                TrackChunk(30, 0f),
                TrackChunk(35, -16f),
                TrackChunk(30, 0f),
                TrackChunk(30, -13f),
                TrackChunk(30, 13f),
                TrackChunk(50, 0f),
                TrackChunk(35, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(25, 0.0f, 0),
                ObstacleSpawn(60, -0.5f, 2),
                ObstacleSpawn(90, 0.0f, 0),
                ObstacleSpawn(120, 0.4f, 1),
                ObstacleSpawn(150, -0.4f, 1),
                ObstacleSpawn(190, 0.0f, 0)
            )
        ),
        // Map 17: Frozen Tundra Dash
        RacingMap(
            id = 17,
            name = "17. Frozen Tundra Dash",
            description = "Smooth drifting corners through aurora-lit snow fields.",
            themeIndex = 3,
            chunks = listOf(
                TrackChunk(35, 0f),
                TrackChunk(40, -11f),
                TrackChunk(30, -13f),
                TrackChunk(30, 13f),
                TrackChunk(45, -15f),
                TrackChunk(40, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(30, 0.0f, 0),
                ObstacleSpawn(70, -0.4f, 1),
                ObstacleSpawn(100, 0.5f, 2),
                ObstacleSpawn(130, -0.3f, 1),
                ObstacleSpawn(165, 0.0f, 0),
                ObstacleSpawn(205, 0.0f, 0)
            )
        ),
        // Map 18: Vaporwave Skyline
        RacingMap(
            id = 18,
            name = "18. Vaporwave Skyline",
            description = "Atmospheric city skyline turns with tight downtown chicanes.",
            themeIndex = 2,
            chunks = listOf(
                TrackChunk(35, 0f),
                TrackChunk(35, -14f),
                TrackChunk(30, 0f),
                TrackChunk(35, 14f),
                TrackChunk(30, -16f),
                TrackChunk(30, 16f),
                TrackChunk(45, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(30, 0.0f, 0),
                ObstacleSpawn(65, -0.4f, 1),
                ObstacleSpawn(95, 0.0f, 0),
                ObstacleSpawn(130, 0.5f, 2),
                ObstacleSpawn(160, -0.4f, 1),
                ObstacleSpawn(200, 0.0f, 0)
            )
        ),
        // Map 19: Turbo Apex GP
        RacingMap(
            id = 19,
            name = "19. Turbo Apex GP",
            description = "Pro-level triple hairpin circuit demanding precision throttle control.",
            themeIndex = 0,
            chunks = listOf(
                TrackChunk(30, 0f),
                TrackChunk(30, -17f),
                TrackChunk(20, 0f),
                TrackChunk(30, 17f),
                TrackChunk(30, -17f),
                TrackChunk(65, 0f),
                TrackChunk(28, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(25, 0.0f, 0),
                ObstacleSpawn(55, -0.5f, 2),
                ObstacleSpawn(80, 0.0f, 0),
                ObstacleSpawn(105, 0.4f, 1),
                ObstacleSpawn(135, -0.4f, 1),
                ObstacleSpawn(170, 0.0f, 0),
                ObstacleSpawn(215, 0.0f, 0)
            )
        ),
        // Map 20: Infinity Master Ring
        RacingMap(
            id = 20,
            name = "20. Infinity Master Ring",
            description = "The ultimate 240-segment championship track featuring every curve style.",
            themeIndex = 2,
            chunks = listOf(
                TrackChunk(35, 0f),
                TrackChunk(40, -15f),
                TrackChunk(30, 0f),
                TrackChunk(40, 15f),
                TrackChunk(30, -18f),
                TrackChunk(30, 18f),
                TrackChunk(50, 0f),
                TrackChunk(25, 0f)
            ),
            obstacles = listOf(
                ObstacleSpawn(30, 0.0f, 0),
                ObstacleSpawn(65, -0.4f, 1),
                ObstacleSpawn(100, 0.0f, 0),
                ObstacleSpawn(135, 0.5f, 2),
                ObstacleSpawn(165, -0.4f, 1),
                ObstacleSpawn(195, 0.0f, 0),
                ObstacleSpawn(225, 0.0f, 0)
            )
        )
    )

    fun getMap(index: Int): RacingMap {
        val safeIndex = index.coerceIn(0, maps.size - 1)
        return maps[safeIndex]
    }

    fun validateAllMaps(): Boolean {
        for (map in maps) {
            if (map.id !in 1..20) return false
            if (map.name.isBlank()) return false
            var totalSegs = 0
            for (chunk in map.chunks) {
                if (chunk.length <= 0) return false
                if (chunk.curve < -22f || chunk.curve > 22f) return false
                totalSegs += chunk.length
            }
            if (totalSegs < 240) return false
        }
        return true
    }
}

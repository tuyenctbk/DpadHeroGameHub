package com.tdpham.games.trivia

data class TriviaQuestion(
    val category: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

object TriviaDatabase {

    val QUESTIONS = listOf(
        // Category 1: Gaming & Arcade Classics
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "In what year was the legendary arcade game PAC-MAN first released by Namco?",
            options = listOf("1978", "1980", "1983", "1985"),
            correctIndex = 1,
            explanation = "Pac-Man was created by Toru Iwatani and released in May 1980 in Japan."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "What is the highest possible score in classic arcade Pac-Man?",
            options = listOf("999,990", "1,000,000", "3,333,360", "9,999,990"),
            correctIndex = 2,
            explanation = "A perfect score is 3,333,360 points achieved on the 256th split-screen level."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "Who created the puzzle phenomenon Tetris in Moscow in 1984?",
            options = listOf("Alexey Pajitnov", "Shigeru Miyamoto", "Satoshi Tajiri", "John Carmack"),
            correctIndex = 0,
            explanation = "Alexey Pajitnov programmed Tetris on an Electronika 60 computer."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "What was Mario's original profession in his 1981 debut in Donkey Kong?",
            options = listOf("Plumber", "Carpenter", "Chef", "Doctor"),
            correctIndex = 1,
            explanation = "Mario was originally named Jumpman and was a carpenter on a construction site."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "What is the famous cheat sequence known as the 'Konami Code'?",
            options = listOf(
                "Up Up Down Down Left Right Left Right B A",
                "Up Down Up Down Left Right B A Start",
                "Left Right Left Right Up Up Down Down A B",
                "Down Down Up Up Left Right B A Start"
            ),
            correctIndex = 0,
            explanation = "Created by Kazuhisa Hashimoto for Gradius on the NES in 1986."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "Which company released the iconic Genesis/Mega Drive console in 1988?",
            options = listOf("Nintendo", "Sega", "Atari", "Sony"),
            correctIndex = 1,
            explanation = "Sega Genesis revolutionized 16-bit arcade-quality console gaming."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "What color is the Blinky ghost in classic Pac-Man?",
            options = listOf("Pink", "Cyan", "Orange", "Red"),
            correctIndex = 3,
            explanation = "Blinky (Shadow) is the aggressive red ghost that chases Pac-Man directly."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "What 1993 FPS game by id Software popularized multiplayer LAN deathmatches?",
            options = listOf("Wolfenstein 3D", "DOOM", "Quake", "Duke Nukem 3D"),
            correctIndex = 1,
            explanation = "DOOM transformed 3D graphics and popularized network deathmatch gameplay."
        ),

        // Category 2: Science & Technology
        TriviaQuestion(
            category = "Science & Tech",
            question = "What does the acronym 'CPU' stand for in computer hardware?",
            options = listOf(
                "Central Processing Unit",
                "Core Program Utility",
                "Computer Performance Unit",
                "Central Protocol Utility"
            ),
            correctIndex = 0,
            explanation = "The CPU is the primary component that executes machine instructions."
        ),
        TriviaQuestion(
            category = "Science & Tech",
            question = "Which planet in our solar system has the most known moons?",
            options = listOf("Jupiter", "Saturn", "Neptune", "Uranus"),
            correctIndex = 1,
            explanation = "Saturn has 146 officially recognized moons, leading the solar system."
        ),
        TriviaQuestion(
            category = "Science & Tech",
            question = "What programming language was developed by JetBrains and is the official standard for Android?",
            options = listOf("Java", "Kotlin", "Swift", "Dart"),
            correctIndex = 1,
            explanation = "Google made Kotlin the preferred language for Android app development in 2019."
        ),
        TriviaQuestion(
            category = "Science & Tech",
            question = "What is the speed of light in a vacuum approximately?",
            options = listOf("300,000 km/s", "150,000 km/s", "500,000 km/s", "1,000,000 km/s"),
            correctIndex = 0,
            explanation = "Light travels at roughly 299,792 kilometers per second in a vacuum."
        ),
        TriviaQuestion(
            category = "Science & Tech",
            question = "Who is credited with inventing the World Wide Web in 1989?",
            options = listOf("Alan Turing", "Tim Berners-Lee", "Bill Gates", "Steve Wozniak"),
            correctIndex = 1,
            explanation = "Sir Tim Berners-Lee invented HTML, HTTP, and the World Wide Web at CERN."
        ),
        TriviaQuestion(
            category = "Science & Tech",
            question = "What element has the chemical symbol 'Au' on the periodic table?",
            options = listOf("Silver", "Gold", "Argon", "Aluminum"),
            correctIndex = 1,
            explanation = "From the Latin word 'Aurum', meaning shining dawn."
        ),

        // Category 3: Pop Culture & Cinema
        TriviaQuestion(
            category = "Pop Culture & Cinema",
            question = "Which movie won the first Academy Award for Best Animated Feature in 2001?",
            options = listOf("Monsters, Inc.", "Shrek", "Toy Story", "Spirited Away"),
            correctIndex = 1,
            explanation = "Shrek won the inaugural Best Animated Feature Oscar at the 74th Academy Awards."
        ),
        TriviaQuestion(
            category = "Pop Culture & Cinema",
            question = "In 'The Matrix' (1999), which pill does Neo take to wake up in reality?",
            options = listOf("Blue Pill", "Red Pill", "Green Pill", "Yellow Pill"),
            correctIndex = 1,
            explanation = "Morpheus offers Neo the Red Pill to see how deep the rabbit hole goes."
        ),
        TriviaQuestion(
            category = "Pop Culture & Cinema",
            question = "What is the name of Thor's magical enchanted hammer in Marvel Comics & MCU?",
            options = listOf("Stormbreaker", "Mjolnir", "Gungnir", "Aegis"),
            correctIndex = 1,
            explanation = "Mjolnir was forged from Uru metal in the heart of a dying star."
        ),
        TriviaQuestion(
            category = "Pop Culture & Cinema",
            question = "Who composed the iconic musical themes for Star Wars, Indiana Jones, and Jurassic Park?",
            options = listOf("Hans Zimmer", "John Williams", "Ennio Morricone", "Danny Elfman"),
            correctIndex = 1,
            explanation = "John Williams has received over 50 Academy Award nominations."
        ),

        // Category 4: World History & Geography
        TriviaQuestion(
            category = "History & World",
            question = "Which ancient wonder of the world is the only one still largely intact today?",
            options = listOf(
                "Colossus of Rhodes",
                "Great Pyramid of Giza",
                "Lighthouse of Alexandria",
                "Hanging Gardens of Babylon"
            ),
            correctIndex = 1,
            explanation = "The Great Pyramid of Giza was built around 2560 BC and stands today in Egypt."
        ),
        TriviaQuestion(
            category = "History & World",
            question = "What is the longest river in the world by general consensus?",
            options = listOf("Amazon River", "Nile River", "Yangtze River", "Mississippi River"),
            correctIndex = 1,
            explanation = "The Nile spans approximately 6,650 kilometers (4,132 miles) through northeast Africa."
        ),
        TriviaQuestion(
            category = "History & World",
            question = "In what year did the Apollo 11 mission first land humans on the Moon?",
            options = listOf("1965", "1967", "1969", "1972"),
            correctIndex = 2,
            explanation = "Neil Armstrong and Buzz Aldrin stepped onto the lunar surface on July 20, 1969."
        ),
        TriviaQuestion(
            category = "History & World",
            question = "What is the smallest independent country in the world by area?",
            options = listOf("Monaco", "Vatican City", "San Marino", "Liechtenstein"),
            correctIndex = 1,
            explanation = "Vatican City covers an area of roughly 0.49 square kilometers (121 acres)."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "Which company originally created and developed the PlayStation console?",
            options = listOf("Sony", "Nintendo", "Sega", "Panasonic"),
            correctIndex = 0,
            explanation = "Ken Kutaragi at Sony led the development of the original PlayStation launched in 1994."
        ),
        TriviaQuestion(
            category = "Gaming & Arcade",
            question = "What is the best-selling video game of all time with over 300 million copies sold?",
            options = listOf("Tetris", "Minecraft", "Grand Theft Auto V", "Wii Sports"),
            correctIndex = 1,
            explanation = "Minecraft created by Mojang has exceeded 300 million units sold worldwide."
        ),
        TriviaQuestion(
            category = "Science & Tech",
            question = "What is the hardest naturally occurring mineral on Earth?",
            options = listOf("Quartz", "Topaz", "Corundum", "Diamond"),
            correctIndex = 3,
            explanation = "Diamond rates a maximum 10 on Mohs scale of mineral hardness."
        ),
        TriviaQuestion(
            category = "Science & Tech",
            question = "Which gas makes up approximately 78 percent of Earth's atmosphere?",
            options = listOf("Oxygen", "Nitrogen", "Carbon Dioxide", "Argon"),
            correctIndex = 1,
            explanation = "Nitrogen constitutes about 78.08% of Earth's dry atmosphere."
        ),
        TriviaQuestion(
            category = "Pop Culture & Cinema",
            question = "What year was the original 'Star Wars: A New Hope' released in theaters?",
            options = listOf("1975", "1977", "1980", "1983"),
            correctIndex = 1,
            explanation = "George Lucas's Star Wars was released on May 25, 1977, changing cinema forever."
        ),
        TriviaQuestion(
            category = "History & World",
            question = "Which desert is the largest hot desert in the world?",
            options = listOf("Gobi Desert", "Kalahari Desert", "Sahara Desert", "Arabian Desert"),
            correctIndex = 2,
            explanation = "The Sahara spans over 9.2 million square kilometers across North Africa."
        ),
        TriviaQuestion(
            category = "History & World",
            question = "Who painted the famous masterpiece 'The Starry Night' in 1889?",
            options = listOf("Claude Monet", "Vincent van Gogh", "Pablo Picasso", "Salvador Dalí"),
            correctIndex = 1,
            explanation = "Vincent van Gogh painted The Starry Night while staying in Saint-Rémy-de-Provence."
        )
    )

    fun getQuestionsForCategory(categoryFilter: String): List<TriviaQuestion> {
        return if (categoryFilter == "ALL") {
            QUESTIONS.shuffled()
        } else {
            QUESTIONS.filter { it.category.contains(categoryFilter, ignoreCase = true) }.shuffled()
        }
    }
}

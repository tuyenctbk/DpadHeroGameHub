package com.tdpham.games.trivia

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import com.tdpham.games.common.DailyRewardManager
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SoundManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Modular Trivia Game Engine for Rank 1: Trivia / Quiz.
 * Handles question loading (API with offline JSON & bundled DB fallbacks),
 * timing, lifelines, scoring multipliers, and automated Firebase score tracking.
 */
class TriviaEngine(private val context: Context) {

    companion object {
        private const val TAG = "TriviaEngine"
    }

    enum class EngineState {
        IDLE,
        LOADING,
        QUESTION_ACTIVE,
        ANSWER_REVEALED,
        GAUNTLET_COMPLETED
    }

    enum class LifelineType {
        FIFTY_FIFTY,
        FREEZE_TIMER,
        SKIP_QUESTION
    }

    interface TriviaEngineListener {
        fun onEngineStateChanged(state: EngineState)
        fun onQuestionReady(question: TriviaQuestion, index: Int, total: Int)
        fun onTimerTick(remainingSeconds: Float, timeFraction: Float)
        fun onAnswerEvaluated(
            isCorrect: Boolean,
            selectedIndex: Int,
            correctIndex: Int,
            pointsEarned: Int,
            currentScore: Int,
            streak: Int,
            explanation: String
        )
        fun onLifelineUsed(type: LifelineType, eliminatedIndices: Set<Int>)
        fun onGauntletCompleted(
            finalScore: Int,
            correctCount: Int,
            totalQuestions: Int,
            isNewHighScore: Boolean
        )
    }

    var listener: TriviaEngineListener? = null

    // State
    var state = EngineState.IDLE
        private set

    val activeQuestions = mutableListOf<TriviaQuestion>()
    var currentIndex = 0
        private set
    var currentQuestion: TriviaQuestion? = null
        private set

    var score = 0
        private set
    var streak = 0
        private set
    var correctCount = 0
        private set
    var gauntletLength = 10
        private set
    var timeLimitSeconds = 15
        private set
    var remainingTimeSeconds = 15f
        private set
    var isUntimed = false
        private set

    var categoryFilter: String = "ALL"

    // Lifeline availability
    var is5050Used = false
        private set
    var isFreezeUsed = false
        private set
    var isSkipUsed = false
        private set
    var isTimerFrozen = false
        private set

    val eliminatedIndices = mutableSetOf<Int>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var timerRunnable: Runnable? = null
    private var advanceRunnable: Runnable? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        val prefs = context.getSharedPreferences("trivia_settings", Context.MODE_PRIVATE)
        val catIdx = prefs.getInt(TriviaOptionsDialog.KEY_CATEGORY, 0)
        categoryFilter = when (catIdx) {
            1 -> "Gaming"
            2 -> "Science"
            3 -> "Pop Culture"
            4 -> "History"
            else -> "ALL"
        }
        val timerMode = prefs.getInt(TriviaOptionsDialog.KEY_TIMER_MODE, 0)
        timeLimitSeconds = when (timerMode) {
            0 -> 15
            1 -> 30
            2 -> 8
            else -> 0
        }
        isUntimed = (timeLimitSeconds == 0)
        gauntletLength = prefs.getInt(TriviaOptionsDialog.KEY_GAUNTLET_LENGTH, 10)
    }

    /**
     * Starts a new gauntlet by loading questions from API (with local JSON fallback).
     */
    fun startGauntlet(fetchFromApi: Boolean = true) {
        stopTimer()
        mainHandler.removeCallbacksAndMessages(null)
        loadSettings()

        state = EngineState.LOADING
        listener?.onEngineStateChanged(state)

        score = 0
        streak = 0
        correctCount = 0
        currentIndex = 0
        is5050Used = false
        isFreezeUsed = false
        isSkipUsed = false
        isTimerFrozen = false
        eliminatedIndices.clear()

        if (fetchFromApi) {
            backgroundExecutor.execute {
                val questions = fetchQuestionsFromApiOrFallback()
                mainHandler.post {
                    setupQuestions(questions)
                }
            }
        } else {
            val questions = loadQuestionsFromLocalAssetsOrDb()
            setupQuestions(questions)
        }
    }

    private fun setupQuestions(questions: List<TriviaQuestion>) {
        activeQuestions.clear()
        activeQuestions.addAll(questions.take(gauntletLength))

        if (activeQuestions.isEmpty()) {
            activeQuestions.addAll(TriviaDatabase.QUESTIONS.shuffled().take(gauntletLength))
        }

        currentIndex = 0
        SoundManager.playProfileSound(SoundManager.SoundProfile.TRIVIA_QUIZ, SoundManager.GameSoundEvent.START)
        presentCurrentQuestion()
    }

    private fun presentCurrentQuestion() {
        if (currentIndex >= activeQuestions.size) {
            finishGauntlet()
            return
        }

        currentQuestion = activeQuestions[currentIndex]
        eliminatedIndices.clear()
        isTimerFrozen = false
        remainingTimeSeconds = timeLimitSeconds.toFloat()
        state = EngineState.QUESTION_ACTIVE

        listener?.onEngineStateChanged(state)
        currentQuestion?.let { q ->
            listener?.onQuestionReady(q, currentIndex, activeQuestions.size)
        }

        if (!isUntimed) {
            startTimer()
        }
    }

    private fun startTimer() {
        stopTimer()
        timerRunnable = object : Runnable {
            override fun run() {
                if (state != EngineState.QUESTION_ACTIVE) return
                if (!isTimerFrozen) {
                    remainingTimeSeconds -= 0.1f
                    if (remainingTimeSeconds < 0f) remainingTimeSeconds = 0f

                    val frac = if (timeLimitSeconds > 0) (remainingTimeSeconds / timeLimitSeconds).coerceIn(0f, 1f) else 1f
                    listener?.onTimerTick(remainingTimeSeconds, frac)

                    if (remainingTimeSeconds <= 3.5f && remainingTimeSeconds > 0.1f && (remainingTimeSeconds * 10).toInt() % 10 == 0) {
                        SoundManager.playTriviaTick()
                    }

                    if (remainingTimeSeconds <= 0f) {
                        // Time out -> answer wrong
                        evaluateAnswer(-1)
                        return
                    }
                }
                mainHandler.postDelayed(this, 100)
            }
        }
        mainHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { mainHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    /**
     * Evaluates selected answer choice (0..3). -1 indicates timeout.
     */
    fun evaluateAnswer(selectedIdx: Int) {
        if (state != EngineState.QUESTION_ACTIVE) return
        stopTimer()

        val q = currentQuestion ?: return
        val isCorrect = (selectedIdx == q.correctIndex)
        state = EngineState.ANSWER_REVEALED
        listener?.onEngineStateChanged(state)

        var pointsEarned = 0
        if (isCorrect) {
            streak++
            correctCount++
            val timeBonus = if (!isUntimed) (remainingTimeSeconds * 10f).toInt() else 25
            val streakBonus = (streak - 1) * 20
            pointsEarned = 100 + timeBonus + streakBonus
            score += pointsEarned
            DailyRewardManager.addCoins(context, 5 + (streak * 2))
            SoundManager.playProfileSound(SoundManager.SoundProfile.TRIVIA_QUIZ, SoundManager.GameSoundEvent.SCORE)
        } else {
            streak = 0
            SoundManager.playProfileSound(SoundManager.SoundProfile.TRIVIA_QUIZ, SoundManager.GameSoundEvent.ERROR)
        }

        listener?.onAnswerEvaluated(
            isCorrect = isCorrect,
            selectedIndex = selectedIdx,
            correctIndex = q.correctIndex,
            pointsEarned = pointsEarned,
            currentScore = score,
            streak = streak,
            explanation = q.explanation
        )

        val nextTask = Runnable {
            currentIndex++
            presentCurrentQuestion()
        }
        advanceRunnable = nextTask
        mainHandler.postDelayed(nextTask, 2800L)
    }

    /**
     * Executes 50:50 lifeline to eliminate 2 incorrect answers.
     */
    fun use5050Lifeline(): Boolean {
        if (state != EngineState.QUESTION_ACTIVE || is5050Used) return false
        val q = currentQuestion ?: return false

        val incorrectIndices = (0..3).filter { it != q.correctIndex }.shuffled()
        eliminatedIndices.addAll(incorrectIndices.take(2))
        is5050Used = true
        SoundManager.playTriviaLifeline()
        listener?.onLifelineUsed(LifelineType.FIFTY_FIFTY, eliminatedIndices)
        return true
    }

    /**
     * Executes Freeze Timer lifeline.
     */
    fun useFreezeLifeline(): Boolean {
        if (state != EngineState.QUESTION_ACTIVE || isFreezeUsed || isUntimed) return false
        isTimerFrozen = true
        isFreezeUsed = true
        SoundManager.playTriviaLifeline()
        listener?.onLifelineUsed(LifelineType.FREEZE_TIMER, eliminatedIndices)
        return true
    }

    /**
     * Executes Skip Question lifeline.
     */
    fun useSkipLifeline(): Boolean {
        if (state != EngineState.QUESTION_ACTIVE || isSkipUsed) return false
        isSkipUsed = true
        stopTimer()
        SoundManager.playTriviaLifeline()
        listener?.onLifelineUsed(LifelineType.SKIP_QUESTION, eliminatedIndices)
        currentIndex++
        presentCurrentQuestion()
        return true
    }

    private fun finishGauntlet() {
        stopTimer()
        state = EngineState.GAUNTLET_COMPLETED
        listener?.onEngineStateChanged(state)

        // Track and persist score in Local Prefs & Firebase Leaderboard
        val prefs = context.getSharedPreferences("trivia_settings", Context.MODE_PRIVATE)
        val catIdx = prefs.getInt(TriviaOptionsDialog.KEY_CATEGORY, 0)
        val isNewHigh = ScoreManager.updateHighScore(context, "trivia", score, catIdx)

        if (correctCount >= (activeQuestions.size / 2)) {
            SoundManager.playTriviaGauntletWin()
            DailyRewardManager.addCoins(context, 50)
        } else {
            SoundManager.playProfileSound(SoundManager.SoundProfile.TRIVIA_QUIZ, SoundManager.GameSoundEvent.GAME_OVER)
        }

        listener?.onGauntletCompleted(
            finalScore = score,
            correctCount = correctCount,
            totalQuestions = activeQuestions.size,
            isNewHighScore = isNewHigh
        )
    }

    /**
     * Fetches fresh questions from OpenTDB API with automated fallback to bundled JSON/DB.
     */
    private fun fetchQuestionsFromApiOrFallback(): List<TriviaQuestion> {
        val categoryIdParam = when (categoryFilter) {
            "Gaming" -> "&category=15" // Entertainment: Video Games
            "Science" -> "&category=17" // Science & Nature
            "Pop Culture" -> "&category=11" // Film
            "History" -> "&category=23" // History
            else -> ""
        }

        val apiUrl = "https://opentdb.com/api.php?amount=${gauntletLength * 2}&type=multiple$categoryIdParam"
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                val json = JSONObject(sb.toString())
                if (json.optInt("response_code", -1) == 0) {
                    val results = json.getJSONArray("results")
                    val parsedList = mutableListOf<TriviaQuestion>()

                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val qText = decodeHtml(item.getString("question"))
                        val cat = decodeHtml(item.optString("category", categoryFilter))
                        val correct = decodeHtml(item.getString("correct_answer"))
                        val incorrectJson = item.getJSONArray("incorrect_answers")
                        val options = mutableListOf<String>()
                        options.add(correct)
                        for (j in 0 until incorrectJson.length()) {
                            options.add(decodeHtml(incorrectJson.getString(j)))
                        }

                        // Shuffle options and remember where the correct answer landed
                        val shuffled = options.shuffled()
                        val correctIdx = shuffled.indexOf(correct)

                        parsedList.add(
                            TriviaQuestion(
                                category = cat,
                                question = qText,
                                options = shuffled,
                                correctIndex = correctIdx,
                                explanation = "Correct answer: $correct"
                            )
                        )
                    }

                    if (parsedList.isNotEmpty()) {
                        Log.d(TAG, "Successfully fetched ${parsedList.size} trivia questions from OpenTDB API")
                        return parsedList
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "API fetch warning (using local fallback): ${e.message}")
        }

        return loadQuestionsFromLocalAssetsOrDb()
    }

    /**
     * Loads questions from bundled assets/trivia_questions.json or TriviaDatabase.kt.
     */
    fun loadQuestionsFromLocalAssetsOrDb(): List<TriviaQuestion> {
        val assetQuestions = mutableListOf<TriviaQuestion>()
        try {
            val inputStream = context.assets.open("trivia_questions.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()

            val jsonArray = JSONArray(sb.toString())
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val cat = item.getString("category")
                val qText = item.getString("question")
                val optsArray = item.getJSONArray("options")
                val opts = mutableListOf<String>()
                for (j in 0 until optsArray.length()) {
                    opts.add(optsArray.getString(j))
                }
                val cIdx = item.getInt("correctIndex")
                val exp = item.optString("explanation", "")

                assetQuestions.add(
                    TriviaQuestion(
                        category = cat,
                        question = qText,
                        options = opts,
                        correctIndex = cIdx,
                        explanation = exp
                    )
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Assets JSON load warning: ${e.message}")
        }

        val combined = (assetQuestions + TriviaDatabase.getQuestionsForCategory(categoryFilter)).distinctBy { it.question }
        val filtered = if (categoryFilter.equals("ALL", ignoreCase = true)) {
            combined
        } else {
            combined.filter { it.category.contains(categoryFilter, ignoreCase = true) }
        }

        return if (filtered.isNotEmpty()) filtered.shuffled() else combined.shuffled()
    }

    private fun decodeHtml(html: String): String {
        return try {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (_: Throwable) {
            html.replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }
    }

    fun release() {
        stopTimer()
        mainHandler.removeCallbacksAndMessages(null)
    }
}

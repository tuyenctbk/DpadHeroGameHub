package com.tdpham.games.trex

import android.graphics.*
import com.tdpham.games.R

internal object TRexDrawer {

    // Shader caches to prevent constant allocation of native objects
    private val shaderCache = mutableMapOf<String, Shader>()

    fun drawObstacle(canvas: Canvas, obs: TRexView.Obstacle, theme: TRexView.Theme, paint: Paint, pathBuffer: Path, isNightMode: Boolean, animationFrame: Int, walkFrame: Int) {
        paint.style = Paint.Style.FILL
        when (obs.type) {
            TRexView.ObstacleType.CACTUS -> {
                paint.color = theme.cactusColor
                val centerX = obs.x + obs.width / 2f
                val stemW = obs.width * 0.4f
                canvas.drawRoundRect(centerX - stemW / 2, obs.y, centerX + stemW / 2, obs.y + obs.height, 10f, 10f, paint)
                
                if (obs.height > 60f) {
                    val armY = obs.y + obs.height * 0.4f
                    canvas.drawRect(centerX - stemW / 2 - 15f, armY, centerX - stemW / 2, armY + 12f, paint)
                    canvas.drawRect(centerX - stemW / 2 - 15f, armY - 15f, centerX - stemW / 2 - 5f, armY, paint)
                    val armY2 = obs.y + obs.height * 0.3f
                    canvas.drawRect(centerX + stemW / 2, armY2, centerX + stemW / 2 + 15f, armY2 + 12f, paint)
                    canvas.drawRect(centerX + stemW / 2 + 5f, armY2 - 20f, centerX + stemW / 2 + 15f, armY2, paint)
                }

                if (obs.variant == 2) {
                    paint.color = Color.parseColor("#F48FB1") // Pink
                    canvas.drawCircle(centerX, obs.y, 8f, paint)
                    if (obs.height > 60f) {
                        canvas.drawCircle(centerX - stemW / 2 - 15f, obs.y + obs.height * 0.4f - 15f, 6f, paint)
                        canvas.drawCircle(centerX + stemW / 2 + 15f, obs.y + obs.height * 0.3f - 20f, 6f, paint)
                    }
                    paint.color = theme.cactusColor
                }

                paint.color = Color.BLACK
                paint.alpha = 40
                canvas.drawRect(centerX - 2f, obs.y + 10f, centerX + 2f, obs.y + obs.height - 10f, paint)
                paint.alpha = 255
            }
            TRexView.ObstacleType.TREE -> {
                paint.color = Color.parseColor("#5D4037")
                val trunkW = obs.width * 0.2f
                canvas.drawRect(obs.x + (obs.width - trunkW) / 2f, obs.y + obs.height * 0.5f, obs.x + (obs.width + trunkW) / 2f, obs.y + obs.height, paint)
                
                paint.color = theme.treeColor
                when(obs.variant % 3) {
                    0 -> { // Pine
                        pathBuffer.reset()
                        for (i in 0..2) {
                            val ty = obs.y + (i * obs.height * 0.2f)
                            val th = obs.height * 0.4f
                            pathBuffer.moveTo(obs.x + obs.width / 2f, ty)
                            pathBuffer.lineTo(obs.x, ty + th)
                            pathBuffer.lineTo(obs.x + obs.width, ty + th)
                        }
                        pathBuffer.close()
                        canvas.drawPath(pathBuffer, paint)
                    }
                    1 -> { // Round
                        canvas.drawCircle(obs.x + obs.width / 2f, obs.y + obs.height * 0.3f, obs.width * 0.5f, paint)
                        canvas.drawCircle(obs.x + obs.width * 0.3f, obs.y + obs.height * 0.45f, obs.width * 0.4f, paint)
                        canvas.drawCircle(obs.x + obs.width * 0.7f, obs.y + obs.height * 0.45f, obs.width * 0.4f, paint)
                    }
                    else -> { // Pointy
                        pathBuffer.reset()
                        pathBuffer.moveTo(obs.x + obs.width * 0.5f, obs.y)
                        pathBuffer.lineTo(obs.x, obs.y + obs.height * 0.7f)
                        pathBuffer.lineTo(obs.x + obs.width, obs.y + obs.height * 0.7f)
                        pathBuffer.close()
                        canvas.drawPath(pathBuffer, paint)
                    }
                }
            }
            TRexView.ObstacleType.PTEROSAUR -> {
                val pterosaurColor = when(obs.variant % 3) {
                    0 -> Color.parseColor("#5D4037") 
                    1 -> Color.parseColor("#455A64") 
                    else -> Color.parseColor("#37474F") 
                }
                paint.color = pterosaurColor
                canvas.drawOval(obs.x + 20, obs.y + 20, obs.x + obs.width - 20, obs.y + obs.height - 10, paint)
                
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + 40, obs.y + 25)
                pathBuffer.lineTo(obs.x - 10, obs.y + 15)
                pathBuffer.lineTo(obs.x + 40, obs.y + 45)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
                
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + 40, obs.y + 25)
                pathBuffer.lineTo(obs.x + 60, obs.y + 5)
                pathBuffer.lineTo(obs.x + 50, obs.y + 35)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)

                val wingSpan = if (walkFrame == 0) -40f else 40f
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x + 30, obs.y + 30)
                pathBuffer.lineTo(obs.x + 50, obs.y + 30 + wingSpan)
                pathBuffer.lineTo(obs.x + 70, obs.y + 30)
                pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
            }
            TRexView.ObstacleType.ROCK -> {
                paint.color = Color.parseColor("#757575")
                if (obs.variant == 3) {
                    for (j in 0..4) {
                        val rx = obs.x + (j * obs.width / 5f)
                        val ry = obs.y + obs.height - 15f - (j * 3f % 10f)
                        val rw = 15f + (j * 7f % 10f)
                        canvas.drawRect(rx, ry, rx + rw, obs.y + obs.height, paint)
                    }
                } else {
                    pathBuffer.reset()
                    pathBuffer.moveTo(obs.x, obs.y + obs.height)
                    when(obs.variant % 4) {
                        0 -> {
                            pathBuffer.lineTo(obs.x + obs.width * 0.1f, obs.y + obs.height * 0.4f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.3f, obs.y)
                            pathBuffer.lineTo(obs.x + obs.width * 0.5f, obs.y + obs.height * 0.2f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.7f, obs.y + obs.height * 0.05f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.9f, obs.y + obs.height * 0.5f)
                        }
                        1 -> {
                            pathBuffer.lineTo(obs.x + obs.width * 0.2f, obs.y + obs.height * 0.2f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.4f, obs.y + obs.height * 0.1f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.5f, obs.y)
                            pathBuffer.lineTo(obs.x + obs.width * 0.8f, obs.y + obs.height * 0.3f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.9f, obs.y + obs.height * 0.1f)
                        }
                        else -> {
                            pathBuffer.lineTo(obs.x + obs.width * 0.05f, obs.y + obs.height * 0.3f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.15f, obs.y + obs.height * 0.1f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.4f, obs.y + obs.height * 0.4f)
                            pathBuffer.lineTo(obs.x + obs.width * 0.7f, obs.y)
                            pathBuffer.lineTo(obs.x + obs.width * 0.95f, obs.y + obs.height * 0.2f)
                        }
                    }
                    pathBuffer.lineTo(obs.x + obs.width, obs.y + obs.height)
                    pathBuffer.close()
                    canvas.drawPath(pathBuffer, paint)
                    
                    paint.color = Color.BLACK
                    paint.alpha = 50
                    canvas.drawLine(obs.x + obs.width * 0.3f, obs.y, obs.x + obs.width * 0.4f, obs.y + obs.height * 0.6f, paint)
                    canvas.drawLine(obs.x + obs.width * 0.7f, obs.y + obs.height * 0.05f, obs.x + obs.width * 0.6f, obs.y + obs.height * 0.7f, paint)
                    paint.color = Color.WHITE
                    paint.alpha = 40
                    canvas.drawCircle(obs.x + obs.width * 0.3f, obs.y + obs.height * 0.25f, obs.width * 0.15f, paint)
                }
                paint.alpha = 255
            }
            TRexView.ObstacleType.CANYON -> {
                val lineY = canvas.height * 0.8f
                
                // No more glowing or surface highlights. Just Blue Water.

                // 1. Lake Body (Deep Blue Water)
                paint.color = Color.parseColor("#0288D1")
                pathBuffer.reset()
                pathBuffer.moveTo(obs.x, lineY - 2f)
                when(obs.variant % 3) {
                    0 -> { pathBuffer.lineTo(obs.x + obs.width * 0.1f, lineY + 120f); pathBuffer.lineTo(obs.x + obs.width * 0.9f, lineY + 120f) }
                    1 -> pathBuffer.lineTo(obs.x + obs.width * 0.5f, lineY + 150f)
                    else -> {
                        pathBuffer.lineTo(obs.x + obs.width * 0.2f, lineY + 80f); pathBuffer.lineTo(obs.x + obs.width * 0.4f, lineY + 140f)
                        pathBuffer.lineTo(obs.x + obs.width * 0.6f, lineY + 100f); pathBuffer.lineTo(obs.x + obs.width * 0.8f, lineY + 130f)
                    }
                }
                pathBuffer.lineTo(obs.x + obs.width, lineY - 2f); pathBuffer.close()
                canvas.drawPath(pathBuffer, paint)
                
                // 2. Water surface line (Subtle Reflection)
                paint.color = Color.WHITE
                paint.alpha = 100
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawLine(obs.x + 10f, lineY + 2f, obs.x + obs.width - 10f, lineY + 2f, paint)
                
                paint.style = Paint.Style.FILL
                paint.alpha = 255
            }
            TRexView.ObstacleType.STUMP -> {
                paint.color = Color.parseColor("#5D4037")
                canvas.drawRect(obs.x, obs.y + 10f, obs.x + obs.width, obs.y + obs.height, paint)
                paint.color = Color.parseColor("#8D6E63")
                canvas.drawOval(obs.x, obs.y, obs.x + obs.width, obs.y + 20f, paint)
            }
            TRexView.ObstacleType.METEOR -> {
                val cx = obs.x + obs.width / 2f
                val cy = obs.y + obs.height / 2f
                val r = obs.width / 2f
                val keyGlow = "meteor_glow_$r"
                paint.shader = shaderCache.getOrPut(keyGlow) {
                    RadialGradient(r, r, r * 1.5f, intArrayOf(Color.argb(150, 255, 111, 0), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                }
                canvas.save(); canvas.translate(obs.x - r * 0.5f, obs.y - r * 0.5f)
                canvas.drawCircle(r * 1.5f, r * 1.5f, r * 1.5f, paint)
                canvas.restore()
                
                val keyTail = "meteor_tail_${obs.width}"
                paint.shader = shaderCache.getOrPut(keyTail) {
                    LinearGradient(0f, 0f, obs.width * 1.5f, -obs.height * 1.5f, 
                        intArrayOf(Color.parseColor("#FF5722"), Color.parseColor("#FFD600"), Color.TRANSPARENT), 
                        floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                }
                canvas.save(); canvas.translate(cx, cy)
                pathBuffer.reset(); pathBuffer.moveTo(0f, -r); pathBuffer.lineTo(obs.width * 2f, -obs.height * 2f); pathBuffer.lineTo(r, 0f)
                pathBuffer.close(); canvas.drawPath(pathBuffer, paint)
                canvas.restore()
                
                paint.shader = null
                paint.color = Color.parseColor("#4E342E")
                when(obs.variant) {
                    0 -> canvas.drawCircle(cx, cy, r, paint)
                    else -> canvas.drawOval(obs.x, obs.y + r * 0.2f, obs.x + obs.width, obs.y + obs.height - r * 0.2f, paint)
                }
                paint.color = Color.parseColor("#FFEB3B"); paint.alpha = 200
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.4f, paint)
                paint.alpha = 255
            }
            TRexView.ObstacleType.THUNDERBOLT -> {
                paint.color = Color.parseColor("#FFEA00")
                pathBuffer.reset(); pathBuffer.moveTo(obs.x + obs.width, obs.y)
                pathBuffer.lineTo(obs.x, obs.y + obs.height * 0.6f); pathBuffer.lineTo(obs.x + obs.width * 0.8f, obs.y + obs.height * 0.5f)
                pathBuffer.lineTo(obs.x + obs.width * 0.2f, obs.y + obs.height); canvas.drawPath(pathBuffer, paint)
            }
            TRexView.ObstacleType.FIRE -> {
                val cx = obs.x + obs.width / 2f
                val cy = obs.y + obs.height
                val random = java.util.Random(obs.x.toLong())
                for (i in 0..4) {
                    val fx = cx + (i - 2) * 25f + (Math.sin(animationFrame * 0.2 + i).toFloat() * 10f)
                    val fy = cy - 20f - random.nextInt(100)
                    paint.color = if (random.nextBoolean()) Color.parseColor("#FFD600") else Color.parseColor("#FF3D00")
                    canvas.drawCircle(fx, fy, 20f + random.nextInt(20), paint)
                }
                paint.color = Color.parseColor("#FF6D00"); paint.alpha = 150
                canvas.drawRect(obs.x, cy - 20f, obs.x + obs.width, cy, paint); paint.alpha = 255
            }
            TRexView.ObstacleType.FALLEN_TREE -> {
                paint.color = Color.parseColor("#5D4037")
                canvas.drawRoundRect(obs.x, obs.y + obs.height * 0.5f, obs.x + obs.width, obs.y + obs.height, 10f, 10f, paint)
                pathBuffer.reset(); pathBuffer.moveTo(obs.x, obs.y + obs.height * 0.5f); pathBuffer.lineTo(obs.x + 20f, obs.y)
                pathBuffer.lineTo(obs.x + 40f, obs.y + obs.height * 0.5f); canvas.drawPath(pathBuffer, paint)
            }
            TRexView.ObstacleType.RAISED_EDGE -> {
                paint.color = theme.groundColor
                pathBuffer.reset(); pathBuffer.moveTo(obs.x, obs.y + obs.height); pathBuffer.lineTo(obs.x + obs.width * 0.2f, obs.y)
                pathBuffer.lineTo(obs.x + obs.width * 0.8f, obs.y + 10f); pathBuffer.lineTo(obs.x + obs.width, obs.y + obs.height)
                pathBuffer.close(); canvas.drawPath(pathBuffer, paint)
                paint.color = Color.BLACK; paint.alpha = 100
                canvas.drawLine(obs.x + obs.width * 0.5f, obs.y + 5f, obs.x + obs.width * 0.5f, obs.y + obs.height, paint); paint.alpha = 255
            }
            TRexView.ObstacleType.BIG_DINO -> {
                val dinoColor = when(obs.variant % 3) {
                    0 -> Color.parseColor("#43A047") 
                    1 -> Color.parseColor("#5D4037") 
                    else -> Color.parseColor("#455A64") 
                }
                paint.color = dinoColor
                val p = obs.width / 30f 
                val headSway = Math.sin(animationFrame * 0.1 + obs.variant).toFloat() * 4 * p
                canvas.save(); canvas.translate(obs.x, obs.y)
                if (obs.variant % 2 != 0) canvas.scale(-1f, 1f, obs.width / 2f, obs.height / 2f)
                
                // Body & Tail
                canvas.drawRoundRect(0f, 15*p, 25*p, 28*p, 6*p, 6*p, paint)
                pathBuffer.reset(); pathBuffer.moveTo(0f, 18*p); pathBuffer.lineTo(-10*p, 22*p); pathBuffer.lineTo(0f, 26*p)
                pathBuffer.close(); canvas.drawPath(pathBuffer, paint)

                // Head (Moved by headSway)
                canvas.save(); canvas.translate(0f, headSway)
                when(obs.variant % 3) {
                    0 -> { 
                        canvas.drawRoundRect(20*p, 0f, 32*p, 8*p, 4*p, 4*p, paint)
                        canvas.drawRect(20*p, 6*p, 25*p, 18*p, paint)
                    }
                    1 -> { 
                        canvas.drawRoundRect(25*p, 18*p, 32*p, 24*p, 2*p, 2*p, paint)
                        canvas.drawRoundRect(0f, 15*p, 28*p, 30*p, 8*p, 8*p, paint)
                        for (j in 0..3) canvas.drawRect(5*p + j*5*p, 10*p, 5*p + j*5*p + 3*p, 15*p, paint)
                    }
                    else -> { 
                        canvas.drawRoundRect(18*p, 10*p, 30*p, 18*p, 3*p, 3*p, paint)
                        canvas.drawRoundRect(0f, 18*p, 25*p, 32*p, 5*p, 5*p, paint)
                        pathBuffer.reset(); pathBuffer.moveTo(5*p, 18*p); pathBuffer.lineTo(12*p, 5*p); pathBuffer.lineTo(20*p, 18*p)
                        pathBuffer.close(); canvas.drawPath(pathBuffer, paint)
                    }
                }
                paint.color = Color.BLACK; val eyeX = if (obs.variant % 3 == 0) 22*p else if (obs.variant % 3 == 1) 28*p else 24*p
                val eyeY = if (obs.variant % 3 == 0) 2*p else if (obs.variant % 3 == 1) 20*p else 12*p
                canvas.drawRect(eyeX, eyeY, eyeX + 2*p, eyeY + 2*p, paint); canvas.restore()

                // Legs
                paint.color = dinoColor
                val legH = if (walkFrame == 0) 5*p else 2*p
                canvas.drawRect(5*p, 28*p, 9*p, 28*p + legH, paint); canvas.drawRect(16*p, 28*p, 20*p, 28*p + (7*p - legH), paint)
                canvas.restore()
            }
        }
    }

    fun drawDino(canvas: Canvas, x: Float, y: Float, color: Int, eyeColor: Int, 
                 p: Float, isGameOver: Boolean, causeOfDeath: TRexView.ObstacleType?, 
                 isDucking: Boolean, duckingProgress: Float, isJumping: Boolean, walkFrame: Int,
                 isNightMode: Boolean, animationFrame: Int, obstacles: List<TRexView.Obstacle>,
                 paint: Paint, pathBuffer: Path, member: String) {
        
        var bodyColor = color
        // Special Colors per Member
        bodyColor = when(member) {
            "NINJA" -> Color.parseColor("#212121") // Stealthy Dark Gray
            "ASTRONAUT" -> Color.parseColor("#BDBDBD") // Silver/Moon color
            "MUMMY" -> Color.parseColor("#FFF9C4") // Linen
            "TEENAGER" -> Color.parseColor("#FFCA28") // Yellow
            "CHEF" -> Color.WHITE
            "ATHLETE" -> Color.parseColor("#FF5252") // Red
            "DRAGON" -> Color.parseColor("#43A047") // Green
            "ZOMBIE" -> Color.parseColor("#9E9D24") // Rotten Green
            "ROBOT" -> Color.parseColor("#78909C") // Metallic
            "KING" -> Color.parseColor("#D4AF37") // Gold
            else -> color // DADDY standard color
        }

        if (isGameOver) {
            when(causeOfDeath) {
                TRexView.ObstacleType.FIRE, TRexView.ObstacleType.METEOR, TRexView.ObstacleType.THUNDERBOLT -> bodyColor = Color.DKGRAY
                else -> {}
            }
        }
        paint.color = bodyColor; paint.style = Paint.Style.FILL
        canvas.save()
        var drawY = y; var drawX = x
        if (isGameOver && causeOfDeath == TRexView.ObstacleType.CANYON) {
            val dinoCenter = 100f + 12 * p
            val foundCanyon = obstacles.find { it.type == TRexView.ObstacleType.CANYON && dinoCenter > it.x && dinoCenter < it.x + it.width }
            if (foundCanyon != null) drawX = foundCanyon.x + (foundCanyon.width / 2f) - (12 * p)
            drawY += 24 * p; canvas.rotate(-45f, drawX + 12*p, drawY + 10*p)
        } else if (isGameOver) {
            val rotation = when {
                causeOfDeath == TRexView.ObstacleType.PTEROSAUR -> -90f 
                isJumping -> -35f 
                isDucking -> 25f 
                causeOfDeath == TRexView.ObstacleType.FIRE || causeOfDeath == TRexView.ObstacleType.METEOR -> 5f 
                else -> 10f 
            }
            canvas.rotate(rotation, drawX + 12*p, drawY + 10*p)
        }
        canvas.translate(drawX, drawY)
        
        // Squish effect for "lower and lower" ducking
        if (duckingProgress > 0) {
            val squash = 1f - (0.7f * duckingProgress)
            canvas.scale(1f, squash, 12 * p, 23 * p)
        }

        val pathPaint = Paint(paint)
        fun drawPaths() {
            if (isDucking || duckingProgress > 0.5f) {
                pathBuffer.reset(); pathBuffer.moveTo(0f, 8*p); pathBuffer.lineTo(20*p, 8*p); pathBuffer.lineTo(26*p, 4*p)
                pathBuffer.lineTo(32*p, 4*p); pathBuffer.lineTo(32*p, 10*p); pathBuffer.lineTo(24*p, 14*p); pathBuffer.lineTo(0f, 14*p)
                pathBuffer.close(); canvas.drawPath(pathBuffer, pathPaint)
                pathBuffer.reset(); pathBuffer.moveTo(0f, 8*p); pathBuffer.lineTo(-10*p, 8*p); pathBuffer.lineTo(0f, 12*p)
                pathBuffer.close(); canvas.drawPath(pathBuffer, pathPaint)
            } else {
                pathBuffer.reset(); pathBuffer.moveTo(12*p, 0f); pathBuffer.lineTo(26*p, 0f); pathBuffer.lineTo(26*p, 8*p)
                pathBuffer.lineTo(16*p, 8*p); pathBuffer.lineTo(16*p, 18*p); pathBuffer.lineTo(10*p, 18*p); pathBuffer.lineTo(10*p, 4*p)
                pathBuffer.close(); canvas.drawPath(pathBuffer, pathPaint)
                pathBuffer.reset(); pathBuffer.moveTo(0f, 8*p); pathBuffer.lineTo(16*p, 8*p); pathBuffer.lineTo(16*p, 18*p)
                pathBuffer.lineTo(0f, 18*p); pathBuffer.close(); canvas.drawPath(pathBuffer, pathPaint)
                pathBuffer.reset(); pathBuffer.moveTo(0f, 8*p); pathBuffer.lineTo(-14*p, 8*p); pathBuffer.lineTo(0f, 14*p)
                pathBuffer.close(); canvas.drawPath(pathBuffer, pathPaint)
            }
        }
        val isBrightSkin = bodyColor == Color.WHITE || bodyColor == Color.parseColor("#BDBDBD")
        if (isBrightSkin) {
            pathPaint.style = Paint.Style.STROKE; pathPaint.strokeWidth = 0.5f * p
            pathPaint.color = if (isNightMode) Color.argb(180, (Color.red(bodyColor) + 50).coerceAtMost(255), (Color.green(bodyColor) + 50).coerceAtMost(255), (Color.blue(bodyColor) + 50).coerceAtMost(255))
            else Color.argb(180, (Color.red(bodyColor) * 0.7f).toInt(), (Color.green(bodyColor) * 0.7f).toInt(), (Color.blue(bodyColor) * 0.7f).toInt())
            drawPaths()
        }
        pathPaint.style = Paint.Style.FILL; pathPaint.color = bodyColor; pathPaint.alpha = 255; drawPaths()
        
        // Eye and Legs
        if (isDucking) {
            paint.color = if (isGameOver) Color.BLACK else eyeColor; canvas.drawRect(24*p, 5*p, 26*p, 7*p, paint)
            if (isGameOver) { 
                paint.color = Color.WHITE; paint.strokeWidth = 2f
                canvas.drawLine(24*p, 5*p, 26*p, 7*p, paint); canvas.drawLine(26*p, 5*p, 24*p, 7*p, paint)
            }
            paint.color = bodyColor
            if (walkFrame == 0) canvas.drawRect(6*p, 14*p, 10*p, 16*p, paint)
            else canvas.drawRect(14*p, 14*p, 18*p, 16*p, paint)
        } else {
            canvas.drawRect(16*p, 10*p, 19*p, 12*p, paint)
            paint.color = if (isGameOver) Color.BLACK else eyeColor; canvas.drawRect(14*p, 2*p, 16*p, 4*p, paint)
            if (isGameOver) {
                paint.color = Color.WHITE; paint.strokeWidth = 2f
                canvas.drawLine(14*p, 2*p, 16*p, 4*p, paint); canvas.drawLine(16*p, 2*p, 14*p, 4*p, paint)
            }
            paint.color = bodyColor; val ly = 18*p
            if (isJumping) { canvas.drawRect(4*p, ly, 7*p, ly + 3*p, paint); canvas.drawRect(10*p, ly, 13*p, ly + 3*p, paint) }
            else {
                if (walkFrame == 0) { canvas.drawRect(4*p, ly, 7*p, ly + 5*p, paint); canvas.drawRect(10*p, ly, 13*p, ly + 2*p, paint) }
                else { canvas.drawRect(4*p, ly, 7*p, ly + 2*p, paint); canvas.drawRect(10*p, ly, 13*p, ly + 5*p, paint) }
            }
        }

        // Character Accessories
        paint.style = Paint.Style.FILL
        val headX = if (isDucking) 24*p else 14*p
        val headY = if (isDucking) 4*p else 0f
        
        when(member) {
            "NINJA" -> {
                paint.color = Color.RED // Ninja Headband
                canvas.drawRect(headX - 2*p, headY + p, headX + 12*p, headY + 3*p, paint)
                // Headband tail
                pathBuffer.reset(); pathBuffer.moveTo(headX, headY + 2*p)
                val tailSway = Math.sin(animationFrame * 0.2).toFloat() * 4*p
                pathBuffer.lineTo(headX - 8*p, headY + 2*p + tailSway); pathBuffer.lineTo(headX - 6*p, headY + 5*p + tailSway)
                canvas.drawPath(pathBuffer, paint)
            }
            "ASTRONAUT" -> {
                paint.color = Color.argb(100, 129, 212, 250)
                canvas.drawCircle(headX + 5*p, headY + 4*p, 10*p, paint)
                paint.style = Paint.Style.STROKE; paint.color = Color.WHITE; paint.strokeWidth = 1f * p
                canvas.drawCircle(headX + 5*p, headY + 4*p, 10*p, paint)
                paint.style = Paint.Style.FILL
                // Oxygen tank
                paint.color = Color.LTGRAY; canvas.drawRoundRect(headX - 12*p, headY + 8*p, headX - 4*p, headY + 16*p, 2*p, 2*p, paint)
            }
            "ATHLETE" -> {
                paint.color = Color.WHITE; canvas.drawRect(headX - p, headY - p, headX + 11*p, headY + 2*p, paint)
                paint.textSize = 6*p; paint.color = Color.BLACK; canvas.drawText("1", headX + 4*p, headY + 12*p, paint)
            }
            "BABY" -> {
                paint.color = Color.parseColor("#4FC3F7") // Blue pacifier
                canvas.drawCircle(headX + 10*p, headY + 6*p, 2*p, paint)
                paint.color = Color.parseColor("#F8BBD0"); canvas.drawCircle(headX + 5*p, headY - 2*p, 3*p, paint)
            }
            "SCIENTIST" -> {
                paint.color = Color.BLACK; canvas.drawRect(headX + 2*p, headY + 6*p, headX + 8*p, headY + 8*p, paint)
                paint.color = Color.WHITE; canvas.drawRect(headX + 4*p, headY + 7*p, headX + 6*p, headY + 8*p, paint)
                // Bubbling Flask
                if (animationFrame % 20 < 10) {
                    paint.color = Color.CYAN; canvas.drawCircle(headX + 12*p, headY + 12*p - (animationFrame % 10), 2*p, paint)
                }
            }
            "DRAGON" -> {
                paint.color = Color.parseColor("#1B5E20")
                pathBuffer.reset(); pathBuffer.moveTo(headX, headY); pathBuffer.lineTo(headX - 4*p, headY - 4*p)
                pathBuffer.lineTo(headX, headY - 8*p); pathBuffer.close(); canvas.drawPath(pathBuffer, paint)
                // Wings
                val wingSway = Math.sin(animationFrame * 0.15).toFloat() * 10*p
                pathBuffer.reset(); pathBuffer.moveTo(headX - 10*p, headY + 10*p)
                pathBuffer.lineTo(headX - 25*p, headY + wingSway); pathBuffer.lineTo(headX - 15*p, headY + 20*p); canvas.drawPath(pathBuffer, paint)
            }
            "ROBOT" -> {
                paint.color = Color.RED; canvas.drawCircle(headX + 2*p, headY + 3*p, p, paint)
                paint.color = Color.BLACK; canvas.drawRect(headX - p, headY - 4*p, headX + p, headY, paint)
                // Antenna light
                if ((animationFrame / 15) % 2 == 0) {
                    paint.color = Color.YELLOW; canvas.drawCircle(headX, headY - 5*p, 2*p, paint)
                }
            }
            "ZOMBIE" -> {
                paint.color = Color.parseColor("#689F38"); canvas.drawCircle(headX + 3*p, headY + 12*p, 2*p, paint)
                paint.color = Color.BLACK; canvas.drawRect(headX + 8*p, headY + 2*p, headX + 10*p, headY + 4*p, paint) // Missing eye
            }
            "KING" -> {
                paint.color = Color.parseColor("#FFD600")
                pathBuffer.reset(); pathBuffer.moveTo(headX, headY); pathBuffer.lineTo(headX, headY - 6*p)
                pathBuffer.lineTo(headX + 3*p, headY - 4*p); pathBuffer.lineTo(headX + 6*p, headY - 10*p)
                pathBuffer.lineTo(headX + 9*p, headY - 4*p); pathBuffer.lineTo(headX + 12*p, headY - 6*p)
                pathBuffer.lineTo(headX + 12*p, headY); pathBuffer.close(); canvas.drawPath(pathBuffer, paint)
            }
            "CHEF" -> {
                paint.color = Color.WHITE; canvas.drawRoundRect(headX, headY - 8*p, headX + 12*p, headY, 2*p, 2*p, paint)
                paint.color = Color.LTGRAY; canvas.drawRect(headX + 12*p, headY + 10*p, headX + 18*p, headY + 12*p, paint) // Spatula
            }
            "MUMMY" -> {
                paint.color = Color.parseColor("#BDBDBD")
                for (i in 0..2) canvas.drawLine(headX, headY + i*3*p, headX + 10*p, headY + i*3*p, paint)
                // Loose bandage
                val looseSway = Math.sin(animationFrame * 0.1).toFloat() * 5*p
                canvas.drawLine(headX - 5*p, headY + 15*p, headX - 15*p, headY + 15*p + looseSway, paint)
            }
            "PIRATE" -> {
                paint.color = Color.BLACK; canvas.drawRect(headX - p, headY + 2*p, headX + 3*p, headY + 5*p, paint)
                paint.strokeWidth = 0.8f * p; canvas.drawLine(headX - 4*p, headY + 3*p, headX + 12*p, headY + p, paint)
                // Earring
                paint.color = Color.YELLOW; paint.style = Paint.Style.STROKE; canvas.drawCircle(headX + 12*p, headY + 8*p, 2*p, paint); paint.style = Paint.Style.FILL
            }
            "GRANDPA" -> {
                paint.color = Color.BLACK; paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.5f * p
                canvas.drawCircle(headX + 2*p, headY + 3*p, 3*p, paint)
                canvas.drawCircle(headX + 8*p, headY + 3*p, 3*p, paint)
                paint.style = Paint.Style.FILL
                // Cane
                paint.color = Color.parseColor("#5D4037"); canvas.drawRect(headX + 10*p, headY + 10*p, headX + 12*p, headY + 20*p, paint)
            }
        }

        if (isGameOver && (causeOfDeath == TRexView.ObstacleType.TREE || causeOfDeath == TRexView.ObstacleType.CACTUS || causeOfDeath == TRexView.ObstacleType.ROCK)) {
            paint.color = Color.YELLOW; val starRot = (animationFrame * 5) % 360
            for (i in 0..2) {
                val angle = Math.toRadians((starRot + i * 120).toDouble())
                val sx = 18*p + Math.cos(angle).toFloat() * 15*p
                val sy = -5*p + Math.sin(angle).toFloat() * 5*p
                canvas.drawCircle(sx, sy, 2*p, paint)
            }
        }
        canvas.restore()
    }
}

package com.barsense.game

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight on-device frame analyser.
 *
 * It looks for red obstacle columns and tracks the largest moving,
 * non-red connected component as a player candidate. This is a heuristic,
 * not a trained detector; Auto mode only acts when confidence is adequate.
 */
class GameVisionEngine {
    data class FrameResult(
        val eggFound: Boolean,
        val eggX: Int,
        val eggY: Int,
        val obstacleAhead: Boolean,
        val gapY: Int,
        val shouldTap: Boolean,
        val confidence: Int,
        val status: String
    )

    private val gridW = 160
    private val gridH = 90
    private var previous = IntArray(gridW * gridH)
    private var hasPrevious = false
    private var lastEggX = -1
    private var lastEggY = -1
    private var lastTapAt = 0L

    @Synchronized
    fun analyze(width: Int, height: Int, buffer: ByteBuffer, rowStride: Int, pixelStride: Int): FrameResult {
        if (width <= 0 || height <= 0) return empty("Invalid frame")
        val rgb = IntArray(gridW * gridH)
        val red = BooleanArray(gridW * gridH)
        val motion = BooleanArray(gridW * gridH)
        val base = buffer.position()
        for (gy in 0 until gridH) {
            val sy = min(height - 1, gy * height / gridH)
            for (gx in 0 until gridW) {
                val sx = min(width - 1, gx * width / gridW)
                val idx = base + sy * rowStride + sx * pixelStride
                if (idx + 2 >= buffer.limit()) continue
                val r = buffer.get(idx).toInt() and 255
                val g = buffer.get(idx + 1).toInt() and 255
                val b = buffer.get(idx + 2).toInt() and 255
                val p = gy * gridW + gx
                rgb[p] = (r shl 16) or (g shl 8) or b
                red[p] = r > 130 && r > g * 1.35 && r > b * 1.25
                if (hasPrevious) {
                    val old = previous[p]
                    val dr = abs(r - ((old shr 16) and 255))
                    val dg = abs(g - ((old shr 8) and 255))
                    val db = abs(b - (old and 255))
                    motion[p] = (dr + dg + db) > 105 && !red[p]
                }
            }
        }
        previous = rgb
        val firstFrame = !hasPrevious
        hasPrevious = true
        if (firstFrame) return empty("Learning scene")

        // Connected components of changed non-red pixels; reject tiny noise.
        val visited = BooleanArray(gridW * gridH)
        var bestArea = 0
        var bestX = -1
        var bestY = -1
        val queue = IntArray(gridW * gridH)
        for (start in 0 until motion.size) {
            if (!motion[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var sx = 0
            var sy = 0
            var area = 0
            while (head < tail) {
                val p = queue[head++]
                val x = p % gridW
                val y = p / gridW
                area++
                sx += x
                sy += y
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 1 until gridW - 1 || ny !in 1 until gridH - 1) continue
                    val np = ny * gridW + nx
                    if (motion[np] && !visited[np]) {
                        visited[np] = true
                        queue[tail++] = np
                    }
                }
            }
            if (area in 4..180) {
                val cx = sx / area
                val cy = sy / area
                val nearTrack = lastEggX < 0 || (abs(cx - lastEggX) < 34 && abs(cy - lastEggY) < 28)
                val weighted = if (nearTrack) area + 40 else area
                if (weighted > bestArea) {
                    bestArea = weighted
                    bestX = cx
                    bestY = cy
                }
            }
        }
        val eggFound = bestX >= 0 && bestArea >= 8
        if (eggFound) {
            lastEggX = if (lastEggX < 0) bestX else (lastEggX * 2 + bestX) / 3
            lastEggY = if (lastEggY < 0) bestY else (lastEggY * 2 + bestY) / 3
        }

        // Find red-heavy columns ahead of the tracked player.
        val eggX = if (eggFound) lastEggX else -1
        val redByColumn = IntArray(gridW)
        for (x in 0 until gridW) {
            for (y in 8 until gridH - 8) if (red[y * gridW + x]) redByColumn[x]++
        }
        var obstacleX = -1
        val startX = if (eggX >= 0) max(0, eggX + 8) else gridW / 3
        for (x in startX until gridW - 2) {
            if (redByColumn[x] >= 7 && redByColumn[x + 1] >= 7) {
                obstacleX = x
                break
            }
        }
        var gapY = -1
        var bestRun = 0
        if (obstacleX >= 0) {
            var runStart = -1
            for (y in 8 until gridH - 8) {
                var redCount = 0
                for (x in obstacleX..min(gridW - 1, obstacleX + 3)) {
                    if (red[y * gridW + x]) redCount++
                }
                val isGap = redCount == 0
                if (isGap && runStart < 0) runStart = y
                if ((!isGap || y == gridH - 9) && runStart >= 0) {
                    val end = if (isGap) y else y - 1
                    val run = end - runStart + 1
                    if (run > bestRun) {
                        bestRun = run
                        gapY = (runStart + end) / 2
                    }
                    runStart = -1
                }
            }
        }
        val distance = if (obstacleX >= 0 && eggX >= 0) obstacleX - eggX else Int.MAX_VALUE
        val confident = eggFound && obstacleX >= 0 && bestRun >= 7
        val needsUpwardMove = confident && gapY < lastEggY - 7
        val now = System.currentTimeMillis()
        val tap = needsUpwardMove && distance in 8..25 && now - lastTapAt > 700
        if (tap) lastTapAt = now
        val confidence = (if (eggFound) 55 else 0) + (if (obstacleX >= 0) 25 else 0) + (if (bestRun >= 7) 20 else 0)
        val status = when {
            !eggFound -> "Searching for moving player"
            obstacleX < 0 -> "Player tracked • waiting for obstacle"
            bestRun < 7 -> "Obstacle found • gap unclear"
            tap -> "Jump signal"
            else -> "Tracking • gap detected"
        }
        return FrameResult(eggFound, lastEggX, lastEggY, obstacleX >= 0, gapY, tap, confidence, status)
    }

    private fun empty(message: String) = FrameResult(false, -1, -1, false, -1, false, 0, message)
}

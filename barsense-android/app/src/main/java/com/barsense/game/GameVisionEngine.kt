package com.barsense.game

import kotlin.math.abs

/**
 * Deterministic, non-ML game-frame analyser.
 * Uses temporal luminance change for a motion candidate and red-column geometry
 * for obstacle gaps. It deliberately reports confidence instead of guessing.
 */
class GameVisionEngine {
    data class Result(
        val motionX: Int, val motionY: Int, val motionStrength: Int,
        val barColumns: List<Int>, val gapTop: Int?, val gapBottom: Int?,
        val status: String
    )

    private var previous = IntArray(0)
    private var previousW = 0
    private var previousH = 0
    private var trackedX = -1
    private var trackedY = -1

    fun analyze(width: Int, height: Int, rgba: ByteArray, pixelStride: Int, rowStride: Int): Result {
        val step = 4
        val sw = width / step
        val sh = height / step
        val gray = IntArray(sw * sh)
        val red = IntArray(sw)
        for (sy in 0 until sh) {
            val y = sy * step
            for (sx in 0 until sw) {
                val x = sx * step
                val i = y * rowStride + x * pixelStride
                if (i + 2 >= rgba.size) continue
                val r = rgba[i].toInt() and 255
                val g = rgba[i + 1].toInt() and 255
                val b = rgba[i + 2].toInt() and 255
                gray[sy * sw + sx] = (r * 30 + g * 59 + b * 11) / 100
                if (r > 135 && r > g * 1.35 && r > b * 1.25) red[sx]++
            }
        }

        val bars = red.indices.filter { red[it] >= (sh * 0.035).toInt().coerceAtLeast(3) }
        val merged = ArrayList<IntRange>()
        for (x in bars) {
            val last = merged.lastOrNull()
            if (last != null && x <= last.last + 2) merged[merged.lastIndex] = last.first..x
            else merged.add(x..x)
        }
        val validBars = merged.filter { it.last - it.first >= 2 }
        val centers = validBars.map { ((it.first + it.last) / 2) * step }

        var mx = -1
        var my = -1
        var motion = 0
        if (previousW == sw && previousH == sh && previous.size == gray.size) {
            // Track local motion around the last candidate; initially search the left 65%.
            val x0 = if (trackedX >= 0) (trackedX / step - 28).coerceAtLeast(0) else 0
            val x1 = if (trackedX >= 0) (trackedX / step + 28).coerceAtMost(sw) else (sw * 0.65).toInt()
            val y0 = if (trackedY >= 0) (trackedY / step - 28).coerceAtLeast(0) else (sh * 0.08).toInt()
            val y1 = if (trackedY >= 0) (trackedY / step + 28).coerceAtMost(sh) else (sh * 0.92).toInt()
            var sxSum = 0L; var sySum = 0L; var weight = 0L
            for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
                val idx = y * sw + x
                val delta = abs(gray[idx] - previous[idx])
                if (delta >= 24) {
                    sxSum += x.toLong() * delta
                    sySum += y.toLong() * delta
                    weight += delta
                }
            }
            motion = weight.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (weight > 0) {
                mx = ((sxSum / weight).toInt()) * step
                my = ((sySum / weight).toInt()) * step
                trackedX = mx; trackedY = my
            }
        }
        previous = gray; previousW = sw; previousH = sh

        // Estimate the largest vertical non-red run between adjacent detected red columns.
        val gap = if (centers.size >= 2) {
            val x = (centers.first() / step).coerceIn(0, sw - 1)
            var top = -1; var bottom = -1; var best = 0; var runStart = -1
            for (y in 0 until sh) {
                val idx = y * sw + x
                val isRed = red[x] > 0 && (gray[idx] < 100 || gray[idx] > 100) // column is evaluated below via frame red test
                val pixel = idx * step * rowStride / maxOf(1, rowStride) // keep index bounded
                val byteIndex = (y * step) * rowStride + (x * step) * pixelStride
                val rr = if (byteIndex + 2 < rgba.size) rgba[byteIndex].toInt() and 255 else 0
                val gg = if (byteIndex + 2 < rgba.size) rgba[byteIndex+1].toInt() and 255 else 0
                val bb = if (byteIndex + 2 < rgba.size) rgba[byteIndex+2].toInt() and 255 else 0
                val obstacle = rr > 135 && rr > gg * 1.35 && rr > bb * 1.25
                if (!obstacle) { if (runStart < 0) runStart = y }
                else if (runStart >= 0) {
                    if (y - runStart > best) { best = y-runStart; top=runStart*step; bottom=y*step }
                    runStart = -1
                }
            }
            if (runStart >= 0 && sh-runStart > best) { top=runStart*step; bottom=height }
            if (top >= 0) top to bottom else null
        } else null

        return Result(mx, my, motion, centers, gap?.first, gap?.second,
            if (mx >= 0 && centers.isNotEmpty()) "TRACKING CANDIDATE" else "CALIBRATION NEEDED")
    }

    fun reset() { previous = IntArray(0); previousW = 0; previousH = 0; trackedX = -1; trackedY = -1 }
}

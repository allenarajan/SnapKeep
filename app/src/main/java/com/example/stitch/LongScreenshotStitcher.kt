package com.example.stitch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StitchPlan(
  val rawFrames: List<Bitmap>,
  val preparedFrames: List<Bitmap>,
  val overlapsPx: List<Int>,
  val trimSystemBars: Boolean = true,
) {
  val totalEstimatedHeight: Int
    get() {
      if (preparedFrames.isEmpty()) return 0
      var total = preparedFrames[0].height
      for (i in 1 until preparedFrames.size) {
        val maxOv = (preparedFrames[i].height - 40).coerceAtLeast(0)
        val overlap = overlapsPx.getOrElse(i - 1) { 0 }.coerceIn(0, maxOv)
        total += (preparedFrames[i].height - overlap).coerceAtLeast(20)
      }
      return total
    }
}

object LongScreenshotStitcher {

  private const val MAX_OUTPUT_WIDTH = 1080
  private const val MAX_OUTPUT_HEIGHT = 16000
  private const val SAMPLE_WIDTH = 144

  suspend fun createStitchPlan(
    inputFrames: List<Bitmap>,
    trimSystemBars: Boolean = true,
  ): StitchPlan =
    withContext(Dispatchers.Default) {
      require(inputFrames.isNotEmpty()) { "At least one frame is required" }

      val targetWidth = min(inputFrames.first().width, MAX_OUTPUT_WIDTH).coerceAtLeast(320)
      val normalized =
        inputFrames.map { bmp ->
          if (bmp.width != targetWidth) {
            val scaledH = (bmp.height * (targetWidth.toFloat() / bmp.width)).toInt().coerceAtLeast(100)
            Bitmap.createScaledBitmap(bmp, targetWidth, scaledH, true)
          } else {
            bmp
          }
        }

      val prepared =
        if (normalized.size == 1 || !trimSystemBars) {
          normalized
        } else {
          normalized.mapIndexed { index, bmp ->
            val topCrop = if (index == 0) 0 else (bmp.height * 0.065f).toInt()
            val bottomCrop = if (index == normalized.lastIndex) 0 else (bmp.height * 0.065f).toInt()
            val newH = (bmp.height - topCrop - bottomCrop).coerceAtLeast(120)
            Bitmap.createBitmap(bmp, 0, topCrop, bmp.width, newH)
          }
        }

      val overlaps =
        if (prepared.size < 2) {
          emptyList()
        } else {
          (0 until prepared.size - 1).map { i ->
            detectBestOverlap(prepared[i], prepared[i + 1])
          }
        }

      StitchPlan(
        rawFrames = normalized,
        preparedFrames = prepared,
        overlapsPx = overlaps,
        trimSystemBars = trimSystemBars,
      )
    }

  suspend fun renderBitmap(plan: StitchPlan): Bitmap =
    withContext(Dispatchers.Default) {
      val frames = plan.preparedFrames
      if (frames.size == 1) return@withContext frames[0].copy(Bitmap.Config.ARGB_8888, false)

      val width = frames[0].width
      val rawTotalHeight = plan.totalEstimatedHeight.coerceAtLeast(100)
      val scaleFactor =
        if (rawTotalHeight > MAX_OUTPUT_HEIGHT) {
          MAX_OUTPUT_HEIGHT.toFloat() / rawTotalHeight.toFloat()
        } else {
          1f
        }

      val finalWidth = (width * scaleFactor).toInt().coerceAtLeast(1)
      val finalHeight = (rawTotalHeight * scaleFactor).toInt().coerceAtLeast(1)
      val output = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(output)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

      var currentY = 0f
      frames.forEachIndexed { index, frame ->
        val maxOv = (frame.height - 40).coerceAtLeast(0)
        val overlap =
          if (index == 0) 0
          else plan.overlapsPx.getOrElse(index - 1) { 0 }.coerceIn(0, maxOv)

        val srcRect = Rect(0, overlap, frame.width, frame.height)
        val sliceH = (frame.height - overlap) * scaleFactor
        val dstRect = Rect(0, currentY.toInt(), finalWidth, (currentY + sliceH).toInt())
        canvas.drawBitmap(frame, srcRect, dstRect, paint)
        currentY += sliceH
      }
      output
    }

  private fun detectBestOverlap(prev: Bitmap, next: Bitmap): Int {
    val prevLuma = toDownsampledCenterLuma(prev)
    val nextLuma = toDownsampledCenterLuma(next)
    val rowWidth = SAMPLE_WIDTH
    val prevRows = prevLuma.size / rowWidth
    val nextRows = nextLuma.size / rowWidth

    val scaleYPrev = prev.height.toFloat() / prevRows.toFloat()
    val scaleYNext = next.height.toFloat() / nextRows.toFloat()

    val minOverlapRows = max(8, (nextRows * 0.06f).toInt())
    val maxOverlapRows = min(prevRows - 4, (nextRows * 0.65f).toInt())
    if (maxOverlapRows <= minOverlapRows) {
      return (next.height * 0.14f).toInt()
    }

    var bestOverlapRows = (nextRows * 0.14f).toInt()
    var bestScore = Float.MAX_VALUE

    fun evaluateOverlap(overlap: Int): Float {
      var diffSum = 0L
      var detailSum = 0L
      var samples = 0
      val prevStartRow = prevRows - overlap
      var r = 0
      while (r < overlap) {
        val pRowOffset = (prevStartRow + r) * rowWidth
        val nRowOffset = r * rowWidth
        val prevAboveOffset = if (prevStartRow + r > 0) (prevStartRow + r - 1) * rowWidth else pRowOffset
        var c = 0
        while (c < rowWidth) {
          val pVal = prevLuma[pRowOffset + c].toInt() and 0xFF
          val nVal = nextLuma[nRowOffset + c].toInt() and 0xFF
          diffSum += abs(pVal - nVal)
          val pLeft = if (c > 0) prevLuma[pRowOffset + c - 1].toInt() and 0xFF else pVal
          val pAbove = prevLuma[prevAboveOffset + c].toInt() and 0xFF
          detailSum += abs(pVal - pLeft) + abs(pVal - pAbove)
          samples++
          c += 2
        }
        r += 2
      }
      if (samples == 0) return Float.MAX_VALUE
      val mad = diffSum.toFloat() / samples.toFloat()
      val detail = detailSum.toFloat() / samples.toFloat()
      val flatPenalty = if (detail < 0.8f) (0.8f - detail) * 6f else 0f
      return mad + flatPenalty
    }

    for (overlap in minOverlapRows..maxOverlapRows step 2) {
      val score = evaluateOverlap(overlap)
      if (score < bestScore) {
        bestScore = score
        bestOverlapRows = overlap
      }
    }

    // Coarse-to-fine refinement around best match
    val refineStart = max(minOverlapRows, bestOverlapRows - 3)
    val refineEnd = min(maxOverlapRows, bestOverlapRows + 3)
    for (overlap in refineStart..refineEnd) {
      val score = evaluateOverlap(overlap)
      if (score < bestScore) {
        bestScore = score
        bestOverlapRows = overlap
      }
    }

    // Fallback if frames didn't overlap cleanly
    if (bestScore > 48f) {
      return (next.height * 0.10f).toInt()
    }

    val detectedPx = ((bestOverlapRows * scaleYPrev + bestOverlapRows * scaleYNext) / 2f).toInt()
    val minPx = (next.height * 0.04f).toInt()
    val maxPx = (next.height * 0.68f).toInt().coerceAtLeast(minPx)
    return detectedPx.coerceIn(minPx, maxPx)
  }

  private fun toDownsampledCenterLuma(bitmap: Bitmap): ByteArray {
    // Use center 72% horizontally to ignore scrollbars & edge bezels
    val cropX = (bitmap.width * 0.14f).toInt()
    val cropW = (bitmap.width * 0.72f).toInt().coerceAtLeast(32)
    val scaledH = (bitmap.height * (SAMPLE_WIDTH.toFloat() / cropW.toFloat())).toInt().coerceAtLeast(24)

    val centerCrop = Bitmap.createBitmap(bitmap, cropX, 0, cropW, bitmap.height)
    val small = Bitmap.createScaledBitmap(centerCrop, SAMPLE_WIDTH, scaledH, true)
    if (centerCrop != bitmap) centerCrop.recycle()

    val pixels = IntArray(SAMPLE_WIDTH * scaledH)
    small.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, scaledH)
    small.recycle()

    val luma = ByteArray(pixels.size)
    for (i in pixels.indices) {
      val px = pixels[i]
      val r = (px shr 16) and 0xFF
      val g = (px shr 8) and 0xFF
      val b = px and 0xFF
      luma[i] = ((r * 30 + g * 59 + b * 11) / 100).toByte()
    }
    return luma
  }
}

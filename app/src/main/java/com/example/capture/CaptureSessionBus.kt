package com.example.capture

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingMediaProjectionToken(
  val resultCode: Int,
  val dataIntent: android.content.Intent,
)

sealed interface CaptureSessionEvent {
  data object Idle : CaptureSessionEvent
  data class Capturing(val frameCount: Int) : CaptureSessionEvent
  data class ReadyToStitch(val frames: List<Bitmap>) : CaptureSessionEvent
  data class Error(val message: String) : CaptureSessionEvent
}

object CaptureSessionBus {
  @Volatile var pendingToken: PendingMediaProjectionToken? = null

  private val _event = MutableStateFlow<CaptureSessionEvent>(CaptureSessionEvent.Idle)
  val event: StateFlow<CaptureSessionEvent> = _event.asStateFlow()

  fun updateCapturing(frameCount: Int) {
    _event.value = CaptureSessionEvent.Capturing(frameCount)
  }

  fun publishFrames(frames: List<Bitmap>) {
    _event.value =
      if (frames.isEmpty()) {
        CaptureSessionEvent.Idle
      } else {
        CaptureSessionEvent.ReadyToStitch(frames)
      }
  }

  fun publishError(message: String) {
    _event.value = CaptureSessionEvent.Error(message)
  }

  fun clearEvent() {
    _event.value = CaptureSessionEvent.Idle
  }
}

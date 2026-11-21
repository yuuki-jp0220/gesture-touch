package jp.gesturetouch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager

class GestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun swipe(kind: String): Boolean {
        if (kind != "SWIPE_UP" && kind != "SWIPE_DOWN") return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastGestureAt < 450) return false
        lastGestureAt = now

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val x = metrics.widthPixels * 0.5f
        val top = metrics.heightPixels * 0.34f
        val bottom = metrics.heightPixels * 0.68f
        val path = Path().apply {
            if (kind == "SWIPE_UP") {
                moveTo(x, bottom)
                lineTo(x, top)
            } else {
                moveTo(x, top)
                lineTo(x, bottom)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 180))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile private var instance: GestureAccessibilityService? = null
        @Volatile private var lastGestureAt = 0L
        val isEnabled: Boolean get() = instance != null

        fun perform(kind: String): Boolean {
            if (kind != "SWIPE_UP" && kind != "SWIPE_DOWN") return false
            val service = instance ?: return false
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            service.mainExecutor.execute {
                result = service.swipe(kind)
                latch.countDown()
            }
            return try {
                latch.await(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                result
            } catch (_: InterruptedException) {
                false
            }
        }
    }
}

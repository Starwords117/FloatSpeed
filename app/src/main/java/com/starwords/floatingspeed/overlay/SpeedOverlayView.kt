package com.starwords.floatingspeed.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.starwords.floatingspeed.console.PermissionState
import com.starwords.floatingspeed.domain.SpeedUnit
import com.starwords.floatingspeed.domain.Units
import com.starwords.floatingspeed.session.SessionSnapshot
import com.starwords.floatingspeed.util.DebugLog
import kotlin.math.roundToInt

class SpeedOverlayView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onDoubleTap: () -> Unit,
    private val onPositionChanged: (Int, Int) -> Unit,
) {

    private val snapshot: MutableState<SessionSnapshot?> = mutableStateOf(null)

    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            OverlayWindow(
                snapshot = snapshot.value,
                onDrag = ::dragBy,
                onDragEnd = ::persistPosition,
                onDoubleTap = onDoubleTap,
            )
        }
    }

    fun attach(x: Int, y: Int, alpha: Float) {
        val metrics = context.resources.displayMetrics
        lp.x = if (x >= 0) x else (metrics.widthPixels - DEFAULT_EST_WIDTH_PX).coerceAtLeast(0)
        lp.y = if (y >= 0) y else DEFAULT_Y_PX
        lp.alpha = alpha
        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        owner.moveToResumed()
        windowManager.addView(view, lp)
        DebugLog.d("overlay attach x=${lp.x} y=${lp.y}")
    }

    fun detach() {
        if (!view.isAttachedToWindow) return
        runCatching { windowManager.removeViewImmediate(view) }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        DebugLog.d("overlay detach")
    }

    fun render(snap: SessionSnapshot?) {
        snapshot.value = snap
        view.contentDescription = if (snap == null) {
            "悬浮测速"
        } else {
            "当前速度 ${snap.speedText} ${Units.spokenUnit(snap.unit)}"
        }
    }

    fun setAlpha(level: Int) {
        lp.alpha = when (level) {
            0 -> ALPHA_LOW
            1 -> ALPHA_MEDIUM
            else -> ALPHA_HIGH
        }
        update()
    }

    private fun dragBy(dx: Float, dy: Float) {
        lp.x += dx.roundToInt()
        lp.y += dy.roundToInt()
        update()
    }

    private fun persistPosition() {
        clampPosition()
        update()
        onPositionChanged(lp.x, lp.y)
    }

    private fun clampPosition() {
        val metrics = context.resources.displayMetrics
        val w = if (view.width > 0) view.width else DEFAULT_EST_WIDTH_PX
        val h = if (view.height > 0) view.height else DEFAULT_HEIGHT_PX
        lp.x = lp.x.coerceIn(0, (metrics.widthPixels - w).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (metrics.heightPixels - h).coerceAtLeast(0))
    }

    private fun update() {
        if (view.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(view, lp) }
        }
    }

    companion object {
        private const val ALPHA_LOW = 0.5f
        private const val ALPHA_MEDIUM = 0.7f
        private const val ALPHA_HIGH = 0.9f
        private const val DEFAULT_EST_WIDTH_PX = 260
        private const val DEFAULT_HEIGHT_PX = 96
        private const val DEFAULT_Y_PX = 140
    }
}

private val OverlayBackground = Color(0xE6000000)
private val OverlayRed = Color(0xFFFF6B6B)
private val OverlayYellow = Color(0xFFFFD54F)

@Composable
private fun OverlayWindow(
    snapshot: SessionSnapshot?,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = OverlayBackground,
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onDragEnd() }) { _, amount ->
                    onDrag(amount.x, amount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            },
    ) {
        val speedText = snapshot?.speedText ?: "--"
        val unitLabel = Units.unitLabel(snapshot?.unit ?: SpeedUnit.KMH)
        val color = when {
            snapshot == null -> Color.White.copy(alpha = 0.5f)
            snapshot.permission == PermissionState.NotGranted -> OverlayRed
            snapshot.accuracyPoor -> OverlayYellow
            snapshot.speedDimmed -> Color.White.copy(alpha = 0.5f)
            else -> Color.White
        }
        Text(
            text = buildAnnotatedString {
                append(speedText)
                withStyle(
                    SpanStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = color,
                    ),
                ) {
                    append(" $unitLabel")
                }
            },
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

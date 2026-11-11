package com.starwords.floatingspeed.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.WindowManager
import com.starwords.floatingspeed.App
import com.starwords.floatingspeed.MainActivity
import com.starwords.floatingspeed.R
import com.starwords.floatingspeed.domain.Units
import com.starwords.floatingspeed.session.SessionManager
import com.starwords.floatingspeed.session.SessionSnapshot
import com.starwords.floatingspeed.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private val app: App get() = application as App
    private val session: SessionManager get() = app.session
    private val settings get() = app.settings

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlay: SpeedOverlayView? = null
    private var collectJob: Job? = null
    private var alphaJob: Job? = null
    private var watchJob: Job? = null
    private var retained = false
    private var showing = false
    private var lastNotifyText: String? = null

    override fun onCreate() {
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> show()
            ACTION_STOP -> hideAndStop()
            null -> restoreOrNull()
        }
        return START_STICKY
    }

    private fun show() {
        if (showing) return
        if (!OverlayPermission.canDrawOverlays(this)) {
            DebugLog.d("fgs show blocked: overlay permission missing")
            serviceScope.launch { settings.setFloatingEnabled(false) }
            notifyOverlayLost()
            stopSelf()
            return
        }
        startInForeground()
        showing = true
        if (!retained) {
            session.retain()
            retained = true
        }
        serviceScope.launch {
            val x = settings.overlayX.first()
            val y = settings.overlayY.first()
            val alphaLevel = settings.overlayAlpha.first()
            try {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = SpeedOverlayView(
                    context = this@OverlayService,
                    windowManager = windowManager,
                    onDoubleTap = ::openConsole,
                    onPositionChanged = { px, py ->
                        serviceScope.launch { settings.setOverlayPosition(px, py) }
                    },
                )
                view.attach(x, y, alphaFor(alphaLevel))
                overlay = view
            } catch (t: Throwable) {
                DebugLog.d("overlay attach failed: ${t.javaClass.simpleName}")
                releaseSession()
                showing = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            collectJob = serviceScope.launch {
                session.state.collect { snap ->
                    overlay?.render(snap)
                    updateNotification(snap)
                }
            }
            alphaJob = serviceScope.launch {
                settings.overlayAlpha.collect { level ->
                    overlay?.setAlpha(level)
                }
            }
            watchJob = serviceScope.launch {
                while (isActive) {
                    delay(OVERLAY_PERMISSION_CHECK_MS)
                    if (!OverlayPermission.canDrawOverlays(this@OverlayService)) {
                        DebugLog.d("overlay permission revoked")
                        settings.setFloatingEnabled(false)
                        notifyOverlayLost()
                        hideAndStop()
                        return@launch
                    }
                }
            }
        }
        DebugLog.d("fgs show")
    }

    private fun hideAndStop() {
        if (!showing) {
            stopSelf()
            return
        }
        DebugLog.d("fgs stop")
        collectJob?.cancel()
        alphaJob?.cancel()
        watchJob?.cancel()
        collectJob = null
        alphaJob = null
        watchJob = null
        overlay?.detach()
        overlay = null
        releaseSession()
        showing = false
        lastNotifyText = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun restoreOrNull() {
        serviceScope.launch {
            val enabled = settings.floatingEnabled.first()
            if (enabled && OverlayPermission.canDrawOverlays(this@OverlayService)) {
                DebugLog.d("fgs sticky restore")
                show()
            } else {
                DebugLog.d("fgs restore skipped")
                stopSelf()
            }
        }
    }

    private fun releaseSession() {
        if (retained) {
            session.release()
            retained = false
        }
    }

    private fun startInForeground() {
        val notification = buildNotification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun openConsole() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "测速服务",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notificationText(snap: SessionSnapshot?): String = when {
        snap == null -> "悬浮测速启动中…"
        snap.stalled -> "定位中断，点此检查"
        snap.speedText == "--" -> "悬浮测速运行中 · 等待定位"
        else -> "悬浮测速运行中 · ${snap.speedText} ${Units.unitLabel(snap.unit)}"
    }

    private fun buildNotification(snap: SessionSnapshot?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val closeIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationText(snap))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "关闭", closeIntent).build())
            .build()
    }

    private fun updateNotification(snap: SessionSnapshot) {
        if (!showing) return
        val text = notificationText(snap)
        if (text == lastNotifyText) return
        lastNotifyText = text
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(snap))
    }

    private fun notifyOverlayLost() {
        val contentIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("悬浮窗权限已失效，点此恢复")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(LOST_NOTIF_ID, notification)
    }

    override fun onDestroy() {
        collectJob?.cancel()
        alphaJob?.cancel()
        watchJob?.cancel()
        overlay?.detach()
        overlay = null
        releaseSession()
        super.onDestroy()
    }

    private fun alphaFor(level: Int): Float = when (level) {
        0 -> 0.5f
        1 -> 0.7f
        else -> 0.9f
    }

    companion object {
        const val ACTION_SHOW = "com.starwords.floatingspeed.action.SHOW"
        const val ACTION_STOP = "com.starwords.floatingspeed.action.STOP"
        private const val CHANNEL_ID = "measure_service"
        private const val NOTIF_ID = 1
        private const val LOST_NOTIF_ID = 2
        private const val OVERLAY_PERMISSION_CHECK_MS = 10_000L
    }
}

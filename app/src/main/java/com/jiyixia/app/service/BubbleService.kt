package com.jiyixia.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.jiyixia.app.R
import com.jiyixia.app.ui.BubbleInputActivity

class BubbleService : Service() {

    companion object {
        private const val TAG = "BubbleService"
        private const val CHANNEL_ID = "bubble_service"
        private const val NOTIFICATION_ID = 100
        private const val HIDE_DELAY_MS = 5000L // 5秒后隐藏
        private const val BUBBLE_SIZE = 120
        private const val STRIP_WIDTH = 12
        private const val STRIP_HEIGHT = 100
        private const val EDGE_MARGIN = 12 // 距离边缘的边距，稍微远一点

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var isHidden = false
    private var isAnimating = false
    private val hideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideRunnable = Runnable { hideBubble() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
        createNotificationChannel()

        // 必须每次 onCreate 都调用 startForeground()，否则 Android 8.0+ 会崩溃
        startForeground(NOTIFICATION_ID, createNotification())

        initBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        removeBubble()
        isRunning = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initBubble() {
        // 创建气泡视图
        bubbleView = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            setBackgroundResource(R.drawable.bg_bubble)
            setPadding(20, 20, 20, 20)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        // 窗口参数
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            120, 120,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        // 触摸拖动 + 点击
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubbleView?.setOnTouchListener { _, event ->
            // 动画期间不响应触摸
            if (isAnimating) return@setOnTouchListener true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false

                    // 如果是隐藏状态，点击直接呼出
                    if (isHidden) {
                        showBubble()
                        return@setOnTouchListener true
                    }

                    // 重置隐藏定时器
                    hideHandler.removeCallbacks(hideRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isHidden) return@setOnTouchListener true

                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) { // 超过 10px 算拖动
                        isDragging = true
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isHidden) return@setOnTouchListener true

                    if (!isDragging) {
                        // 点击：打开快速记账
                        openQuickInput()
                    }
                    // 吸附到最近的屏幕边缘
                    snapToEdge(params)
                    // 重新启动隐藏定时器
                    startHideTimer()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(bubbleView, params)
            // 启动隐藏定时器
            startHideTimer()
        } catch (e: Exception) {
            Log.e(TAG, "添加气泡失败", e)
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val centerX = params.x + 60 // 气泡中心
        params.x = if (centerX < screenWidth / 2) EDGE_MARGIN else screenWidth - BUBBLE_SIZE - EDGE_MARGIN
        try {
            windowManager.updateViewLayout(bubbleView, params)
        } catch (e: Exception) {
            Log.e(TAG, "吸附失败", e)
        }
    }

    private fun startHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun hideBubble() {
        if (isHidden || isAnimating || bubbleView == null) return
        isHidden = true
        isAnimating = true

        val params = bubbleView?.layoutParams as? WindowManager.LayoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels

        // 切换到小条背景
        (bubbleView as? ImageView)?.apply {
            setImageResource(0) // 清除图标
            setBackgroundResource(R.drawable.bg_strip)
            setPadding(0, 0, 0, 0)
        }

        // 计算目标位置（左侧或右侧，带边距）
        val endX = if (params.x < screenWidth / 2) EDGE_MARGIN else screenWidth - STRIP_WIDTH - EDGE_MARGIN

        // 动画：气泡缩小并移动到边缘
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float

                // 宽度：从 BUBBLE_SIZE 缩小到 STRIP_WIDTH
                params.width = (BUBBLE_SIZE + (STRIP_WIDTH - BUBBLE_SIZE) * fraction).toInt()
                // 高度：从 BUBBLE_SIZE 缩小到 STRIP_HEIGHT
                params.height = (BUBBLE_SIZE + (STRIP_HEIGHT - BUBBLE_SIZE) * fraction).toInt()
                // X 位置：移动到边缘
                params.x = (params.x + (endX - params.x) * fraction).toInt()

                try {
                    windowManager.updateViewLayout(bubbleView, params)
                } catch (e: Exception) {
                    Log.e(TAG, "动画更新失败", e)
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    // 确保最终状态正确
                    params.width = STRIP_WIDTH
                    params.height = STRIP_HEIGHT
                    params.x = endX
                    try {
                        windowManager.updateViewLayout(bubbleView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "最终状态更新失败", e)
                    }
                }
            })
        }
        animator.start()
    }

    private fun showBubble() {
        if (!isHidden || isAnimating || bubbleView == null) return
        isHidden = false
        isAnimating = true

        val params = bubbleView?.layoutParams as? WindowManager.LayoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels

        // 计算目标位置（左侧或右侧，带边距）
        val endX = if (params.x < screenWidth / 2) EDGE_MARGIN else screenWidth - BUBBLE_SIZE - EDGE_MARGIN

        // 动画：小条展开成气泡
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float

                // 宽度：从 STRIP_WIDTH 扩大到 BUBBLE_SIZE
                params.width = (STRIP_WIDTH + (BUBBLE_SIZE - STRIP_WIDTH) * fraction).toInt()
                // 高度：从 STRIP_HEIGHT 扩大到 BUBBLE_SIZE
                params.height = (STRIP_HEIGHT + (BUBBLE_SIZE - STRIP_HEIGHT) * fraction).toInt()
                // X 位置：移动到目标位置
                params.x = (params.x + (endX - params.x) * fraction).toInt()

                try {
                    windowManager.updateViewLayout(bubbleView, params)
                } catch (e: Exception) {
                    Log.e(TAG, "动画更新失败", e)
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    // 确保最终状态正确
                    params.width = BUBBLE_SIZE
                    params.height = BUBBLE_SIZE
                    params.x = endX

                    // 恢复气泡背景和图标
                    (bubbleView as? ImageView)?.apply {
                        setImageResource(R.drawable.ic_bubble)
                        setBackgroundResource(R.drawable.bg_bubble)
                        setPadding(20, 20, 20, 20)
                    }

                    try {
                        windowManager.updateViewLayout(bubbleView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "最终状态更新失败", e)
                    }
                }
            })
        }
        animator.start()

        // 重新启动隐藏定时器
        startHideTimer()
    }

    private fun removeBubble() {
        try {
            bubbleView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "移除气泡失败", e)
        }
        bubbleView = null
    }

    private fun openQuickInput() {
        val intent = Intent(this, BubbleInputActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "悬浮气泡",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮气泡快速记账"
                // 静默通知：无声音、无震动、无横幅
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, com.jiyixia.app.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("记一下 - 悬浮气泡已开启")
            .setContentText("点击气泡快速记账")
            .setSmallIcon(R.drawable.ic_bubble)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}
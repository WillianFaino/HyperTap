package com.hypertap.autoclicker.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.hypertap.autoclicker.ui.theme.CyanPrimary
import com.hypertap.autoclicker.ui.theme.PurpleAccent
import com.hypertap.autoclicker.ui.theme.StatusActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hypertap.autoclicker.MainActivity
import com.hypertap.autoclicker.R
import com.hypertap.autoclicker.data.local.HyperTapDatabase
import com.hypertap.autoclicker.data.repository.MacroRepository
import com.hypertap.autoclicker.engine.MacroExecutionEngine
import com.hypertap.autoclicker.model.ExecutionState
import com.hypertap.autoclicker.model.MacroPattern
import com.hypertap.autoclicker.model.Point2D
import com.hypertap.autoclicker.model.TapAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import java.util.UUID

class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val CHANNEL_ID = "hypertap_overlay_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private lateinit var macroRepository: MacroRepository
    private val executionEngine = MacroExecutionEngine()

    // Lifecycle para habilitar ComposeView em Services
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    enum class AutomationMode {
        SINGLE_OR_MULTI_TAP,
        CONTINUOUS_DRAG_LOOP
    }

    enum class MarkerSize(val label: String, val sizeDp: Int, val fontSizeSp: Int) {
        COMPACT("P", 34, 13),
        STANDARD("M", 46, 16),
        LARGE("G", 58, 20)
    }

    // Views de sobreposição
    private var toolbarView: ComposeView? = null
    private var pathCanvasView: ComposeView? = null
    private val activeTargetViews = mutableMapOf<String, View>()
    private var currentConfigDialogView: ComposeView? = null

    // Estados observáveis compartilhados
    private val targetPoints = mutableStateListOf<InteractiveTarget>()
    private val isTargetsVisible = mutableStateOf(true)
    private val automationMode = mutableStateOf(AutomationMode.SINGLE_OR_MULTI_TAP)
    private val isClosedLoop = mutableStateOf(true)
    private val currentMarkerSize = mutableStateOf(MarkerSize.STANDARD)
    private val activeRipples = mutableStateListOf<TouchRippleEffect>()
    private val globalDragStepDurationMs = mutableStateOf(250L)
    private val isToolbarExpanded = mutableStateOf(true)

    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0

    class InteractiveTarget(
        val id: String = UUID.randomUUID().toString(),
        initialIndex: Int,
        initialX: Float,
        initialY: Float,
        initialDelayMs: Long = 200L,
        initialPressDurationMs: Long = 50L
    ) {
        var index by mutableStateOf(initialIndex)
        var x by mutableStateOf(initialX)
        var y by mutableStateOf(initialY)
        var delayMs by mutableStateOf(initialDelayMs)
        var pressDurationMs by mutableStateOf(initialPressDurationMs)
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lastScreenWidth = resources.displayMetrics.widthPixels
        lastScreenHeight = resources.displayMetrics.heightPixels
        val database = HyperTapDatabase.getDatabase(this)
        macroRepository = MacroRepository(database.macroDao())

        // Monitora cliques em tempo real para disparar efeitos visuais de onda
        serviceScope.launch {
            executionEngine.onActionExecuted.collect { (x, y) ->
                triggerRipple(x, y)
            }
        }

        // Garante que durante a execução os alvos fiquem com FLAG_NOT_TOUCHABLE para que
        // os cliques despachados pelo AccessibilityService atravessem os marcadores
        // e atinjam diretamente os botões/jogos por baixo.
        serviceScope.launch {
            executionEngine.executionState.collect { state ->
                val isRunning = (state == ExecutionState.RUNNING)
                updateTargetsTouchPassThrough(isRunning)
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        initPathCanvasOverlay()
        initFloatingToolbar()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val newWidth = resources.displayMetrics.widthPixels
        val newHeight = resources.displayMetrics.heightPixels
        val oldWidth = lastScreenWidth
        val oldHeight = lastScreenHeight

        if (oldWidth > 0 && oldHeight > 0 && (oldWidth != newWidth || oldHeight != newHeight)) {
            val sizePx = (currentMarkerSize.value.sizeDp * resources.displayMetrics.density).toInt()
            val markerRadius = sizePx / 2f

            // 1. Reposiciona todos os alvos proporcionalmente no novo espaço da tela
            targetPoints.forEach { target ->
                val ratioX = (target.x / oldWidth.toFloat()).coerceIn(0f, 1f)
                val ratioY = (target.y / oldHeight.toFloat()).coerceIn(0f, 1f)

                val newX = (ratioX * newWidth).coerceIn(markerRadius, newWidth - markerRadius)
                val newY = (ratioY * newHeight).coerceIn(markerRadius, newHeight - markerRadius)

                target.x = newX
                target.y = newY

                val view = activeTargetViews[target.id]
                if (view != null) {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = newX.toInt() - (sizePx / 2)
                    params.y = newY.toInt() - (sizePx / 2)
                    windowManager.updateViewLayout(view, params)
                }
            }

            // 2. Reposiciona e mantém a barra de ferramentas flutuante dentro dos limites da tela
            toolbarView?.let { toolbar ->
                val params = toolbar.layoutParams as WindowManager.LayoutParams
                val toolbarW = toolbar.width.coerceAtLeast(1)
                val toolbarH = toolbar.height.coerceAtLeast(1)

                val ratioX = (params.x.toFloat() / oldWidth.toFloat()).coerceIn(0f, 1f)
                val ratioY = (params.y.toFloat() / oldHeight.toFloat()).coerceIn(0f, 1f)

                params.x = (ratioX * newWidth).toInt().coerceIn(0, (newWidth - toolbarW).coerceAtLeast(0))
                params.y = (ratioY * newHeight).toInt().coerceIn(0, (newHeight - toolbarH).coerceAtLeast(0))
                windowManager.updateViewLayout(toolbar, params)
                adjustToolbarPositionWithinScreen()
            }

            // 3. Atualiza o canvas de sobreposição
            pathCanvasView?.let { canvas ->
                val params = canvas.layoutParams as WindowManager.LayoutParams
                windowManager.updateViewLayout(canvas, params)
            }
        }

        lastScreenWidth = newWidth
        lastScreenHeight = newHeight
    }

    private fun toggleToolbarExpansion() {
        val willExpand = !isToolbarExpanded.value
        isToolbarExpanded.value = willExpand
        adjustToolbarPositionWithinScreen(willExpand)
    }

    private fun adjustToolbarPositionWithinScreen(isExpanded: Boolean = isToolbarExpanded.value) {
        val toolbar = toolbarView ?: return
        val params = toolbar.layoutParams as? WindowManager.LayoutParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = resources.displayMetrics.density

        val targetWidthPx = if (isLandscape) {
            if (isExpanded) (500 * density).toInt() else (90 * density).toInt()
        } else {
            (54 * density).toInt()
        }

        val targetHeightPx = if (isLandscape) {
            (52 * density).toInt()
        } else {
            if (isExpanded) (520 * density).toInt() else (110 * density).toInt()
        }

        if (params.x + targetWidthPx > screenWidth) {
            params.x = (screenWidth - targetWidthPx).coerceAtLeast(10)
        }
        if (params.y + targetHeightPx > screenHeight) {
            params.y = (screenHeight - targetHeightPx).coerceAtLeast(10)
        }

        params.x = params.x.coerceAtLeast(0)
        params.y = params.y.coerceAtLeast(0)

        windowManager.updateViewLayout(toolbar, params)
    }

    private fun triggerRipple(x: Float, y: Float) {
        val ripple = TouchRippleEffect(
            id = System.nanoTime(),
            x = x,
            y = y,
            startMillis = System.currentTimeMillis()
        )
        activeRipples.add(ripple)
        serviceScope.launch {
            kotlinx.coroutines.delay(500)
            activeRipples.remove(ripple)
        }
    }

    private fun cycleMarkerSize() {
        val next = when (currentMarkerSize.value) {
            MarkerSize.STANDARD -> MarkerSize.LARGE
            MarkerSize.LARGE -> MarkerSize.COMPACT
            MarkerSize.COMPACT -> MarkerSize.STANDARD
        }
        currentMarkerSize.value = next
        val sizePx = (next.sizeDp * resources.displayMetrics.density).toInt()
        activeTargetViews.forEach { (id, view) ->
            val target = targetPoints.find { it.id == id } ?: return@forEach
            val params = view.layoutParams as WindowManager.LayoutParams
            params.width = sizePx
            params.height = sizePx
            params.x = target.x.toInt() - (sizePx / 2)
            params.y = target.y.toInt() - (sizePx / 2)
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun initPathCanvasOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            alpha = 0.8f // Compatibilidade com Android 12+ Untrusted Touch Blocking
        }

        pathCanvasView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                val mode by automationMode
                val visible by isTargetsVisible
                val closed by isClosedLoop
                val currentSpeed by globalDragStepDurationMs

                ContinuousPathOverlayCanvas(
                    waypoints = if (mode == AutomationMode.CONTINUOUS_DRAG_LOOP && visible) targetPoints else emptyList(),
                    isClosedLoop = closed,
                    activeRipples = if (visible) activeRipples else emptyList(),
                    stepDurationMs = currentSpeed
                )
            }
        }

        windowManager.addView(pathCanvasView, layoutParams)
    }

    private fun initFloatingToolbar() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 250
        }

        toolbarView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                val execState by executionEngine.executionState.collectAsState()
                val currentMode by automationMode
                val closedLoop by isClosedLoop
                val currentSize by currentMarkerSize
                val isExpanded by isToolbarExpanded
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                FloatingToolbarComposable(
                    executionState = execState,
                    automationMode = currentMode,
                    isClosedLoop = closedLoop,
                    markerSize = currentSize,
                    isLandscape = isLandscape,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        toggleToolbarExpansion()
                    },
                    onToggleMode = {
                        automationMode.value = if (automationMode.value == AutomationMode.SINGLE_OR_MULTI_TAP)
                            AutomationMode.CONTINUOUS_DRAG_LOOP
                        else
                            AutomationMode.SINGLE_OR_MULTI_TAP
                    },
                    onToggleClosedLoop = {
                        isClosedLoop.value = !isClosedLoop.value
                    },
                    onCycleMarkerSize = {
                        cycleMarkerSize()
                    },
                    onOpenSpeedConfig = {
                        showGlobalSpeedDialog()
                    },
                    onPlayPause = {
                        togglePlayPause()
                    },
                    onAddPoint = {
                        addNewTarget()
                    },
                    onRemovePoint = {
                        removeLastTarget()
                    },
                    onToggleVisibility = {
                        isTargetsVisible.value = !isTargetsVisible.value
                        updateTargetsVisibility()
                    },
                    onSaveMacro = {
                        saveCurrentMacro()
                    },
                    onClose = {
                        stopSelf()
                    },
                    onDragToolbar = { deltaX, deltaY ->
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        val toolbarW = this.width.coerceAtLeast(1)
                        val toolbarH = this.height.coerceAtLeast(1)

                        layoutParams.x = (layoutParams.x + deltaX.toInt()).coerceIn(0, (screenWidth - toolbarW).coerceAtLeast(0))
                        layoutParams.y = (layoutParams.y + deltaY.toInt()).coerceIn(0, (screenHeight - toolbarH).coerceAtLeast(0))
                        windowManager.updateViewLayout(this, layoutParams)
                    }
                )
            }
        }

        windowManager.addView(toolbarView, layoutParams)
    }

    private fun addNewTarget() {
        val newIndex = targetPoints.size + 1
        val displayMetrics = resources.displayMetrics
        val target = InteractiveTarget(
            initialIndex = newIndex,
            initialX = (displayMetrics.widthPixels / 2f),
            initialY = (displayMetrics.heightPixels / 2f)
        )
        targetPoints.add(target)
        renderTargetView(target)
    }

    private fun removeLastTarget() {
        if (targetPoints.isNotEmpty()) {
            val lastTarget = targetPoints.removeLast()
            activeTargetViews.remove(lastTarget.id)?.let { view ->
                windowManager.removeView(view)
            }
        }
    }

    private fun deleteTarget(target: InteractiveTarget) {
        targetPoints.remove(target)
        activeTargetViews.remove(target.id)?.let { view ->
            windowManager.removeView(view)
        }
        // Re-indexa os alvos restantes automaticamente
        targetPoints.forEachIndexed { idx, t ->
            t.index = idx + 1
        }
    }

    private fun updateTargetsVisibility() {
        val visibility = if (isTargetsVisible.value) View.VISIBLE else View.GONE
        activeTargetViews.values.forEach { it.visibility = visibility }
    }

    private fun showTargetConfigDialog(target: InteractiveTarget) {
        dismissConfigDialog()

        val dialogWidthPx = (340 * resources.displayMetrics.density).toInt()

        val dialogParams = WindowManager.LayoutParams(
            dialogWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val dialogView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                TargetConfigDialog(
                    targetIndex = target.index,
                    posX = target.x,
                    posY = target.y,
                    currentDelayMs = target.delayMs,
                    currentDurationMs = target.pressDurationMs,
                    onSave = { newDelay, newDuration ->
                        target.delayMs = newDelay
                        target.pressDurationMs = newDuration
                    },
                    onDelete = {
                        deleteTarget(target)
                        dismissConfigDialog()
                    },
                    onDismiss = {
                        dismissConfigDialog()
                    }
                )
            }
        }

        currentConfigDialogView = dialogView
        windowManager.addView(dialogView, dialogParams)
    }

    private fun showGlobalSpeedDialog() {
        dismissConfigDialog()

        val dialogWidthPx = (340 * resources.displayMetrics.density).toInt()

        val dialogParams = WindowManager.LayoutParams(
            dialogWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val dialogView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                val currentSpeed by globalDragStepDurationMs
                GlobalSpeedConfigDialog(
                    currentStepDurationMs = currentSpeed,
                    onSave = { newSpeed, applyToAll ->
                        globalDragStepDurationMs.value = newSpeed
                        if (applyToAll) {
                            targetPoints.forEach { it.delayMs = newSpeed }
                        }
                    },
                    onDismiss = {
                        dismissConfigDialog()
                    }
                )
            }
        }

        currentConfigDialogView = dialogView
        windowManager.addView(dialogView, dialogParams)
    }

    private fun dismissConfigDialog() {
        currentConfigDialogView?.let { view ->
            windowManager.removeView(view)
            currentConfigDialogView = null
        }
    }

    private fun updateTargetsTouchPassThrough(isExecuting: Boolean) {
        activeTargetViews.values.forEach { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            val newFlags = if (isExecuting) {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            params.alpha = 0.8f
            if (params.flags != newFlags) {
                params.flags = newFlags
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun renderTargetView(target: InteractiveTarget) {
        val sizePx = (currentMarkerSize.value.sizeDp * resources.displayMetrics.density).toInt()
        val isRunning = (executionEngine.executionState.value == ExecutionState.RUNNING)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    (if (isRunning) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = target.x.toInt() - (sizePx / 2)
            y = target.y.toInt() - (sizePx / 2)
            alpha = 0.8f
        }

        val targetView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                val mode by automationMode
                val currentSize by currentMarkerSize
                val isDrag = mode == AutomationMode.CONTINUOUS_DRAG_LOOP
                val isFirst = target.index == 1

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            if (isDrag) PurpleAccent.copy(alpha = 0.9f)
                            else CyanPrimary.copy(alpha = 0.85f)
                        )
                        .border(
                            width = if (isDrag && isFirst) 3.dp else 2.dp,
                            color = if (isDrag && isFirst) StatusActive else Color.White,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isDrag) "W${target.index}" else "${target.index}",
                        color = if (isDrag) Color.White else Color.Black,
                        fontSize = currentSize.fontSizeSp.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var touchDownTime = 0L
                private var hasMoved = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            touchDownTime = System.currentTimeMillis()
                            hasMoved = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                                hasMoved = true
                            }
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(v, params)

                            // Atualiza a coordenada central do alvo
                            target.x = params.x + (sizePx / 2f)
                            target.y = params.y + (sizePx / 2f)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val duration = System.currentTimeMillis() - touchDownTime
                            if (!hasMoved && duration < 350) {
                                // Toque rápido: abre o diálogo de configuração individual
                                showTargetConfigDialog(target)
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        activeTargetViews[target.id] = targetView
        windowManager.addView(targetView, params)
    }

    private fun togglePlayPause() {
        when (executionEngine.executionState.value) {
            ExecutionState.IDLE -> {
                if (targetPoints.isEmpty()) return

                val pattern = if (automationMode.value == AutomationMode.CONTINUOUS_DRAG_LOOP) {
                    if (targetPoints.size < 2) return
                    val waypoints = targetPoints.map { Point2D(it.x, it.y) }
                    MacroPattern(
                        name = "Arrasto Contínuo #${System.currentTimeMillis() % 1000}",
                        repeatCount = 0,
                        actions = listOf(
                            TapAction.ContinuousLoop(
                                id = UUID.randomUUID().toString(),
                                waypoints = waypoints,
                                stepDurationMs = globalDragStepDurationMs.value,
                                loopCount = 1,
                                isClosedLoop = isClosedLoop.value,
                                delayBeforeMs = 100L
                            )
                        )
                    )
                } else {
                    val actions = targetPoints.map { target ->
                        TapAction.SingleTap(
                            id = target.id,
                            point = Point2D(target.x, target.y),
                            pressDurationMs = target.pressDurationMs,
                            delayBeforeMs = target.delayMs
                        )
                    }
                    MacroPattern(
                        name = "Sequência de Cliques #${System.currentTimeMillis() % 1000}",
                        repeatCount = 0,
                        actions = actions
                    )
                }
                executionEngine.start(pattern)
            }
            ExecutionState.RUNNING -> executionEngine.pause()
            ExecutionState.PAUSED -> executionEngine.resume()
        }
    }

    private fun saveCurrentMacro() {
        if (targetPoints.isEmpty()) return
        serviceScope.launch {
            val pattern = if (automationMode.value == AutomationMode.CONTINUOUS_DRAG_LOOP) {
                val waypoints = targetPoints.map { Point2D(it.x, it.y) }
                MacroPattern(
                    name = "Macro Arrasto Contínuo #${System.currentTimeMillis() % 1000}",
                    repeatCount = 0,
                    actions = listOf(
                        TapAction.ContinuousLoop(
                            id = UUID.randomUUID().toString(),
                            waypoints = waypoints,
                            stepDurationMs = globalDragStepDurationMs.value,
                            loopCount = 1,
                            isClosedLoop = isClosedLoop.value,
                            delayBeforeMs = 100L
                        )
                    )
                )
            } else {
                val actions = targetPoints.map { target ->
                    TapAction.SingleTap(
                        id = target.id,
                        point = Point2D(target.x, target.y),
                        pressDurationMs = target.pressDurationMs,
                        delayBeforeMs = target.delayMs
                    )
                }
                MacroPattern(
                    name = "Macro Cliques #${System.currentTimeMillis() % 1000}",
                    repeatCount = 0,
                    actions = actions
                )
            }
            macroRepository.saveMacro(pattern)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executionEngine.stop()
        serviceScope.cancel()

        dismissConfigDialog()
        pathCanvasView?.let { windowManager.removeView(it) }
        toolbarView?.let { windowManager.removeView(it) }
        activeTargetViews.values.forEach { windowManager.removeView(it) }
        activeTargetViews.clear()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun FloatingToolbarComposable(
    executionState: ExecutionState,
    automationMode: FloatingOverlayService.AutomationMode,
    isClosedLoop: Boolean,
    markerSize: FloatingOverlayService.MarkerSize,
    isLandscape: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleMode: () -> Unit,
    onToggleClosedLoop: () -> Unit,
    onCycleMarkerSize: () -> Unit,
    onOpenSpeedConfig: () -> Unit,
    onPlayPause: () -> Unit,
    onAddPoint: () -> Unit,
    onRemovePoint: () -> Unit,
    onToggleVisibility: () -> Unit,
    onSaveMacro: () -> Unit,
    onClose: () -> Unit,
    onDragToolbar: (Float, Float) -> Unit
) {
    Surface(
        modifier = (if (isLandscape) Modifier.height(if (isExpanded) 50.dp else 46.dp) else Modifier.width(if (isExpanded) 52.dp else 46.dp))
            .clip(RoundedCornerShape(24.dp))
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = FastOutSlowInEasing
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragToolbar(dragAmount.x, dragAmount.y)
                }
            },
        color = Color(0xF0181A20),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Indicador de arrasto vertical
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )

                Spacer(modifier = Modifier.width(2.dp))

                // Botão de Expandir / Colapsar
                ToolbarIconButton(onClick = onToggleExpand, size = 36.dp) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "Recolher Menu" else "Expandir Menu",
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (!isExpanded) {
                    // Quando colapsado no modo paisagem: apenas Play/Pause rápido
                    ToolbarIconButton(onClick = onPlayPause, size = 36.dp) {
                        Icon(
                            imageVector = when (executionState) {
                                ExecutionState.RUNNING -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = "Iniciar ou Pausar",
                            tint = if (executionState == ExecutionState.RUNNING) Color(0xFFFFD600) else Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Alternar Modo
                    ToolbarIconButton(onClick = onToggleMode, size = 36.dp) {
                        Icon(
                            imageVector = if (automationMode == FloatingOverlayService.AutomationMode.CONTINUOUS_DRAG_LOOP)
                                Icons.Default.Gesture
                            else
                                Icons.Default.TouchApp,
                            contentDescription = "Alternar Modo",
                            tint = if (automationMode == FloatingOverlayService.AutomationMode.CONTINUOUS_DRAG_LOOP)
                                PurpleAccent
                            else
                                CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Alternar Loop Fechado
                    if (automationMode == FloatingOverlayService.AutomationMode.CONTINUOUS_DRAG_LOOP) {
                        ToolbarIconButton(onClick = onToggleClosedLoop, size = 36.dp) {
                            Icon(
                                imageVector = if (isClosedLoop) Icons.Default.AllInclusive else Icons.Default.LinearScale,
                                contentDescription = "Loop Fechado ou Aberto",
                                tint = if (isClosedLoop) CyanPrimary else Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    VerticalDivider(color = Color(0xFF2E3340), modifier = Modifier.height(20.dp).padding(vertical = 2.dp))

                    // Play / Pause
                    ToolbarIconButton(onClick = onPlayPause, size = 36.dp) {
                        Icon(
                            imageVector = when (executionState) {
                                ExecutionState.RUNNING -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = "Iniciar ou Pausar",
                            tint = if (executionState == ExecutionState.RUNNING) Color(0xFFFFD600) else Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Adicionar Alvo
                    ToolbarIconButton(onClick = onAddPoint, size = 36.dp) {
                        Icon(Icons.Default.AddLocation, contentDescription = "Adicionar Alvo", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    // Remover Alvo
                    ToolbarIconButton(onClick = onRemovePoint, size = 36.dp) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remover Alvo", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }

                    // Alternar Tamanho Marcador (P, M, G)
                    ToolbarIconButton(onClick = onCycleMarkerSize, size = 36.dp) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF252A38))
                                .border(1.dp, CyanPrimary.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = markerSize.label,
                                color = CyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Velocidade Global do Fluxo / Arrasto (⚡)
                    ToolbarIconButton(onClick = onOpenSpeedConfig, size = 36.dp) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Velocidade Global do Fluxo",
                            tint = if (automationMode == FloatingOverlayService.AutomationMode.CONTINUOUS_DRAG_LOOP)
                                PurpleAccent
                            else
                                CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Visibilidade
                    ToolbarIconButton(onClick = onToggleVisibility, size = 36.dp) {
                        Icon(Icons.Default.Visibility, contentDescription = "Alternar Visibilidade", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }

                    // Salvar
                    ToolbarIconButton(onClick = onSaveMacro, size = 36.dp) {
                        Icon(Icons.Default.Save, contentDescription = "Salvar Padrão", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }

                    VerticalDivider(color = Color.DarkGray, modifier = Modifier.height(20.dp).padding(vertical = 2.dp))

                    // Fechar
                    ToolbarIconButton(onClick = onClose, size = 36.dp) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar Menu", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Indicador de arrasto horizontal
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Botão de Expandir / Colapsar
                ToolbarIconButton(onClick = onToggleExpand, size = 38.dp) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Recolher Menu" else "Expandir Menu",
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (!isExpanded) {
                    // Quando colapsado no modo retrato: apenas Play/Pause rápido
                    ToolbarIconButton(onClick = onPlayPause, size = 38.dp) {
                        Icon(
                            imageVector = when (executionState) {
                                ExecutionState.RUNNING -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = "Iniciar ou Pausar",
                            tint = if (executionState == ExecutionState.RUNNING) Color(0xFFFFD600) else Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Alternar Modo
                    ToolbarIconButton(onClick = onToggleMode, size = 38.dp) {
                        Icon(
                            imageVector = if (automationMode == FloatingOverlayService.AutomationMode.CONTINUOUS_DRAG_LOOP)
                                Icons.Default.Gesture
                            else
                                Icons.Default.TouchApp,
                            contentDescription = "Alternar Modo",
                            tint = if (automationMode == FloatingOverlayService.AutomationMode.CONTINUOUS_DRAG_LOOP)
                                PurpleAccent
                            else
                                CyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Alternar Loop Fechado
                    if (automationMode == FloatingOverlayService.AutomationMode.CONTINUOUS_DRAG_LOOP) {
                        ToolbarIconButton(onClick = onToggleClosedLoop, size = 38.dp) {
                            Icon(
                                imageVector = if (isClosedLoop) Icons.Default.AllInclusive else Icons.Default.LinearScale,
                                contentDescription = "Loop Fechado ou Aberto",
                                tint = if (isClosedLoop) CyanPrimary else Color.LightGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF2E3340), modifier = Modifier.padding(horizontal = 6.dp))

                    // Play / Pause
                    ToolbarIconButton(onClick = onPlayPause, size = 38.dp) {
                        Icon(
                            imageVector = when (executionState) {
                                ExecutionState.RUNNING -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = "Iniciar ou Pausar",
                            tint = if (executionState == ExecutionState.RUNNING) Color(0xFFFFD600) else Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Adicionar Alvo
                    ToolbarIconButton(onClick = onAddPoint, size = 38.dp) {
                        Icon(Icons.Default.AddLocation, contentDescription = "Adicionar Alvo", tint = Color.White, modifier = Modifier.size(22.dp))
                    }

                    // Remover Alvo
                    ToolbarIconButton(onClick = onRemovePoint, size = 38.dp) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remover Alvo", tint = Color.LightGray, modifier = Modifier.size(22.dp))
                    }

                    // Alternar Tamanho Marcador (P, M, G)
                    ToolbarIconButton(onClick = onCycleMarkerSize, size = 38.dp) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF252A38))
                                .border(1.dp, CyanPrimary.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = markerSize.label,
                                color = CyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Velocidade Global do Fluxo / Arrasto (⚡)
                    ToolbarIconButton(onClick = onOpenSpeedConfig, size = 38.dp) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Velocidade Global do Fluxo",
                            tint = if (automationMode == FloatingOverlayService.AutomationMode.CONTINUOUS_DRAG_LOOP)
                                PurpleAccent
                            else
                                CyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Visibilidade
                    ToolbarIconButton(onClick = onToggleVisibility, size = 38.dp) {
                        Icon(Icons.Default.Visibility, contentDescription = "Alternar Visibilidade", tint = Color.LightGray, modifier = Modifier.size(22.dp))
                    }

                    // Salvar
                    ToolbarIconButton(onClick = onSaveMacro, size = 38.dp) {
                        Icon(Icons.Default.Save, contentDescription = "Salvar Padrão", tint = Color.LightGray, modifier = Modifier.size(22.dp))
                    }

                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(horizontal = 6.dp))

                    // Fechar
                    ToolbarIconButton(onClick = onClose, size = 38.dp) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar Menu", tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}


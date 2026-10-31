package com.hypertap.autoclicker.overlay

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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Views de sobreposição
    private var toolbarView: ComposeView? = null
    private val activeTargetViews = mutableMapOf<String, View>()
    private var currentConfigDialogView: ComposeView? = null

    // Estados observáveis compartilhados
    private val targetPoints = mutableStateListOf<InteractiveTarget>()
    private val isTargetsVisible = mutableStateOf(true)

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
        val database = HyperTapDatabase.getDatabase(this)
        macroRepository = MacroRepository(database.macroDao())

        startForeground(NOTIFICATION_ID, createNotification())
        initFloatingToolbar()
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

                FloatingToolbarComposable(
                    executionState = execState,
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
                        layoutParams.x += deltaX.toInt()
                        layoutParams.y += deltaY.toInt()
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

        val dialogWidthPx = (320 * resources.displayMetrics.density).toInt()

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

    private fun dismissConfigDialog() {
        currentConfigDialogView?.let { view ->
            windowManager.removeView(view)
            currentConfigDialogView = null
        }
    }

    private fun renderTargetView(target: InteractiveTarget) {
        val sizePx = (48 * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
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
            x = target.x.toInt() - (sizePx / 2)
            y = target.y.toInt() - (sizePx / 2)
        }

        val targetView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xCC00E5FF))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${target.index}",
                        color = Color.Black,
                        fontSize = 18.sp,
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
                val actions = targetPoints.map { target ->
                    TapAction.SingleTap(
                        id = target.id,
                        point = Point2D(target.x, target.y),
                        pressDurationMs = target.pressDurationMs,
                        delayBeforeMs = target.delayMs
                    )
                }
                val pattern = MacroPattern(
                    name = "Padrão Atual",
                    repeatCount = 0, // Infinito até pausar
                    actions = actions
                )
                executionEngine.start(pattern)
            }
            ExecutionState.RUNNING -> executionEngine.pause()
            ExecutionState.PAUSED -> executionEngine.resume()
        }
    }

    private fun saveCurrentMacro() {
        if (targetPoints.isEmpty()) return
        serviceScope.launch {
            val actions = targetPoints.map { target ->
                TapAction.SingleTap(
                    id = target.id,
                    point = Point2D(target.x, target.y),
                    pressDurationMs = target.pressDurationMs,
                    delayBeforeMs = target.delayMs
                )
            }
            val pattern = MacroPattern(
                name = "Padrão Salvo #${System.currentTimeMillis() % 1000}",
                repeatCount = 0,
                actions = actions
            )
            macroRepository.saveMacro(pattern)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executionEngine.stop()
        serviceScope.cancel()

        dismissConfigDialog()
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
fun FloatingToolbarComposable(
    executionState: ExecutionState,
    onPlayPause: () -> Unit,
    onAddPoint: () -> Unit,
    onRemovePoint: () -> Unit,
    onToggleVisibility: () -> Unit,
    onSaveMacro: () -> Unit,
    onClose: () -> Unit,
    onDragToolbar: (Float, Float) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(54.dp)
            .clip(RoundedCornerShape(24.dp))
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
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Indicador de arrasto
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Play / Pause
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = when (executionState) {
                        ExecutionState.RUNNING -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = "Iniciar ou Pausar",
                    tint = if (executionState == ExecutionState.RUNNING) Color(0xFFFFD600) else Color(0xFF00E5FF)
                )
            }

            // Adicionar Ponto
            IconButton(onClick = onAddPoint) {
                Icon(Icons.Default.AddLocation, contentDescription = "Adicionar Alvo", tint = Color.White)
            }

            // Remover Ponto
            IconButton(onClick = onRemovePoint) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remover Alvo", tint = Color.LightGray)
            }

            // Visibilidade dos Alvos
            IconButton(onClick = onToggleVisibility) {
                Icon(Icons.Default.Visibility, contentDescription = "Alternar Visibilidade", tint = Color.LightGray)
            }

            // Salvar
            IconButton(onClick = onSaveMacro) {
                Icon(Icons.Default.Save, contentDescription = "Salvar Padrão", tint = Color.LightGray)
            }

            Divider(color = Color.DarkGray, modifier = Modifier.padding(horizontal = 8.dp))

            // Fechar
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fechar Menu", tint = Color(0xFFFF5252))
            }
        }
    }
}


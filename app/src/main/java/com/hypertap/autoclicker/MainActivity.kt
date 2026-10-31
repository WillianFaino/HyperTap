package com.hypertap.autoclicker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hypertap.autoclicker.accessibility.HyperTapAccessibilityService
import com.hypertap.autoclicker.model.MacroPattern
import com.hypertap.autoclicker.overlay.FloatingOverlayService
import com.hypertap.autoclicker.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as HyperTapApp).repository

        setContent {
            HyperTapTheme {
                MainAppScreen(
                    repository = repository,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                    onStartFloatingMenu = { startFloatingMenu() }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun startFloatingMenu() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val isA11yActive = HyperTapAccessibilityService.instance != null

        if (!hasOverlay) {
            Toast.makeText(this, "Permita a sobreposição de tela primeiro!", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }

        if (!isA11yActive) {
            Toast.makeText(this, "Ative o serviço de acessibilidade do HyperTap!", Toast.LENGTH_SHORT).show()
            requestAccessibilityPermission()
            return
        }

        FloatingOverlayService.start(this)
        Toast.makeText(this, "Menu flutuante iniciado!", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainAppScreen(
    repository: com.hypertap.autoclicker.data.repository.MacroRepository,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onStartFloatingMenu: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val savedMacros by repository.allMacros.collectAsState(initial = emptyList())
    val isA11yActive by HyperTapAccessibilityService.isServiceActive.collectAsState()

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    // Atualiza checagem ao recompor
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Hyper",
                                style = MaterialTheme.typography.headlineMedium,
                                color = CyanPrimary
                            )
                            Text(
                                text = "Tap",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Cliques e Gestos Automatizados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    // Badge POCO X8 Pro / Android 16
                    Surface(
                        color = DarkCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
                    ) {
                        Text(
                            text = "POCO X8 Pro • Android 16",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary
                        )
                    }
                }
            }

            // Permissões
            item {
                Text(
                    text = "STATUS DE PERMISSÕES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // Card Acessibilidade
            item {
                PermissionCard(
                    title = "Serviço de Acessibilidade",
                    description = "Necessário para simular toques, swipes e arrasto contínuo.",
                    isGranted = isA11yActive,
                    icon = Icons.Default.AccessibilityNew,
                    onGrantClick = onRequestAccessibilityPermission
                )
            }

            // Card Sobreposição
            item {
                PermissionCard(
                    title = "Sobrepor a outros aplicativos",
                    description = "Necessário para desenhar o menu flutuante e alvos sobre jogos e apps.",
                    isGranted = hasOverlayPermission,
                    icon = Icons.Default.Layers,
                    onGrantClick = onRequestOverlayPermission
                )
            }

            // Dica HyperOS / Android 16
            item {
                HyperOSTipCard()
            }

            // Botão Principal de Início
            item {
                Button(
                    onClick = onStartFloatingMenu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "INICIAR MENU FLUTUANTE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Seção Padrões Salvos
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PADRÕES SALVOS (${savedMacros.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            if (savedMacros.isEmpty()) {
                item {
                    EmptyMacrosState()
                }
            } else {
                items(savedMacros) { macro ->
                    MacroItemCard(
                        macro = macro,
                        onDelete = {
                            coroutineScope.launch {
                                repository.deleteMacro(macro)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onGrantClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) StatusActive.copy(alpha = 0.2f) else StatusWarning.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) StatusActive else StatusWarning
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isGranted) {
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "Ativar", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Ativado",
                    tint = StatusActive,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun HyperOSTipCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E2028),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF323644))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dica para POCO / HyperOS & Android 16",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CyanPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• Em instalações manuais, toque e segure o ícone do app > 'Informações do app' > 3 pontos no topo > 'Permitir configurações restritas' para liberar a Acessibilidade.\n• Habilite 'Exibir janelas pop-up em segundo plano' nas permissões adicionais do HyperOS.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun EmptyMacrosState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.DarkGray
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Nenhum padrão gravado ainda",
            color = TextSecondary,
            fontSize = 15.sp
        )
        Text(
            text = "Inicie o menu flutuante para posicionar alvos e salvar seus padrões.",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun MacroItemCard(
    macro: MacroPattern,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = macro.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${macro.actions.size} ações • ${if (macro.repeatCount == 0) "Loop Infinito" else "${macro.repeatCount} ciclos"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyanPrimary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Excluir",
                    tint = StatusDanger
                )
            }
        }
    }
}


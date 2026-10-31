package com.hypertap.autoclicker.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypertap.autoclicker.ui.theme.*

@Composable
fun TargetConfigDialog(
    targetIndex: Int,
    posX: Float,
    posY: Float,
    currentDelayMs: Long,
    currentDurationMs: Long,
    onSave: (newDelayMs: Long, newDurationMs: Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var delayMs by remember { mutableStateOf(currentDelayMs) }
    var pressDurationMs by remember { mutableStateOf(currentDurationMs) }

    val delayPresets = listOf(50L, 100L, 250L, 500L, 1000L)
    val durationPresets = listOf(20L, 50L, 100L, 200L, 500L)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF2E3340), RoundedCornerShape(24.dp)),
        color = Color(0xF516181F),
        tonalElevation = 12.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // Cabeçalho com badge do ponto
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$targetIndex",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Configurar Ponto #$targetIndex",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "X: ${posX.toInt()} px • Y: ${posY.toInt()} px",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = Color(0xFF2A2E3D))
            Spacer(modifier = Modifier.height(14.dp))

            // Seção 1: Delay Antes do Toque
            Text(
                text = "TEMPO DE ESPERA (DELAY ANTES)",
                style = MaterialTheme.typography.labelSmall,
                color = CyanPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$delayMs ms",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { delayMs = (delayMs - 100).coerceAtLeast(10L) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("-100", fontSize = 11.sp, color = Color.White)
                    }
                    Button(
                        onClick = { delayMs = (delayMs - 50).coerceAtLeast(10L) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("-50", fontSize = 11.sp, color = Color.White)
                    }
                    Button(
                        onClick = { delayMs += 50 },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+50", fontSize = 11.sp, color = CyanPrimary)
                    }
                    Button(
                        onClick = { delayMs += 100 },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+100", fontSize = 11.sp, color = CyanPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Presets de Delay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                delayPresets.forEach { preset ->
                    val isSelected = delayMs == preset
                    Surface(
                        onClick = { delayMs = preset },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) CyanPrimary.copy(alpha = 0.25f) else Color(0xFF222630),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (preset >= 1000) "${preset / 1000}s" else "${preset}ms",
                            modifier = Modifier.padding(vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CyanPrimary else TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Seção 2: Duração da Pressão (Hold)
            Text(
                text = "DURAÇÃO DO TOQUE (HOLD)",
                style = MaterialTheme.typography.labelSmall,
                color = CyanPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$pressDurationMs ms",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { pressDurationMs = (pressDurationMs - 50).coerceAtLeast(10L) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("-50", fontSize = 11.sp, color = Color.White)
                    }
                    Button(
                        onClick = { pressDurationMs = (pressDurationMs - 10).coerceAtLeast(10L) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("-10", fontSize = 11.sp, color = Color.White)
                    }
                    Button(
                        onClick = { pressDurationMs = (pressDurationMs + 10).coerceAtMost(5000L) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+10", fontSize = 11.sp, color = CyanPrimary)
                    }
                    Button(
                        onClick = { pressDurationMs = (pressDurationMs + 50).coerceAtMost(5000L) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+50", fontSize = 11.sp, color = CyanPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Presets de Duração
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                durationPresets.forEach { preset ->
                    val isSelected = pressDurationMs == preset
                    Surface(
                        onClick = { pressDurationMs = preset },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) CyanPrimary.copy(alpha = 0.25f) else Color(0xFF222630),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${preset}ms",
                            modifier = Modifier.padding(vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CyanPrimary else TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFF2A2E3D))
            Spacer(modifier = Modifier.height(16.dp))

            // Botões de Ação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusDanger.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Excluir", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        onSave(delayMs, pressDurationMs)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Concluir", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

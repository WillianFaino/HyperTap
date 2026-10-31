package com.hypertap.autoclicker.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypertap.autoclicker.ui.theme.*

@Composable
fun GlobalSpeedConfigDialog(
    currentStepDurationMs: Long,
    onSave: (newStepDurationMs: Long, applyToAllNodes: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var stepDurationMs by remember { mutableStateOf(currentStepDurationMs) }
    var applyToAllNodes by remember { mutableStateOf(true) }

    data class SpeedPreset(val label: String, val ms: Long, val desc: String)
    val presets = listOf(
        SpeedPreset("Ultra", 80L, "Insano"),
        SpeedPreset("Rápido", 150L, "Veloz"),
        SpeedPreset("Normal", 250L, "Padrão"),
        SpeedPreset("Suave", 500L, "Fluido"),
        SpeedPreset("Lento", 1000L, "Calmo")
    )

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
                .verticalScroll(rememberScrollState())
        ) {
            // Cabeçalho com ícone de velocidade
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
                            .background(PurpleAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Velocidade do Fluxo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Ajuste global de velocidade do arrasto contínuo",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF2A2E3D))
            Spacer(modifier = Modifier.height(14.dp))

            // Duração por segmento
            Text(
                text = "TEMPO POR TRECHO (DURAÇÃO DO PASSO)",
                style = MaterialTheme.typography.labelSmall,
                color = PurpleAccent,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "$stepDurationMs ms",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val segmentsPerSec = if (stepDurationMs > 0) (1000f / stepDurationMs) else 0f
                    Text(
                        text = String.format("%.1f trechos/s", segmentsPerSec),
                        fontSize = 12.sp,
                        color = CyanPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SpeedStepperButton(
                        text = "-50",
                        onClick = { stepDurationMs = (stepDurationMs - 50).coerceAtLeast(30L) },
                        isNegative = true
                    )
                    SpeedStepperButton(
                        text = "-10",
                        onClick = { stepDurationMs = (stepDurationMs - 10).coerceAtLeast(30L) },
                        isNegative = true
                    )
                    SpeedStepperButton(
                        text = "+10",
                        onClick = { stepDurationMs = (stepDurationMs + 10).coerceAtMost(3000L) },
                        isNegative = false
                    )
                    SpeedStepperButton(
                        text = "+50",
                        onClick = { stepDurationMs = (stepDurationMs + 50).coerceAtMost(3000L) },
                        isNegative = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets de Velocidade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = stepDurationMs == preset.ms
                    Surface(
                        onClick = { stepDurationMs = preset.ms },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) PurpleAccent.copy(alpha = 0.3f) else Color(0xFF222630),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, PurpleAccent) else null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = preset.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "${preset.ms}ms",
                                fontSize = 10.sp,
                                color = if (isSelected) CyanPrimary else Color.Gray,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Checkbox: Aplicar como delay padrão a todos os nós individuais
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = applyToAllNodes,
                    onCheckedChange = { applyToAllNodes = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CyanPrimary,
                        checkmarkColor = Color.Black
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Padronizar também os nós existentes com este tempo",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF2A2E3D))
            Spacer(modifier = Modifier.height(16.dp))

            // Botões de Ação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3E4352))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancelar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = {
                        onSave(stepDurationMs, applyToAllNodes)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aplicar", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun SpeedStepperButton(
    text: String,
    onClick: () -> Unit,
    isNegative: Boolean
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF222630),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3340)),
        modifier = Modifier.width(44.dp)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isNegative) Color.White else CyanPrimary,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}


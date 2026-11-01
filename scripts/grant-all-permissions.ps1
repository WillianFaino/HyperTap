# ==============================================================================
# Script de Concessao Automatica de Permissoes e Confianca - HyperTap
# Concede Acessibilidade, Sobreposicao, Notificacoes, Inicializacao Automatica e
# Isencao de Otimizacao de Bateria via ADB sem necessidade de intervencao manual.
# ==============================================================================

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb.exe"
}

$pkg = "com.hypertap.autoclicker.debug"
$service = "$pkg/com.hypertap.autoclicker.accessibility.HyperTapAccessibilityService"

Write-Host ">>> Injetando configuracoes e permissoes de confianca para o HyperTap..." -ForegroundColor Cyan

# 1. Permissao de Sobreposicao (SYSTEM_ALERT_WINDOW)
& $adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow
Write-Host "  [OK] Sobreposicao de tela autorizada" -ForegroundColor Green

# 2. Ignora Bloqueio de Configuracoes Restritas (Android 13+)
& $adb shell appops set $pkg ACCESS_RESTRICTED_SETTINGS allow
Write-Host "  [OK] Configuracoes restritas desbloqueadas" -ForegroundColor Green

# 3. Permissoes Especificas do MIUI / HyperOS (Janelas Pop-up em 2o plano e Autostart)
& $adb shell appops set $pkg 10021 allow 2>$null # AUTO_START
& $adb shell appops set $pkg 10022 allow 2>$null # DISPLAY_POPUP_WINDOW
& $adb shell appops set $pkg 10023 allow 2>$null # BACKGROUND_POPUP
Write-Host "  [OK] Permissoes avancadas do HyperOS configuradas" -ForegroundColor Green

# 4. Isencao da Otimizacao de Bateria (Evita que o sistema mate o servico em segundo plano)
& $adb shell dumpsys deviceidle whitelist "+$pkg" | Out-Null
Write-Host "  [OK] Isencao de bateria (Doze Whitelist) aplicada" -ForegroundColor Green

# 5. Permissao de Notificacoes em Primeiro Plano
& $adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS 2>$null
Write-Host "  [OK] Permissao de notificacoes concedida" -ForegroundColor Green

# 6. Ativacao Direta do Servico de Acessibilidade
$currentServices = & $adb shell settings get secure enabled_accessibility_services
if (-not $currentServices.Contains($service)) {
    if ([string]::IsNullOrWhiteSpace($currentServices) -or $currentServices -eq "null") {
        $newServices = $service
    } else {
        $newServices = $currentServices + ":" + $service
    }
    & $adb shell settings put secure enabled_accessibility_services $newServices
} else {
    # Re-toggle para restabelecer o bind imediatamente se estava suspenso
    $tempServices = ($currentServices -replace [regex]::Escape($service), "") -replace "::", ":" -replace "^:|:$", ""
    & $adb shell settings put secure enabled_accessibility_services $tempServices
    Start-Sleep -Milliseconds 200
    & $adb shell settings put secure enabled_accessibility_services $currentServices
}

& $adb shell settings put secure accessibility_enabled 1
Write-Host "  [OK] Servico de Acessibilidade conectado e ativado!" -ForegroundColor Green
Write-Host "`n>>> Todas as permissoes foram concedidas com sucesso sem intervencao do usuario!" -ForegroundColor Magenta

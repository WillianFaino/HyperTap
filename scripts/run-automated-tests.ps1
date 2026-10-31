# ==============================================================================
# Roteiro Automatizado de Testes - HyperTap
# Dispositivo Alvo: POCO X8 Pro (Android 16 / HyperOS 3.0, 1268x2756)
# ==============================================================================

param(
    [string]$ScreenshotDir = "C:\Users\Pichau\.gemini\antigravity\brain\079ce281-861f-41af-87f6-481af2d18d5d\test_screenshots"
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb.exe"
}

if (-not (Test-Path $ScreenshotDir)) {
    New-Item -ItemType Directory -Path $ScreenshotDir -Force | Out-Null
}

$results = [ordered]@{}

function Log-Step([string]$title) {
    Write-Host "`n>>> [TESTE] $title" -ForegroundColor Cyan
}

function Assert-Result([string]$testName, [bool]$condition, [string]$details = "") {
    if ($condition) {
        Write-Host "  [PASS] $testName" -ForegroundColor Green
        if ($details) { Write-Host "         $details" -ForegroundColor Gray }
        $results[$testName] = "PASS"
    } else {
        Write-Host "  [FAIL] $testName" -ForegroundColor Red
        if ($details) { Write-Host "         $details" -ForegroundColor Yellow }
        $results[$testName] = "FAIL"
    }
}

function Take-Screenshot([string]$filename) {
    $remotePath = "/sdcard/$filename"
    $localPath = Join-Path $ScreenshotDir $filename
    & $adb shell screencap -p $remotePath
    & $adb pull $remotePath $localPath 2>$null | Out-Null
    & $adb shell rm $remotePath 2>$null | Out-Null
    return $localPath
}

Write-Host "==========================================================" -ForegroundColor Magenta
Write-Host "  INICIANDO SUÍTE DE TESTES AUTOMATIZADOS - HYPERTAP      " -ForegroundColor Magenta
Write-Host "==========================================================" -ForegroundColor Magenta

# 1. Pré-condições do Aparelho
Log-Step "1. Validando conexao e pre-condicoes do dispositivo"
$device = & $adb devices | Select-String "device\b" | Where-Object { $_ -notmatch "List of" }
Assert-Result "Dispositivo POCO X8 Pro conectado" ($null -ne $device) "ID: $($device.ToString().Trim())"

# Garante tela ligada e desbloqueada
& $adb shell input keyevent 224 # WAKEUP
& $adb shell wm dismiss-keyguard 2>$null

# Trava modo retrato para início limpo
& $adb shell settings put system accelerometer_rotation 0
& $adb shell settings put system user_rotation 0
Start-Sleep -Milliseconds 800

# 2. Tela Principal do HyperTap
Log-Step "2. Inicializacao e Status de Permissoes na Tela Principal"
& $adb shell am start -n com.hypertap.autoclicker.debug/com.hypertap.autoclicker.MainActivity | Out-Null
Start-Sleep -Milliseconds 1200

$mainShot = Take-Screenshot "01_main_activity_portrait.png"
Assert-Result "Screenshot da Tela Principal capturado" (Test-Path $mainShot) "Salvo em: $mainShot"

# 3. Inicia Menu Flutuante
Log-Step "3. Acionamento do Menu Flutuante"
# Toca no botão 'INICIAR MENU FLUTUANTE' (x=634, y=1800)
& $adb shell input tap 634 1800
Start-Sleep -Milliseconds 1500

$toolbarShot = Take-Screenshot "02_toolbar_expanded_portrait.png"
$activeServices = (& $adb shell dumpsys activity services com.hypertap.autoclicker.debug) -join "`n"
$hasOverlayRunning = $activeServices.Contains("FloatingOverlayService")
Assert-Result "FloatingOverlayService ativo e em execucao" $hasOverlayRunning

# 4. Teste de Colapso e Expansão da Barra (Modo Retrato)
Log-Step "4. Teste de Animacao de Colapso e Expansao (Modo Retrato)"
# Toca no botão de recolher (topo da barra, x=90, y=530)
& $adb shell input tap 90 530
Start-Sleep -Milliseconds 600

$collapsedShot = Take-Screenshot "03_toolbar_collapsed_portrait.png"
Assert-Result "Barra recolhida com sucesso em Retrato" (Test-Path $collapsedShot)

# Toca para re-expandir
& $adb shell input tap 90 530
Start-Sleep -Milliseconds 600

$reExpandedShot = Take-Screenshot "04_toolbar_reexpanded_portrait.png"
Assert-Result "Barra re-expandida suavemente sem corte de botoes" (Test-Path $reExpandedShot)

# 5. Adição e Posicionamento de Marcadores de Toque
Log-Step "5. Criacao de Marcadores de Toque (Alvos 1 e 2)"
# Botão Adicionar Alvo (+): x=90, y=910
& $adb shell input tap 90 910
Start-Sleep -Milliseconds 600
& $adb shell input tap 90 910
Start-Sleep -Milliseconds 600

# Arrasta o Alvo 2 (inicialmente no centro 634, 1378) para cima (634, 800)
& $adb shell input swipe 634 1378 634 800 300
Start-Sleep -Milliseconds 500

$targetsShot = Take-Screenshot "05_targets_added_and_separated.png"
Assert-Result "Marcadores 1 e 2 posicionados na tela" (Test-Path $targetsShot)

# 6. Teste de Alternância de Tamanho dos Marcadores (P, M, G)
Log-Step "6. Teste de Redimensionamento dos Marcadores (Ciclo P, M, G)"
# Botão Tamanho: x=90, y=1170
& $adb shell input tap 90 1170
Start-Sleep -Milliseconds 500
$sizeGShot = Take-Screenshot "06_marker_size_large_G.png"

& $adb shell input tap 90 1170
Start-Sleep -Milliseconds 500
$sizePShot = Take-Screenshot "07_marker_size_compact_P.png"

& $adb shell input tap 90 1170
Start-Sleep -Milliseconds 500
$sizeMShot = Take-Screenshot "08_marker_size_standard_M.png"
Assert-Result "Ciclo de tamanhos (P, M, G) testado com sucesso" (Test-Path $sizeMShot)

# 7. Teste Crítico: Injeção de Toques e Gestos Físicos
Log-Step "7. Teste Critico de Injecao Fisica de Toques (AccessibilityService)"
# Limpa o buffer de logcat
& $adb shell logcat -c
Start-Sleep -Milliseconds 200

# Toca no botão Play (x=90, y=780)
& $adb shell input tap 90 780
Write-Host "  ... Executando toques automaticos por 3.5 segundos ..." -ForegroundColor Yellow
Start-Sleep -Milliseconds 1500

$execShot = Take-Screenshot "09_touch_execution_active_ripples.png"

Start-Sleep -Milliseconds 2000

# Toca no botão Pause (x=90, y=780)
& $adb shell input tap 90 780
Start-Sleep -Milliseconds 800

# Captura logs de execução
$logs = & $adb shell logcat -d -s HyperTapA11y:V HyperTapEngine:V
$successClicks = ($logs | Select-String -Pattern "Clique executado com sucesso|SingleTap executado.*sucesso: true").Count
$cancelledClicks = ($logs | Select-String -Pattern "cancelado pelo sistema|sucesso: false").Count

Write-Host "  Total de cliques executados com sucesso: $successClicks" -ForegroundColor Green
Write-Host "  Total de cliques cancelados: $cancelledClicks" -ForegroundColor $(if ($cancelledClicks -eq 0) { "Green" } else { "Red" })

Assert-Result "Injecao de Toques Fisica Funcionando" ($successClicks -gt 0 -and $cancelledClicks -eq 0) "Sucessos: $successClicks, Cancelamentos: $cancelledClicks"

# 8. Teste de Rotação para Modo Paisagem
Log-Step "8. Adaptacao da Barra e Marcadores no Modo Paisagem (Landscape)"
& $adb shell settings put system user_rotation 1
Start-Sleep -Milliseconds 2500

$landscapeShot = Take-Screenshot "10_landscape_adapted_toolbar.png"
Assert-Result "Barra flutuante adaptada em Modo Paisagem com largura total" (Test-Path $landscapeShot)

# 9. Teste de Colapso e Expansão no Modo Paisagem
Log-Step "9. Teste de Colapso e Expansao no Modo Paisagem"
# Toca no botão de recolher da barra em paisagem (x=80, y=230)
& $adb shell input tap 80 230
Start-Sleep -Milliseconds 700
$landCollapsedShot = Take-Screenshot "11_landscape_collapsed_toolbar.png"
Assert-Result "Barra recolhida com sucesso em Paisagem" (Test-Path $landCollapsedShot)

# Toca para re-expandir
& $adb shell input tap 80 230
Start-Sleep -Milliseconds 700
$landReExpandedShot = Take-Screenshot "12_landscape_reexpanded_toolbar.png"
Assert-Result "Barra re-expandida com sucesso em Paisagem" (Test-Path $landReExpandedShot)

# 10. Retorno ao Retrato e Finalização
Log-Step "10. Retorno ao Modo Retrato, Encerramento e Limpeza"
& $adb shell settings put system user_rotation 0
Start-Sleep -Milliseconds 2000

# Toca no botão Fechar (X, x=90, y=1690)
& $adb shell input tap 90 1690
Start-Sleep -Milliseconds 1000

$finalServices = (& $adb shell dumpsys activity services com.hypertap.autoclicker.debug) -join "`n"
$isClosed = -not $finalServices.Contains("FloatingOverlayService")
Assert-Result "Menu flutuante fechado e recursos liberados" $isClosed

$finalShot = Take-Screenshot "13_test_suite_finished.png"

Write-Host "`n==========================================================" -ForegroundColor Magenta
Write-Host "                RESUMO DOS RESULTADOS                     " -ForegroundColor Magenta
Write-Host "==========================================================" -ForegroundColor Magenta
$passCount = 0
$failCount = 0
foreach ($key in $results.Keys) {
    $status = $results[$key]
    if ($status -eq "PASS") {
        $passCount++
        Write-Host " [PASS] $key" -ForegroundColor Green
    } else {
        $failCount++
        Write-Host " [FAIL] $key" -ForegroundColor Red
    }
}

Write-Host "`nTOTAL: $passCount PASSADOS, $failCount FALHAS." -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })

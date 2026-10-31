# Script de Compilação, Deploy e Teste no POCO X8 Pro (HyperOS / Android 16)
param(
    [switch]$WirelessPair,
    [string]$PairAddress,
    [string]$PairCode,
    [string]$ConnectAddress
)

$adb = "C:\Users\Pichau\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$java = "C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:LOCALAPPDATA\Android\Sdk\platform-tools;" + $env:PATH

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " ⚡ HyperTap - Deploy no POCO X8 Pro" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# Pareamento sem fio se solicitado
if ($WirelessPair -and $PairAddress -and $PairCode) {
    Write-Host "Iniciando pareamento sem fio com $PairAddress..." -ForegroundColor Yellow
    & $adb pair $PairAddress $PairCode
    if ($ConnectAddress) {
        & $adb connect $ConnectAddress
    }
}

# 1. Verifica dispositivos conectados
Write-Host "`n[1/4] Verificando conexao com o dispositivo..." -ForegroundColor Yellow
$devices = & $adb devices -l | Where-Object { $_ -match "device\b" -and $_ -notmatch "List of" }

if (-not $devices) {
    Write-Host "`n[!] Nenhum aparelho detectado via ADB." -ForegroundColor Red
    Write-Host "Por favor, siga as etapas de conexao:" -ForegroundColor White
    Write-Host "  1. Conecte o POCO X8 Pro ao PC via cabo USB (ou ative Depuracao sem fio)." -ForegroundColor Gray
    Write-Host "  2. No aparelho, acesse Configuracoes adicionais > Opcoes do desenvolvedor." -ForegroundColor Gray
    Write-Host "  3. Habilite 'Depuracao USB' e 'Instalar via USB'." -ForegroundColor Gray
    Write-Host "  4. Quando aparecer a mensagem na tela do celular, marque 'Sempre permitir' e toque em OK.`n" -ForegroundColor Gray
    exit 1
}

# 2. Mostra informações do dispositivo
$model = & $adb shell getprop ro.product.model
$androidVer = & $adb shell getprop ro.build.version.release
$hyperOsVer = & $adb shell getprop ro.mi.os.version.name
Write-Host "Dispositivo conectado: $model (Android $androidVer, HyperOS: $hyperOsVer)" -ForegroundColor Green

# 3. Compilação do APK Debug
Write-Host "`n[2/4] Compilando APK do HyperTap..." -ForegroundColor Yellow
Set-Location "C:\Users\Pichau\.gemini\antigravity\scratch\hypertap"

$buildResult = cmd /c "gradlew.bat assembleDebug"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro durante a compilacao. Abra o projeto no Android Studio para verificar pendencias de SDK." -ForegroundColor Red
    exit 1
}

$apkPath = "app/build/outputs/apk/debug/app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "APK nao encontrado em $apkPath" -ForegroundColor Red
    exit 1
}

# 4. Instalação e Execução
Write-Host "`n[3/4] Instalando APK no $model..." -ForegroundColor Yellow
& $adb install -r -d -g $apkPath

Write-Host "`n[4/4] Iniciando HyperTap na tela..." -ForegroundColor Yellow
& $adb shell am start -n com.hypertap.autoclicker/.MainActivity

Write-Host "`n[OK] HyperTap instalado e executando com sucesso no seu POCO X8 Pro!" -ForegroundColor Green

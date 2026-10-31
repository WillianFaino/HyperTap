# ⚡ HyperTap - Cliques Automatizados

**HyperTap** é um aplicativo Android nativo de alta performance para automação avançada de toques e gestos na tela, desenvolvido sob medida para **Android 16** e dispositivos com tela de 120Hz/144Hz como o **POCO X8 Pro**.

---

## 🎯 Funcionalidades

- **Toque em Posição Única:** Dispare cliques em coordenadas específicas com precisão de milissegundos.
- **Múltiplos Toques Simultâneos:** Dispare até 10 cliques ao mesmo tempo na tela.
- **Swipe / Arrasto com Início e Fim:** Deslize suave de um ponto A até B com duração programável.
- **Arrasto Contínuo em Loop (Sem Tirar o Dedo):** Concatenação contínua de traços com `StrokeDescription(..., willContinue = true)` permitindo círculos, polígonos ou vaivéns ininterruptos.
- **Menu Flutuante sobre Outros Apps:** Barra compacta que acompanha você em qualquer jogo ou aplicativo com botões de Play, Pause, Adicionar Alvo (+), Remover Alvo (-), Visibilidade dos Marcadores e Salvar Padrão.
- **Pinos de Alvo Arrastáveis (①, ②, ③...):** Posicione livremente com o dedo onde os toques devem acontecer.
- **Persistência de Padrões (Room Database):** Salve e reutilize macros ilimitadas com seus intervalos e configurações.

---

## 🛠️ Stack Tecnológica

- **Linguagem:** Kotlin 2.x (K2 Compiler)
- **UI:** Jetpack Compose + Material Design 3
- **Injeção de Gestos:** Android `AccessibilityService` com `dispatchGesture`
- **Sobreposição Flutuante:** `WindowManager` com `TYPE_APPLICATION_OVERLAY`
- **Banco de Dados Local:** Room Database + Kotlinx Serialization
- **Assincronismo:** Kotlin Coroutines & StateFlow

---

## 📱 Configuração no POCO X8 Pro (HyperOS / Android 16)

1. **Ativar o Modo Desenvolvedor:**
   - Acesse **Configurações** > **Sobre o telefone**.
   - Toque 7 vezes em **Versão do SO** até liberar o modo desenvolvedor.
2. **Permitir Configurações Restritas (Android 16):**
   - Ao instalar o APK pela primeira vez, o Android bloqueia o serviço de acessibilidade por segurança.
   - Vá na tela inicial, toque e segure no ícone do **HyperTap** > **Informações do app**.
   - No canto superior direito, toque nos **3 pontinhos** e selecione **Permitir configurações restritas**.
3. **Permissões do HyperOS:**
   - Em **Informações do app** > **Outras permissões**, garanta que **Exibir janelas pop-up em segundo plano** esteja ativo.
   - Em **Economia de bateria**, defina como **Sem restrições** para que a automação não seja suspensa em sessões longas.

---

## 🚀 Como Compilar e Rodar no Aparelho

### Opção 1: Via Script Automatizado (PowerShell)
Com o POCO X8 Pro conectado via USB ou Wi-Fi Debugging:
```powershell
./scripts/deploy-poco.ps1
```
Para parear e conectar via Wi-Fi sem cabos:
```powershell
./scripts/deploy-poco.ps1 -WirelessPair -PairAddress "192.168.1.X:PORTA" -PairCode "CODIGO" -ConnectAddress "192.168.1.X:PORTA_CONEXAO"
```

### Opção 2: Pelo Android Studio
1. Abra a pasta `hypertap` no **Android Studio**.
2. Selecione seu **POCO X8 Pro** na barra de dispositivos superior.
3. Clique no botão verde de **Run (Shift + F10)**.



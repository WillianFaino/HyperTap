# HyperTap - Diretrizes e Regras do Projeto

Este repositório segue regras estritas de governança, versionamento e execução contínua.

## 1. Operações de Git Exclusivamente via Terminal
- Todas as operações de controle de versão (criação de branch, commit, push, PR, status, fetch, pull) devem ser realizadas estritamente através da linha de comando (Git CLI e GitHub CLI).
- Nenhuma ferramenta gráfica ou automação externa fora do terminal deve ser utilizada para ações de repositório.

## 2. Monitoramento Ativo de Processos
- Qualquer comando ou tarefa despachada em segundo plano deve ser ativamente acompanhada.
- Comandos bloqueados, sem progresso ou travados aguardando entrada interativa oculta devem ser cancelados imediatamente para evitar desperdício de tempo.

## 3. Modelo de Branches e Proteção da `main`
- A branch `main` é protegida. Nenhum commit de desenvolvimento deve ser feito diretamente nela.
- Toda nova funcionalidade, ajuste, correção ou documentação deve ser desenvolvida em uma branch apartada e descritiva:
  - `feature/<nome>`: novas funcionalidades
  - `fix/<nome>`: correções de bugs
  - `refactor/<nome>`: refatorações de código
  - `docs/<nome>`: atualizações de documentação
- Utilizar mensagens de commit semânticas (Conventional Commits: `feat:`, `fix:`, `docs:`, `refactor:`, etc.).
- Toda branch finalizada deve ser enviada para o repositório remoto (`git push -u origin <branch>`) e ter um Pull Request aberto via `gh pr create`.

## 4. Autorização Estrita de Merge (Exclusivo do Usuário)
- **REGRA INVIOLÁVEL:** O assistente/agente está terminantemente proibido de realizar merges para a branch `main` (`git merge main`, `gh pr merge`, etc.).
- Apenas o usuário possui a autoridade de revisar, aprovar e realizar o merge no GitHub.

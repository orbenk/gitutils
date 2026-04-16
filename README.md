# git-commit-ai

Script PowerShell que gera mensagens de commit semântico automaticamente usando IA, a partir do diff do repositório atual.

![Fluxo do script](./commit_script_flow.svg)

## Funcionalidades

- Detecta automaticamente diff **staged** ou **unstaged**
- Envia o diff + nome da branch + arquivos alterados para o **Google Gemini 2.0 Flash** (gratuito, sem cartão de crédito)
- Gera mensagem seguindo o padrão **[Conventional Commits](https://www.conventionalcommits.org/)** em português
- Menu interativo: commitar direto, copiar para clipboard ou editar antes de commitar
- Truncagem configurável do diff para evitar exceder limites da API

## Pré-requisitos

- PowerShell 5.1 ou superior
- Git instalado e disponível no PATH
- Chave de API gratuita do Google Gemini

## Obtendo a API Key

1. Acesse [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)
2. Clique em **Create API Key**
3. Copie a chave gerada (não é necessário cartão de crédito)

> O modelo `gemini-2.0-flash` possui cota gratuita de **1.500 requisições/dia**.

## Instalação

Clone o repositório ou baixe o arquivo `Get-CommitMessage.ps1` diretamente.

Se necessário, libere a execução de scripts no PowerShell:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

## Configuração

Configure a API Key como variável de ambiente para não precisar informá-la a cada execução:

```powershell
# Sessão atual
$env:GEMINI_API_KEY = "sua_chave_aqui"

# Persistente (adicione ao seu $PROFILE)
[System.Environment]::SetEnvironmentVariable("GEMINI_API_KEY", "sua_chave_aqui", "User")
```

## Como usar

```powershell
# Uso básico — considera o que está no stage (git add feito previamente)
.\Get-CommitMessage.ps1

# Informando a chave diretamente
.\Get-CommitMessage.ps1 -ApiKey "sua_chave_aqui"

# Executa git add -A automaticamente antes de gerar o diff
.\Get-CommitMessage.ps1 -AutoStage

# Aumenta o limite de linhas do diff enviado para a IA
.\Get-CommitMessage.ps1 -MaxDiffLines 800

# Combinando parâmetros
.\Get-CommitMessage.ps1 -AutoStage -MaxDiffLines 800
```

### Menu interativo

Após gerar a mensagem, o script exibe:

```
  ─────────────────────────────────────────
   Mensagem de commit gerada:
  ─────────────────────────────────────────

  feat(auth): adiciona validação de token JWT na rota de login

  ─────────────────────────────────────────

  O que deseja fazer?
  [1] Usar esta mensagem e fazer o commit
  [2] Copiar para a área de transferência
  [3] Editar antes de commitar
  [4] Cancelar
```

## Parâmetros

| Parâmetro      | Tipo    | Padrão                  | Descrição                                              |
|----------------|---------|-------------------------|--------------------------------------------------------|
| `-ApiKey`      | string  | `$env:GEMINI_API_KEY`   | Chave de API do Google Gemini                          |
| `-MaxDiffLines`| int     | `500`                   | Limite de linhas do diff enviado para a IA             |
| `-AutoStage`   | switch  | —                       | Executa `git add -A` antes de coletar o diff           |

## Exemplos de mensagens geradas

```
feat(checkout): adiciona suporte a pagamento via PIX
fix(api): corrige timeout na requisição de consulta de CEP
refactor(auth): extrai lógica de validação para serviço dedicado
docs(readme): atualiza instruções de instalação no Windows
chore(deps): atualiza dependências para versões mais recentes
```

## Estrutura do repositório

```
git-commit-ai/
├── Get-CommitMessage.ps1   # Script principal
├── commit_script_flow.svg  # Diagrama do fluxo
└── README.md
```

## Licença

MIT

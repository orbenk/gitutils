#Requires -Version 5.1
<#
.SYNOPSIS
    Gera uma mensagem de commit semântico usando IA (Google Gemini) a partir do diff do repositório atual.

.DESCRIPTION
    Coleta o diff staged (ou unstaged se não houver staged), o nome da branch atual
    e envia para a API gratuita do Google Gemini, que retorna uma mensagem de commit
    seguindo o padrão Conventional Commits.

.PARAMETER ApiKey
    Chave de API do Google Gemini (obtenha em https://aistudio.google.com/app/apikey).
    Pode ser definida via variável de ambiente GEMINI_API_KEY.

.PARAMETER MaxDiffLines
    Limite de linhas do diff enviado para a IA (padrão: 500).

.PARAMETER AutoStage
    Se informado, executa 'git add -A' antes de gerar o diff.

.EXAMPLE
    .\Get-CommitMessage.ps1 -ApiKey "SUA_CHAVE_AQUI"

.EXAMPLE
    $env:GEMINI_API_KEY = "SUA_CHAVE_AQUI"
    .\Get-CommitMessage.ps1 -AutoStage

.NOTES
    Modelo: gemini-2.0-flash (gratuito, sem necessidade de cartão de crédito)
    Obtenha sua chave em: https://aistudio.google.com/app/apikey
#>

[CmdletBinding()]
param(
    [string]$ApiKey = "",

    [int]$MaxDiffLines = 500,

    [switch]$AutoStage,

    # Modo não-interativo: suprime toda a saída de status e escreve apenas a mensagem
    # de commit no stdout. Usado pelo plugin do IntelliJ/JetBrains.
    [switch]$OutputOnly,

    # Provedor de IA: Groq (padrão, gratuito, ~14400 req/dia) ou Gemini.
    [ValidateSet("Groq", "Gemini")]
    [string]$Provider = "Groq"
)

# ── Helpers ─────────────────────────────────────────────────────────────────

function Write-Step {
    param([string]$Message, [string]$Color = "Cyan")
    if (-not $OutputOnly) { Write-Host "`n  » $Message" -ForegroundColor $Color }
}

function Write-Success {
    param([string]$Message)
    if (-not $OutputOnly) { Write-Host "  ✔ $Message" -ForegroundColor Green }
}

function Write-Fail {
    param([string]$Message)
    if ($OutputOnly) { Write-Error $Message } else { Write-Host "`n  ✘ $Message" -ForegroundColor Red }
    exit 1
}

function Invoke-LlmApi {
    param([string]$Prompt, [int]$MaxTokens = 300)

    if ($Provider -eq "Groq") {
        $url  = "https://api.groq.com/openai/v1/chat/completions"
        $body = @{
            model       = "llama-3.3-70b-versatile"
            messages    = @(@{ role = "user"; content = $Prompt })
            temperature = 0.2
            max_tokens  = $MaxTokens
        } | ConvertTo-Json -Depth 10

        try {
            $resp = Invoke-RestMethod `
                -Uri $url -Method Post `
                -Headers @{ Authorization = "Bearer $ApiKey" } `
                -ContentType "application/json; charset=utf-8" `
                -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
                -ErrorAction Stop
        }
        catch {
            $code = $_.Exception.Response.StatusCode.value__
            if ($code -eq 401) { Write-Fail "API Key inválida (401). Verifique GROQ_API_KEY em https://console.groq.com" }
            if ($code -eq 429) { Write-Fail "Limite de requisições atingido (429). Aguarde e tente novamente." }
            Write-Fail "Erro ao chamar a API do Groq: $_"
        }

        return $resp.choices[0].message.content.Trim()
    }
    else {
        $url  = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$ApiKey"
        $body = @{
            contents         = @(@{ parts = @(@{ text = $Prompt }) })
            generationConfig = @{ temperature = 0.2; maxOutputTokens = $MaxTokens }
        } | ConvertTo-Json -Depth 10

        try {
            $resp = Invoke-RestMethod `
                -Uri $url -Method Post `
                -ContentType "application/json; charset=utf-8" `
                -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) `
                -ErrorAction Stop
        }
        catch {
            $code = $_.Exception.Response.StatusCode.value__
            if ($code -eq 403) { Write-Fail "API Key inválida (403). Verifique GEMINI_API_KEY em https://aistudio.google.com/app/apikey" }
            if ($code -eq 429) { Write-Fail "Limite de requisições atingido (429). Aguarde e tente novamente." }
            Write-Fail "Erro ao chamar a API do Gemini: $_"
        }

        return $resp.candidates[0].content.parts[0].text.Trim()
    }
}

# ── Validações iniciais ──────────────────────────────────────────────────────

if (-not $ApiKey) {
    $ApiKey = if ($Provider -eq "Groq") { $env:GROQ_API_KEY } else { $env:GEMINI_API_KEY }
}

if (-not $ApiKey) {
    $envVar = if ($Provider -eq "Groq") { "GROQ_API_KEY" } else { "GEMINI_API_KEY" }
    Write-Fail "API Key não encontrada. Defina $envVar ou passe -ApiKey 'sua_chave'."
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Fail "Git não encontrado no PATH. Instale o Git e tente novamente."
}

$repoRoot = git rev-parse --show-toplevel 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Não foi possível encontrar um repositório Git no diretório atual."
}

# ── Branch atual ────────────────────────────────────────────────────────────

Write-Step "Detectando branch atual..."
$branchName = git rev-parse --abbrev-ref HEAD 2>&1
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branchName)) {
    $branchName = "unknown"
}
Write-Success "Branch: $branchName"

# ── Auto stage ──────────────────────────────────────────────────────────────

if ($AutoStage) {
    Write-Step "Executando 'git add -A'..."
    git add -A | Out-Null
    Write-Success "Todos os arquivos adicionados ao stage."
}

# ── Coleta do diff ──────────────────────────────────────────────────────────

Write-Step "Coletando diff..."

$diffContent = git diff --cached 2>&1
$diffType = "staged"

if ([string]::IsNullOrWhiteSpace($diffContent)) {
    $diffContent = git diff 2>&1
    $diffType = "unstaged"
}

if ([string]::IsNullOrWhiteSpace($diffContent)) {
    Write-Fail "Nenhuma alteração encontrada (staged ou unstaged). Faça alterações antes de executar o script."
}

$diffLines = ($diffContent -split "`n")
$totalLines = $diffLines.Count

if ($totalLines -gt $MaxDiffLines) {
    Write-Host "  ! Diff muito grande ($totalLines linhas). Truncando para $MaxDiffLines linhas." -ForegroundColor Yellow
    $diffContent = ($diffLines | Select-Object -First $MaxDiffLines) -join "`n"
    $diffContent += "`n... [diff truncado — $($totalLines - $MaxDiffLines) linhas omitidas]"
}

Write-Success "Diff coletado ($diffType, $([Math]::Min($totalLines, $MaxDiffLines)) linhas)"

# ── Arquivos modificados ────────────────────────────────────────────────────

$changedFiles = git diff --cached --name-only 2>&1
if ([string]::IsNullOrWhiteSpace($changedFiles)) {
    $changedFiles = git diff --name-only 2>&1
}

# ── Prompt para o Gemini ────────────────────────────────────────────────────

$prompt = @"
Você é um especialista em Git e Conventional Commits. Analise o diff abaixo e gere uma mensagem de commit semântico.

## Contexto
- Branch: $branchName
- Arquivos modificados:
$changedFiles

## Diff
``````diff
$diffContent
``````

## Instruções
Gere UMA mensagem de commit seguindo o padrão Conventional Commits:
- Formato: <tipo>(<escopo opcional>): <descrição curta em português>
- Tipos válidos: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
- A descrição deve ter no máximo 72 caracteres
- Use o imperativo: "adiciona", "corrige", "atualiza" (não "adicionado", "corrigido")
- Se necessário, adicione um corpo com explicação após uma linha em branco
- Se houver breaking change, adicione "BREAKING CHANGE:" no rodapé

Responda APENAS com a mensagem de commit, sem explicações adicionais, sem blocos de código markdown.
"@

# ── Chamada à IA ────────────────────────────────────────────────────────────

Write-Step "Enviando diff para o $Provider..."
$commitMessage = Invoke-LlmApi -Prompt $prompt

if ([string]::IsNullOrWhiteSpace($commitMessage)) {
    Write-Fail "A IA não retornou uma mensagem de commit válida."
}

# ── Exibição do resultado ───────────────────────────────────────────────────

Write-Host ""
Write-Host "  ─────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "   Mensagem de commit gerada:" -ForegroundColor White
Write-Host "  ─────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""
Write-Host $commitMessage -ForegroundColor Yellow
Write-Host ""
Write-Host "  ─────────────────────────────────────────" -ForegroundColor DarkGray

# ── Modo não-interativo (plugin IntelliJ) ───────────────────────────────────

if ($OutputOnly) {
    Write-Output $commitMessage
    exit 0
}

# ── Ação interativa ─────────────────────────────────────────────────────────

Write-Host ""
Write-Host "  O que deseja fazer?" -ForegroundColor White
Write-Host "  [1] Usar esta mensagem e fazer o commit" -ForegroundColor Cyan
Write-Host "  [2] Copiar para a área de transferência" -ForegroundColor Cyan
Write-Host "  [3] Editar antes de commitar" -ForegroundColor Cyan
Write-Host "  [4] Cancelar" -ForegroundColor DarkGray
Write-Host ""

$choice = Read-Host "  Escolha"

switch ($choice) {
    "1" {
        Write-Step "Realizando commit..."
        git commit -m $commitMessage
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Commit realizado com sucesso!"
        } else {
            Write-Fail "Falha ao realizar o commit. Verifique se há arquivos no stage."
        }
    }
    "2" {
        $commitMessage | Set-Clipboard
        Write-Success "Mensagem copiada para a área de transferência!"
    }
    "3" {
        $edited = Read-Host "  Edite a mensagem"
        if (-not [string]::IsNullOrWhiteSpace($edited)) {
            git commit -m $edited
            if ($LASTEXITCODE -eq 0) {
                Write-Success "Commit realizado com a mensagem editada!"
            } else {
                Write-Fail "Falha ao realizar o commit."
            }
        } else {
            Write-Host "  Operação cancelada." -ForegroundColor DarkGray
        }
    }
    default {
        Write-Host "  Operação cancelada." -ForegroundColor DarkGray
    }
}

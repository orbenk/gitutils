#Requires -Version 5.1
<#
.SYNOPSIS
    Gera um Pull Request no GitHub com título e descrição gerados por IA (Google Gemini).

.DESCRIPTION
    Compara a branch atual com a branch base, coleta o diff, histórico de commits e
    arquivos modificados. Envia tudo ao Gemini para gerar título e corpo do PR em
    markdown. Em seguida, usa o GitHub CLI (gh) para abrir o PR.

.PARAMETER ApiKey
    Chave de API do Google Gemini.
    Pode ser definida via variável de ambiente GEMINI_API_KEY.

.PARAMETER BaseBranch
    Branch de destino do PR (padrão: detecta automaticamente main/master/develop).

.PARAMETER Draft
    Cria o PR como rascunho (Draft PR).

.PARAMETER Reviewer
    Um ou mais revisores do GitHub separados por vírgula (ex: "joao,maria").

.PARAMETER Label
    Um ou mais labels separados por vírgula (ex: "bug,enhancement").

.PARAMETER MaxDiffLines
    Limite de linhas do diff enviado para a IA (padrão: 600).

.PARAMETER NoPush
    Não faz push da branch antes de criar o PR.

.EXAMPLE
    .\New-PullRequest.ps1

.EXAMPLE
    .\New-PullRequest.ps1 -BaseBranch develop -Draft -Reviewer "joao,maria"

.EXAMPLE
    $env:GEMINI_API_KEY = "SUA_CHAVE"
    .\New-PullRequest.ps1 -Label "enhancement" -NoPush

.NOTES
    Pré-requisitos:
      - GitHub CLI (gh) instalado e autenticado: https://cli.github.com
      - Chave Gemini gratuita: https://aistudio.google.com/app/apikey
#>

[CmdletBinding()]
param(
    [string]$ApiKey      = $env:GEMINI_API_KEY,
    [string]$BaseBranch  = "",
    [switch]$Draft,
    [string]$Reviewer    = "",
    [string]$Label       = "",
    [int]$MaxDiffLines   = 600,
    [switch]$NoPush
)

# ── Helpers ──────────────────────────────────────────────────────────────────

function Write-Step    { param([string]$Msg) Write-Host "`n  » $Msg" -ForegroundColor Cyan }
function Write-Success { param([string]$Msg) Write-Host "  ✔ $Msg"  -ForegroundColor Green }
function Write-Warn    { param([string]$Msg) Write-Host "  ! $Msg"  -ForegroundColor Yellow }
function Write-Fail    { param([string]$Msg) Write-Host "`n  ✘ $Msg`n" -ForegroundColor Red; exit 1 }

function Invoke-Gemini {
    param([string]$Prompt)

    $url  = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$ApiKey"
    $body = @{
        contents         = @(@{ parts = @(@{ text = $Prompt }) })
        generationConfig = @{ temperature = 0.3; maxOutputTokens = 1500 }
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
        if ($code -eq 403) { Write-Fail "API Key inválida ou sem permissão (403). Verifique em https://aistudio.google.com/app/apikey" }
        if ($code -eq 429) { Write-Fail "Limite de requisições atingido (429). Aguarde e tente novamente." }
        Write-Fail "Erro ao chamar Gemini: $_"
    }

    return $resp.candidates[0].content.parts[0].text.Trim()
}

function Show-Separator { Write-Host "  $("─" * 55)" -ForegroundColor DarkGray }

# ── Validações ────────────────────────────────────────────────────────────────

if (-not $ApiKey) {
    Write-Fail "GEMINI_API_KEY não definida. Use -ApiKey ou exporte a variável de ambiente."
}

foreach ($cmd in @("git", "gh")) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Fail "'$cmd' não encontrado no PATH. Instale-o antes de continuar."
    }
}

$null = git rev-parse --show-toplevel 2>&1
if ($LASTEXITCODE -ne 0) { Write-Fail "Nenhum repositório Git encontrado no diretório atual." }

# Verifica autenticação do gh
$null = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) { Write-Fail "GitHub CLI não autenticado. Execute: gh auth login" }

# ── Branch atual ──────────────────────────────────────────────────────────────

Write-Step "Detectando branches..."

$currentBranch = (git rev-parse --abbrev-ref HEAD 2>&1).Trim()
if ($LASTEXITCODE -ne 0 -or $currentBranch -eq "HEAD") {
    Write-Fail "Não foi possível detectar a branch atual (HEAD detached?)."
}

# ── Branch base ───────────────────────────────────────────────────────────────

if ([string]::IsNullOrWhiteSpace($BaseBranch)) {
    # Tenta detectar a branch padrão do repositório remoto
    $defaultBranch = (gh repo view --json defaultBranchRef --jq ".defaultBranchRef.name" 2>&1).Trim()
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($defaultBranch)) {
        $BaseBranch = $defaultBranch
    }
    else {
        # Fallback: procura main > master > develop localmente
        foreach ($candidate in @("main", "master", "develop")) {
            $exists = git rev-parse --verify $candidate 2>&1
            if ($LASTEXITCODE -eq 0) { $BaseBranch = $candidate; break }
        }
    }
}

if ([string]::IsNullOrWhiteSpace($BaseBranch)) {
    Write-Fail "Não foi possível detectar a branch base. Use -BaseBranch para especificá-la."
}

if ($currentBranch -eq $BaseBranch) {
    Write-Fail "A branch atual ($currentBranch) é igual à branch base. Mude para uma feature branch antes de criar o PR."
}

Write-Success "Branch atual : $currentBranch"
Write-Success "Branch base  : $BaseBranch"

# ── Push da branch (opcional) ─────────────────────────────────────────────────

if (-not $NoPush) {
    Write-Step "Fazendo push da branch '$currentBranch'..."
    git push --set-upstream origin $currentBranch 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "Push falhou ou a branch já está atualizada. Continuando sem push."
    } else {
        Write-Success "Push realizado."
    }
}

# ── Coleta de dados para o contexto ───────────────────────────────────────────

Write-Step "Coletando dados da branch..."

# Commits exclusivos da branch atual
$commitLog = git log "$BaseBranch..$currentBranch" --oneline --no-merges 2>&1
if ([string]::IsNullOrWhiteSpace($commitLog)) {
    Write-Fail "Nenhum commit encontrado entre '$BaseBranch' e '$currentBranch'. Certifique-se de ter commitado suas alterações."
}
$commitCount = ($commitLog -split "`n" | Where-Object { $_ -ne "" }).Count
Write-Success "$commitCount commit(s) detectado(s)"

# Arquivos modificados
$changedFiles = git diff "$BaseBranch..$currentBranch" --name-status 2>&1

# Diff completo (truncado)
$rawDiff  = git diff "$BaseBranch..$currentBranch" 2>&1
$diffLines = ($rawDiff -split "`n")
$totalLines = $diffLines.Count
$wasTruncated = $false

if ($totalLines -gt $MaxDiffLines) {
    Write-Warn "Diff grande ($totalLines linhas). Truncando para $MaxDiffLines linhas."
    $rawDiff = ($diffLines | Select-Object -First $MaxDiffLines) -join "`n"
    $rawDiff += "`n... [diff truncado — $($totalLines - $MaxDiffLines) linhas omitidas]"
    $wasTruncated = $true
}
Write-Success "Diff coletado ($([Math]::Min($totalLines, $MaxDiffLines)) linhas$(if ($wasTruncated) { ', truncado' }))"

# Tenta obter repo e owner para contexto
$repoInfo = (gh repo view --json nameWithOwner --jq ".nameWithOwner" 2>&1).Trim()

# ── Prompt ────────────────────────────────────────────────────────────────────

$prompt = @"
Você é um engenheiro de software sênior especialista em code review e documentação técnica.
Analise as informações abaixo e gere o conteúdo completo de um Pull Request profissional.

## Contexto do repositório
- Repositório : $repoInfo
- Branch de origem : $currentBranch
- Branch de destino: $BaseBranch
- Total de commits : $commitCount

## Commits incluídos
$commitLog

## Arquivos modificados (status + nome)
$changedFiles

## Diff
``````diff
$rawDiff
``````

## Instruções
Responda SOMENTE com um objeto JSON válido, sem markdown, sem blocos de código, sem texto antes ou depois.
O JSON deve ter exatamente esta estrutura:

{
  "title": "<título curto e direto em português, máx 72 chars, seguindo Conventional Commits: tipo(escopo): descrição>",
  "body": "<corpo completo do PR em markdown, em português>"
}

Regras para o "title":
- Seguir Conventional Commits: feat, fix, docs, refactor, perf, test, chore, ci, build
- Imperativo: "adiciona", "corrige", "atualiza" (não "adicionado")
- Máximo 72 caracteres

Regras para o "body" (markdown):
- Seção "## Resumo" — 2 a 4 frases descrevendo O QUÊ e POR QUÊ
- Seção "## Alterações" — lista com bullet points das principais mudanças técnicas
- Seção "## Como testar" — passos numerados para validar as mudanças
- Seção "## Checklist" com checkboxes markdown:
  - [ ] Código revisado pelo autor
  - [ ] Testes adicionados/atualizados
  - [ ] Documentação atualizada (se aplicável)
  - [ ] Sem warnings ou erros de lint
- Se houver breaking changes, adicione seção "## ⚠️ Breaking Changes"
- Seja técnico e objetivo. Não repita o título no corpo.
"@

# ── Chama a IA ────────────────────────────────────────────────────────────────

Write-Step "Enviando contexto para o Gemini..."
$rawResponse = Invoke-Gemini -Prompt $prompt

# Remove possíveis blocos de código que a IA insira mesmo sendo instruída a não fazer
$cleanJson = $rawResponse -replace '(?s)```(?:json)?\s*', '' -replace '```', ''

try {
    $pr = $cleanJson | ConvertFrom-Json -ErrorAction Stop
} catch {
    Write-Fail "A IA retornou um JSON inválido. Resposta bruta:`n$rawResponse"
}

$prTitle = $pr.title.Trim()
$prBody  = $pr.body.Trim()

if ([string]::IsNullOrWhiteSpace($prTitle) -or [string]::IsNullOrWhiteSpace($prBody)) {
    Write-Fail "Título ou corpo do PR estão vazios. Tente novamente."
}

# ── Exibição do resultado ─────────────────────────────────────────────────────

Write-Host ""
Show-Separator
Write-Host "   PR gerado pela IA:" -ForegroundColor White
Show-Separator
Write-Host ""
Write-Host "  TÍTULO:" -ForegroundColor DarkGray
Write-Host "  $prTitle" -ForegroundColor Yellow
Write-Host ""
Write-Host "  CORPO:" -ForegroundColor DarkGray
$prBody -split "`n" | ForEach-Object { Write-Host "  $_" -ForegroundColor White }
Write-Host ""
Show-Separator

# ── Ação interativa ───────────────────────────────────────────────────────────

Write-Host ""
Write-Host "  O que deseja fazer?" -ForegroundColor White
Write-Host "  [1] Criar PR agora" -ForegroundColor Cyan
Write-Host "  [2] Criar PR como Draft" -ForegroundColor Cyan
Write-Host "  [3] Editar título antes de criar" -ForegroundColor Cyan
Write-Host "  [4] Abrir no editor (salva em arquivo temporário)" -ForegroundColor Cyan
Write-Host "  [5] Copiar corpo para área de transferência" -ForegroundColor Cyan
Write-Host "  [6] Cancelar" -ForegroundColor DarkGray
Write-Host ""

$choice = Read-Host "  Escolha"

# Salva o corpo em arquivo temporário para uso pelo gh
$tempBody = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.md'
$prBody | Set-Content -Path $tempBody -Encoding UTF8

function Invoke-GhPr {
    param([string]$Title, [bool]$IsDraft = $false)

    $ghArgs = @(
        "pr", "create",
        "--title", $Title,
        "--body-file", $tempBody,
        "--base", $BaseBranch
    )

    if ($IsDraft)                                        { $ghArgs += "--draft" }
    if (-not [string]::IsNullOrWhiteSpace($Reviewer))   { $ghArgs += @("--reviewer", $Reviewer) }
    if (-not [string]::IsNullOrWhiteSpace($Label))       { $ghArgs += @("--label", $Label) }

    Write-Step "Criando PR com 'gh'..."
    & gh @ghArgs

    if ($LASTEXITCODE -eq 0) {
        Write-Success "PR criado com sucesso!"
    } else {
        Write-Fail "Falha ao criar o PR. Verifique se o repositório está conectado ao GitHub e o gh está autenticado."
    }
}

switch ($choice) {
    "1" {
        Invoke-GhPr -Title $prTitle -IsDraft $Draft.IsPresent
    }
    "2" {
        Invoke-GhPr -Title $prTitle -IsDraft $true
    }
    "3" {
        Write-Host ""
        Write-Host "  Título atual: $prTitle" -ForegroundColor DarkGray
        $newTitle = Read-Host "  Novo título"
        if ([string]::IsNullOrWhiteSpace($newTitle)) { $newTitle = $prTitle }
        Invoke-GhPr -Title $newTitle -IsDraft $Draft.IsPresent
    }
    "4" {
        # Abre o arquivo temporário no editor padrão para edição manual
        Write-Host ""
        Write-Host "  Arquivo temporário: $tempBody" -ForegroundColor DarkGray
        if ($env:EDITOR) {
            & $env:EDITOR $tempBody
        } elseif (Get-Command code -ErrorAction SilentlyContinue) {
            code --wait $tempBody
        } elseif (Get-Command notepad -ErrorAction SilentlyContinue) {
            Start-Process notepad $tempBody -Wait
        } else {
            Write-Warn "Nenhum editor encontrado. Edite manualmente: $tempBody"
        }
        $newTitle = Read-Host "  Título (Enter para manter: '$prTitle')"
        if ([string]::IsNullOrWhiteSpace($newTitle)) { $newTitle = $prTitle }
        Invoke-GhPr -Title $newTitle -IsDraft $Draft.IsPresent
    }
    "5" {
        $prBody | Set-Clipboard
        Write-Success "Corpo do PR copiado para a área de transferência!"
    }
    default {
        Write-Host "  Operação cancelada." -ForegroundColor DarkGray
    }
}

# Limpeza do arquivo temporário
if (Test-Path $tempBody) { Remove-Item $tempBody -Force }

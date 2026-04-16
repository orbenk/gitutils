#Requires -Version 5.1
<#
.SYNOPSIS
    Cria branches Git com nomenclatura padronizada de forma interativa.

.DESCRIPTION
    Guia o usuário na criação de uma branch com prefixo semântico
    (feature, fix, hotfix, release, etc.), sanitiza o nome automaticamente
    e oferece opções de checkout e push imediato.

.PARAMETER BaseBranch
    Branch de origem para criar a nova branch.
    Se omitido, usa a branch atual.

.PARAMETER Push
    Faz push imediato após criar a branch.

.EXAMPLE
    .\New-Branch.ps1

.EXAMPLE
    .\New-Branch.ps1 -BaseBranch main -Push

.NOTES
    Segue as convenções do Git Flow e Conventional Commits.
#>

[CmdletBinding()]
param(
    [string]$BaseBranch = "",
    [switch]$Push
)

# ── Helpers ───────────────────────────────────────────────────────────────────

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host "  $Text" -ForegroundColor White
    Write-Host "  $("─" * ($Text.Length))" -ForegroundColor DarkGray
}

function Write-Step    { param([string]$Msg) Write-Host "`n  » $Msg" -ForegroundColor Cyan }
function Write-Success { param([string]$Msg) Write-Host "  ✔ $Msg"  -ForegroundColor Green }
function Write-Warn    { param([string]$Msg) Write-Host "  ! $Msg"  -ForegroundColor Yellow }
function Write-Fail    { param([string]$Msg) Write-Host "`n  ✘ $Msg`n" -ForegroundColor Red; exit 1 }
function Write-Muted   { param([string]$Msg) Write-Host "  $Msg"    -ForegroundColor DarkGray }

function Read-Option {
    param([string]$Prompt, [string[]]$Valid)
    while ($true) {
        $input = (Read-Host "`n  $Prompt").Trim()
        if ($input -in $Valid) { return $input }
        Write-Warn "Opção inválida. Escolha entre: $($Valid -join ', ')"
    }
}

function ConvertTo-Slug {
    param([string]$Text)
    # Normaliza acentos
    $normalized = $Text.Normalize([System.Text.NormalizationForm]::FormD)
    $slug = [System.Text.StringBuilder]::new()
    foreach ($char in $normalized.ToCharArray()) {
        $cat = [System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($char)
        if ($cat -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$slug.Append($char)
        }
    }
    return $slug.ToString() `
        .ToLower() `
        -replace '[^a-z0-9\s_-]', '' `
        -replace '\s+', '-' `
        -replace '-{2,}', '-' `
        -replace '^-|-$', ''
}

# ── Validações ────────────────────────────────────────────────────────────────

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Fail "Git não encontrado no PATH."
}

$null = git rev-parse --show-toplevel 2>&1
if ($LASTEXITCODE -ne 0) { Write-Fail "Nenhum repositório Git encontrado no diretório atual." }

# ── Definição dos tipos de branch ─────────────────────────────────────────────

$branchTypes = [ordered]@{
    "1" = @{
        key         = "feature"
        prefix      = "feature"
        label       = "Feature"
        description = "Nova funcionalidade"
        example     = "feature/login-oauth2"
        color       = "Cyan"
    }
    "2" = @{
        key         = "fix"
        prefix      = "fix"
        label       = "Fix"
        description = "Correção de bug em desenvolvimento"
        example     = "fix/botao-submit-desativado"
        color       = "Yellow"
    }
    "3" = @{
        key         = "hotfix"
        prefix      = "hotfix"
        label       = "Hotfix"
        description = "Correção urgente em produção"
        example     = "hotfix/falha-calculo-imposto"
        color       = "Red"
    }
    "4" = @{
        key         = "release"
        prefix      = "release"
        label       = "Release"
        description = "Preparação de nova versão"
        example     = "release/2.4.0"
        color       = "Green"
    }
    "5" = @{
        key         = "chore"
        prefix      = "chore"
        label       = "Chore"
        description = "Tarefa técnica sem impacto funcional"
        example     = "chore/atualizar-dependencias"
        color       = "DarkCyan"
    }
    "6" = @{
        key         = "docs"
        prefix      = "docs"
        label       = "Docs"
        description = "Documentação apenas"
        example     = "docs/guia-instalacao"
        color       = "DarkCyan"
    }
    "7" = @{
        key         = "refactor"
        prefix      = "refactor"
        label       = "Refactor"
        description = "Refatoração de código sem mudança de comportamento"
        example     = "refactor/servico-autenticacao"
        color       = "DarkCyan"
    }
    "8" = @{
        key         = "test"
        prefix      = "test"
        label       = "Test"
        description = "Adição ou correção de testes"
        example     = "test/cobertura-modulo-pagamento"
        color       = "DarkCyan"
    }
    "9" = @{
        key         = "ci"
        prefix      = "ci"
        label       = "CI/CD"
        description = "Pipelines e automações de build/deploy"
        example     = "ci/pipeline-deploy-homologacao"
        color       = "DarkCyan"
    }
}

# ── Cabeçalho ─────────────────────────────────────────────────────────────────

Clear-Host
Write-Host ""
Write-Host "  ╔══════════════════════════════════════════╗" -ForegroundColor DarkGray
Write-Host "  ║        Criador de Branches Git           ║" -ForegroundColor White
Write-Host "  ╚══════════════════════════════════════════╝" -ForegroundColor DarkGray

# ── Branch atual ──────────────────────────────────────────────────────────────

$currentBranch = (git rev-parse --abbrev-ref HEAD 2>&1).Trim()

if ([string]::IsNullOrWhiteSpace($BaseBranch)) {
    $BaseBranch = $currentBranch
}

Write-Host ""
Write-Muted "Branch atual  : $currentBranch"
Write-Muted "Branch origem : $BaseBranch"

# ── Escolha do tipo ───────────────────────────────────────────────────────────

Write-Header "Tipo da branch"
Write-Host ""

foreach ($key in $branchTypes.Keys) {
    $type  = $branchTypes[$key]
    $num   = "[$key]".PadRight(4)
    $label = $type.label.PadRight(10)
    $ex    = $type.example
    Write-Host "  $num" -NoNewline -ForegroundColor DarkGray
    Write-Host " $label" -NoNewline -ForegroundColor $type.color
    Write-Host " — $($type.description)" -NoNewline -ForegroundColor Gray
    Write-Host "  (ex: $ex)" -ForegroundColor DarkGray
}

$typeChoice = Read-Option -Prompt "Escolha o tipo [1-9]" -Valid ($branchTypes.Keys)
$selectedType = $branchTypes[$typeChoice]

Write-Success "Tipo selecionado: $($selectedType.label)"

# ── Número de ticket/issue (opcional) ────────────────────────────────────────

Write-Header "Ticket / Issue (opcional)"
Write-Muted "Deixe em branco para pular. Ex: 1234, PROJ-42, GH-99"
Write-Host ""

$ticketRaw = (Read-Host "  Número do ticket").Trim()
$ticketSlug = ""
if (-not [string]::IsNullOrWhiteSpace($ticketRaw)) {
    $ticketSlug = (ConvertTo-Slug -Text $ticketRaw) + "-"
}

# ── Nome descritivo ───────────────────────────────────────────────────────────

Write-Header "Nome da branch"
Write-Muted "Descreva em palavras o que será feito."
Write-Muted "Exemplo: 'Adicionar login com OAuth2'  →  feature/adicionar-login-com-oauth2"
Write-Host ""

$nameRaw = ""
while ([string]::IsNullOrWhiteSpace($nameRaw)) {
    $nameRaw = (Read-Host "  Nome").Trim()
    if ([string]::IsNullOrWhiteSpace($nameRaw)) {
        Write-Warn "O nome não pode ser vazio."
    }
}

$nameSlug    = ConvertTo-Slug -Text $nameRaw
$branchName  = "$($selectedType.prefix)/$ticketSlug$nameSlug"

# ── Confirmação ───────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "  ──────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "   Branch que será criada:" -ForegroundColor White
Write-Host ""
Write-Host "   $branchName" -ForegroundColor Yellow
Write-Host ""
Write-Host "   A partir de: $BaseBranch" -ForegroundColor DarkGray
Write-Host "  ──────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""

$confirm = Read-Option -Prompt "Confirmar? [s] Sim  [e] Editar nome  [n] Cancelar" -Valid @("s", "e", "n")

if ($confirm -eq "n") {
    Write-Host "`n  Operação cancelada.`n" -ForegroundColor DarkGray
    exit 0
}

if ($confirm -eq "e") {
    Write-Host ""
    $customName = (Read-Host "  Nome completo da branch (sem o prefixo '$($selectedType.prefix)/')").Trim()
    if ([string]::IsNullOrWhiteSpace($customName)) {
        Write-Warn "Nome vazio. Mantendo o nome gerado."
    } else {
        $branchName = "$($selectedType.prefix)/$(ConvertTo-Slug -Text $customName)"
    }
    Write-Host ""
    Write-Host "  Branch final: $branchName" -ForegroundColor Yellow
    Write-Host ""
    $reconfirm = Read-Option -Prompt "Criar esta branch? [s/n]" -Valid @("s", "n")
    if ($reconfirm -eq "n") {
        Write-Host "`n  Operação cancelada.`n" -ForegroundColor DarkGray
        exit 0
    }
}

# ── Verifica se a branch já existe ───────────────────────────────────────────

$existsLocal  = git rev-parse --verify $branchName 2>&1
$existsRemote = git ls-remote --heads origin $branchName 2>&1

if ($LASTEXITCODE -eq 0 -or -not [string]::IsNullOrWhiteSpace($existsRemote)) {
    Write-Warn "A branch '$branchName' já existe."
    $overwrite = Read-Option -Prompt "Fazer checkout nela mesmo assim? [s/n]" -Valid @("s", "n")
    if ($overwrite -eq "n") {
        Write-Host "`n  Operação cancelada.`n" -ForegroundColor DarkGray
        exit 0
    }
    Write-Step "Fazendo checkout na branch existente..."
    git checkout $branchName
    if ($LASTEXITCODE -eq 0) { Write-Success "Checkout realizado em '$branchName'." }
    else { Write-Fail "Falha ao fazer checkout." }
    exit 0
}

# ── Criação da branch ─────────────────────────────────────────────────────────

Write-Step "Criando branch a partir de '$BaseBranch'..."

# Garante que a base está atualizada se for remota
$remoteBase = git ls-remote --heads origin $BaseBranch 2>&1
if (-not [string]::IsNullOrWhiteSpace($remoteBase)) {
    Write-Muted "Atualizando '$BaseBranch' do remote..."
    git fetch origin "$BaseBranch`:$BaseBranch" --quiet 2>&1 | Out-Null
}

git checkout -b $branchName $BaseBranch
if ($LASTEXITCODE -ne 0) { Write-Fail "Falha ao criar a branch '$branchName'." }

Write-Success "Branch '$branchName' criada e checkout realizado!"

# ── Push imediato ─────────────────────────────────────────────────────────────

$doPush = $Push.IsPresent
if (-not $doPush) {
    Write-Host ""
    $pushChoice = Read-Option -Prompt "Fazer push da branch para o remote agora? [s/n]" -Valid @("s", "n")
    $doPush = ($pushChoice -eq "s")
}

if ($doPush) {
    Write-Step "Fazendo push para origin/$branchName..."
    git push --set-upstream origin $branchName
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Push realizado. Branch disponível no remote."
    } else {
        Write-Warn "Push falhou. Execute manualmente: git push --set-upstream origin $branchName"
    }
}

# ── Resumo final ──────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "  ──────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "   Pronto! Resumo:" -ForegroundColor White
Write-Host ""
Write-Muted "  Branch criada : $branchName"
Write-Muted "  Origem        : $BaseBranch"
Write-Muted "  Push          : $(if ($doPush) { 'sim' } else { 'não' })"
Write-Host ""
Write-Muted "  Próximos passos:"
Write-Muted "    git add . && .\Get-CommitMessage.ps1"
Write-Muted "    .\New-PullRequest.ps1 -BaseBranch $BaseBranch"
Write-Host ""
Write-Host "  ──────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""

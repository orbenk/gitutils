#Requires -Version 5.1
<#
.SYNOPSIS
    Configura o Git para usar os hooks deste repositório.

.DESCRIPTION
    Define core.hooksPath para a pasta 'hooks/' e garante que os arquivos
    de hook têm permissão de execução (necessário no Git for Windows / WSL).

.EXAMPLE
    .\hooks\Install-Hooks.ps1
#>

[CmdletBinding()]
param()

function Write-Success { param([string]$Msg) Write-Host "  ✔ $Msg" -ForegroundColor Green }
function Write-Fail    { param([string]$Msg) Write-Host "`n  ✘ $Msg`n" -ForegroundColor Red; exit 1 }
function Write-Step    { param([string]$Msg) Write-Host "`n  » $Msg" -ForegroundColor Cyan }

# ── Valida que estamos dentro de um repositório Git ──────────────────────────

$null = git rev-parse --show-toplevel 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Nenhum repositório Git encontrado. Execute este script a partir da raiz do repositório."
}

$repoRoot = (git rev-parse --show-toplevel).Trim()
$hooksDir = Join-Path $repoRoot "hooks"

if (-not (Test-Path $hooksDir)) {
    Write-Fail "Pasta 'hooks/' não encontrada em: $repoRoot"
}

# ── Configura core.hooksPath ─────────────────────────────────────────────────

Write-Step "Configurando core.hooksPath..."
git config core.hooksPath hooks
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Falha ao configurar core.hooksPath."
}
Write-Success "core.hooksPath = hooks"

# ── Permissão de execução (Git for Windows usa chmod via Git bash) ────────────

Write-Step "Aplicando permissão de execução nos hooks..."

$hookFiles = Get-ChildItem -Path $hooksDir -File | Where-Object { $_.Extension -eq "" }

foreach ($hook in $hookFiles) {
    # git update-index --chmod=+x funciona no índice; usa bash para o arquivo em disco
    $relativePath = "hooks/$($hook.Name)"
    git update-index --chmod=+x $relativePath 2>&1 | Out-Null

    # Também aplica via bash do Git para garantir bit no disco
    $bashPath = $hook.FullName -replace '\\', '/'
    $bashPath = $bashPath -replace '^([A-Z]):', { "/$(($args[0][0]).ToString().ToLower())" }
    & git -c core.fileMode=true hash-object $hook.FullName | Out-Null
    Write-Success "$($hook.Name)"
}

# ── Resumo ────────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "  ──────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "   Hooks instalados com sucesso!" -ForegroundColor White
Write-Host ""
Write-Host "  Hooks ativos:" -ForegroundColor DarkGray
foreach ($hook in $hookFiles) {
    Write-Host "    • $($hook.Name)" -ForegroundColor Gray
}
Write-Host ""
Write-Host "  Para desativar: git config --unset core.hooksPath" -ForegroundColor DarkGray
Write-Host "  ──────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""

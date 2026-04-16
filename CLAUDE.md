# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A collection of three standalone PowerShell scripts (PowerShell 5.1+) that automate the Git workflow using the Google Gemini 2.0 Flash API (free tier). No build system, no tests, no dependencies beyond PowerShell itself.

## Scripts

| Script | Purpose |
|---|---|
| `New-Branch.ps1` | Interactive branch creation with standardized naming (Git Flow prefixes) |
| `Get-CommitMessage.ps1` | Generates a Conventional Commits message from the current diff via Gemini |
| `New-PullRequest.ps1` | Generates PR title + markdown body via Gemini and opens the PR using `gh` CLI |

## Running the scripts

```powershell
# Prerequisite: set the Gemini API key (get one free at https://aistudio.google.com/app/apikey)
$env:GEMINI_API_KEY = "sua_chave_aqui"

# 1. Create a branch
.\New-Branch.ps1 -BaseBranch main

# 2. Stage and commit with AI-generated message
git add .
.\Get-CommitMessage.ps1          # uses staged diff; falls back to unstaged
.\Get-CommitMessage.ps1 -AutoStage -MaxDiffLines 800   # stage everything first

# 3. Open a PR
.\New-PullRequest.ps1            # auto-detects base branch via gh CLI
.\New-PullRequest.ps1 -BaseBranch develop -Draft -Reviewer "joao,maria"
```

`New-PullRequest.ps1` additionally requires the GitHub CLI (`gh`) installed and authenticated (`gh auth login`).

## Git Hooks

The `hooks/` directory contains hooks that enforce the naming and commit conventions at git operation time.

| Hook | Trigger | Validates |
|---|---|---|
| `commit-msg` | every `git commit` | Conventional Commits format |
| `pre-push` | every `git push` | Branch name prefix and kebab-case format |

**One-time setup** (run once per clone):

```powershell
.\hooks\Install-Hooks.ps1
# equivalent to: git config core.hooksPath hooks
```

To disable: `git config --unset core.hooksPath`

The hooks are bash scripts (compatible with Git for Windows). They mirror the rules already encoded in `Get-CommitMessage.ps1` (valid Conventional Commits types) and `New-Branch.ps1` (valid prefixes: `feature`, `fix`, `hotfix`, `release`, `chore`, `docs`, `refactor`, `test`, `ci`). Base branches (`main`, `master`, `develop`) are never blocked by `pre-push`.

## Architecture

All three scripts follow the same internal structure:

1. **Helper functions** — `Write-Step`, `Write-Success`, `Write-Fail`, `Write-Warn` for consistent colored console output. `Write-Fail` always calls `exit 1`.
2. **Validation block** — checks for required tools (`git`, `gh`) and environment (`GEMINI_API_KEY`, inside a git repo).
3. **Data collection** — runs git commands to gather diff, branch names, commit log, changed files.
4. **Gemini API call** — sends a structured prompt via `Invoke-RestMethod` to `generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent`. `New-PullRequest.ps1` wraps this in a reusable `Invoke-Gemini` function.
5. **Interactive menu** — presents numbered options and acts on the user's choice (commit, copy to clipboard, open editor, create PR, cancel).

### Prompt conventions
- All prompts and generated output are in **Portuguese**.
- Commit messages follow **Conventional Commits** (`feat`, `fix`, `docs`, `refactor`, etc.) with imperative verbs (`adiciona`, `corrige`, `atualiza`).
- `New-PullRequest.ps1` instructs the AI to return a **JSON object** (`{ "title": "...", "body": "..." }`), then strips stray markdown code fences before parsing with `ConvertFrom-Json`.

### Diff truncation
Both AI-facing scripts truncate diffs before sending:
- `Get-CommitMessage.ps1`: default `MaxDiffLines = 500`
- `New-PullRequest.ps1`: default `MaxDiffLines = 600`

Truncated diffs append a `[diff truncado — N linhas omitidas]` marker so the AI is aware.

### Branch naming (`New-Branch.ps1`)
Uses `ConvertTo-Slug` to sanitize user input: strips accents (Unicode normalization FormD), lowercases, replaces spaces with hyphens, removes invalid characters. Final format: `<prefix>/<ticket->?<slug>` (e.g., `feature/proj-142-adicionar-autenticacao-com-oauth2`).

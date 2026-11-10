# Coleta evidências reais da API do dashboard e grava em docs/evidencias/respostas-api.md.
# Uso (PowerShell, na raiz do projeto, com o sistema rodando em http://localhost:8080):
#   powershell -ExecutionPolicy Bypass -File docs/evidencias/coletar-api.ps1

param([string]$Base = "http://localhost:8080")

$saida = Join-Path $PSScriptRoot "respostas-api.md"

# Cada caso: descrição, usuário:senha (vazio = sem login), caminho, status esperado
$casos = @(
    @("Opções de filtro", "gerente01:teste123", "/api/dashboard/opcoes", "200"),
    @("RF01 - Resumo dos últimos 30 dias", "gerente01:teste123", "/api/dashboard/resumo", "200"),
    @("RF01 - Resumo só de entregas (admin)", "admin:teste123", "/api/dashboard/resumo?modalidade=ENTREGA", "200"),
    @("RF02 - Vendas por dia", "gerente01:teste123", "/api/dashboard/vendas?agrupamento=DIA", "200"),
    @("RF02 - Horário de pico", "gerente01:teste123", "/api/dashboard/vendas?agrupamento=HORA_DO_DIA", "200"),
    @("RF02 - Por dia da semana (finalizados e cancelados)", "gerente01:teste123", "/api/dashboard/vendas?agrupamento=DIA_DA_SEMANA&status=FINALIZADO&status=CANCELADO", "200"),
    @("RF03 - Situação dos pedidos e fila", "gerente01:teste123", "/api/dashboard/pedidos/situacao", "200"),
    @("RF04 - Top 3 produtos por valor", "gerente01:teste123", "/api/dashboard/produtos/ranking?criterio=FATURAMENTO&limite=3", "200"),
    @("Entrega x retirada", "gerente01:teste123", "/api/dashboard/canais", "200"),
    @("Erro - sem login", "", "/api/dashboard/resumo", "401"),
    @("Erro - atendente não acessa", "atendente01:teste123", "/api/dashboard/resumo", "403"),
    @("Erro - período invertido", "gerente01:teste123", "/api/dashboard/resumo?inicio=2026-11-10&fim=2026-11-01", "400"),
    @("Erro - data em formato errado", "gerente01:teste123", "/api/dashboard/resumo?inicio=10/11/2026", "400"),
    @("Erro - status inexistente", "gerente01:teste123", "/api/dashboard/resumo?status=ENTREGUE", "400"),
    @("Erro - limite do ranking fora da faixa", "gerente01:teste123", "/api/dashboard/produtos/ranking?limite=50", "400")
)

$linhas = @(
    "# Evidências - respostas reais da API do dashboard",
    "",
    ("Gerado automaticamente em " + (Get-Date -Format "dd/MM/yyyy HH:mm:ss") + " contra " + $Base + " (MongoDB Atlas)."),
    "",
    "| # | Caso | Esperado | Obtido | Resultado |",
    "|---|---|---|---|---|"
)
$detalhes = @()
$numero = 0

foreach ($caso in $casos) {
    $numero++
    $parametros = @("-s", "-w", '\nHTTP_STATUS:%{http_code}')
    if ($caso[1]) { $parametros += @("-u", $caso[1]) }
    $parametros += ($Base + $caso[2])

    $resposta = (& curl.exe @parametros) -join "`n"
    $status = ([regex]::Match($resposta, "HTTP_STATUS:(\d+)")).Groups[1].Value
    $corpo = ($resposta -replace "HTTP_STATUS:\d+", "").Trim()
    try { $corpo = ($corpo | ConvertFrom-Json | ConvertTo-Json -Depth 10) } catch { }
    if ($corpo.Length -gt 2500) { $corpo = $corpo.Substring(0, 2500) + "`n... (resposta cortada)" }

    $ok = if ($status -eq $caso[3]) { "OK" } else { "DIFERENTE" }
    $usuario = if ($caso[1]) { $caso[1].Split(":")[0] } else { "sem login" }
    $linhas += ("| " + $numero + " | " + $caso[0] + " | " + $caso[3] + " | " + $status + " | " + $ok + " |")

    $detalhes += ("## " + $numero + ". " + $caso[0])
    $detalhes += ""
    $detalhes += ('`GET ' + $caso[2] + '` como **' + $usuario + '** -> HTTP ' + $status)
    $detalhes += ""
    $detalhes += '```json'
    $detalhes += $corpo
    $detalhes += '```'
    $detalhes += ""
}

($linhas + "" + $detalhes) | Set-Content -Encoding UTF8 $saida
Write-Host "Evidências gravadas em $saida"

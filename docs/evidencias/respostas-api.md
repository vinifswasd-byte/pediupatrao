# Evidências - respostas reais da API do dashboard

Gerado automaticamente em 10/11/2026 19:34:30 contra http://localhost:8080 (MongoDB Atlas).

| # | Caso | Esperado | Obtido | Resultado |
|---|---|---|---|---|
| 1 | Opções de filtro | 200 | 200 | OK |
| 2 | RF01 - Resumo dos últimos 30 dias | 200 | 200 | OK |
| 3 | RF01 - Resumo só de entregas (admin) | 200 | 200 | OK |
| 4 | RF02 - Vendas por dia | 200 | 200 | OK |
| 5 | RF02 - Horário de pico | 200 | 200 | OK |
| 6 | RF02 - Por dia da semana (finalizados e cancelados) | 200 | 200 | OK |
| 7 | RF03 - Situação dos pedidos e fila | 200 | 200 | OK |
| 8 | RF04 - Top 3 produtos por valor | 200 | 200 | OK |
| 9 | Entrega x retirada | 200 | 200 | OK |
| 10 | Erro - sem login | 401 | 401 | OK |
| 11 | Erro - atendente não acessa | 403 | 403 | OK |
| 12 | Erro - período invertido | 400 | 400 | OK |
| 13 | Erro - data em formato errado | 400 | 400 | OK |
| 14 | Erro - status inexistente | 400 | 400 | OK |
| 15 | Erro - limite do ranking fora da faixa | 400 | 400 | OK |

## 1. Opções de filtro

`GET /api/dashboard/opcoes` como **gerente01** -> HTTP 200

```json
{
    "inicioPadrao":  "2026-10-12",
    "fimPadrao":  "2026-11-10",
    "maximoDias":  366,
    "status":  [
                   {
                       "valor":  "RECEBIDO",
                       "rotulo":  "Recebido"
                   },
                   {
                       "valor":  "EM_PREPARACAO",
                       "rotulo":  "Em preparação"
                   },
                   {
                       "valor":  "PRONTO",
                       "rotulo":  "Pronto"
                   },
                   {
                       "valor":  "DESPACHADO",
                       "rotulo":  "Saiu / Retirado"
                   },
                   {
                       "valor":  "FINALIZADO",
                       "rotulo":  "Finalizado"
                   },
                   {
                       "valor":  "CANCELADO",
                       "rotulo":  "Cancelado"
                   }
               ],
    "modalidades":  [
                        {
                            "valor":  "ENTREGA",
                            "rotulo":  "Saiu para entrega"
                        },
                        {
                            "valor":  "RETIRADA",
                            "rotulo":  "Retirado no balcão"
                        }
                    ],
    "agrupamentos":  [
                         {
                             "valor":  "DIA",
                             "rotulo":  "Por dia"
                         },
                         {
                             "valor":  "MES",
                             "rotulo":  "Por mês"
                         },
                         {
                             "valor":  "HORA_DO_DIA",
                             "rotulo":  "Por hora do dia"
                         },
                         {
                             "valor":  "DIA_DA_SEMANA",
                             "rotulo":  "Por dia da semana"
                         }
                     ],
    "produtos":  [
                     {
                         "valor":  "6af39bc4e21dba7610c840d0",
                         "rotulo":  "Brownie"
                     },
                     {
                         "valor":  "6af39bc4e21dba7610c840cb",
                         "rotulo":  "Pizza Calabresa"
                     },
                     {
                         "valor":  "6af39bc4e21dba7610c840cc",
      
... (resposta cortada)
```

## 2. RF01 - Resumo dos últimos 30 dias

`GET /api/dashboard/resumo` como **gerente01** -> HTTP 200

```json
{
    "inicio":  "2026-10-12",
    "fim":  "2026-11-10",
    "totalPedidos":  297,
    "pedidosValidos":  274,
    "pedidosCancelados":  23,
    "faturamento":  34631.65,
    "ticketMedio":  126.39,
    "totalDescontos":  454.35,
    "valorCancelado":  2802.20,
    "itensVendidos":  817,
    "clientesAtendidos":  9,
    "taxaCancelamento":  7.7,
    "tempoMedioAteSaidaMinutos":  35,
    "comparativo":  {
                        "inicioAnterior":  "2026-09-12",
                        "fimAnterior":  "2026-10-11",
                        "faturamentoAnterior":  13879.00,
                        "pedidosAnterior":  118,
                        "variacaoFaturamento":  149.5,
                        "variacaoPedidos":  151.7
                    },
    "atualizadoEm":  "2026-11-10T22:34:32.642740200Z"
}
```

## 3. RF01 - Resumo só de entregas (admin)

`GET /api/dashboard/resumo?modalidade=ENTREGA` como **admin** -> HTTP 200

```json
{
    "inicio":  "2026-10-12",
    "fim":  "2026-11-10",
    "totalPedidos":  193,
    "pedidosValidos":  193,
    "pedidosCancelados":  0,
    "faturamento":  25226.80,
    "ticketMedio":  130.71,
    "totalDescontos":  398.20,
    "valorCancelado":  0.00,
    "itensVendidos":  596,
    "clientesAtendidos":  9,
    "taxaCancelamento":  0.0,
    "tempoMedioAteSaidaMinutos":  34,
    "comparativo":  {
                        "inicioAnterior":  "2026-09-12",
                        "fimAnterior":  "2026-10-11",
                        "faturamentoAnterior":  9826.35,
                        "pedidosAnterior":  75,
                        "variacaoFaturamento":  156.7,
                        "variacaoPedidos":  157.3
                    },
    "atualizadoEm":  "2026-11-10T22:34:33.507570Z"
}
```

## 4. RF02 - Vendas por dia

`GET /api/dashboard/vendas?agrupamento=DIA` como **gerente01** -> HTTP 200

```json
{
    "agrupamento":  "DIA",
    "pontos":  [
                   {
                       "chave":  "2026-10-12",
                       "rotulo":  "12/10",
                       "pedidos":  8,
                       "cancelados":  2,
                       "faturamento":  773.45,
                       "ticketMedio":  128.91
                   },
                   {
                       "chave":  "2026-10-13",
                       "rotulo":  "13/10",
                       "pedidos":  5,
                       "cancelados":  1,
                       "faturamento":  558.00,
                       "ticketMedio":  139.50
                   },
                   {
                       "chave":  "2026-10-14",
                       "rotulo":  "14/10",
                       "pedidos":  5,
                       "cancelados":  0,
                       "faturamento":  876.00,
                       "ticketMedio":  175.20
                   },
                   {
                       "chave":  "2026-10-15",
                       "rotulo":  "15/10",
                       "pedidos":  8,
                       "cancelados":  1,
                       "faturamento":  957.95,
                       "ticketMedio":  136.85
                   },
                   {
                       "chave":  "2026-10-16",
                       "rotulo":  "16/10",
                       "pedidos":  13,
                       "cancelados":  2,
                       "faturamento":  1547.60,
                       "ticketMedio":  140.69
                   },
                   {
                       "chave":  "2026-10-17",
                       "rotulo":  "17/10",
                       "pedidos":  15,
                       "cancelados":  3,
                       "faturamento":  2004.00,
                       "ticketMedio":  167.00
                   },
                   {
                       "chave":  "2026-10-18",
                       "rotulo":  "18/10",
                       "pedidos":  15,
                       "cancelados":  1,
                       "faturamento":  1752.10,
                       "ticketMedio":  125.15
                   },
                   {
                       "chave":  "2026-10-19",
                       "rotulo":  "19/10",
                       "pedidos":  5,
                       "cancelados":  1,
                       "faturamento":  445.00,
           
... (resposta cortada)
```

## 5. RF02 - Horário de pico

`GET /api/dashboard/vendas?agrupamento=HORA_DO_DIA` como **gerente01** -> HTTP 200

```json
{
    "agrupamento":  "HORA_DO_DIA",
    "pontos":  [
                   {
                       "chave":  "0",
                       "rotulo":  "00h",
                       "pedidos":  0,
                       "cancelados":  0,
                       "faturamento":  0.00,
                       "ticketMedio":  0.00
                   },
                   {
                       "chave":  "1",
                       "rotulo":  "01h",
                       "pedidos":  0,
                       "cancelados":  0,
                       "faturamento":  0.00,
                       "ticketMedio":  0.00
                   },
                   {
                       "chave":  "2",
                       "rotulo":  "02h",
                       "pedidos":  0,
                       "cancelados":  0,
                       "faturamento":  0.00,
                       "ticketMedio":  0.00
                   },
                   {
                       "chave":  "3",
                       "rotulo":  "03h",
                       "pedidos":  0,
                       "cancelados":  0,
                       "faturamento":  0.00,
                       "ticketMedio":  0.00
                   },
                   {
                       "chave":  "4",
                       "rotulo":  "04h",
                       "pedidos":  0,
                       "cancelados":  0,
                       "faturamento":  0.00,
                       "ticketMedio":  0.00
                   },
                   {
                       "chave":  "5",
                       "rotulo":  "05h",
                       "pedidos":  0,
                       "cancelados":  0,
                       "faturamento":  0.00,
                       "ticketMedio":  0.00
                   },
                   {
                       "chave":  "6",
                       "rotulo":  "06h",
                       "pedidos":  0,
                       "cancelados":  0,
                       "faturamento":  0.00,
                       "ticketMedio":  0.00
                   },
                   {
                       "chave":  "7",
                       "rotulo":  "07h",
                       "pedidos":  0,
                       "cancelados":  0,
                       "faturamento":  0.00,
                       "ticketMedio":  0.00
                   },
                   {
                       "chave":  "8",
... (resposta cortada)
```

## 6. RF02 - Por dia da semana (finalizados e cancelados)

`GET /api/dashboard/vendas?agrupamento=DIA_DA_SEMANA&status=FINALIZADO&status=CANCELADO` como **gerente01** -> HTTP 200

```json
{
    "agrupamento":  "DIA_DA_SEMANA",
    "pontos":  [
                   {
                       "chave":  "1",
                       "rotulo":  "Seg",
                       "pedidos":  39,
                       "cancelados":  4,
                       "faturamento":  4239.45,
                       "ticketMedio":  121.13
                   },
                   {
                       "chave":  "2",
                       "rotulo":  "Ter",
                       "pedidos":  30,
                       "cancelados":  2,
                       "faturamento":  3354.65,
                       "ticketMedio":  119.81
                   },
                   {
                       "chave":  "3",
                       "rotulo":  "Qua",
                       "pedidos":  27,
                       "cancelados":  0,
                       "faturamento":  3966.00,
                       "ticketMedio":  146.89
                   },
                   {
                       "chave":  "4",
                       "rotulo":  "Qui",
                       "pedidos":  32,
                       "cancelados":  4,
                       "faturamento":  3565.90,
                       "ticketMedio":  127.35
                   },
                   {
                       "chave":  "5",
                       "rotulo":  "Sex",
                       "pedidos":  54,
                       "cancelados":  5,
                       "faturamento":  6172.50,
                       "ticketMedio":  125.97
                   },
                   {
                       "chave":  "6",
                       "rotulo":  "Sáb",
                       "pedidos":  56,
                       "cancelados":  6,
                       "faturamento":  6487.50,
                       "ticketMedio":  129.75
                   },
                   {
                       "chave":  "7",
                       "rotulo":  "Dom",
                       "pedidos":  55,
                       "cancelados":  2,
                       "faturamento":  6573.65,
                       "ticketMedio":  124.03
                   }
               ],
    "pico":  {
                 "chave":  "7",
                 "rotulo":  "Dom",
                 "pedidos":  55,
                 "cancelados":  2,
                 "faturamento":  6573.65,
                 "ticketMedio":  124.03
             }
}
```

## 7. RF03 - Situação dos pedidos e fila

`GET /api/dashboard/pedidos/situacao` como **gerente01** -> HTTP 200

```json
{
    "total":  297,
    "porStatus":  [
                      {
                          "status":  "RECEBIDO",
                          "descricao":  "Recebido",
                          "quantidade":  0,
                          "percentual":  0.0,
                          "valor":  0.00
                      },
                      {
                          "status":  "EM_PREPARACAO",
                          "descricao":  "Em preparação",
                          "quantidade":  2,
                          "percentual":  0.7,
                          "valor":  94.00
                      },
                      {
                          "status":  "PRONTO",
                          "descricao":  "Pronto",
                          "quantidade":  1,
                          "percentual":  0.3,
                          "valor":  90.00
                      },
                      {
                          "status":  "DESPACHADO",
                          "descricao":  "Saiu / Retirado",
                          "quantidade":  1,
                          "percentual":  0.3,
                          "valor":  88.00
                      },
                      {
                          "status":  "FINALIZADO",
                          "descricao":  "Finalizado",
                          "quantidade":  270,
                          "percentual":  90.9,
                          "valor":  34359.65
                      },
                      {
                          "status":  "CANCELADO",
                          "descricao":  "Cancelado",
                          "quantidade":  23,
                          "percentual":  7.7,
                          "valor":  2802.20
                      }
                  ],
    "emAndamento":  [
                        {
                            "id":  "6af39bc5e21dba7610c84275",
                            "codigo":  "c84275",
                            "cliente":  "Ana Ribeiro",
                            "status":  "PRONTO",
                            "statusDescricao":  "Pronto",
                            "entradaEm":  "2026-11-10T22:03:00Z",
                            "minutosEmAberto":  31,
                            "atrasado":  false,
                            "valorTotal":  90.00
                        },
                        {
                            "id":  "6af39bc5e21dba7610c84273",
     
... (resposta cortada)
```

## 8. RF04 - Top 3 produtos por valor

`GET /api/dashboard/produtos/ranking?criterio=FATURAMENTO&limite=3` como **gerente01** -> HTTP 200

```json
{
    "criterio":  "FATURAMENTO",
    "unidadesTotais":  817,
    "faturamentoTotal":  35086.00,
    "produtos":  [
                     {
                         "posicao":  1,
                         "produtoId":  "6ad2c203bc90dd0f022034ed",
                         "nome":  "pizza de calabresa",
                         "quantidade":  164,
                         "faturamento":  11480.00,
                         "pedidos":  111,
                         "participacao":  32.7
                     },
                     {
                         "posicao":  2,
                         "produtoId":  "6af39bc4e21dba7610c840cc",
                         "nome":  "Pizza Frango c/ Catupiry",
                         "quantidade":  144,
                         "faturamento":  6768.00,
                         "pedidos":  95,
                         "participacao":  19.3
                     },
                     {
                         "posicao":  3,
                         "produtoId":  "6af39bc4e21dba7610c840cb",
                         "nome":  "Pizza Calabresa",
                         "quantidade":  122,
                         "faturamento":  5490.00,
                         "pedidos":  85,
                         "participacao":  15.6
                     }
                 ]
}
```

## 9. Entrega x retirada

`GET /api/dashboard/canais` como **gerente01** -> HTTP 200

```json
{
    "value":  [
                  {
                      "modalidade":  "ENTREGA",
                      "descricao":  "Saiu para entrega",
                      "pedidos":  193,
                      "faturamento":  25226.80,
                      "percentual":  71.2
                  },
                  {
                      "modalidade":  "RETIRADA",
                      "descricao":  "Retirado no balcão",
                      "pedidos":  78,
                      "faturamento":  9220.85,
                      "percentual":  28.8
                  }
              ],
    "Count":  2
}
```

## 10. Erro - sem login

`GET /api/dashboard/resumo` como **sem login** -> HTTP 401

```json
{
    "type":  "about:blank",
    "title":  "Não autenticado",
    "status":  401,
    "detail":  "Faça login ou envie usuário e senha (HTTP Basic) para usar a API.",
    "instance":  "/api/dashboard/resumo"
}
```

## 11. Erro - atendente não acessa

`GET /api/dashboard/resumo` como **atendente01** -> HTTP 403

```json
{
    "type":  "about:blank",
    "title":  "Acesso negado",
    "status":  403,
    "detail":  "Perfil sem permissão para esta operação.",
    "instance":  "/api/dashboard/resumo"
}
```

## 12. Erro - período invertido

`GET /api/dashboard/resumo?inicio=2026-11-10&fim=2026-11-01` como **gerente01** -> HTTP 400

```json
{
    "type":  "about:blank",
    "title":  "Filtro inválido",
    "status":  400,
    "detail":  "A data inicial (2026-11-10) é posterior à data final (2026-11-01).",
    "instance":  "/api/dashboard/resumo"
}
```

## 13. Erro - data em formato errado

`GET /api/dashboard/resumo?inicio=10/11/2026` como **gerente01** -> HTTP 400

```json
{
    "type":  "about:blank",
    "title":  "Parâmetro inválido",
    "status":  400,
    "detail":  "Valor inválido para \u0027inicio\u0027: 10/11/2026. Datas no formato AAAA-MM-DD e status/modalidade com os valores de /api/dashboard/opcoes.",
    "instance":  "/api/dashboard/resumo"
}
```

## 14. Erro - status inexistente

`GET /api/dashboard/resumo?status=ENTREGUE` como **gerente01** -> HTTP 400

```json
{
    "type":  "about:blank",
    "title":  "Parâmetro inválido",
    "status":  400,
    "detail":  "Valor inválido para \u0027status\u0027: ENTREGUE. Datas no formato AAAA-MM-DD e status/modalidade com os valores de /api/dashboard/opcoes.",
    "instance":  "/api/dashboard/resumo"
}
```

## 15. Erro - limite do ranking fora da faixa

`GET /api/dashboard/produtos/ranking?limite=50` como **gerente01** -> HTTP 400

```json
{
    "type":  "about:blank",
    "title":  "Filtro inválido",
    "status":  400,
    "detail":  "O limite do ranking deve ficar entre 1 e 20.",
    "instance":  "/api/dashboard/produtos/ranking"
}
```


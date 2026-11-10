# API do Dashboard — `/api/dashboard`

Todos os endpoints são **GET** (somente leitura) e respondem **JSON**, exceto `/eventos`, que é um fluxo SSE.

## Autenticação e permissões

| Situação | Resposta |
|---|---|
| Sem login | `401 Unauthorized` |
| Logado como ATENDENTE | `403 Forbidden` (a tentativa vai para a auditoria) |
| Logado como **ADMIN** ou **GERENTE** | `200 OK` |

Formas de autenticar:
- **No navegador:** usa a sessão do login (cookie). É assim que a tela `/dashboard` chama a API.
- **Por ferramenta** (curl, Postman, REST Client): HTTP Basic, por exemplo `-u gerente01:teste123`.

## Filtros comuns (RF05)

Valem para `/resumo`, `/vendas`, `/pedidos/situacao`, `/produtos/ranking` e `/canais`.

| Parâmetro | Tipo | Obrigatório | Padrão | Descrição |
|---|---|---|---|---|
| `inicio` | data `AAAA-MM-DD` | não | 29 dias antes de `fim` | Primeiro dia do período (horário de Brasília) |
| `fim` | data `AAAA-MM-DD` | não | hoje | Último dia do período (entra inteiro) |
| `status` | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `DESPACHADO`, `FINALIZADO`, `CANCELADO` | não | todos | Pode repetir: `status=FINALIZADO&status=CANCELADO` |
| `modalidade` | `ENTREGA` ou `RETIRADA` | não | todas | Só pedidos que saíram dessa forma |
| `produtoId` | texto (id do produto) | não | todos | Só pedidos que contêm esse produto |

**Validações:**
- `inicio` não pode ser posterior a `fim`;
- o período tem no máximo **366 dias**;
- datas e valores de enum precisam estar no formato da tabela.

## Formato de erro (todos os endpoints)

Os erros seguem o padrão *Problem Details* (RFC 7807):

```json
{
  "type": "about:blank",
  "title": "Filtro inválido",
  "status": 400,
  "detail": "A data inicial (2026-11-10) é posterior à data final (2026-11-01).",
  "instance": "/api/dashboard/resumo"
}
```

| Status | Quando |
|---|---|
| `400` | Data em formato errado, status ou modalidade inexistente, período invertido ou maior que 366 dias, `limite` fora de 1–20 |
| `401` | Requisição sem autenticação |
| `403` | Usuário sem perfil ADMIN ou GERENTE |
| `500` | Falha inesperada (por exemplo, banco fora do ar) |

---

## `GET /api/dashboard/opcoes`

Valores aceitos pelos filtros e o período padrão. A tela usa isso para montar os campos.

**Parâmetros:** nenhum.

```bash
curl -u gerente01:teste123 http://localhost:8080/api/dashboard/opcoes
```

```json
{
  "inicioPadrao": "2026-10-12",
  "fimPadrao": "2026-11-10",
  "maximoDias": 366,
  "status": [ { "valor": "RECEBIDO", "rotulo": "Recebido" }, { "valor": "EM_PREPARACAO", "rotulo": "Em preparação" } ],
  "modalidades": [ { "valor": "ENTREGA", "rotulo": "Saiu para entrega" }, { "valor": "RETIRADA", "rotulo": "Retirado no balcão" } ],
  "agrupamentos": [ { "valor": "DIA", "rotulo": "Por dia" }, { "valor": "MES", "rotulo": "Por mês" } ],
  "produtos": [ { "valor": "6730a1f2c9e77b2d4e1a0b11", "rotulo": "Pizza Calabresa" } ]
}
```

---

## `GET /api/dashboard/resumo` — RF01

Indicadores gerais do período e comparação com o período anterior de mesmo tamanho.

**Parâmetros:** filtros comuns.

```bash
curl -u gerente01:teste123 "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-10"
```

```json
{
  "inicio": "2026-11-01",
  "fim": "2026-11-10",
  "totalPedidos": 96,
  "pedidosValidos": 89,
  "pedidosCancelados": 7,
  "faturamento": 9812.40,
  "ticketMedio": 110.25,
  "totalDescontos": 186.30,
  "valorCancelado": 702.00,
  "itensVendidos": 231,
  "clientesAtendidos": 8,
  "taxaCancelamento": 7.3,
  "tempoMedioAteSaidaMinutos": 33,
  "comparativo": {
    "inicioAnterior": "2026-10-22",
    "fimAnterior": "2026-10-31",
    "faturamentoAnterior": 8640.00,
    "pedidosAnterior": 88,
    "variacaoFaturamento": 13.6,
    "variacaoPedidos": 9.1
  },
  "atualizadoEm": "2026-11-10T18:42:05.123Z"
}
```

| Campo | Significado |
|---|---|
| `faturamento` | Soma de `valorTotal` dos pedidos **não cancelados** (já com desconto) |
| `ticketMedio` | `faturamento / pedidosValidos` |
| `valorCancelado` | Soma do valor dos pedidos cancelados |
| `taxaCancelamento` | % de cancelados sobre o total |
| `tempoMedioAteSaidaMinutos` | Média entre entrada e saída; `null` se nenhum pedido saiu |
| `variacao*` | % em relação ao período anterior; `null` se o anterior foi zero |

---

## `GET /api/dashboard/vendas` — RF02

Série para os gráficos. Os períodos sem venda vêm com zero.

**Parâmetros:** filtros comuns, mais:

| Parâmetro | Valores | Padrão |
|---|---|---|
| `agrupamento` | `DIA`, `MES`, `HORA_DO_DIA`, `DIA_DA_SEMANA` | `DIA` |

```bash
curl -u gerente01:teste123 "http://localhost:8080/api/dashboard/vendas?inicio=2026-11-08&fim=2026-11-10&agrupamento=DIA"
```

```json
{
  "agrupamento": "DIA",
  "pontos": [
    { "chave": "2026-11-08", "rotulo": "08/11", "pedidos": 14, "cancelados": 1, "faturamento": 1520.00, "ticketMedio": 116.92 },
    { "chave": "2026-11-09", "rotulo": "09/11", "pedidos": 0,  "cancelados": 0, "faturamento": 0.00,    "ticketMedio": 0.00 },
    { "chave": "2026-11-10", "rotulo": "10/11", "pedidos": 6,  "cancelados": 0, "faturamento": 610.50,  "ticketMedio": 101.75 }
  ],
  "pico": { "chave": "2026-11-08", "rotulo": "08/11", "pedidos": 14, "cancelados": 1, "faturamento": 1520.00, "ticketMedio": 116.92 }
}
```

Formato da `chave` e do `rotulo` em cada agrupamento:

| Agrupamento | `chave` | `rotulo` |
|---|---|---|
| `DIA` | `2026-11-10` | `10/11` |
| `MES` | `2026-11` | `11/2026` |
| `HORA_DO_DIA` | `0` a `23` | `00h` a `23h` |
| `DIA_DA_SEMANA` | `1` (segunda) a `7` (domingo) | `Seg` a `Dom` |

**Erros:** `400` se `agrupamento` não for um dos valores aceitos.

---

## `GET /api/dashboard/pedidos/situacao` — RF03

Quantidade de pedidos por status no período, mais a fila de pedidos abertos agora (até 10, do mais antigo para o mais novo).

**Parâmetros:** filtros comuns. A fila (`emAndamento`) não depende do filtro.

```bash
curl -u admin:teste123 http://localhost:8080/api/dashboard/pedidos/situacao
```

```json
{
  "total": 214,
  "porStatus": [
    { "status": "RECEBIDO", "descricao": "Recebido", "quantidade": 3, "percentual": 1.4, "valor": 270.00 },
    { "status": "FINALIZADO", "descricao": "Finalizado", "quantidade": 183, "percentual": 85.5, "valor": 11980.20 },
    { "status": "CANCELADO", "descricao": "Cancelado", "quantidade": 17, "percentual": 7.9, "valor": 1033.20 }
  ],
  "emAndamento": [
    {
      "id": "6730f0c1a9e77b2d4e1a0c9f",
      "codigo": "1a0c9f",
      "cliente": "Maria Souza",
      "status": "RECEBIDO",
      "statusDescricao": "Recebido",
      "entradaEm": "2026-11-10T20:05:00Z",
      "minutosEmAberto": 55,
      "atrasado": true,
      "valorTotal": 92.00
    }
  ],
  "limiteAtrasoMinutos": 40
}
```

`porStatus` sempre traz os 6 status, inclusive os que têm zero. `atrasado` fica `true` quando o pedido está aberto há mais de `limiteAtrasoMinutos`.

---

## `GET /api/dashboard/produtos/ranking` — RF04

Produtos mais vendidos, sem contar pedidos cancelados.

**Parâmetros:** filtros comuns, mais:

| Parâmetro | Valores | Padrão |
|---|---|---|
| `criterio` | `QUANTIDADE` (unidades) ou `FATURAMENTO` (valor) | `QUANTIDADE` |
| `limite` | 1 a 20 | 5 |

```bash
curl -u gerente01:teste123 "http://localhost:8080/api/dashboard/produtos/ranking?criterio=FATURAMENTO&limite=3"
```

```json
{
  "criterio": "FATURAMENTO",
  "unidadesTotais": 503,
  "faturamentoTotal": 19250.00,
  "produtos": [
    { "posicao": 1, "produtoId": "6730a1f2c9e77b2d4e1a0b11", "nome": "Pizza Calabresa", "quantidade": 142, "faturamento": 6390.00, "pedidos": 118, "participacao": 33.2 },
    { "posicao": 2, "produtoId": "6730a1f2c9e77b2d4e1a0b12", "nome": "Pizza Frango c/ Catupiry", "quantidade": 101, "faturamento": 4747.00, "pedidos": 90, "participacao": 24.7 },
    { "posicao": 3, "produtoId": "6730a1f2c9e77b2d4e1a0b13", "nome": "Pizza Margherita", "quantidade": 88, "faturamento": 3696.00, "pedidos": 80, "participacao": 19.2 }
  ]
}
```

`participacao` é a % sobre `unidadesTotais` ou `faturamentoTotal`, conforme o critério. O faturamento por produto é o valor dos itens **antes** do desconto do pedido.

**Erros:** `400` se `limite` estiver fora de 1–20 ou `criterio` for inválido.

---

## `GET /api/dashboard/canais`

Pedidos não cancelados que já saíram, separados por entrega e retirada.

**Parâmetros:** filtros comuns.

```bash
curl -u gerente01:teste123 http://localhost:8080/api/dashboard/canais
```

```json
[
  { "modalidade": "ENTREGA", "descricao": "Saiu para entrega", "pedidos": 131, "faturamento": 8900.00, "percentual": 69.3 },
  { "modalidade": "RETIRADA", "descricao": "Retirado no balcão", "pedidos": 58, "faturamento": 3700.00, "percentual": 30.7 }
]
```

---

## `GET /api/dashboard/eventos` — RF07 (Server-Sent Events)

Abre uma conexão que fica ligada. O servidor envia uma mensagem sempre que um pedido é criado,
muda de status, recebe desconto ou é cancelado.

- **Resposta:** `Content-Type: text/event-stream`.
- **Ao conectar:** chega o evento `conectado`.
- **A cada 25 s:** chega um comentário `: ping`, que mantém a conexão viva.
- **Duração:** a conexão fecha após 30 minutos; o `EventSource` do navegador reconecta sozinho.

```bash
curl -N -u gerente01:teste123 http://localhost:8080/api/dashboard/eventos
```

```text
event:conectado
data:ok

event:pedido-alterado
data:{"pedidoId":"6730f0c1a9e77b2d4e1a0c9f","operacao":"MUDANCA_STATUS_PEDIDO","status":"EM_PREPARACAO","ocorridoEm":"2026-11-10T20:07:12.345Z"}
```

| `operacao` | Quando |
|---|---|
| `CRIACAO_PEDIDO` | Pedido registrado |
| `MUDANCA_STATUS_PEDIDO` | Preparo, pronto, saída ou finalização |
| `DESCONTO_PEDIDO` | Gerente aplicou desconto |
| `CANCELAMENTO_PEDIDO` | Gerente cancelou |

Uso em JavaScript:

```js
const fonte = new EventSource('/api/dashboard/eventos');
fonte.addEventListener('pedido-alterado', () => recarregarDashboard());
```

**Erros:** `401` sem login; `403` para ATENDENTE.

---

As requisições acima, incluindo os casos de erro, estão prontas em [`docs/dashboard.http`](dashboard.http).

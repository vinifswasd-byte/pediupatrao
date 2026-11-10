# Testes do Dashboard — estratégia e evidências

## 1. Estratégia

| Nível | O que cobre | Ferramenta |
|---|---|---|
| Unidade | Regras do filtro, cálculos do dashboard (ticket médio, percentuais, comparativo, períodos vazios), montagem do `$match` e gerador de dados | JUnit 5 + Mockito |
| Segurança / API | Quem acessa cada rota (401/403/200) e erros de filtro (400) | Spring `@WebMvcTest` + MockMvc |
| Integração com o banco | Endpoints reais contra o MongoDB Atlas, com os pipelines de aggregation | Script `docs/evidencias/coletar-api.ps1` (curl) |
| Funcional (tela) | Dashboard no navegador com os três perfis, filtros e tempo real | Roteiro manual (seção 3) e vídeo |

## 2. Testes automatizados

Para rodar: `.\mvnw.cmd test` (Windows) ou `./mvnw test` (Linux/Mac).
Saída completa da última execução: [`evidencias/testes-automatizados.txt`](evidencias/testes-automatizados.txt).

**Desta atividade (dashboard):**

| Classe | Casos | Verifica |
|---|---|---|
| `FiltroDashboardTest` | 5 | Padrão de 30 dias, período invertido e acima de 366 dias recusados, fuso de Brasília no início e fim, período anterior de mesmo tamanho (RF05) |
| `DashboardRepositoryTest` | 3 | Filtros viram o `$match` certo (período, status, modalidade, produto); agrupamento por dia usa `America/Sao_Paulo` |
| `DashboardServiceTest` | 7 | Ticket médio, taxa de cancelamento, tempo médio e variação vs. período anterior (RF01); dias sem venda preenchidos com zero e pico (RF02); 24 horas no agrupamento por hora; percentuais por status (RF03); participação no ranking e limite (RF04); sem divisão por zero |
| `DashboardApiSegurancaTest` | 7 | 401 sem login, 403 para atendente, 200 para gerente e admin, 400 para data mal formatada, status inexistente e período invertido |
| `CargaDemonstracaoTest` | 1 | Dados de demonstração sem pedidos no futuro, com valores positivos, desconto até 20% e status coerentes |
| `PedidoServiceTest` (atualizado) | 9 | Além das regras anteriores, confere que o dashboard é avisado quando um pedido é criado (RF07) |

**Da atividade anterior (continuam passando):** `FluxoPedidoTest`, `AuditoriaServiceTest`, `SegurancaApiTest`.

## 3. Roteiro de teste funcional

Executado com o sistema conectado ao MongoDB Atlas e com a carga de demonstração.

| # | Requisito | Passos | Resultado esperado |
|---|---|---|---|
| 1 | RNF01 | Entrar como `atendente01` e abrir `/dashboard` | Página de acesso negado (403) |
| 2 | RF01 | Entrar como `gerente01` e abrir o Dashboard | Cartões com faturamento, pedidos, ticket médio, itens, cancelamentos e tempo até a saída, com variação ▲/▼ |
| 3 | RF02 | Trocar o agrupamento do gráfico de vendas (dia, mês, hora, dia da semana) | Gráfico redesenhado e "Melhor resultado" atualizado |
| 4 | RF03 | Ver a rosca de status e a fila "Pedidos em andamento agora" | Quantidades por status; pedidos abertos há mais de 40 min em vermelho |
| 5 | RF04 | Alternar o ranking entre Unidades e Valor | Ordem e rótulos mudam conforme o critério |
| 6 | RF05 | Usar os atalhos 7 dias e Este mês, filtrar por status, saída e produto | Todos os blocos refletem o filtro; a URL guarda o filtro |
| 7 | RF05 | Colocar a data inicial depois da final e filtrar | Aviso "A data inicial não pode ser depois da final" |
| 8 | RF07 | Com o dashboard aberto, registrar um pedido em outra janela (atendente) | Aviso "Novo pedido #..." e números atualizados sozinhos em cerca de 2 s |
| 9 | RF07 | Cancelar esse pedido como gerente | Cancelamentos e valor perdido sobem sem recarregar a página |
| 10 | RF06 | Rodar `docs/evidencias/coletar-api.ps1` | Tabela de casos com todos "OK" em `respostas-api.md` |

## 4. Evidências

- [`evidencias/testes-automatizados.txt`](evidencias/testes-automatizados.txt): saída do `mvnw test`.
- [`evidencias/respostas-api.md`](evidencias/respostas-api.md): respostas reais de cada endpoint e de cada caso de erro, geradas pelo script contra o Atlas.
- Vídeo de demonstração: link no README.

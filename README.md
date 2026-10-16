# PediuPatrão — Controle de acesso, pedidos e auditoria

Evolução do sistema de pedidos da pizzaria (Atividade Prática M1 – Parte 2).
Tecnologias mantidas: **Java 21, Spring Boot 3.4, Spring Security, MongoDB (Atlas) e Thymeleaf**.

## Como executar

**Pré-requisitos:** JDK 21 instalado (o Maven vem junto no projeto pelo `mvnw`).

1. Crie um banco no MongoDB Atlas e copie a string de conexão.
2. Copie `src/main/resources/segredos.properties.exemplo` para `src/main/resources/segredos.properties`
   e cole a sua string em `MONGODB_URI=...`.
   Esse arquivo está no `.gitignore`, então a senha do banco não vai para o GitHub.
   (Também funciona definir a variável de ambiente `MONGODB_URI`.)
3. Rode:
   ```bash
   ./mvnw spring-boot:run        # Linux/Mac
   mvnw.cmd spring-boot:run      # Windows
   ```
   No NetBeans: abrir o projeto e clicar em *Run*.
4. Acesse `http://localhost:8080`.

Na primeira execução são criados três usuários (senha `teste123`, configurável em `application.properties`):

| Usuário       | Perfil    |
|---------------|-----------|
| `admin`       | ADMIN     |
| `gerente01`   | GERENTE   |
| `atendente01` | ATENDENTE |

**Testes automatizados:** `./mvnw test`

## Requisitos implementados

### RF01 — Usuários e permissões
- Três perfis (`ADMIN`, `GERENTE`, `ATENDENTE`), lidos do banco no login (`UsuarioDetailsService`). Antes, todos entravam como `USER`.
- Autorização em **duas camadas**:
  1. por rota e método HTTP no `SecurityConfig`;
  2. por método nos services, com `@PreAuthorize`.
- Somente o **GERENTE** cancela e dá desconto, nem o ADMIN consegue.
- `/api/usuarios` deixou de ser pública: antes qualquer pessoa sem login conseguia criar um ADMIN.
- Proteção contra autoelevação (`UsuarioService`): ninguém altera o próprio perfil nem se exclui, o último ADMIN não pode ser rebaixado nem excluído, e só perfis válidos são aceitos.
- A senha nunca aparece nas respostas da API (`@JsonProperty(WRITE_ONLY)`) nem na auditoria.
- **CSRF reativado.** Os formulários Thymeleaf enviam o token automaticamente e o JavaScript o envia no cabeçalho (meta tags no `layout.html`). Chamadas com `Authorization: Basic` (curl/Postman) ficam dispensadas, porque o navegador não envia esse cabeçalho sozinho.
- Acesso negado:
  - na API, devolve JSON 403 (sem login, 401);
  - nas telas, mostra a página de erro;
  - em todos os casos, a tentativa é **registrada na auditoria** como `ACESSO_NEGADO`.

### RF02 — Cadastro de pedidos
- O `Pedido` agora tem itens (produto, quantidade, preço unitário, subtotal), descontos, subtotal, total, data/hora de entrada e responsável.
- **O valor é calculado no servidor.** O formulário/JSON envia só `produtoId` e `quantidade`; o preço vem do cadastro de produtos. A entidade não tem setters para os valores, então não dá para forçar um total.
- Validações:
  - cliente existente;
  - pelo menos um item;
  - produto ativo e com preço;
  - quantidade entre 1 e 50;
  - itens repetidos são somados.
- `@Version` (bloqueio otimista): duas alterações simultâneas no mesmo pedido não se sobrescrevem.
- Valores em `BigDecimal` com arredondamento bancário (HALF_EVEN).

### RF03 — Entrada, saída e transições
Fluxo controlado por uma tabela de transições (`FluxoPedido`):

```
RECEBIDO → EM_PREPARACAO → PRONTO → DESPACHADO → FINALIZADO
   └──────────┴──────────────┴──→ CANCELADO (só gerente, antes da saída)
```

- `DESPACHADO` + `modalidadeSaida` (`ENTREGA` ou `RETIRADA`) representam "saiu para entrega / retirado". A regra é a mesma nos dois casos, então foi usado um status só.
- A entrada (RECEBIDO) e a saída gravam obrigatoriamente data/hora e responsável.
- Transições inválidas (ex.: finalizar um pedido em preparação) e qualquer mudança depois de cancelado são recusadas.
- Cada pedido guarda também um histórico (`historico`) das movimentações.

### RF04 — Cancelamentos e descontos (exclusivos do GERENTE)
- **Cancelamento:**
  - exige justificativa e só é possível antes da saída;
  - preserva itens e valores;
  - depois de cancelado, o pedido fica bloqueado.
- **Desconto:**
  - é percentual, com motivo obrigatório e só antes da saída;
  - pode haver mais de um por pedido (cada um registrado com motivo, autor e horário);
  - **a soma fica limitada a 20%**.
- As duas operações vão para a auditoria com os valores anteriores e novos e a justificativa.

### RF05 — Auditoria
- Entidade `RegistroAuditoria`, `AuditoriaRepository` e `AuditoriaService` próprios. Coleção `auditoria` no MongoDB.
- O que é auditado:
  - **pedidos:** inclusão, mudança de status, desconto e cancelamento;
  - **clientes:** inclusão, alteração (endereço, telefone etc.) e exclusão;
  - **produtos e usuários:** inclusão, alteração e exclusão;
  - tentativas de acesso negadas.
- Cada registro guarda:
  - usuário e perfil;
  - data/hora;
  - operação e entidade com o id;
  - **somente os campos que mudaram**, com valor anterior e novo;
  - justificativa, quando a regra exige.
- Como funciona: cada entidade implementa `Auditavel` e devolve uma "foto" (`retratoParaAuditoria()`) dos campos que podem ser auditados. O service compara a foto de antes com a de depois. A senha nunca entra na foto.
- **Integridade:**
  - o `AuditoriaRepository` só tem métodos de inserir e consultar, sem update nem delete;
  - a API de auditoria só tem rotas `GET`;
  - cada registro tem um número de sequência único e o **hash SHA-256 do registro anterior** (corrente de hash);
  - o botão **Verificar integridade** (`/api/auditoria/integridade`) recalcula a corrente e aponta o registro adulterado ou apagado.
- **Dados pessoais:** na consulta, o telefone aparece mascarado (`(11) *****-4321`).
- **Acesso:** consulta exclusiva de ADMIN e GERENTE.

## Matriz de permissões

| Ação                                      | ADMIN | GERENTE | ATENDENTE |
|-------------------------------------------|:-----:|:-------:|:---------:|
| Gerenciar usuários e perfis               |   ✔   |         |           |
| Consultar pedidos                         |   ✔   |    ✔    |     ✔     |
| Registrar pedido / avançar status         |       |    ✔    |     ✔     |
| Cancelar pedido / aplicar desconto        |       |    ✔    |           |
| Consultar clientes                        |       |    ✔    |     ✔     |
| Cadastrar / alterar clientes              |       |         |     ✔     |
| Excluir clientes                          |       |    ✔    |           |
| Consultar produtos                        |   ✔   |    ✔    |     ✔     |
| Cadastrar / alterar produtos              |       |    ✔    |           |
| Consultar auditoria                       |   ✔   |    ✔    |           |

## API principal

| Método | Rota | Quem |
|---|---|---|
| GET | `/api/pedidos`, `/api/pedidos/{id}` | todos |
| POST | `/api/pedidos` | gerente, atendente |
| POST | `/api/pedidos/{id}/preparo` · `/pronto` · `/saida?modalidade=ENTREGA\|RETIRADA` · `/finalizacao` | gerente, atendente |
| POST | `/api/pedidos/{id}/cancelamento` `{"motivo": "..."}` | gerente |
| POST | `/api/pedidos/{id}/descontos` `{"percentual": 10, "motivo": "..."}` | gerente |
| GET | `/api/auditoria?entidade=Cliente&entidadeId=...` | admin, gerente |
| GET | `/api/auditoria/integridade` | admin, gerente |

Erros seguem o padrão *Problem Details* (RFC 7807):
- `422`: regra de negócio violada;
- `403`: sem permissão;
- `401`: sem login;
- `404`: não encontrado;
- `409`: conflito de edição simultânea.

O arquivo [`docs/demonstracao.http`](docs/demonstracao.http) traz requisições prontas, inclusive as tentativas bloqueadas.

## Principais alterações nos arquivos

- **Segurança:** `SecurityConfig`, `UsuarioDetailsService`, `security/TratadorAcessoNegado`, `security/EntradaApiNaoAutenticada`, `security/UsuarioLogado`.
- **Pedidos:** `Pedido`, `ItemPedido`, `DescontoAplicado`, `MovimentacaoPedido`, `StatusPedido`, `ModalidadeSaida`, `FluxoPedido`, `PedidoService`, `PedidoController`, `PedidoPaginaController`.
- **Auditoria:** `RegistroAuditoria`, `AlteracaoCampo`, `TipoOperacao`, `Auditavel`, `AuditoriaRepository`, `AuditoriaService`, `AuditoriaController`, `AuditoriaPaginaController`.
- **Clientes, produtos e usuários:** services com regras de negócio, validações e auditoria.
- **Tratamento de erros:**
  - `ApiErroHandler` (JSON) e `PaginaErroHandler` (mensagem na tela);
  - `MongoConfig` converte os status antigos dos pedidos já gravados.
- **Front (só o necessário, mantendo o Bootstrap):**
  - token CSRF no layout;
  - "logado como" e botão Sair na sidebar, além do link de Auditoria;
  - perfis novos no formulário de usuário;
  - lista de pedidos com ações;
  - tela de novo pedido e tela de auditoria;
  - formulário de produtos corrigido (ele postava JSON na API e não salvava).
- **Configuração:** string do MongoDB fora do repositório (`segredos.properties`).

## Testes

- `FluxoPedidoTest`: transições válidas e bloqueadas.
- `PedidoServiceTest`: cálculo do total no servidor, limite de 20%, desconto/cancelamento após saída, justificativa obrigatória, registro de saída.
- `AuditoriaServiceTest`: só campos alterados, corrente de hash, detecção de registro adulterado ou apagado, máscara de telefone.
- `SegurancaApiTest`: 401 sem login, 403 para atendente/admin ao cancelar ou dar desconto, 403 sem token CSRF.

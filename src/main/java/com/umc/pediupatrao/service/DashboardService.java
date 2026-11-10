package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.FormatadorDatas;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CanalVendaDTO;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroDTO;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroDTO.OpcaoDTO;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosDTO;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasDTO;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasDTO.ComparativoDTO;
import com.umc.pediupatrao.dto.dashboard.SerieVendasDTO;
import com.umc.pediupatrao.dto.dashboard.SerieVendasDTO.PontoDTO;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosDTO;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosDTO.EmAndamentoDTO;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosDTO.StatusDTO;
import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.FiltroInvalidoException;
import com.umc.pediupatrao.repository.DashboardRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras do dashboard administrativo.
 *
 * O DashboardRepository faz as contas pesadas no MongoDB. Aqui ficam as regras
 * de negócio em cima do resultado: preencher dias sem venda com zero, calcular
 * ticket médio, percentuais, comparação com o período anterior e alertas de atraso.
 *
 * Só ADMIN e GERENTE acessam (o @PreAuthorize na classe vale para todos os métodos).
 */
@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    /** Pedido aberto há mais que isso aparece como atrasado na fila. */
    public static final int LIMITE_ATRASO_MINUTOS = 40;

    public static final int RANKING_LIMITE_PADRAO = 5;
    public static final int RANKING_LIMITE_MAXIMO = 20;

    private static final EnumSet<StatusPedido> STATUS_EM_ABERTO =
            EnumSet.of(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO);

    private static final String[] DIAS_DA_SEMANA = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
    private static final DateTimeFormatter ROTULO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter ROTULO_MES = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final BigDecimal CEM = new BigDecimal("100");

    private final DashboardRepository dashboardRepository;
    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final Clock relogio;

    public DashboardService(DashboardRepository dashboardRepository, PedidoRepository pedidoRepository,
            ProdutoRepository produtoRepository, Clock relogio) {
        this.dashboardRepository = dashboardRepository;
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.relogio = relogio;
    }

    /** Valida os parâmetros recebidos e aplica os padrões (últimos 30 dias). */
    public FiltroDashboard montarFiltro(LocalDate inicio, LocalDate fim, Collection<StatusPedido> status,
            ModalidadeSaida modalidade, String produtoId) {
        return FiltroDashboard.criar(inicio, fim, status, modalidade, produtoId, hoje());
    }

    public OpcoesFiltroDTO opcoes() {
        FiltroDashboard padrao = montarFiltro(null, null, null, null, null);
        List<OpcaoDTO> status = Arrays.stream(StatusPedido.values())
                .map(s -> new OpcaoDTO(s.name(), s.getDescricao())).toList();
        List<OpcaoDTO> modalidades = Arrays.stream(ModalidadeSaida.values())
                .map(m -> new OpcaoDTO(m.name(), m.getDescricao())).toList();
        List<OpcaoDTO> agrupamentos = Arrays.stream(Agrupamento.values())
                .map(a -> new OpcaoDTO(a.name(), a.getDescricao())).toList();
        List<OpcaoDTO> produtos = produtoRepository.findAll().stream()
                .sorted(Comparator.comparing(p -> p.getNome() == null ? "" : p.getNome()))
                .map(p -> new OpcaoDTO(p.getId(), rotuloProduto(p)))
                .toList();
        return new OpcoesFiltroDTO(padrao.inicio(), padrao.fim(), FiltroDashboard.MAXIMO_DIAS,
                status, modalidades, agrupamentos, produtos);
    }

    /** RF01: indicadores gerais + comparação com o período anterior. */
    public ResumoVendasDTO resumo(FiltroDashboard filtro) {
        Document atual = dashboardRepository.resumo(filtro);
        FiltroDashboard anterior = filtro.periodoAnterior();
        Document passado = dashboardRepository.resumo(anterior);

        long total = inteiro(atual.get("totalPedidos"));
        long cancelados = inteiro(atual.get("cancelados"));
        long validos = total - cancelados;
        BigDecimal faturamento = dinheiro(atual.get("faturamento"));

        Object listaClientes = atual.get("clientes");
        long clientes = listaClientes instanceof Collection<?> c ? c.size() : 0;

        Object tempoMs = atual.get("tempoMedioSaidaMs");
        Long tempoMinutos = tempoMs instanceof Number n ? Math.round(n.doubleValue() / 60000.0) : null;

        BigDecimal faturamentoAnterior = dinheiro(passado.get("faturamento"));
        long pedidosAnterior = inteiro(passado.get("totalPedidos"));
        ComparativoDTO comparativo = new ComparativoDTO(anterior.inicio(), anterior.fim(),
                faturamentoAnterior, pedidosAnterior,
                variacao(faturamento, faturamentoAnterior),
                variacao(BigDecimal.valueOf(total), BigDecimal.valueOf(pedidosAnterior)));

        return new ResumoVendasDTO(filtro.inicio(), filtro.fim(), total, validos, cancelados,
                faturamento, dividir(faturamento, validos),
                dinheiro(atual.get("descontos")), dinheiro(atual.get("valorCancelado")),
                inteiro(atual.get("itensVendidos")), clientes,
                percentual(cancelados, total), tempoMinutos, comparativo,
                Instant.now(relogio));
    }

    /** RF02: série para os gráficos, com os buracos preenchidos com zero. */
    public SerieVendasDTO vendas(FiltroDashboard filtro, Agrupamento agrupamento) {
        Agrupamento tipo = agrupamento == null ? Agrupamento.DIA : agrupamento;

        Map<String, Document> porChave = new HashMap<>();
        for (Document d : dashboardRepository.serie(filtro, tipo)) {
            porChave.put(String.valueOf(d.get("_id")), d);
        }

        List<PontoDTO> pontos = new ArrayList<>();
        for (String[] chaveERotulo : chavesEsperadas(filtro, tipo)) {
            Document d = porChave.getOrDefault(chaveERotulo[0], new Document());
            long pedidos = inteiro(d.get("pedidos"));
            long cancelados = inteiro(d.get("cancelados"));
            BigDecimal faturamento = dinheiro(d.get("faturamento"));
            pontos.add(new PontoDTO(chaveERotulo[0], chaveERotulo[1], pedidos, cancelados,
                    faturamento, dividir(faturamento, pedidos - cancelados)));
        }

        PontoDTO pico = pontos.stream()
                .filter(p -> p.faturamento().signum() > 0)
                .max(Comparator.comparing(PontoDTO::faturamento))
                .orElse(null);
        return new SerieVendasDTO(tipo, pontos, pico);
    }

    /** RF03: contagem por status no período + fila de pedidos abertos agora. */
    public SituacaoPedidosDTO situacao(FiltroDashboard filtro) {
        Map<String, Document> porStatus = new HashMap<>();
        long total = 0;
        for (Document d : dashboardRepository.porStatus(filtro)) {
            porStatus.put(String.valueOf(d.get("_id")), d);
            total += inteiro(d.get("quantidade"));
        }

        List<StatusDTO> contagem = new ArrayList<>();
        for (StatusPedido status : StatusPedido.values()) {
            Document d = porStatus.getOrDefault(status.name(), new Document());
            long quantidade = inteiro(d.get("quantidade"));
            contagem.add(new StatusDTO(status, status.getDescricao(), quantidade,
                    percentual(quantidade, total), dinheiro(d.get("valor"))));
        }

        // A fila mostra o que está aberto AGORA, independente do período filtrado:
        // é a visão operacional para o gerente agir.
        Instant agora = Instant.now(relogio);
        List<EmAndamentoDTO> fila = pedidoRepository.findTop10ByStatusInOrderByEntradaEmAsc(STATUS_EM_ABERTO).stream()
                .map(p -> paraFila(p, agora))
                .toList();

        return new SituacaoPedidosDTO(total, contagem, fila, LIMITE_ATRASO_MINUTOS);
    }

    /** RF04: ranking dos produtos mais vendidos. */
    public RankingProdutosDTO ranking(FiltroDashboard filtro, CriterioRanking criterio, Integer limite) {
        CriterioRanking ordem = criterio == null ? CriterioRanking.QUANTIDADE : criterio;
        int quantos = limite == null ? RANKING_LIMITE_PADRAO : limite;
        if (quantos < 1 || quantos > RANKING_LIMITE_MAXIMO) {
            throw new FiltroInvalidoException("O limite do ranking deve ficar entre 1 e " + RANKING_LIMITE_MAXIMO + ".");
        }

        Document resultado = dashboardRepository.ranking(filtro, ordem, quantos);
        Document totais = primeiro(resultado.get("total"));
        long unidadesTotais = inteiro(totais.get("quantidade"));
        BigDecimal faturamentoTotal = dinheiro(totais.get("faturamento"));

        List<RankingProdutosDTO.ProdutoDTO> produtos = new ArrayList<>();
        int posicao = 1;
        for (Document d : listaDeDocumentos(resultado.get("ranking"))) {
            long quantidade = inteiro(d.get("quantidade"));
            BigDecimal faturamento = dinheiro(d.get("faturamento"));
            BigDecimal participacao = ordem == CriterioRanking.FATURAMENTO
                    ? percentual(faturamento, faturamentoTotal)
                    : percentual(quantidade, unidadesTotais);
            produtos.add(new RankingProdutosDTO.ProdutoDTO(posicao++, String.valueOf(d.get("_id")),
                    d.getString("nome"), quantidade, faturamento, inteiro(d.get("pedidos")), participacao));
        }
        return new RankingProdutosDTO(ordem, unidadesTotais, faturamentoTotal, produtos);
    }

    /** Divisão entre entrega e retirada (complementa o RF02). */
    public List<CanalVendaDTO> canais(FiltroDashboard filtro) {
        Map<String, Document> porModalidade = new HashMap<>();
        long total = 0;
        for (Document d : dashboardRepository.porModalidade(filtro)) {
            porModalidade.put(String.valueOf(d.get("_id")), d);
            total += inteiro(d.get("pedidos"));
        }
        List<CanalVendaDTO> canais = new ArrayList<>();
        for (ModalidadeSaida modalidade : ModalidadeSaida.values()) {
            Document d = porModalidade.getOrDefault(modalidade.name(), new Document());
            long pedidos = inteiro(d.get("pedidos"));
            canais.add(new CanalVendaDTO(modalidade, modalidade.getDescricao(), pedidos,
                    dinheiro(d.get("faturamento")), percentual(pedidos, total)));
        }
        return canais;
    }

    /**
     * Lista de [chave, rótulo] que o gráfico deve ter, mesmo sem vendas.
     * Assim um dia sem pedido aparece como zero, em vez de sumir do eixo.
     */
    static List<String[]> chavesEsperadas(FiltroDashboard filtro, Agrupamento agrupamento) {
        List<String[]> chaves = new ArrayList<>();
        switch (agrupamento) {
            case DIA -> {
                for (LocalDate d = filtro.inicio(); !d.isAfter(filtro.fim()); d = d.plusDays(1)) {
                    chaves.add(new String[]{d.toString(), ROTULO_DIA.format(d)});
                }
            }
            case MES -> {
                YearMonth ultimo = YearMonth.from(filtro.fim());
                for (YearMonth m = YearMonth.from(filtro.inicio()); !m.isAfter(ultimo); m = m.plusMonths(1)) {
                    chaves.add(new String[]{m.toString(), ROTULO_MES.format(m)});
                }
            }
            case HORA_DO_DIA -> {
                for (int h = 0; h < 24; h++) {
                    chaves.add(new String[]{String.valueOf(h), String.format("%02dh", h)});
                }
            }
            case DIA_DA_SEMANA -> {
                // O Mongo numera de 1 (segunda) a 7 (domingo)
                for (int d = 1; d <= 7; d++) {
                    chaves.add(new String[]{String.valueOf(d), DIAS_DA_SEMANA[d - 1]});
                }
            }
        }
        return chaves;
    }

    private EmAndamentoDTO paraFila(Pedido p, Instant agora) {
        long minutos = p.getEntradaEm() == null ? 0 : Math.max(0, Duration.between(p.getEntradaEm(), agora).toMinutes());
        return new EmAndamentoDTO(p.getId(), p.codigoCurto(), p.getClienteNome(), p.getStatus(),
                p.getStatus() == null ? "" : p.getStatus().getDescricao(), p.getEntradaEm(),
                minutos, minutos > LIMITE_ATRASO_MINUTOS, p.getValorTotal());
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio.withZone(FormatadorDatas.FUSO_PIZZARIA));
    }

    private static String rotuloProduto(Produto p) {
        String nome = p.getNome() == null ? "(sem nome)" : p.getNome();
        return p.disponivelParaVenda() ? nome : nome + " (inativo)";
    }

    // Conversões: o Mongo devolve Decimal128 para valores somados com $toDecimal,
    // Integer/Long para contagens e nada (null) quando não há pedidos no período.

    static BigDecimal dinheiro(Object valor) {
        BigDecimal numero;
        if (valor instanceof Decimal128 d) {
            numero = d.bigDecimalValue();
        } else if (valor instanceof BigDecimal b) {
            numero = b;
        } else if (valor instanceof Number n) {
            numero = new BigDecimal(n.toString());
        } else {
            numero = BigDecimal.ZERO;
        }
        return numero.setScale(2, RoundingMode.HALF_EVEN);
    }

    static long inteiro(Object valor) {
        if (valor instanceof Decimal128 d) {
            return d.bigDecimalValue().longValue();
        }
        return valor instanceof Number n ? n.longValue() : 0L;
    }

    static BigDecimal percentual(long parte, long total) {
        return percentual(BigDecimal.valueOf(parte), BigDecimal.valueOf(total));
    }

    static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** Variação em % entre dois valores; null quando não existe base de comparação. */
    static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal dividir(BigDecimal valor, long quantidade) {
        if (quantidade <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return valor.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_EVEN);
    }

    private static Document primeiro(Object lista) {
        List<Document> documentos = listaDeDocumentos(lista);
        return documentos.isEmpty() ? new Document() : documentos.get(0);
    }

    private static List<Document> listaDeDocumentos(Object lista) {
        if (!(lista instanceof List<?> itens)) {
            return List.of();
        }
        List<Document> documentos = new ArrayList<>();
        for (Object item : itens) {
            if (item instanceof Document d) {
                documentos.add(d);
            }
        }
        return documentos;
    }
}

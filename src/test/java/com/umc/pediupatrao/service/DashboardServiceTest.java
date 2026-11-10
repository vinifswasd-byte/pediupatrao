package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosDTO;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasDTO;
import com.umc.pediupatrao.dto.dashboard.SerieVendasDTO;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosDTO;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.FiltroInvalidoException;
import com.umc.pediupatrao.repository.DashboardRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testa as regras que o DashboardService aplica em cima do resultado do banco.
 * O DashboardRepository é falso (Mockito) e devolve documentos como o MongoDB devolveria.
 */
class DashboardServiceTest {

    // 10/11/2026 15:00 em Brasília
    private static final Instant AGORA = Instant.parse("2026-11-10T18:00:00Z");

    private DashboardRepository repositorio;
    private PedidoRepository pedidoRepository;
    private DashboardService service;

    @BeforeEach
    void preparar() {
        repositorio = mock(DashboardRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        service = new DashboardService(repositorio, pedidoRepository, mock(ProdutoRepository.class),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void resumoCalculaTicketMedioTaxaECrescimento() {
        FiltroDashboard f = service.montarFiltro(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), null, null, null);
        when(repositorio.resumo(f)).thenReturn(new Document("totalPedidos", 10).append("cancelados", 2)
                .append("faturamento", dec("400.00")).append("descontos", dec("20.00"))
                .append("valorCancelado", dec("90.00")).append("itensVendidos", 25)
                .append("clientes", List.of("c1", "c2", "c3")).append("tempoMedioSaidaMs", 1_800_000.0));
        when(repositorio.resumo(f.periodoAnterior())).thenReturn(new Document("totalPedidos", 8)
                .append("faturamento", dec("320.00")));

        ResumoVendasDTO r = service.resumo(f);

        assertEquals(8, r.pedidosValidos());
        assertEquals(new BigDecimal("50.00"), r.ticketMedio()); // 400 / 8 pedidos válidos
        assertEquals(new BigDecimal("20.0"), r.taxaCancelamento()); // 2 de 10
        assertEquals(3, r.clientesAtendidos());
        assertEquals(30L, r.tempoMedioAteSaidaMinutos());
        assertEquals(new BigDecimal("25.0"), r.comparativo().variacaoFaturamento()); // 320 -> 400
        assertEquals(new BigDecimal("25.0"), r.comparativo().variacaoPedidos()); // 8 -> 10
    }

    @Test
    void resumoSemVendasNaoDivideZeroNemInventaCrescimento() {
        FiltroDashboard f = service.montarFiltro(null, null, null, null, null);
        when(repositorio.resumo(any())).thenReturn(new Document());

        ResumoVendasDTO r = service.resumo(f);

        assertEquals(0, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertNull(r.comparativo().variacaoFaturamento());
        assertNull(r.tempoMedioAteSaidaMinutos());
    }

    @Test
    void serieDiariaPreencheDiasSemVendaComZero() {
        FiltroDashboard f = service.montarFiltro(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10), null, null, null);
        when(repositorio.serie(f, Agrupamento.DIA)).thenReturn(List.of(
                new Document("_id", "2026-11-08").append("pedidos", 3).append("faturamento", dec("150.00")),
                new Document("_id", "2026-11-10").append("pedidos", 1).append("faturamento", dec("45.00"))));

        SerieVendasDTO s = service.vendas(f, Agrupamento.DIA);

        assertEquals(3, s.pontos().size());
        assertEquals("09/11", s.pontos().get(1).rotulo());
        assertEquals(0, s.pontos().get(1).pedidos());
        assertEquals(new BigDecimal("50.00"), s.pontos().get(0).ticketMedio());
        assertEquals("2026-11-08", s.pico().chave());
    }

    @Test
    void seriePorHoraTemAs24Horas() {
        FiltroDashboard f = service.montarFiltro(null, null, null, null, null);
        when(repositorio.serie(f, Agrupamento.HORA_DO_DIA)).thenReturn(List.of(
                new Document("_id", 20).append("pedidos", 7).append("faturamento", dec("300.00"))));

        SerieVendasDTO s = service.vendas(f, Agrupamento.HORA_DO_DIA);

        assertEquals(24, s.pontos().size());
        assertEquals("20h", s.pico().rotulo());
    }

    @Test
    void situacaoTrazTodosOsStatusComPercentual() {
        FiltroDashboard f = service.montarFiltro(null, null, null, null, null);
        when(repositorio.porStatus(f)).thenReturn(List.of(
                new Document("_id", "FINALIZADO").append("quantidade", 3).append("valor", dec("150.00")),
                new Document("_id", "CANCELADO").append("quantidade", 1).append("valor", dec("40.00"))));
        when(pedidoRepository.findTop10ByStatusInOrderByEntradaEmAsc(any())).thenReturn(List.of());

        SituacaoPedidosDTO s = service.situacao(f);

        assertEquals(4, s.total());
        assertEquals(StatusPedido.values().length, s.porStatus().size());
        SituacaoPedidosDTO.StatusDTO finalizado = s.porStatus().stream()
                .filter(x -> x.status() == StatusPedido.FINALIZADO).findFirst().orElseThrow();
        assertEquals(new BigDecimal("75.0"), finalizado.percentual());
    }

    @Test
    void rankingCalculaParticipacaoELimite() {
        FiltroDashboard f = service.montarFiltro(null, null, null, null, null);
        when(repositorio.ranking(eq(f), eq(CriterioRanking.QUANTIDADE), anyInt())).thenReturn(new Document()
                .append("ranking", List.of(
                        new Document("_id", "p1").append("nome", "Calabresa").append("quantidade", 30)
                                .append("faturamento", dec("1350.00")).append("pedidos", 20),
                        new Document("_id", "p2").append("nome", "Refrigerante").append("quantidade", 10)
                                .append("faturamento", dec("120.00")).append("pedidos", 10)))
                .append("total", List.of(new Document("quantidade", 50).append("faturamento", dec("1600.00")))));

        RankingProdutosDTO r = service.ranking(f, CriterioRanking.QUANTIDADE, 5);

        assertEquals(1, r.produtos().get(0).posicao());
        assertEquals(new BigDecimal("60.0"), r.produtos().get(0).participacao()); // 30 de 50
        assertThrows(FiltroInvalidoException.class, () -> service.ranking(f, CriterioRanking.QUANTIDADE, 50));
    }

    @Test
    void variacaoSoExisteComBaseDeComparacao() {
        assertNull(DashboardService.variacao(new BigDecimal("10"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("-50.0"), DashboardService.variacao(new BigDecimal("5"), new BigDecimal("10")));
        assertTrue(DashboardService.dinheiro(null).signum() == 0);
    }

    private static Decimal128 dec(String valor) {
        return new Decimal128(new BigDecimal(valor));
    }
}

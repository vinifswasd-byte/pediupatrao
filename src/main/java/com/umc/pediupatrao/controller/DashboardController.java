package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CanalVendaDTO;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboardParams;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroDTO;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosDTO;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasDTO;
import com.umc.pediupatrao.dto.dashboard.SerieVendasDTO;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosDTO;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.PainelTempoReal;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API que alimenta o dashboard administrativo (RF06).
 *
 * Todas as rotas são GET (só leitura) e aceitam os mesmos filtros:
 * inicio, fim, status (pode repetir), modalidade e produtoId.
 * Documentação completa com exemplos em docs/api-dashboard.md.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final PainelTempoReal painelTempoReal;

    public DashboardController(DashboardService dashboardService, PainelTempoReal painelTempoReal) {
        this.dashboardService = dashboardService;
        this.painelTempoReal = painelTempoReal;
    }

    /** Valores aceitos nos filtros e o período padrão. */
    @GetMapping("/opcoes")
    public OpcoesFiltroDTO opcoes() {
        return dashboardService.opcoes();
    }

    /** RF01: indicadores gerais. */
    @GetMapping("/resumo")
    public ResumoVendasDTO resumo(@ModelAttribute FiltroDashboardParams params) {
        return dashboardService.resumo(filtro(params));
    }

    /** RF02: série para os gráficos. agrupamento = DIA, MES, HORA_DO_DIA ou DIA_DA_SEMANA. */
    @GetMapping("/vendas")
    public SerieVendasDTO vendas(@ModelAttribute FiltroDashboardParams params,
            @RequestParam(defaultValue = "DIA") Agrupamento agrupamento) {
        return dashboardService.vendas(filtro(params), agrupamento);
    }

    /** RF03: pedidos por status e fila dos abertos. */
    @GetMapping("/pedidos/situacao")
    public SituacaoPedidosDTO situacao(@ModelAttribute FiltroDashboardParams params) {
        return dashboardService.situacao(filtro(params));
    }

    /** RF04: produtos mais vendidos. criterio = QUANTIDADE ou FATURAMENTO; limite de 1 a 20. */
    @GetMapping("/produtos/ranking")
    public RankingProdutosDTO ranking(@ModelAttribute FiltroDashboardParams params,
            @RequestParam(defaultValue = "QUANTIDADE") CriterioRanking criterio,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.ranking(filtro(params), criterio, limite);
    }

    /** Pedidos por canal de saída (entrega x retirada). */
    @GetMapping("/canais")
    public List<CanalVendaDTO> canais(@ModelAttribute FiltroDashboardParams params) {
        return dashboardService.canais(filtro(params));
    }

    /**
     * RF07: canal de tempo real (Server-Sent Events).
     * A conexão fica aberta e recebe "pedido-alterado" sempre que um pedido muda.
     */
    @GetMapping(path = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos() {
        return painelTempoReal.conectar();
    }

    private FiltroDashboard filtro(FiltroDashboardParams p) {
        return dashboardService.montarFiltro(p.getInicio(), p.getFim(), p.getStatus(),
                p.getModalidade(), p.getProdutoId());
    }
}

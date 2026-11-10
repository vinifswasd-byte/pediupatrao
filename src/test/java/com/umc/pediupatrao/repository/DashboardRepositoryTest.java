package com.umc.pediupatrao.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.StatusPedido;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** Confere se os filtros viram o $match certo no MongoDB. */
class DashboardRepositoryTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    @Test
    void semFiltrosExtrasSoLimitaPeriodoEPedidosComItens() {
        FiltroDashboard f = FiltroDashboard.criar(null, null, null, null, null, HOJE);
        Document criterios = (Document) DashboardRepository.filtroBasico(f).get("$match");

        Document periodo = (Document) criterios.get("entradaEm");
        assertEquals(Date.from(f.inicioInstante()), periodo.get("$gte"));
        assertEquals(Date.from(f.fimExclusivo()), periodo.get("$lt"));
        assertTrue(criterios.containsKey("itens.0"));
        assertFalse(criterios.containsKey("status"));
        assertFalse(criterios.containsKey("modalidadeSaida"));
    }

    @Test
    void filtrosOpcionaisEntramNoMatch() {
        FiltroDashboard f = FiltroDashboard.criar(null, null,
                List.of(StatusPedido.FINALIZADO, StatusPedido.CANCELADO), ModalidadeSaida.ENTREGA, "p1", HOJE);
        Document criterios = (Document) DashboardRepository.filtroBasico(f).get("$match");

        assertEquals(new Document("$in", List.of("CANCELADO", "FINALIZADO")), criterios.get("status"));
        assertEquals("ENTREGA", criterios.get("modalidadeSaida"));
        assertEquals("p1", criterios.get("itens.produtoId"));
    }

    @Test
    void agrupamentoPorDiaUsaFusoDeBrasilia() {
        Document chave = (Document) DashboardRepository.chaveDoAgrupamento(Agrupamento.DIA);
        Document dateToString = (Document) chave.get("$dateToString");
        assertEquals("%Y-%m-%d", dateToString.get("format"));
        assertEquals("America/Sao_Paulo", dateToString.get("timezone"));
        assertTrue(((Document) DashboardRepository.chaveDoAgrupamento(Agrupamento.HORA_DO_DIA)).containsKey("$hour"));
    }
}

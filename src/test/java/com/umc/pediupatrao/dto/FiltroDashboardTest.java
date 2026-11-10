package com.umc.pediupatrao.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.FiltroInvalidoException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regras do filtro do dashboard (RF05), sem banco e sem Spring. */
class FiltroDashboardTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    @Test
    void semDatasUsaOsUltimos30Dias() {
        FiltroDashboard f = FiltroDashboard.criar(null, null, null, null, null, HOJE);
        assertEquals(LocalDate.of(2026, 10, 12), f.inicio());
        assertEquals(HOJE, f.fim());
        assertEquals(30, f.dias());
        assertTrue(f.status().isEmpty());
        assertNull(f.produtoId());
    }

    @Test
    void dataInicialDepoisDaFinalEhRecusada() {
        FiltroInvalidoException erro = assertThrows(FiltroInvalidoException.class,
                () -> FiltroDashboard.criar(HOJE, HOJE.minusDays(1), null, null, null, HOJE));
        assertTrue(erro.getMessage().contains("posterior"));
    }

    @Test
    void periodoMaiorQueUmAnoEhRecusado() {
        assertThrows(FiltroInvalidoException.class,
                () -> FiltroDashboard.criar(HOJE.minusDays(400), HOJE, null, null, null, HOJE));
    }

    @Test
    void fimEntraInteiroNoHorarioDeBrasilia() {
        FiltroDashboard f = FiltroDashboard.criar(HOJE, HOJE, List.of(StatusPedido.FINALIZADO), null, " abc ", HOJE);
        // 10/11 00:00 em Brasília = 03:00 UTC; o fim é 11/11 00:00 em Brasília
        assertEquals(Instant.parse("2026-11-10T03:00:00Z"), f.inicioInstante());
        assertEquals(Instant.parse("2026-11-11T03:00:00Z"), f.fimExclusivo());
        assertEquals("abc", f.produtoId());
    }

    @Test
    void periodoAnteriorTemOMesmoTamanho() {
        FiltroDashboard f = FiltroDashboard.criar(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), null, null, null, HOJE);
        FiltroDashboard anterior = f.periodoAnterior();
        assertEquals(LocalDate.of(2026, 10, 22), anterior.inicio());
        assertEquals(LocalDate.of(2026, 10, 31), anterior.fim());
        assertEquals(f.dias(), anterior.dias());
    }
}

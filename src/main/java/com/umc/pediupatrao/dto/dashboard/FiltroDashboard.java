package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.config.FormatadorDatas;
import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.FiltroInvalidoException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Filtro já validado que todas as consultas do dashboard usam (RF05).
 *
 * As datas são dias do calendário da pizzaria (horário de Brasília).
 * O "fim" entra inteiro na consulta: de 01/11 a 10/11 pega até 10/11 às 23:59:59.
 */
public record FiltroDashboard(
        LocalDate inicio,
        LocalDate fim,
        Set<StatusPedido> status,
        ModalidadeSaida modalidade,
        String produtoId) {

    /** Sem datas informadas, o dashboard mostra os últimos 30 dias. */
    public static final int DIAS_PADRAO = 30;

    /** Limite para a consulta não ficar pesada demais. */
    public static final int MAXIMO_DIAS = 366;

    public static FiltroDashboard criar(LocalDate inicio, LocalDate fim, Collection<StatusPedido> status,
            ModalidadeSaida modalidade, String produtoId, LocalDate hoje) {

        LocalDate fimFinal = fim != null ? fim : hoje;
        LocalDate inicioFinal = inicio != null ? inicio : fimFinal.minusDays(DIAS_PADRAO - 1L);

        if (inicioFinal.isAfter(fimFinal)) {
            throw new FiltroInvalidoException("A data inicial (" + inicioFinal + ") é posterior à data final (" + fimFinal + ").");
        }
        long dias = ChronoUnit.DAYS.between(inicioFinal, fimFinal) + 1;
        if (dias > MAXIMO_DIAS) {
            throw new FiltroInvalidoException("O período pode ter no máximo " + MAXIMO_DIAS + " dias (foram pedidos " + dias + ").");
        }

        Set<StatusPedido> statusFinal = (status == null || status.isEmpty())
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(status));
        String produtoFinal = (produtoId == null || produtoId.isBlank()) ? null : produtoId.trim();

        return new FiltroDashboard(inicioFinal, fimFinal, statusFinal, modalidade, produtoFinal);
    }

    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    public Instant inicioInstante() {
        return inicio.atStartOfDay(FormatadorDatas.FUSO_PIZZARIA).toInstant();
    }

    /** Meia-noite do dia seguinte ao fim (a consulta usa "menor que"). */
    public Instant fimExclusivo() {
        return fim.plusDays(1).atStartOfDay(FormatadorDatas.FUSO_PIZZARIA).toInstant();
    }

    /**
     * Mesmo tamanho de período, logo antes do atual, com os mesmos filtros.
     * Ex.: 11/10 a 10/11 compara com 10/09 a 10/10. Usado no comparativo do resumo.
     */
    public FiltroDashboard periodoAnterior() {
        LocalDate fimAnterior = inicio.minusDays(1);
        LocalDate inicioAnterior = fimAnterior.minusDays(dias() - 1);
        return new FiltroDashboard(inicioAnterior, fimAnterior, status, modalidade, produtoId);
    }
}

package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados do gráfico de vendas (RF02). Os períodos sem venda vêm com zero,
 * para o gráfico não "pular" dias.
 */
public record SerieVendasDTO(
        Agrupamento agrupamento,
        List<PontoDTO> pontos,
        PontoDTO pico) {

    public record PontoDTO(
            String chave,
            String rotulo,
            long pedidos,
            long cancelados,
            BigDecimal faturamento,
            BigDecimal ticketMedio) {
    }
}

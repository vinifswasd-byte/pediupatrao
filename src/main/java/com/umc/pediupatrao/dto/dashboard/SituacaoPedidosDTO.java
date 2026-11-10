package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Situação dos pedidos (RF03): quantos estão em cada status no período
 * e a fila de pedidos ainda abertos agora, do mais antigo para o mais novo.
 */
public record SituacaoPedidosDTO(
        long total,
        List<StatusDTO> porStatus,
        List<EmAndamentoDTO> emAndamento,
        int limiteAtrasoMinutos) {

    public record StatusDTO(
            StatusPedido status,
            String descricao,
            long quantidade,
            BigDecimal percentual,
            BigDecimal valor) {
    }

    public record EmAndamentoDTO(
            String id,
            String codigo,
            String cliente,
            StatusPedido status,
            String statusDescricao,
            Instant entradaEm,
            long minutosEmAberto,
            boolean atrasado,
            BigDecimal valorTotal) {
    }
}

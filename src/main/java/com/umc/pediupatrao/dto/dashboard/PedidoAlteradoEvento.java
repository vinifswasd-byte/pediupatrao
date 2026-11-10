package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import java.time.Instant;

/**
 * Aviso interno de que um pedido mudou. O PedidoService publica,
 * e o PainelTempoReal repassa para os dashboards abertos (RF07).
 * Leva só o mínimo: a tela usa isso apenas como "sinal" para recarregar os dados.
 */
public record PedidoAlteradoEvento(
        String pedidoId,
        TipoOperacao operacao,
        StatusPedido status,
        Instant ocorridoEm) {
}

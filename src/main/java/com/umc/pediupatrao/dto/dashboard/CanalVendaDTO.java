package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.ModalidadeSaida;
import java.math.BigDecimal;

/** Pedidos que saíram por entrega ou por retirada no balcão. */
public record CanalVendaDTO(
        ModalidadeSaida modalidade,
        String descricao,
        long pedidos,
        BigDecimal faturamento,
        BigDecimal percentual) {
}

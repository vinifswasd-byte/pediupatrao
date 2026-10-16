package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cada desconto dado pelo gerente fica guardado separado, com o motivo.
 * Assim dá para ver no pedido que, por exemplo, teve 10% por atraso
 * e depois mais 5% por cortesia, em vez de só um número final.
 */
public record DescontoAplicado(
        BigDecimal percentual,
        BigDecimal valor,
        String motivo,
        String aplicadoPor,
        Instant aplicadoEm) {
}

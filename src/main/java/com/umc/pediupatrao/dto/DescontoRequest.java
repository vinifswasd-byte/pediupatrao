package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

/** Pedido de desconto do gerente: percentual (ex.: 10 para 10%) e o motivo. */
public record DescontoRequest(BigDecimal percentual, String motivo) {
}

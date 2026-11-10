package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Indicadores gerais do período (RF01).
 *
 * Pedidos cancelados contam no total de pedidos, mas não no faturamento:
 * o valor deles aparece separado em "valorCancelado" (o que a pizzaria deixou de ganhar).
 */
public record ResumoVendasDTO(
        LocalDate inicio,
        LocalDate fim,
        long totalPedidos,
        long pedidosValidos,
        long pedidosCancelados,
        BigDecimal faturamento,
        BigDecimal ticketMedio,
        BigDecimal totalDescontos,
        BigDecimal valorCancelado,
        long itensVendidos,
        long clientesAtendidos,
        BigDecimal taxaCancelamento,
        Long tempoMedioAteSaidaMinutos,
        ComparativoDTO comparativo,
        Instant atualizadoEm) {

    /**
     * Comparação com o período anterior de mesmo tamanho.
     * As variações vêm em % e ficam null quando o período anterior não teve vendas
     * (não dá para calcular crescimento a partir de zero).
     */
    public record ComparativoDTO(
            LocalDate inicioAnterior,
            LocalDate fimAnterior,
            BigDecimal faturamentoAnterior,
            long pedidosAnterior,
            BigDecimal variacaoFaturamento,
            BigDecimal variacaoPedidos) {
    }
}

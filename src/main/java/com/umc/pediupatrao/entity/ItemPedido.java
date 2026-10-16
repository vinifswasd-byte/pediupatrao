package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Uma linha do pedido: qual produto, quantas unidades e quanto custou.
 *
 * Guardamos nome e preço "congelados" no momento da venda. Se o preço da
 * calabresa subir amanhã, o pedido de hoje continua com o valor de hoje.
 */
public record ItemPedido(
        String produtoId,
        String nomeProduto,
        BigDecimal precoUnitario,
        int quantidade,
        BigDecimal subtotal) {

    public static ItemPedido de(Produto produto, int quantidade) {
        BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal subtotal = preco.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_EVEN);
        return new ItemPedido(produto.getId(), produto.getNome(), preco, quantidade, subtotal);
    }

    /** Texto curto para listas e para a auditoria, ex.: "2x Calabresa". */
    public String resumo() {
        return quantidade + "x " + nomeProduto;
    }
}

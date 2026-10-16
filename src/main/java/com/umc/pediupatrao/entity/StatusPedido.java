package com.umc.pediupatrao.entity;

/**
 * Etapas por onde um pedido passa.
 * Quais mudanças são permitidas fica na classe FluxoPedido, não aqui.
 */
public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    DESPACHADO("Saiu / Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}

package com.umc.pediupatrao.entity;

/**
 * Tipos de evento que a auditoria registra.
 */
public enum TipoOperacao {
    CRIACAO_PEDIDO("Inclusão de pedido"),
    MUDANCA_STATUS_PEDIDO("Mudança de status do pedido"),
    DESCONTO_PEDIDO("Desconto aplicado"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),

    CRIACAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),

    CRIACAO_PRODUTO("Inclusão de produto"),
    ALTERACAO_PRODUTO("Alteração de produto"),
    EXCLUSAO_PRODUTO("Exclusão de produto"),

    CRIACAO_USUARIO("Inclusão de usuário"),
    ALTERACAO_USUARIO("Alteração de usuário"),
    EXCLUSAO_USUARIO("Exclusão de usuário"),

    ACESSO_NEGADO("Tentativa de acesso negada");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}

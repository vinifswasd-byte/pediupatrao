package com.umc.pediupatrao.entity;

/**
 * Como o pedido deixou a pizzaria.
 * Preferi um único status DESPACHADO + esta modalidade, em vez de dois status
 * separados, porque a regra é a mesma nos dois casos: depois de sair, não cancela
 * e não dá desconto. Muda só o jeito que saiu.
 */
public enum ModalidadeSaida {
    ENTREGA("Saiu para entrega"),
    RETIRADA("Retirado no balcão");

    private final String descricao;

    ModalidadeSaida(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}

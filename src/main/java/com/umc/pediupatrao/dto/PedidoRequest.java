package com.umc.pediupatrao.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Dados para registrar um pedido. Serve tanto para o formulário da tela quanto para o JSON da API.
 *
 * Exemplo de JSON:
 * { "clienteId": "...", "itens": [ { "produtoId": "...", "quantidade": 2 } ], "observacao": "sem cebola" }
 *
 * Mesmo que alguém envie "valorTotal" ou "status" junto, esses campos nem existem aqui
 * e são ignorados: o servidor calcula tudo.
 */
public class PedidoRequest {

    private String clienteId;
    private String observacao;
    private List<ItemPedidoRequest> itens = new ArrayList<>();

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public List<ItemPedidoRequest> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedidoRequest> itens) {
        this.itens = itens;
    }
}

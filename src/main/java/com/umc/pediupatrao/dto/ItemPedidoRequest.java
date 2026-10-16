package com.umc.pediupatrao.dto;

/**
 * Um item do jeito que chega do formulário ou do JSON: só o produto e a quantidade.
 * Preço não vem daqui de propósito. Quem decide o preço é o cadastro de produtos.
 */
public class ItemPedidoRequest {

    private String produtoId;
    private Integer quantidade;

    public ItemPedidoRequest() {
    }

    public ItemPedidoRequest(String produtoId, Integer quantidade) {
        this.produtoId = produtoId;
        this.quantidade = quantidade;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }
}

package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Produtos mais vendidos (RF04). "participacao" é a fatia do produto
 * no total do período, pelo critério escolhido (unidades ou valor).
 * O faturamento por produto é o valor dos itens antes dos descontos do pedido.
 */
public record RankingProdutosDTO(
        CriterioRanking criterio,
        long unidadesTotais,
        BigDecimal faturamentoTotal,
        List<ProdutoDTO> produtos) {

    public record ProdutoDTO(
            int posicao,
            String produtoId,
            String nome,
            long quantidade,
            BigDecimal faturamento,
            long pedidos,
            BigDecimal participacao) {
    }
}

package com.umc.pediupatrao.entity;

import com.umc.pediupatrao.exception.RegraNegocioException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O "mapa" de caminhos que um pedido pode seguir.
 *
 * Funciona como uma tabela: para cada status, a lista de status para onde ele pode ir.
 * Se não está na tabela, não pode. Exemplo: de EM_PREPARACAO não existe caminho
 * direto para FINALIZADO, então essa mudança é recusada.
 *
 * Deixar isso numa tabela separada facilita mudar o fluxo no futuro
 * sem mexer em vários if/else espalhados pelo código.
 */
public final class FluxoPedido {

    private static final Map<StatusPedido, Set<StatusPedido>> CAMINHOS = new EnumMap<>(StatusPedido.class);

    static {
        CAMINHOS.put(StatusPedido.RECEBIDO, EnumSet.of(StatusPedido.EM_PREPARACAO, StatusPedido.CANCELADO));
        CAMINHOS.put(StatusPedido.EM_PREPARACAO, EnumSet.of(StatusPedido.PRONTO, StatusPedido.CANCELADO));
        CAMINHOS.put(StatusPedido.PRONTO, EnumSet.of(StatusPedido.DESPACHADO, StatusPedido.CANCELADO));
        CAMINHOS.put(StatusPedido.DESPACHADO, EnumSet.of(StatusPedido.FINALIZADO));
        // FINALIZADO e CANCELADO são o fim da linha: não vão para lugar nenhum.
        CAMINHOS.put(StatusPedido.FINALIZADO, EnumSet.noneOf(StatusPedido.class));
        CAMINHOS.put(StatusPedido.CANCELADO, EnumSet.noneOf(StatusPedido.class));
    }

    private FluxoPedido() {
        // só métodos estáticos, não precisa instanciar
    }

    public static boolean podeIr(StatusPedido de, StatusPedido para) {
        if (de == null || para == null) {
            return false;
        }
        return CAMINHOS.getOrDefault(de, Set.of()).contains(para);
    }

    public static void validarTransicao(StatusPedido de, StatusPedido para) {
        if (de == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode mais ter o status alterado.");
        }
        if (!podeIr(de, para)) {
            throw new RegraNegocioException("Não é possível passar o pedido de "
                    + descrever(de) + " para " + descrever(para) + ".");
        }
    }

    /** Próximos passos "normais", sem o cancelamento, que tem botão próprio para o gerente. */
    public static List<StatusPedido> proximosPassos(StatusPedido atual) {
        return CAMINHOS.getOrDefault(atual, Set.of()).stream()
                .filter(s -> s != StatusPedido.CANCELADO)
                .toList();
    }

    public static boolean podeCancelar(StatusPedido atual) {
        return podeIr(atual, StatusPedido.CANCELADO);
    }

    /** Desconto só enquanto o pedido ainda está na pizzaria e não foi cancelado. */
    public static boolean aceitaDesconto(StatusPedido atual) {
        return atual == StatusPedido.RECEBIDO
                || atual == StatusPedido.EM_PREPARACAO
                || atual == StatusPedido.PRONTO;
    }

    private static String descrever(StatusPedido status) {
        return status == null ? "(sem status)" : status.getDescricao();
    }
}

package com.umc.pediupatrao.entity;

import java.time.Instant;

/**
 * Linha do tempo do pedido, guardada dentro do próprio documento no Mongo.
 * Serve para a tela mostrar rápido por onde o pedido passou, sem precisar
 * ir buscar na auditoria. A auditoria continua sendo o registro oficial.
 */
public record MovimentacaoPedido(
        StatusPedido de,
        StatusPedido para,
        String responsavel,
        Instant em,
        String observacao) {
}

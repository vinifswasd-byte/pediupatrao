package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.exception.RegraNegocioException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Confere a tabela de caminhos do pedido, sem banco e sem Spring. */
class FluxoPedidoTest {

    @Test
    void caminhoNormalDoPedidoEhPermitido() {
        assertTrue(FluxoPedido.podeIr(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO));
        assertTrue(FluxoPedido.podeIr(StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO));
        assertTrue(FluxoPedido.podeIr(StatusPedido.PRONTO, StatusPedido.DESPACHADO));
        assertTrue(FluxoPedido.podeIr(StatusPedido.DESPACHADO, StatusPedido.FINALIZADO));
    }

    @Test
    void naoPulaEtapas() {
        // exemplo do enunciado: finalizar um pedido que ainda está em preparação
        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> FluxoPedido.validarTransicao(StatusPedido.EM_PREPARACAO, StatusPedido.FINALIZADO));
        assertTrue(erro.getMessage().contains("Em preparação"));
        assertFalse(FluxoPedido.podeIr(StatusPedido.RECEBIDO, StatusPedido.DESPACHADO));
        assertFalse(FluxoPedido.podeIr(StatusPedido.PRONTO, StatusPedido.EM_PREPARACAO));
    }

    @Test
    void cancelamentoSoAntesDaSaida() {
        assertTrue(FluxoPedido.podeCancelar(StatusPedido.RECEBIDO));
        assertTrue(FluxoPedido.podeCancelar(StatusPedido.EM_PREPARACAO));
        assertTrue(FluxoPedido.podeCancelar(StatusPedido.PRONTO));
        assertFalse(FluxoPedido.podeCancelar(StatusPedido.DESPACHADO));
        assertFalse(FluxoPedido.podeCancelar(StatusPedido.FINALIZADO));
    }

    @Test
    void pedidoCanceladoNaoMudaMais() {
        for (StatusPedido destino : StatusPedido.values()) {
            assertFalse(FluxoPedido.podeIr(StatusPedido.CANCELADO, destino));
        }
        assertThrows(RegraNegocioException.class,
                () -> FluxoPedido.validarTransicao(StatusPedido.CANCELADO, StatusPedido.EM_PREPARACAO));
    }

    @Test
    void botoesNaoMostramCancelamento() {
        assertEquals(List.of(StatusPedido.EM_PREPARACAO), FluxoPedido.proximosPassos(StatusPedido.RECEBIDO));
        assertEquals(List.of(), FluxoPedido.proximosPassos(StatusPedido.FINALIZADO));
    }
}

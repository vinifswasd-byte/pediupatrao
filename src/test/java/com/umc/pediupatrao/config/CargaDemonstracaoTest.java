package com.umc.pediupatrao.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** A carga de demonstração precisa gerar pedidos coerentes com as regras do sistema. */
class CargaDemonstracaoTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T23:30:00Z"); // 20:30 em Brasília

    @Test
    void geraPedidosValidosSemNadaNoFuturo() {
        List<Pedido> pedidos = CargaDemonstracao.gerar(cardapio(), clientes(), AGORA, 30, new Random(1));

        assertFalse(pedidos.isEmpty());
        for (Pedido p : pedidos) {
            assertFalse(p.getEntradaEm().isAfter(AGORA), "pedido no futuro");
            assertFalse(p.getItens().isEmpty());
            assertTrue(p.getValorTotal().signum() > 0);
            assertTrue(p.percentualDescontoAcumulado().intValue() <= 20);
            if (p.getSaidaEm() != null) {
                assertTrue(p.getSaidaEm().isAfter(p.getEntradaEm()));
            }
        }
        assertTrue(pedidos.stream().anyMatch(p -> p.getStatus() == StatusPedido.CANCELADO));
        assertTrue(pedidos.stream().anyMatch(p -> p.getStatus() == StatusPedido.FINALIZADO));
    }

    private static List<Produto> cardapio() {
        return List.of(produto("Calabresa", 45.0), produto("Margherita", 42.0),
                produto("Portuguesa", 48.0), produto("Refrigerante", 12.0));
    }

    private static Produto produto(String nome, double preco) {
        Produto p = new Produto();
        p.setId(nome);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(Boolean.TRUE);
        return p;
    }

    private static List<Cliente> clientes() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Cliente Teste");
        return List.of(c);
    }
}

package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testa as regras do PedidoService com repositórios "de mentira" (Mockito),
 * então roda sem precisar do MongoDB.
 */
class PedidoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-16T18:30:00Z");

    private PedidoRepository pedidoRepository;
    private ProdutoRepository produtoRepository;
    private ClienteRepository clienteRepository;
    private AuditoriaService auditoria;
    private PedidoService service;

    @BeforeEach
    void preparar() {
        pedidoRepository = mock(PedidoRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        auditoria = mock(AuditoriaService.class);
        UsuarioLogado usuarioLogado = mock(UsuarioLogado.class);
        when(usuarioLogado.nome()).thenReturn("gerente01");

        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository,
                auditoria, usuarioLogado, Clock.fixed(AGORA, ZoneOffset.UTC));

        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));
        when(produtoRepository.findById("calabresa")).thenReturn(Optional.of(produto("calabresa", "Calabresa", 40.0, true)));
        when(produtoRepository.findById("refri")).thenReturn(Optional.of(produto("refri", "Refrigerante", 8.0, true)));
        when(produtoRepository.findById("velho")).thenReturn(Optional.of(produto("velho", "Pizza fora do cardápio", 30.0, false)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void totalEhCalculadoNoServidorEJuntaItensRepetidos() {
        Pedido pedido = service.registrar(requisicao(
                new ItemPedidoRequest("calabresa", 1),
                new ItemPedidoRequest("refri", 1),
                new ItemPedidoRequest("calabresa", 1)));

        assertEquals(2, pedido.getItens().size());
        assertEquals(new BigDecimal("88.00"), pedido.getValorTotal());
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals("gerente01", pedido.getRegistradoPor());
        assertEquals(AGORA, pedido.getEntradaEm());
        verify(auditoria).registrar(eq(TipoOperacao.CRIACAO_PEDIDO), eq("Pedido"), any(), anyMap(), anyMap(), isNull());
    }

    @Test
    void naoAceitaProdutoInativoNemPedidoVazio() {
        assertThrows(RegraNegocioException.class,
                () -> service.registrar(requisicao(new ItemPedidoRequest("velho", 1))));
        assertThrows(RegraNegocioException.class,
                () -> service.registrar(requisicao(new ItemPedidoRequest("calabresa", 0))));
    }

    @Test
    void descontoAcumuladoNaoPassaDeVintePorCento() {
        Pedido pedido = pedidoSalvo();

        service.aplicarDesconto("p1", new BigDecimal("15"), "Atraso na entrega");
        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto("p1", new BigDecimal("10"), "Cortesia extra"));
        service.aplicarDesconto("p1", new BigDecimal("5"), "Cliente fiel");

        assertEquals(0, new BigDecimal("20").compareTo(pedido.percentualDescontoAcumulado()));
        // 20% de 88,00 = 17,60
        assertEquals(new BigDecimal("70.40"), pedido.getValorTotal());
    }

    @Test
    void descontoExigeMotivoEValorPositivo() {
        pedidoSalvo();
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("5"), ""));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("-5"), "Motivo ok"));
    }

    @Test
    void descontoNaoPodeDepoisDaSaida() {
        pedidoSalvo();
        service.avancar("p1", StatusPedido.EM_PREPARACAO, null);
        service.avancar("p1", StatusPedido.PRONTO, null);
        service.avancar("p1", StatusPedido.DESPACHADO, ModalidadeSaida.ENTREGA);

        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto("p1", new BigDecimal("5"), "Tarde demais"));
    }

    @Test
    void saidaRegistraHorarioEResponsavel() {
        Pedido pedido = pedidoSalvo();
        service.avancar("p1", StatusPedido.EM_PREPARACAO, null);
        service.avancar("p1", StatusPedido.PRONTO, null);
        service.avancar("p1", StatusPedido.DESPACHADO, ModalidadeSaida.RETIRADA);

        assertEquals(StatusPedido.DESPACHADO, pedido.getStatus());
        assertEquals(ModalidadeSaida.RETIRADA, pedido.getModalidadeSaida());
        assertEquals(AGORA, pedido.getSaidaEm());
        assertEquals("gerente01", pedido.getSaidaRegistradaPor());
    }

    @Test
    void saidaSemModalidadeEhRecusada() {
        pedidoSalvo();
        service.avancar("p1", StatusPedido.EM_PREPARACAO, null);
        service.avancar("p1", StatusPedido.PRONTO, null);
        assertThrows(RegraNegocioException.class, () -> service.avancar("p1", StatusPedido.DESPACHADO, null));
    }

    @Test
    void cancelamentoExigeJustificativaETravaOPedido() {
        Pedido pedido = pedidoSalvo();

        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "  "));
        service.cancelar("p1", "Cliente desistiu");

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertEquals("Cliente desistiu", pedido.getMotivoCancelamento());
        // os itens e valores continuam lá (histórico preservado)
        assertEquals(new BigDecimal("88.00"), pedido.getValorTotal());

        assertThrows(RegraNegocioException.class, () -> service.avancar("p1", StatusPedido.EM_PREPARACAO, null));
        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto("p1", new BigDecimal("5"), "Depois de cancelado"));
        verify(auditoria).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("Pedido"), any(), anyMap(), anyMap(),
                eq("Cliente desistiu"));
    }

    @Test
    void cancelamentoNaoPassaPelaRotaDeStatusComum() {
        pedidoSalvo();
        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> service.avancar("p1", StatusPedido.CANCELADO, null));
        assertTrue(erro.getMessage().contains("gerente"));
    }

    // Monta um pedido de 88,00 (2 calabresas + 1 refri) e faz o repositório devolvê-lo pelo id "p1".
    private Pedido pedidoSalvo() {
        Pedido pedido = service.registrar(requisicao(
                new ItemPedidoRequest("calabresa", 2),
                new ItemPedidoRequest("refri", 1)));
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private static PedidoRequest requisicao(ItemPedidoRequest... itens) {
        PedidoRequest req = new PedidoRequest();
        req.setClienteId("c1");
        req.setItens(List.of(itens));
        return req;
    }

    private static Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(ativo);
        return p;
    }
}

package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.DescontoAplicado;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.ModalidadeSaida;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio dos pedidos.
 *
 * Cada método público tem um @PreAuthorize dizendo quem pode chamá-lo.
 * Isso é uma segunda barreira: mesmo que alguém esqueça de proteger uma rota
 * no SecurityConfig, o service recusa quem não tem o perfil certo.
 */
@Service
public class PedidoService {

    /** O desconto somado de um pedido nunca passa de 20% (regra do enunciado, RF04). */
    public static final BigDecimal LIMITE_DESCONTO_PERCENTUAL = new BigDecimal("20");

    private static final int QUANTIDADE_MAXIMA_POR_ITEM = 50;
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 5;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoria;
    private final UsuarioLogado usuarioLogado;
    private final Clock relogio;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoria,
            UsuarioLogado usuarioLogado, Clock relogio) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoria = auditoria;
        this.usuarioLogado = usuarioLogado;
        this.relogio = relogio;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "entradaEm"));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscar(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido", id));
    }

    /** RF02 + RF03: registra o pedido com itens, valores calculados aqui, entrada e responsável. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido registrar(PedidoRequest dados) {
        if (dados == null || dados.getClienteId() == null || dados.getClienteId().isBlank()) {
            throw new RegraNegocioException("Selecione o cliente do pedido.");
        }
        Cliente cliente = clienteRepository.findById(dados.getClienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", dados.getClienteId()));

        List<ItemPedido> itens = montarItens(dados.getItens());
        String observacao = textoOpcional(dados.getObservacao(), 200);

        Pedido pedido = Pedido.novo(cliente, itens, observacao, usuarioLogado.nome(), agora());
        Pedido salvo = pedidoRepository.save(pedido);

        auditoria.registrar(TipoOperacao.CRIACAO_PEDIDO, "Pedido", salvo.getId(),
                Map.of(), salvo.retratoParaAuditoria(), null);
        return salvo;
    }

    /**
     * RF03: avança o pedido no fluxo (preparo, pronto, saída, finalização).
     * Cancelamento não passa por aqui: tem método próprio, só do gerente.
     */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido avancar(String id, StatusPedido destino, ModalidadeSaida modalidade) {
        if (destino == null) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        if (destino == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar use a opção de cancelamento, que é exclusiva do gerente.");
        }
        if (destino == StatusPedido.RECEBIDO) {
            throw new RegraNegocioException("Um pedido não pode voltar para Recebido.");
        }

        Pedido pedido = buscarParaAlterar(id);
        Map<String, String> antes = pedido.retratoParaAuditoria();
        String quem = usuarioLogado.nome();
        Instant agora = agora();

        switch (destino) {
            case DESPACHADO -> pedido.registrarSaida(modalidade, quem, agora);
            case FINALIZADO -> pedido.finalizar(quem, agora);
            default -> pedido.mudarStatus(destino, quem, agora, null);
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoria.registrar(TipoOperacao.MUDANCA_STATUS_PEDIDO, "Pedido", salvo.getId(),
                antes, salvo.retratoParaAuditoria(), null);
        return salvo;
    }

    /** RF04: cancelamento, só gerente, com justificativa e antes da saída. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String motivo) {
        String justificativa = justificativaObrigatoria(motivo, "o cancelamento");
        Pedido pedido = buscarParaAlterar(id);
        Map<String, String> antes = pedido.retratoParaAuditoria();

        pedido.cancelar(justificativa, usuarioLogado.nome(), agora());

        Pedido salvo = pedidoRepository.save(pedido);
        auditoria.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, "Pedido", salvo.getId(),
                antes, salvo.retratoParaAuditoria(), justificativa);
        return salvo;
    }

    /**
     * RF04: desconto percentual, só gerente, antes da saída.
     * Pode haver mais de um desconto no mesmo pedido, mas a soma não passa de 20%.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String motivo) {
        String justificativa = justificativaObrigatoria(motivo, "o desconto");
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O percentual de desconto precisa ser maior que zero.");
        }
        BigDecimal percentualLimpo = percentual.stripTrailingZeros();
        if (percentualLimpo.scale() > 2) {
            throw new RegraNegocioException("Use no máximo duas casas decimais no percentual.");
        }

        Pedido pedido = buscarParaAlterar(id);
        if (!pedido.aceitaDesconto()) {
            throw new RegraNegocioException("Desconto só pode ser aplicado antes da saída do pedido e em pedidos não cancelados.");
        }

        BigDecimal jaConcedido = pedido.percentualDescontoAcumulado();
        BigDecimal totalComNovo = jaConcedido.add(percentualLimpo);
        if (totalComNovo.compareTo(LIMITE_DESCONTO_PERCENTUAL) > 0) {
            BigDecimal disponivel = LIMITE_DESCONTO_PERCENTUAL.subtract(jaConcedido);
            throw new RegraNegocioException("O desconto acumulado ficaria em " + totalComNovo.toPlainString()
                    + "%, acima do limite de " + LIMITE_DESCONTO_PERCENTUAL.toPlainString() + "%. "
                    + "Ainda é possível conceder até " + disponivel.toPlainString() + "%.");
        }

        Map<String, String> antes = pedido.retratoParaAuditoria();
        BigDecimal valor = pedido.getSubtotal()
                .multiply(percentualLimpo)
                .divide(CEM, 2, RoundingMode.HALF_EVEN);

        pedido.adicionarDesconto(new DescontoAplicado(percentualLimpo, valor, justificativa,
                usuarioLogado.nome(), agora()));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoria.registrar(TipoOperacao.DESCONTO_PEDIDO, "Pedido", salvo.getId(),
                antes, salvo.retratoParaAuditoria(), justificativa);
        return salvo;
    }

    /**
     * Transforma o que veio da tela em itens de verdade:
     * busca cada produto no banco, usa o preço do cadastro e junta produtos repetidos.
     * Linhas com quantidade vazia ou zero são ignoradas (é o caso do formulário,
     * que mostra todos os produtos e o atendente preenche só o que o cliente pediu).
     */
    private List<ItemPedido> montarItens(List<ItemPedidoRequest> solicitados) {
        Map<String, Integer> quantidadePorProduto = new LinkedHashMap<>();
        if (solicitados != null) {
            for (ItemPedidoRequest item : solicitados) {
                if (item == null || item.getQuantidade() == null || item.getQuantidade() == 0) {
                    continue;
                }
                if (item.getQuantidade() < 0) {
                    throw new RegraNegocioException("Quantidade não pode ser negativa.");
                }
                if (item.getProdutoId() == null || item.getProdutoId().isBlank()) {
                    throw new RegraNegocioException("Item sem produto informado.");
                }
                quantidadePorProduto.merge(item.getProdutoId(), item.getQuantidade(), Integer::sum);
            }
        }
        if (quantidadePorProduto.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa de pelo menos um item.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        quantidadePorProduto.forEach((produtoId, quantidade) -> {
            if (quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("Quantidade máxima por item é " + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", produtoId));
            if (!produto.disponivelParaVenda()) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " está sem preço válido.");
            }
            itens.add(ItemPedido.de(produto, quantidade));
        });
        return itens;
    }

    private Pedido buscarParaAlterar(String id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido", id));
        if (pedido.modeloAntigo()) {
            throw new RegraNegocioException("Este pedido é do modelo antigo (sem itens) e está disponível apenas para consulta.");
        }
        return pedido;
    }

    private String justificativaObrigatoria(String texto, String paraQue) {
        if (texto == null || texto.trim().length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("Informe uma justificativa para " + paraQue
                    + " (mínimo de " + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres).");
        }
        return textoOpcional(texto, 300);
    }

    private static String textoOpcional(String texto, int tamanhoMaximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.trim();
        if (limpo.length() > tamanhoMaximo) {
            throw new RegraNegocioException("Texto muito longo (máximo de " + tamanhoMaximo + " caracteres).");
        }
        return limpo;
    }

    private Instant agora() {
        return Instant.now(relogio).truncatedTo(ChronoUnit.MILLIS);
    }
}

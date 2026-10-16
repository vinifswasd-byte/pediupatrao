package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cardápio da pizzaria.
 * Todos os perfis consultam (o atendente precisa para montar o pedido),
 * mas preço e produto são informação comercial: só o gerente altera.
 */
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoria;

    public ProdutoService(ProdutoRepository produtoRepository, AuditoriaService auditoria) {
        this.produtoRepository = produtoRepository;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Produto> listarDisponiveis() {
        return produtoRepository.findAll().stream()
                .filter(Produto::disponivelParaVenda)
                .toList();
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Produto cadastrar(Produto dados) {
        dados.setId(null);
        validar(dados);
        Produto salvo = produtoRepository.save(dados);
        auditoria.registrar(TipoOperacao.CRIACAO_PRODUTO, "Produto", salvo.getId(),
                Map.of(), salvo.retratoParaAuditoria(), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Produto atualizar(String id, Produto dados) {
        Produto existente = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", id));
        Map<String, String> antes = existente.retratoParaAuditoria();

        existente.setNome(dados.getNome());
        existente.setTipo(dados.getTipo());
        existente.setPreco(dados.getPreco());
        existente.setAtivo(dados.getAtivo());
        validar(existente);

        Produto salvo = produtoRepository.save(existente);
        auditoria.registrar(TipoOperacao.ALTERACAO_PRODUTO, "Produto", salvo.getId(),
                antes, salvo.retratoParaAuditoria(), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Produto existente = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", id));
        produtoRepository.deleteById(id);
        auditoria.registrar(TipoOperacao.EXCLUSAO_PRODUTO, "Produto", id,
                existente.retratoParaAuditoria(), Map.of(), null);
    }

    private void validar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("O nome do produto é obrigatório.");
        }
        if (produto.getPreco() == null || produto.getPreco() <= 0) {
            throw new RegraNegocioException("O preço do produto precisa ser maior que zero.");
        }
        if (produto.getAtivo() == null) {
            produto.setAtivo(Boolean.FALSE);
        }
    }
}

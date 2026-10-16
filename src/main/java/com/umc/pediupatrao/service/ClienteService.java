package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cadastro de clientes.
 * Atendente cadastra e altera, gerente consulta (e é quem pode excluir).
 * Toda inclusão, alteração e exclusão vai para a auditoria (RF05).
 */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoria) {
        this.clienteRepository = clienteRepository;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente cadastrar(Cliente dados) {
        Cliente novo = new Cliente();
        novo.copiarDadosEditaveis(dados);
        validar(novo);

        Cliente salvo = clienteRepository.save(novo);
        auditoria.registrar(TipoOperacao.CRIACAO_CLIENTE, "Cliente", salvo.getId(),
                Map.of(), salvo.retratoParaAuditoria(), null);
        return salvo;
    }

    /**
     * Altera o cliente e registra campo a campo o que mudou
     * (por exemplo: logradouro "Rua das Flores" -> "Av. Brasil").
     */
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, Cliente dados) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));

        Map<String, String> antes = existente.retratoParaAuditoria();
        existente.copiarDadosEditaveis(dados);
        validar(existente);

        Cliente salvo = clienteRepository.save(existente);
        auditoria.registrar(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", salvo.getId(),
                antes, salvo.retratoParaAuditoria(), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));
        clienteRepository.deleteById(id);
        // Guardamos a última foto do cliente para saber o que foi apagado.
        auditoria.registrar(TipoOperacao.EXCLUSAO_CLIENTE, "Cliente", id,
                existente.retratoParaAuditoria(), Map.of(), null);
    }

    private void validar(Cliente cliente) {
        if (cliente.getNome() == null) {
            throw new RegraNegocioException("O nome do cliente é obrigatório.");
        }
        if (cliente.getTelefone() == null) {
            throw new RegraNegocioException("O telefone do cliente é obrigatório.");
        }
        String digitos = cliente.getTelefone().replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 11) {
            throw new RegraNegocioException("Telefone inválido: use DDD + número (10 ou 11 dígitos).");
        }
    }
}

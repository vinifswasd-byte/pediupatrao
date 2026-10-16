package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.OperacaoNaoPermitidaException;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administração de usuários e perfis (RF01). Tudo aqui é exclusivo do ADMIN:
 * o @PreAuthorize em cima da classe vale para todos os métodos.
 *
 * Proteções contra ganhar privilégio indevido:
 * - ninguém altera o próprio perfil (nem o admin);
 * - ninguém se exclui;
 * - o último ADMIN não pode ser rebaixado nem excluído;
 * - só os perfis ADMIN, GERENTE e ATENDENTE são aceitos.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final UsuarioLogado usuarioLogado;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoria, UsuarioLogado usuarioLogado) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.usuarioLogado = usuarioLogado;
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Usuario criar(Usuario dados) {
        String username = usernameValido(dados.getUsername());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com o login " + username + ".");
        }
        Perfil perfil = perfilValido(dados.getRole());
        validarSenha(dados.getPassword());

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setRole(perfil.name());
        novo.setPassword(passwordEncoder.encode(dados.getPassword()));

        Usuario salvo = usuarioRepository.save(novo);
        auditoria.registrar(TipoOperacao.CRIACAO_USUARIO, "Usuario", salvo.getId(),
                Map.of(), salvo.retratoParaAuditoria(), null);
        return salvo;
    }

    public Usuario atualizar(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
        Map<String, String> antes = existente.retratoParaAuditoria();

        boolean ehEleMesmo = existente.getUsername().equals(usuarioLogado.nome());
        Perfil perfilAtual = Perfil.de(existente.getRole());
        Perfil perfilNovo = perfilValido(dados.getRole());

        if (ehEleMesmo && perfilNovo != perfilAtual) {
            throw new OperacaoNaoPermitidaException("Você não pode alterar o seu próprio perfil.");
        }
        if (perfilAtual == Perfil.ADMIN && perfilNovo != Perfil.ADMIN && ehUltimoAdmin()) {
            throw new OperacaoNaoPermitidaException("Este é o único ADMIN do sistema e não pode ser rebaixado.");
        }

        String username = usernameValido(dados.getUsername());
        usuarioRepository.findByUsername(username)
                .filter(outro -> !outro.getId().equals(existente.getId()))
                .ifPresent(outro -> {
                    throw new RegraNegocioException("Já existe um usuário com o login " + username + ".");
                });
        if (ehEleMesmo && !username.equals(existente.getUsername())) {
            throw new OperacaoNaoPermitidaException("Para trocar o seu próprio login, peça a outro administrador.");
        }

        existente.setUsername(username);
        existente.setRole(perfilNovo.name());

        // Senha em branco = manter a atual.
        boolean trocouSenha = dados.getPassword() != null && !dados.getPassword().isBlank();
        if (trocouSenha) {
            validarSenha(dados.getPassword());
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
        }

        Usuario salvo = usuarioRepository.save(existente);
        // A senha não entra na foto. Só avisamos na justificativa que ela foi redefinida.
        auditoria.registrar(TipoOperacao.ALTERACAO_USUARIO, "Usuario", salvo.getId(),
                antes, salvo.retratoParaAuditoria(), trocouSenha ? "Senha redefinida" : null);
        return salvo;
    }

    public void excluir(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
        if (existente.getUsername().equals(usuarioLogado.nome())) {
            throw new OperacaoNaoPermitidaException("Você não pode excluir o seu próprio usuário.");
        }
        if (Perfil.de(existente.getRole()) == Perfil.ADMIN && ehUltimoAdmin()) {
            throw new OperacaoNaoPermitidaException("Este é o único ADMIN do sistema e não pode ser excluído.");
        }
        usuarioRepository.deleteById(id);
        auditoria.registrar(TipoOperacao.EXCLUSAO_USUARIO, "Usuario", id,
                existente.retratoParaAuditoria(), Map.of(), null);
    }

    private boolean ehUltimoAdmin() {
        return usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1;
    }

    private static String usernameValido(String username) {
        if (username == null || username.isBlank()) {
            throw new RegraNegocioException("Informe o login do usuário.");
        }
        String limpo = username.trim();
        if (!limpo.matches("[A-Za-z0-9._-]{3,30}")) {
            throw new RegraNegocioException("Login deve ter de 3 a 30 caracteres: letras, números, ponto, hífen ou _.");
        }
        return limpo;
    }

    private static Perfil perfilValido(String valor) {
        Perfil perfil = Perfil.de(valor);
        if (perfil == null) {
            throw new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        return perfil;
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("A senha precisa ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}

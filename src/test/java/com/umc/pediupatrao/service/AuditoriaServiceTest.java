package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ResultadoVerificacao;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditoriaServiceTest {

    private final List<RegistroAuditoria> gravados = new ArrayList<>();
    private AuditoriaService service;

    @BeforeEach
    void preparar() {
        // Um "banco" em memória: o mock guarda o que for inserido numa lista.
        AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
        when(repositorio.inserir(any())).thenAnswer(chamada -> {
            RegistroAuditoria r = chamada.getArgument(0);
            gravados.add(r);
            return r;
        });
        when(repositorio.buscarUltimo()).thenAnswer(chamada ->
                gravados.isEmpty() ? Optional.empty() : Optional.of(gravados.get(gravados.size() - 1)));

        UsuarioLogado usuario = mock(UsuarioLogado.class);
        when(usuario.nome()).thenReturn("atendente01");
        when(usuario.perfil()).thenReturn("ATENDENTE");

        service = new AuditoriaService(repositorio, usuario,
                Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneOffset.UTC));
    }

    @Test
    void registraSoOsCamposQueMudaram() {
        Map<String, String> antes = foto("Maria", "Rua das Flores");
        Map<String, String> depois = foto("Maria", "Av. Brasil");

        RegistroAuditoria r = service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", "c1", antes, depois, null);

        assertEquals(List.of(new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil")), r.alteracoes());
        assertEquals("atendente01", r.usuario());
        assertEquals("ATENDENTE", r.perfil());
        assertEquals(1, r.sequencia());
    }

    @Test
    void edicaoSemMudancaNaoGeraRegistro() {
        RegistroAuditoria r = service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", "c1",
                foto("Maria", "Rua A"), foto("Maria", "Rua A"), null);
        assertNull(r);
        assertTrue(gravados.isEmpty());
    }

    @Test
    void registrosFormamUmaCorrenteDeHash() {
        RegistroAuditoria primeiro = service.registrar(TipoOperacao.CRIACAO_CLIENTE, "Cliente", "c1",
                Map.of(), foto("Maria", "Rua A"), null);
        RegistroAuditoria segundo = service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", "c1",
                foto("Maria", "Rua A"), foto("Maria", "Rua B"), null);

        assertEquals(AuditoriaService.HASH_INICIAL, primeiro.hashAnterior());
        assertEquals(primeiro.hash(), segundo.hashAnterior());
        assertTrue(AuditoriaService.verificarCorrente(gravados).integra());
    }

    @Test
    void verificacaoDetectaRegistroAdulterado() {
        service.registrar(TipoOperacao.CRIACAO_CLIENTE, "Cliente", "c1", Map.of(), foto("Maria", "Rua A"), null);
        service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", "c1",
                foto("Maria", "Rua A"), foto("Maria", "Rua B"), null);

        // Simula alguém editando o primeiro registro direto no banco
        RegistroAuditoria original = gravados.get(0);
        RegistroAuditoria adulterado = new RegistroAuditoria(original.id(), original.sequencia(), original.ocorridoEm(),
                "outra_pessoa", original.perfil(), original.operacao(), original.entidade(), original.entidadeId(),
                original.alteracoes(), original.justificativa(), original.hashAnterior(), original.hash());
        gravados.set(0, adulterado);

        ResultadoVerificacao resultado = AuditoriaService.verificarCorrente(gravados);
        assertFalse(resultado.integra());
        assertEquals(1L, resultado.sequenciaComProblema());
    }

    @Test
    void verificacaoDetectaRegistroApagado() {
        service.registrar(TipoOperacao.CRIACAO_CLIENTE, "Cliente", "c1", Map.of(), foto("A", "Rua 1"), null);
        service.registrar(TipoOperacao.CRIACAO_CLIENTE, "Cliente", "c2", Map.of(), foto("B", "Rua 2"), null);
        service.registrar(TipoOperacao.CRIACAO_CLIENTE, "Cliente", "c3", Map.of(), foto("C", "Rua 3"), null);

        gravados.remove(1);
        assertFalse(AuditoriaService.verificarCorrente(gravados).integra());
    }

    @Test
    void telefoneApareceMascarado() {
        assertEquals("(11) *****-4321", AuditoriaService.mascararTelefone("(11) 98765-4321"));
        assertNull(AuditoriaService.mascararTelefone(null));
    }

    private static Map<String, String> foto(String nome, String logradouro) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nome", nome);
        m.put("logradouro", logradouro);
        return m;
    }
}

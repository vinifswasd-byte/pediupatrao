package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.TipoOperacao;
import java.time.Instant;
import java.util.List;

/**
 * O que a tela e a API de auditoria devolvem.
 * É uma cópia do registro já com os dados pessoais mascarados
 * e só o começo do hash, que é o suficiente para conferência visual.
 */
public record EventoAuditoriaDTO(
        long sequencia,
        Instant ocorridoEm,
        String usuario,
        String perfil,
        TipoOperacao operacao,
        String operacaoDescricao,
        String entidade,
        String entidadeId,
        List<AlteracaoCampo> alteracoes,
        String justificativa,
        String hashResumido) {
}

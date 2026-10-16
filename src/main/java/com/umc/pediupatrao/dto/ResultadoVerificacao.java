package com.umc.pediupatrao.dto;

/**
 * Resposta da verificação da corrente de hashes da auditoria.
 * Se "integra" for false, "sequenciaComProblema" diz qual registro foi adulterado.
 */
public record ResultadoVerificacao(
        boolean integra,
        int registrosVerificados,
        Long sequenciaComProblema,
        String mensagem) {
}

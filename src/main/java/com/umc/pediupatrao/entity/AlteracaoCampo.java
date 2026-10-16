package com.umc.pediupatrao.entity;

/**
 * Um campo que mudou: qual era o valor e qual ficou.
 * Ex.: logradouro, "Rua das Flores" -> "Av. Brasil".
 */
public record AlteracaoCampo(String campo, String valorAnterior, String valorNovo) {
}

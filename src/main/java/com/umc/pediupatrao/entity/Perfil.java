package com.umc.pediupatrao.entity;

/**
 * Os três perfis de acesso da pizzaria.
 * ADMIN cuida dos usuários, GERENTE cuida da operação e ATENDENTE do balcão.
 */
public enum Perfil {
    ADMIN,
    GERENTE,
    ATENDENTE;

    /**
     * Converte o texto gravado no banco para o enum.
     * Perfis antigos ou inválidos (como o USER da versão anterior) viram null,
     * e quem estiver com perfil null consegue logar mas não acessa nada.
     */
    public static Perfil de(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Perfil.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

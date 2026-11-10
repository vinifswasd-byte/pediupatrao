package com.umc.pediupatrao.exception;

/**
 * Filtro do dashboard com valor que não faz sentido,
 * como data inicial depois da final ou período grande demais.
 * Vira um 400 (Bad Request) na API.
 */
public class FiltroInvalidoException extends RuntimeException {

    public FiltroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
